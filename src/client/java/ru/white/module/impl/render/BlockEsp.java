package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(name = "Block ESP", category = Category.RENDER, desc = "Подсвечивает блоки в радиусе")
public class BlockEsp extends Module {

    public SliderSetting radius = new SliderSetting(this, "Радиус", 30, 5, 60, 5);

    private static BlockEsp instance;
    private final List<String> blockIds = new CopyOnWriteArrayList<>();
    private final List<BlockPos> foundBlocks = new CopyOnWriteArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 18);
    private int scanTick = 0;

    public BlockEsp() {
        instance = this;
    }

    public static BlockEsp getInstance() {
        return instance;
    }

    public boolean addBlock(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        if (blockIds.contains(id)) return false;
        blockIds.add(id);
        rescan();
        return true;
    }

    public boolean removeBlock(Block block) {
        boolean removed = blockIds.remove(Registries.BLOCK.getId(block).toString());
        if (removed) rescan();
        return removed;
    }

    public int clearBlocks() {
        int count = blockIds.size();
        blockIds.clear();
        rescan();
        return count;
    }

    public Set<Block> getTargetBlocks() {
        Set<Block> targetBlocks = new HashSet<>();
        for (String id : blockIds) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) {
                Registries.BLOCK.getOptionalValue(identifier).ifPresent(targetBlocks::add);
            }
        }
        return targetBlocks;
    }

    @Override
    protected void onDisable() {
        foundBlocks.clear();
        scanTick = 0;
    }

    public void rescan() {
        scanTick = 5;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.world == null || mc.player == null) {
            foundBlocks.clear();
            return;
        }
        Set<Block> targetBlocks = getTargetBlocks();
        if (targetBlocks.isEmpty()) {
            foundBlocks.clear();
            return;
        }
        if (++scanTick < 5) return;
        scanTick = 0;

        List<BlockPos> found = new ArrayList<>();
        int r = radius.getValue().intValue();
        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getTopYInclusive();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        outer:
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(minY, py - r); y <= Math.min(maxY, py + r); y++) {
                    mutable.set(x, y, z);
                    Block block = mc.world.getBlockState(mutable).getBlock();
                    if (targetBlocks.contains(block)) {
                        found.add(mutable.toImmutable());
                        if (found.size() >= 2000) break outer;
                    }
                }
            }
        }

        foundBlocks.clear();
        foundBlocks.addAll(found);
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.world == null || mc.player == null || foundBlocks.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        int color = ColorUtil.fade(1);
        int cB = ColorUtil.replAlpha(color, 0.7f);
        int cT = ColorUtil.replAlpha(color, 0.0f);

        VertexConsumer buffer = immediate.getBuffer(BOX_LAYER);

        for (BlockPos pos : foundBlocks) {
            float x0 = (float) (pos.getX() - cam.x);
            float y0 = (float) (pos.getY() - cam.y);
            float z0 = (float) (pos.getZ() - cam.z);
            drawGradientBox(buffer, matrix, x0, y0, z0, x0 + 1f, y0 + 1f, z0 + 1f, cB, cT);
        }

        immediate.draw();
    }

    private static void drawGradientBox(VertexConsumer b, Matrix4f m,
                                        float x0, float y0, float z0,
                                        float x1, float y1, float z1,
                                        int cB, int cT) {
        // Bottom face
        b.vertex(m, x0, y0, z0).color(cB);
        b.vertex(m, x1, y0, z0).color(cB);
        b.vertex(m, x1, y0, z1).color(cB);
        b.vertex(m, x0, y0, z1).color(cB);

        // Top face
        b.vertex(m, x0, y1, z0).color(cT);
        b.vertex(m, x0, y1, z1).color(cT);
        b.vertex(m, x1, y1, z1).color(cT);
        b.vertex(m, x1, y1, z0).color(cT);

        // Front face (maxZ)
        b.vertex(m, x0, y0, z1).color(cB);
        b.vertex(m, x1, y0, z1).color(cB);
        b.vertex(m, x1, y1, z1).color(cT);
        b.vertex(m, x0, y1, z1).color(cT);

        // Back face (minZ)
        b.vertex(m, x1, y0, z0).color(cB);
        b.vertex(m, x0, y0, z0).color(cB);
        b.vertex(m, x0, y1, z0).color(cT);
        b.vertex(m, x1, y1, z0).color(cT);

        // Left face (minX)
        b.vertex(m, x0, y0, z0).color(cB);
        b.vertex(m, x0, y0, z1).color(cB);
        b.vertex(m, x0, y1, z1).color(cT);
        b.vertex(m, x0, y1, z0).color(cT);

        // Right face (maxX)
        b.vertex(m, x1, y0, z1).color(cB);
        b.vertex(m, x1, y0, z0).color(cB);
        b.vertex(m, x1, y1, z0).color(cT);
        b.vertex(m, x1, y1, z1).color(cT);
    }

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "block_esp"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_LAYER = RenderLayer.of(
            "block_esp",
            RenderSetup.builder(BOX_PIPELINE).expectedBufferSize(1 << 12).build()
    );
}
