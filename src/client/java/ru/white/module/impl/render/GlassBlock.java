package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.render.GlassBlockRenderer;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;

@Getter
@ModuleInfo(name = "Glass Block", category = Category.RENDER, desc = "Делает наведенный блок стеклянным")
public class GlassBlock extends Module {

    private static GlassBlock instance;

    public BooleanSetting enableBlur = new BooleanSetting(this, "Блюр", true);
    public SliderSetting blurRadius = new SliderSetting(this, "Сила размытия", 2.5f, 1.0f, 5.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting blurIterations = new SliderSetting(this, "Качество", 3, 1, 5, 1)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting saturation = new SliderSetting(this, "Насыщенность", 0, 0.0f, 2.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public BooleanSetting enableTint = new BooleanSetting(this, "Оттенок", false);
    public SliderSetting tintIntensity = new SliderSetting(this, "Сила оттенка", 0.2f, 0.0f, 0.5f, 0.01f)
            .setVisible(() -> enableBlur.getValue() && enableTint.getValue());
    public ColorSetting tintColor = new ColorSetting(this, "Цвет оттенка", 0xFF00FFFF)
            .setVisible(() -> enableTint.getValue());
    public BooleanSetting enableEdgeGlow = new BooleanSetting(this, "Свечение краёв", true);
    public SliderSetting edgeGlowIntensity = new SliderSetting(this, "Сила свечения", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue());
    public BooleanSetting shimmer = new BooleanSetting(this, "Шиммер", true)
            .setVisible(() -> enableEdgeGlow.getValue());
    public SliderSetting shimmerWidth = new SliderSetting(this, "Ширина шиммера", 0.04f, 0.01f, 0.15f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting shimmerPeriod = new SliderSetting(this, "Период шиммера", 5f, 1f, 15f, 0.5f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting moveSpeed = new SliderSetting(this, "Скорость перемещения", 0.18f, 0.05f, 0.6f, 0.01f);

    private final BufferAllocator allocator = new BufferAllocator(1 << 14);
    private final Animation moveAnimation = new Animation();
    private final Animation alphaAnimation = new Animation();

    private BlockPos targetPos;
    private BlockPos lastTargetPos;
    private Vec3d fromOrigin = Vec3d.ZERO;
    private Vec3d toOrigin = Vec3d.ZERO;
    private List<Box> targetBoxes = Collections.emptyList();

    public GlassBlock() {
        instance = this;
    }

    public static GlassBlock getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        targetPos = null;
        lastTargetPos = null;
        fromOrigin = Vec3d.ZERO;
        toOrigin = Vec3d.ZERO;
        moveAnimation.set(1.0);
        alphaAnimation.set(0.0);

        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    @Override
    protected void onDisable() {
        GlassBlockRenderer.getInstance().setEnabled(false);
        targetPos = null;
        lastTargetPos = null;
        targetBoxes = Collections.emptyList();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        targetPos = null;
        lastTargetPos = null;
        targetBoxes = Collections.emptyList();

        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;

        updateTarget();
        moveAnimation.update();
        alphaAnimation.update();

        if (alphaAnimation.get() <= 0.01f || targetBoxes.isEmpty()) return;

        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        updateRendererSettings();

        if (!renderer.captureSceneBeforeBlock()) return;
        renderMask(event, getAnimatedOrigin(), alphaAnimation.get());
        renderer.captureSceneAfterBlock();
        renderer.renderGlassEffect();
    }

    private void updateTarget() {
        BlockPos newTarget = getLookedBlock();

        if (newTarget != null) {
            BlockState state = mc.world.getBlockState(newTarget);
            VoxelShape shape = state.getOutlineShape(mc.world, newTarget);
            if (shape.isEmpty()) {
                shape = state.getCollisionShape(mc.world, newTarget);
            }

            List<Box> boxes = shape.isEmpty() ? Collections.singletonList(new Box(0, 0, 0, 1, 1, 1)) : shape.getBoundingBoxes();
            targetBoxes = boxes;
        }

        if (newTarget == null) {
            targetPos = null;
            alphaAnimation.run(0.0, moveSpeed.getValue(), Easings.QUAD_OUT, true);
            return;
        }

        if (!newTarget.equals(targetPos)) {
            fromOrigin = lastTargetPos == null ? Vec3d.of(newTarget) : getAnimatedOrigin();
            toOrigin = Vec3d.of(newTarget);
            targetPos = newTarget;
            lastTargetPos = newTarget;
            moveAnimation.set(0.0);
            moveAnimation.run(1.0, moveSpeed.getValue(), Easings.QUAD_OUT);
        }

        alphaAnimation.run(1.0, moveSpeed.getValue() * 0.75f, Easings.QUAD_OUT, true);
    }

    private BlockPos getLookedBlock() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (mc.world == null || mc.world.getBlockState(pos).isAir()) {
            return null;
        }

        return pos;
    }

    private Vec3d getAnimatedOrigin() {
        float t = moveAnimation.get();
        return new Vec3d(
                fromOrigin.x + (toOrigin.x - fromOrigin.x) * t,
                fromOrigin.y + (toOrigin.y - fromOrigin.y) * t,
                fromOrigin.z + (toOrigin.z - fromOrigin.z) * t
        );
    }

    private void renderMask(EventRender3D event, Vec3d origin, float alpha) {
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(MASK_LAYER);
        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        int color = (((int) (255 * alpha)) << 24) | 0x00FFFFFF;
        for (Box box : targetBoxes) {
            double x0 = origin.x + box.minX - cam.x;
            double y0 = origin.y + box.minY - cam.y;
            double z0 = origin.z + box.minZ - cam.z;
            double x1 = origin.x + box.maxX - cam.x;
            double y1 = origin.y + box.maxY - cam.y;
            double z1 = origin.z + box.maxZ - cam.z;
            drawBox(buffer, matrix, x0, y0, z0, x1, y1, z1, color);
        }

        immediate.draw();
    }

    private void updateRendererSettings() {
        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.setBlurEnabled(enableBlur.getValue());
        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getValue().intValue());
        renderer.setSaturation(saturation.getValue());
        renderer.setReflect(true);

        if (enableBlur.getValue() && enableTint.getValue()) {
            renderer.setTintColor(tintColor.getValue());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }

        renderer.setOutlineEnabled(enableEdgeGlow.getValue());
        if (enableEdgeGlow.getValue()) {
            float strength = edgeGlowIntensity.getValue() * 20.0f;
            renderer.setOutlineGlowStrength(strength);
            int outlineRgb = enableTint.getValue() ? tintColor.getValue() : 0xFFFFFFFF;
            renderer.setOutlineColor(outlineRgb);
            renderer.setShimmerEnabled(shimmer.getValue());
            renderer.setShimmerWidth(shimmerWidth.getValue());
            renderer.setShimmerPeriodSec(shimmerPeriod.getValue());
        }
    }

    private static void drawBox(VertexConsumer buffer, Matrix4f matrix,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int color) {
        float x0 = (float) minX, x1 = (float) maxX;
        float y0 = (float) minY, y1 = (float) maxY;
        float z0 = (float) minZ, z1 = (float) maxZ;

        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x0, y0, z1).color(color);

        buffer.vertex(matrix, x0, y1, z0).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);

        buffer.vertex(matrix, x0, y0, z1).color(color);
        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);

        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x0, y1, z0).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);

        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x0, y0, z1).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);
        buffer.vertex(matrix, x0, y1, z0).color(color);

        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
    }

    private static final RenderPipeline MASK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "glass_block_mask"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer MASK_LAYER = RenderLayer.of(
            "glass_block_mask",
            RenderSetup.builder(MASK_PIPELINE).expectedBufferSize(1 << 12).build()
    );
}
