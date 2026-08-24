package ru.white.module.impl.utils;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventRender3D;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.white.Client;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.other.Instance;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        name = "Report Helper",
        desc = "Очередь репортов и подсветка игроков",
        category = Category.OTHER
)
public class ReportHelper extends Module {
    private static final Pattern NICK_PATTERN = Pattern.compile("(?i)\\b[A-Za-z0-9_]{3,16}\\b");
    private static final Pattern REPORT_TITLE_PATTERN = Pattern.compile("(?iu)(?:жалоба\\s+на|репорт\\s+на|report\\s+(?:on|for)|complaint\\s+(?:on|for))\\s+([A-Za-z0-9_]{3,16})");
    private static final Pattern WAIT_SECONDS_PATTERN = Pattern.compile("(?iu)подождать:?\\s*(\\d+)\\s*сек");
    private static final long AUTO_LIST_INTERVAL_MS = 60_000L;
    private static final long AUTO_LIST_PAGE_TIMEOUT_MS = 2_500L;
    private static final int MAX_AUTO_LIST_PAGES = 10;

    public final BindSetting reportBind = new BindSetting(this, "Бинд репорта");
    public final ColorSetting reportColor = new ColorSetting(this, "Цвет подсветки", 0xFFFF5555);
    public final SliderSetting delaySeconds = new SliderSetting(this, "Задержка репорта", 31, 31, 90, 1);
    public final BooleanSetting hud = new BooleanSetting(this, "Худ", true);
    public final BooleanSetting autoCheckList = new BooleanSetting(this, "Проверять список", false);

    private final Deque<String> reportQueue = new ArrayDeque<>();
    private final Set<String> queuedNames = new LinkedHashSet<>();
    private final Set<String> reportedNames = new LinkedHashSet<>();
    private final Set<String> listNames = new LinkedHashSet<>();

    private String lastAttackedName;
    private String waitingName;
    private long waitingSince;
    private long lastListScan;
    private long nextReportAllowedAt;
    private long lastAutoListRequest;
    private long autoListRequestedAt;
    private boolean autoListScreen;
    private int autoListPagesClicked;
    private long autoListPageClickedAt;
    private String autoListLastPageSignature = "";
    private String lastFailedName;

    public static ReportHelper get() {
        return Instance.get(ReportHelper.class);
    }

    public static boolean isAutoListActive() {
        try {
            ReportHelper helper = get();
            return helper != null && helper.isEnabled() && helper.autoListScreen;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isReported(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }
        return isReportedName(player.getName().getString());
    }

    public static boolean isReportedName(String name) {
        try {
            ReportHelper helper = get();
            return helper != null && helper.isEnabled() && helper.shouldHighlightName(name);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int getColorForEntity(Entity entity, int fallback) {
        return isReported(entity) ? getReportColor(fallback) : fallback;
    }

    public static int getReportColor(int fallback) {
        try {
            ReportHelper helper = get();
            return helper != null && helper.isEnabled() ? helper.reportColor.getValue() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static int getReportColorRgb(int fallback) {
        return getReportColor(fallback) & 0xFFFFFF;
    }

    public static boolean hasHighlightedTargets() {
        try {
            ReportHelper helper = get();
            return helper != null && helper.isEnabled() && helper.getAllHighlightedCount() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldHighlight(Entity entity) {
        try {
            ReportHelper helper = get();
            return helper != null && helper.isEnabled() && helper.shouldHighlightEntity(entity);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int getShaderColor(int fallback, Collection<Entity> targets) {
        return hasHighlightedTargets() ? getReportColor(fallback) : fallback;
    }

    @Override
    protected void onDisable() {
        reportQueue.clear();
        queuedNames.clear();
        waitingName = null;
        autoListScreen = false;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        autoListScreen = false;
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof PlayerEntity player && player != mc.player) {
            lastAttackedName = player.getName().getString();
        }
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        if (reportBind.getValue() != -1 && event.getKey() == reportBind.getValue()) {
            queueLastAttacked();
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        requestReportListIfNeeded();
        scanReportListScreen();
        processQueue();
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof GameMessageS2CPacket message) {
            handleServerText(message.content().getString());
        }
    }

    @EventHandler(priority = -400)
    public void onDisplay(EventDisplay event) {
        if (!hud.getValue() || mc.player == null || mc.options.hudHidden) {
            return;
        }

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;
        float screenWidth = (float) (mc.getWindow().getScaledWidth() / scaleFix);

        String title = "Репорт Хелпер";
        String target = lastAttackedName == null ? "Цель: Нет" : "Цель: " + lastAttackedName;
        String queue = "Очередь: " + reportQueue.size();
        String wait = waitingName == null ? cooldownText() : "Отправлен: " + waitingName;
        if (lastFailedName != null && waitingName == null && reportQueue.isEmpty()) {
            wait = "Ошибка: " + lastFailedName;
        }
        String marked = "Помечено: " + getAllHighlightedCount();

        float width = Math.max(118, Math.max(Fonts.sf_medium.getWidth(target, 5.5F), Fonts.sf_medium.getWidth(wait, 5.5F)) + 16);
        float x = screenWidth - width - 8;
        float y = 42;
        float height = 55;

        RenderUtil.Blur.blur(x, y, width, height, 1, 5, ColorUtil.getRectColor(1F));
        //RenderUtil.Render2D.outline(x, y, width, height, 0.5F, ColorUtil.multAlpha(reportColor.getValue(), 0.8F), 5);
        Fonts.sf_medium.draw(title, x + 6, y + 5, 6.5F, reportColor.getValue());
        Fonts.sf_medium.draw(target, x + 6, y + 18, 5.5F, ColorUtil.WHITE);
        Fonts.sf_medium.draw(queue + " | " + marked, x + 6, y + 29, 5.5F, ColorUtil.WHITE);
        Fonts.sf_medium.draw(wait, x + 6, y + 40, 5.5F, ColorUtil.WHITE);
    }

    // ── 3D-кристалл над зарепорченными игроками ──────────────────────────────

    private static final RenderPipeline CRYSTAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "report_crystal"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer CRYSTAL_LAYER = RenderLayer.of(
            "report_crystal",
            RenderSetup.builder(CRYSTAL_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    private final BufferAllocator crystalAllocator = new BufferAllocator(1 << 14);

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack stack = e.getMatrixStack();
        Matrix4f mat = stack.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(crystalAllocator);

        int color = reportColor.getValue();
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        long time = System.currentTimeMillis();
        boolean any = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !shouldHighlightName(player.getName().getString())) {
                continue;
            }
            if (mc.player.squaredDistanceTo(player) > 128 * 128) {
                continue;
            }

            Vec3d pos = player.getLerpedPos(tickDelta);

            // у каждого игрока своя фаза, чтобы кристаллы не крутились синхронно
            long phase = (player.getName().getString().hashCode() & 0xFFFF) * 7L;

            float spin = ((time + phase) % 4000L) / 4000F * (float) (Math.PI * 2);
            double bob = Math.sin((time + phase) / 900.0) * 0.1;

            double cx = pos.x;
            double cy = pos.y + player.getHeight() + 0.85 + bob;
            double cz = pos.z;

            drawCrystal(immediate.getBuffer(CRYSTAL_LAYER), mat, cam, cx, cy, cz, spin, cr, cg, cb);
            any = true;
        }

        if (any) {
            immediate.draw();
        }
    }

    private void drawCrystal(VertexConsumer vc, Matrix4f mat, Vec3d cam,
                             double cx, double cy, double cz, float spin,
                             int cr, int cg, int cb) {
        float r = 0.18F;  // радиус "экватора"
        float h = 0.30F;  // половина высоты

        float x = (float) (cx - cam.x);
        float y = (float) (cy - cam.y);
        float z = (float) (cz - cam.z);

        // 4 точки экватора, повёрнутые на spin
        float[][] eq = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float a = spin + i * ((float) Math.PI / 2F);
            eq[i][0] = x + (float) Math.cos(a) * r;
            eq[i][1] = z + (float) Math.sin(a) * r;
        }

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            // соседние грани разной яркости — так виден объём при вращении
            float shadeTop = i % 2 == 0 ? 1.0F : 0.78F;
            float shadeBot = i % 2 == 0 ? 0.68F : 0.52F;

            // верхняя грань
            tri(vc, mat,
                    x, y + h, z,
                    eq[i][0], y, eq[i][1],
                    eq[j][0], y, eq[j][1],
                    (int) (cr * shadeTop), (int) (cg * shadeTop), (int) (cb * shadeTop), 210);
            // нижняя грань
            tri(vc, mat,
                    x, y - h, z,
                    eq[j][0], y, eq[j][1],
                    eq[i][0], y, eq[i][1],
                    (int) (cr * shadeBot), (int) (cg * shadeBot), (int) (cb * shadeBot), 210);
        }
    }

    private void tri(VertexConsumer vc, Matrix4f mat,
                     float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     int r, int g, int b, int a) {
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a);
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a);
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a);
    }

    private void queueLastAttacked() {
        if (lastAttackedName == null || lastAttackedName.isBlank()) {
            ChatUtils.addChatMessage("Репорт Хелпер: Сначала ударь игрока.");
            return;
        }
        queueReport(lastAttackedName);
    }

    private void queueReport(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty() || isSelf(normalized) || hasReportedName(normalized) || queuedNames.contains(normalized)) {
            return;
        }

        reportQueue.addLast(name);
        queuedNames.add(normalized);
        lastFailedName = null;
        ChatUtils.addChatMessage("Репорт Хелпер: Добавлен в очередь " + name);
    }

    private void processQueue() {
        if (mc.player == null || mc.player.networkHandler == null || reportQueue.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (waitingName != null) {
            if (now - waitingSince > 12_000L) {
                queueFirst(waitingName);
                waitingName = null;
            } else {
                return;
            }
        }

        if (now < nextReportAllowedAt) {
            return;
        }

        String name = reportQueue.pollFirst();
        if (name == null) {
            return;
        }
        queuedNames.remove(normalizeName(name));
        if (hasReportedName(name) || isSelf(name)) {
            return;
        }

        sendCommand("report " + name + " чит");
        waitingName = name;
        waitingSince = now;
    }

    private void queueFirst(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty() || isSelf(normalized) || hasReportedName(normalized) || queuedNames.contains(normalized)) {
            return;
        }
        reportQueue.addFirst(name);
        queuedNames.add(normalized);
    }

    private void requestReportListIfNeeded() {
        if (!autoCheckList.getValue() || mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        if (mc.currentScreen != null || waitingName != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (autoListScreen && now - autoListRequestedAt > 5_000L) {
            autoListScreen = false;
        }
        if (now - lastAutoListRequest < AUTO_LIST_INTERVAL_MS) {
            return;
        }

        lastAutoListRequest = now;
        autoListRequestedAt = now;
        autoListScreen = true;
        autoListPagesClicked = 0;
        autoListPageClickedAt = 0;
        autoListLastPageSignature = "";
        sendCommand("report list");
    }

    private void scanReportListScreen() {
        if (mc.currentScreen == null || mc.player == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GenericContainerScreen) || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastListScan < 500L) {
            return;
        }
        lastListScan = now;

        boolean foundAny = false;
        int nextSlot = -1;
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < Math.min(containerSlots, handler.slots.size()); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }

            String name = extractReportName(stack);
            if (name != null) {
                foundAny = true;
                listNames.add(normalizeName(name));
                reportedNames.add(normalizeName(name));
            }
            if (nextSlot == -1 && isNextPageStack(stack)) {
                nextSlot = i;
            }
        }

        if (foundAny && waitingName != null && hasReportedName(waitingName)) {
            waitingName = null;
        }

        if (autoListScreen) {
            boolean reportScreen = isReportListScreen() || foundAny || nextSlot != -1;
            if (!reportScreen) {
                autoListScreen = false;
                return;
            }

            String signature = pageSignature(handler, containerSlots);
            if (signature.equals(autoListLastPageSignature)) {
                // страница ещё не обновилась после клика "вперёд" — ждём, но не вечно
                if (now - autoListPageClickedAt > AUTO_LIST_PAGE_TIMEOUT_MS) {
                    closeAutoList(handler);
                }
                return;
            }

            if (nextSlot != -1 && autoListPagesClicked < MAX_AUTO_LIST_PAGES && mc.interactionManager != null) {
                autoListLastPageSignature = signature;
                autoListPagesClicked++;
                autoListPageClickedAt = now;
                autoListRequestedAt = now;
                mc.interactionManager.clickSlot(handler.syncId, nextSlot, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
            closeAutoList(handler);
        }
    }

    private String pageSignature(GenericContainerScreenHandler handler, int containerSlots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(containerSlots, handler.slots.size()); i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty()) {
                sb.append(i).append(':').append(stack.getName().getString()).append(';');
            }
        }
        return sb.toString();
    }

    private boolean isReportListScreen() {
        if (mc.currentScreen == null) {
            return false;
        }
        String title = clean(mc.currentScreen.getTitle().getString()).toLowerCase(Locale.ROOT);
        return title.contains("репорт") || title.contains("жалоб") || title.contains("report");
    }

    private String extractReportName(ItemStack stack) {
        String fromName = extractReportName(stack.getName().getString());
        if (fromName != null) {
            return fromName;
        }

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return null;
        }
        for (Text line : lore.lines()) {
            String name = extractReportName(line.getString());
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private boolean isNextPageStack(ItemStack stack) {
        if (containsNextPageText(stack.getName().getString())) {
            return true;
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return false;
        }
        for (Text line : lore.lines()) {
            if (containsNextPageText(line.getString())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNextPageText(String raw) {
        String lower = clean(raw).toLowerCase(Locale.ROOT);
        return lower.contains("впер") || lower.contains("следующ") || lower.contains("next");
    }

    private void closeAutoList(GenericContainerScreenHandler handler) {
        autoListScreen = false;
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(handler.syncId));
        }
        mc.setScreen(null);
    }

    private String extractReportName(String raw) {
        String text = clean(raw);
        if (text.isEmpty()) {
            return null;
        }

        Matcher title = REPORT_TITLE_PATTERN.matcher(text);
        if (title.find()) {
            return title.group(1);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("жалоб") && !lower.contains("репорт") && !lower.contains("report")) {
            return null;
        }

        Matcher nick = NICK_PATTERN.matcher(text);
        while (nick.find()) {
            String value = nick.group();
            if (!isCommonWord(value)) {
                return value;
            }
        }
        return null;
    }

    private void handleServerText(String raw) {
        String text = clean(raw);
        if (text.isEmpty()) {
            return;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (waitingName != null) {
            if (isCooldownMessage(lower)) {
                queueFirst(waitingName);
                waitingName = null;
                nextReportAllowedAt = System.currentTimeMillis() + parseWaitMs(lower);
            } else if (isAfkMessage(lower)) {
                lastFailedName = waitingName;
                waitingName = null;
            } else if (isNotFoundMessage(lower)) {
                lastFailedName = waitingName;
                waitingName = null;
            } else if (isAlreadyReportedMessage(lower)) {
                markReported(waitingName);
                waitingName = null;
            } else if (isSuccessMessage(lower)) {
                markReported(waitingName);
                waitingName = null;
                nextReportAllowedAt = System.currentTimeMillis() + getReportDelayMs();
            }
        }

        String listName = extractReportName(text);
        if (listName != null) {
            markReported(listName);
        }
    }

    private boolean isSuccessMessage(String lower) {
        return lower.contains("жалоба успешно отправлена")
                || (lower.contains("репорт на") && lower.contains("был принят"));
    }

    private boolean isCooldownMessage(String lower) {
        return lower.contains("необходимо подождать") || lower.contains("cooldown");
    }

    private boolean isAlreadyReportedMessage(String lower) {
        return lower.contains("уже отправляли") || lower.contains("already");
    }

    private boolean isAfkMessage(String lower) {
        return lower.contains("игрок находится в афк") || lower.contains("afk");
    }

    private boolean isNotFoundMessage(String lower) {
        return lower.contains("игрок не найден") || lower.contains("player not found");
    }

    private long parseWaitMs(String lower) {
        Matcher matcher = WAIT_SECONDS_PATTERN.matcher(lower);
        if (matcher.find()) {
            return Math.max(1, Long.parseLong(matcher.group(1))) * 1000L + 500L;
        }
        return 5_000L;
    }

    private long getReportDelayMs() {
        return Math.max(31_000L, delaySeconds.getValue().longValue() * 1000L);
    }

    private void markReported(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty() || isSelf(normalized)) {
            return;
        }
        reportedNames.add(normalized);
        listNames.add(normalized);
    }

    private boolean shouldHighlightEntity(Entity entity) {
        return entity instanceof PlayerEntity player && shouldHighlightName(player.getName().getString());
    }

    private boolean hasReportedName(String name) {
        String normalized = normalizeName(name);
        return reportedNames.contains(normalized) || listNames.contains(normalized);
    }

    private boolean shouldHighlightName(String name) {
        String normalized = normalizeName(name);
        return hasReportedName(normalized)
                || queuedNames.contains(normalized)
                || (waitingName != null && normalizeName(waitingName).equals(normalized));
    }

    private int getAllHighlightedCount() {
        Set<String> all = new LinkedHashSet<>(reportedNames);
        all.addAll(listNames);
        all.addAll(queuedNames);
        if (waitingName != null) {
            all.add(normalizeName(waitingName));
        }
        return all.size();
    }

    private String cooldownText() {
        long left = Math.max(0, nextReportAllowedAt - System.currentTimeMillis());
        return left <= 0 ? "Готово" : "Кд: " + ((left + 999) / 1000) + "с";
    }

    private void sendCommand(String command) {
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        mc.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private boolean isSelf(String name) {
        return mc.getSession() != null && normalizeName(mc.getSession().getUsername()).equals(normalizeName(name));
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        Matcher matcher = NICK_PATTERN.matcher(clean(name));
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
    }

    private String clean(String value) {
        String stripped = Formatting.strip(value == null ? "" : value);
        if (stripped == null) {
            return "";
        }
        return stripped.replace('§', ' ').trim();
    }

    private boolean isCommonWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return lower.equals("report") || lower.equals("complaint") || lower.equals("status")
                || lower.equals("date") || lower.equals("reason") || lower.equals("minecraft")
                || lower.equals("list") || lower.equals("next") || lower.equals("page");
    }
}
