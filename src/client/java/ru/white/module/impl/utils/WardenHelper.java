package ru.white.module.impl.utils;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Projection;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "Warden Helper", desc = "Отслеживание сундуков в Варден зоне: таймеры, ESP и неймтеги", category = Category.OTHER)
public class WardenHelper extends Module {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)\\s*(с|s|сек|sec)");

    private final Map<BlockPos, ChestData> trackedChests = new ConcurrentHashMap<>();
    private final Set<BlockPos> gpsNotified = new HashSet<>();
    private final Set<BlockPos> chatNotified = new HashSet<>();

    private static final int COLOR_RED = ColorUtil.getColor(255, 60, 60, 180);
    private static final int COLOR_YELLOW = ColorUtil.getColor(255, 220, 50, 180);
    private static final int COLOR_GREEN = ColorUtil.getColor(60, 255, 60, 180);
    private static final int COLOR_UNKNOWN = ColorUtil.getColor(180, 180, 180, 120);

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "warden_helper_esp"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_LAYER = RenderLayer.of(
            "warden_helper_esp",
            RenderSetup.builder(BOX_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    @Override
    public void onEnable() {
        super.onEnable();
        trackedChests.clear();
        gpsNotified.clear();
        chatNotified.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        trackedChests.clear();
        gpsNotified.clear();
        chatNotified.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        trackedChests.clear();
        gpsNotified.clear();
        chatNotified.clear();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null)
            return;
        if (!isInWardenZone())
            return;

        updateTimersFromHolograms();
    }

    private boolean isInWardenZone() {
        if (mc.player == null) return false;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        return Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
    }

    private boolean isChestInZone(BlockPos pos) {
        if (mc.player == null) return false;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        boolean inXZ = Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
        boolean inY = Math.abs(y - mc.player.getY()) <= 30.0;
        return inXZ && inY;
    }

    private void updateTimersFromHolograms() {
        for (Map.Entry<BlockPos, ChestData> entry : trackedChests.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();

            Box searchBox = new Box(pos).expand(1, 3, 1);
            List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(
                    ArmorStandEntity.class, searchBox, e -> e.hasCustomName());

            boolean foundTimer = false;
            for (ArmorStandEntity stand : stands) {
                String name = stand.getCustomName().getString();
                String clean = ColorUtil.removeFormatting(name);
                if (clean == null)
                    continue;

                long seconds = parseTimeToSeconds(clean);
                if (seconds >= 0) {
                    data.updateFromHologram(seconds);
                    foundTimer = true;
                    break;
                }
            }

            if (!foundTimer) {
                data.hologramVisible = false;
            }
        }
    }

    private long parseTimeToSeconds(String text) {
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            if (timeMatcher.group(3) != null) {
                int hours = Integer.parseInt(timeMatcher.group(1));
                int minutes = Integer.parseInt(timeMatcher.group(2));
                int seconds = Integer.parseInt(timeMatcher.group(3));
                return hours * 3600L + minutes * 60L + seconds;
            } else {
                int minutes = Integer.parseInt(timeMatcher.group(1));
                int seconds = Integer.parseInt(timeMatcher.group(2));
                return minutes * 60L + seconds;
            }
        }

        Matcher secMatcher = SECONDS_PATTERN.matcher(text);
        if (secMatcher.find()) {
            return Long.parseLong(secMatcher.group(1));
        }

        String stripped = text.replaceAll("[^0-9:]", "").trim();
        if (!stripped.isEmpty() && stripped.matches("\\d+")) {
            long val = Long.parseLong(stripped);
            if (val > 0 && val < 36000) {
                return val;
            }
        }

        return -1;
    }

    @EventHandler(priority = -500)
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null)
            return;
        if (!isInWardenZone())
            return;

        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(BOX_LAYER);

        ChunkPos playerChunkPos = mc.player.getChunkPos();
        int renderDistance = mc.options.getViewDistance().getValue();

        for (int chunkX = playerChunkPos.x - renderDistance; chunkX <= playerChunkPos.x + renderDistance; chunkX++) {
            for (int chunkZ = playerChunkPos.z - renderDistance; chunkZ <= playerChunkPos.z + renderDistance; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null)
                    continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity) {
                        BlockPos pos = blockEntity.getPos();

                        if (!isChestInZone(pos)) continue;

                        if (!trackedChests.containsKey(pos)) {
                            trackedChests.put(pos, new ChestData(pos));
                        }

                        ChestData data = trackedChests.get(pos);
                        if (!data.hasTimer()) continue;

                        int color = getTimerColor(data);

                        float x1 = (float) (pos.getX() - camPos.x);
                        float y1 = (float) (pos.getY() - camPos.y);
                        float z1 = (float) (pos.getZ() - camPos.z);

                        int cB = ColorUtil.replAlpha(color, 0.8f);
                        int cT = ColorUtil.replAlpha(color, 0.0f);

                        drawGradientBox(buffer, matrix, x1, y1, z1, x1 + 1f, y1 + 1f, z1 + 1f, cB, cT);
                    }
                }
            }
        }

        immediate.draw();
    }

    private String formatSeconds(int totalSec) {
        if (totalSec <= 0) return "0:00";
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%d:%02d", m, s);
    }

    private int getTimerColor(ChestData data) {
        if (!data.hasTimer()) {
            return COLOR_UNKNOWN;
        }

        float remaining = data.getRemainingSeconds();

        if (remaining <= 0) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.3 + 0.7);
            int g = (int) (255 * pulse);
            return ColorUtil.getColor(0, g, (int) (128 * pulse), 220);
        }

        if (remaining > 120) {
            return COLOR_RED;
        } else if (remaining > 20) {
            float factor = 1.0f - (remaining - 20f) / 100f;
            return ColorUtil.interpolateColor(COLOR_RED, COLOR_YELLOW, factor);
        } else {
            float factor = 1.0f - remaining / 20f;
            return ColorUtil.interpolateColor(COLOR_YELLOW, COLOR_GREEN, factor);
        }
    }

    private void drawGradientBox(VertexConsumer b, Matrix4f m,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int cB, int cT) {
        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x2, y1, z1).color(cB);
        b.vertex(m, x2, y1, z2).color(cB);
        b.vertex(m, x1, y1, z2).color(cB);

        b.vertex(m, x1, y2, z1).color(cT);
        b.vertex(m, x1, y2, z2).color(cT);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x2, y2, z1).color(cT);

        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x1, y2, z1).color(cT);
        b.vertex(m, x2, y2, z1).color(cT);
        b.vertex(m, x2, y1, z1).color(cB);

        b.vertex(m, x1, y1, z2).color(cB);
        b.vertex(m, x2, y1, z2).color(cB);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x1, y2, z2).color(cT);

        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x1, y1, z2).color(cB);
        b.vertex(m, x1, y2, z2).color(cT);
        b.vertex(m, x1, y2, z1).color(cT);

        b.vertex(m, x2, y1, z1).color(cB);
        b.vertex(m, x2, y2, z1).color(cT);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x2, y1, z2).color(cB);
    }

    private static class ChestData {
        final BlockPos pos;
        long readyAtMs = -1;
        long lastHologramUpdateMs = 0;
        boolean hologramVisible = false;
        long lastReadSeconds = -1;

        ChestData(BlockPos pos) {
            this.pos = pos;
        }

        void updateFromHologram(long remainingSeconds) {
            hologramVisible = true;
            lastHologramUpdateMs = System.currentTimeMillis();

            long newReadyAt = System.currentTimeMillis() + remainingSeconds * 1000L;

            if (readyAtMs == -1) {
                readyAtMs = newReadyAt;
                lastReadSeconds = remainingSeconds;
            } else {
                float currentEstimate = getRemainingSeconds();
                float diff = Math.abs(currentEstimate - remainingSeconds);

                if (diff > 3) {
                    readyAtMs = newReadyAt;
                }
                lastReadSeconds = remainingSeconds;
            }
        }

        boolean hasTimer() {
            return readyAtMs > 0;
        }

        float getRemainingSeconds() {
            if (readyAtMs <= 0)
                return -1;
            float remaining = (readyAtMs - System.currentTimeMillis()) / 1000f;
            return Math.max(0, remaining);
        }

        void reset() {
            readyAtMs = -1;
            hologramVisible = false;
            lastReadSeconds = -1;
        }
    }
}
