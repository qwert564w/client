package ru.white.module.impl.utils;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.notification.NotificationManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        name = "DanjHelper",
        desc = "Отслеживание бочек в данже: таймеры открытия и градиентный ESP",
        category = Category.OTHER
)
public class DanjHelper extends Module {

    private final Map<BlockPos, DanjBlockData> trackedBlocks = new ConcurrentHashMap<>();
    private final Set<BlockPos> gpsNotified = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> chatNotified = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)\\s*(с|s|сек|sec)");

    private static final int COLOR_RED = ColorUtil.getColor(255, 60, 60, 180);
    private static final int COLOR_YELLOW = ColorUtil.getColor(255, 220, 50, 180);
    private static final int COLOR_GREEN = ColorUtil.getColor(60, 255, 60, 180);
    private static final int COLOR_UNKNOWN = ColorUtil.getColor(180, 180, 180, 120);

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "danj_helper_esp"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_LAYER = RenderLayer.of(
            "danj_helper_esp",
            RenderSetup.builder(BOX_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;
        if (!isInDanjZone()) return;

        scanBarrelsAndHolograms();
        checkNotifications();
    }

    private boolean isInDanjZone() {
        if (mc.player == null) return false;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        return Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
    }

    private boolean isBlockInZone(BlockPos pos) {
        if (mc.player == null) return false;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        boolean inXZ = Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
        boolean inY = Math.abs(y - mc.player.getY()) <= 30.0;
        return inXZ && inY;
    }

    private void scanBarrelsAndHolograms() {
        Box box = mc.player.getBoundingBox().expand(32.0);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box);

        for (Entity entity : entities) {
            if (!(entity instanceof ArmorStandEntity armorStand)) continue;

            if (!armorStand.hasCustomName() || armorStand.getCustomName() == null) continue;

            String name = armorStand.getCustomName().getString();
            String clean = ColorUtil.removeFormatting(name);
            if (clean == null) continue;

            long seconds = parseTimeToSeconds(clean);
            if (seconds >= 0) {
                BlockPos pos = armorStand.getBlockPos().down();
                trackedBlocks.computeIfAbsent(pos, DanjBlockData::new).updateFromHologram(seconds);
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

    private void checkNotifications() {
        for (Map.Entry<BlockPos, DanjBlockData> entry : trackedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            DanjBlockData data = entry.getValue();

            if (!data.hasTimer()) continue;

            float remaining = data.getRemainingSeconds();
            if (remaining <= 30.0f && remaining > 0.0f) {
                if (!gpsNotified.contains(pos)) {
                    mc.player.networkHandler.sendChatMessage(".gps set " + pos.getX() + " " + pos.getZ());
                    gpsNotified.add(pos);
                }

                if (!chatNotified.contains(pos)) {
                    String msg = "Бочка откроется через " + (int) remaining + " сек! [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
                    ChatUtils.addChatMessage("§b[DanjHelper] " + msg);
                    NotificationManager.send(msg, NotificationManager.Type.WARNING);
                    chatNotified.add(pos);
                }
            }
        }
    }

    @EventHandler(priority = -500)
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (!isInDanjZone()) return;

        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(BOX_LAYER);

        ChunkPos playerChunkPos = mc.player.getChunkPos();
        int renderDistance = mc.options.getViewDistance().getValue();

        for (int chunkX = playerChunkPos.x - renderDistance; chunkX <= playerChunkPos.x + renderDistance; chunkX++) {
            for (int chunkZ = playerChunkPos.z - renderDistance; chunkZ <= playerChunkPos.z + renderDistance; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof BarrelBlockEntity) {
                        BlockPos pos = blockEntity.getPos();
                        if (!isBlockInZone(pos)) continue;

                        if (!trackedBlocks.containsKey(pos)) {
                            trackedBlocks.put(pos, new DanjBlockData(pos));
                        }

                        DanjBlockData data = trackedBlocks.get(pos);
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

    private int getTimerColor(DanjBlockData data) {
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

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    private void resetState() {
        trackedBlocks.clear();
        gpsNotified.clear();
        chatNotified.clear();
    }

    private static class DanjBlockData {
        final BlockPos pos;
        long readyAtMs = -1L;

        DanjBlockData(BlockPos pos) {
            this.pos = pos;
        }

        void updateFromHologram(long remainingSeconds) {
            this.readyAtMs = System.currentTimeMillis() + remainingSeconds * 1000L;
        }

        boolean hasTimer() {
            return readyAtMs > 0L;
        }

        float getRemainingSeconds() {
            if (readyAtMs <= 0L) return -1.0f;
            float remaining = (float) (readyAtMs - System.currentTimeMillis()) / 1000.0f;
            return Math.max(0.0f, remaining);
        }
    }
}
