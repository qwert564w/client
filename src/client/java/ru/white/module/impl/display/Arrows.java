package ru.white.module.impl.display;

import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

import java.util.List;

@ModuleInfo(name = "Arrows", category = Category.RENDER, desc = "Стрелки на игроков")
public class Arrows extends Module {

    public BooleanSetting onlyArmored = new BooleanSetting(this, "Игнорировать голых", false);
    public SliderSetting size = new SliderSetting(this,"Размер",30,12,80,1);
    public SliderSetting radius = new SliderSetting(this,"Радиус от прицела",50,20,80,1);

    private final Animation animation = new Animation();
    private static final Identifier ARROW_TEX = Identifier.of("client", "textures/arrow.png");

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player != null) {
            animation.run(mc.player.isSprinting() ? 1 : mc.currentScreen == null ? 0 : 6 , 0.1f, Easings.SINE_OUT);
        }

    }

    @EventHandler(priority = -500)
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden || !mc.options.getPerspective().equals(Perspective.FIRST_PERSON)) return;

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .filter(p -> !onlyArmored.getValue() || hasArmor(p))
                .toList();

        if (players.isEmpty()) return;

        animation.update();

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float partialTicks = e.getPartialTicks();
        float middleW = screenWidth / 2f;
        float middleH = screenHeight / 2f;
        float size = this.size.getValue() / 4;
        float posY = middleH - radius.getValue() - (radius.getValue() / 4) * animation.get();
        float offset = posY + size / 2f - middleH; // negative: arrow is above center

        for (AbstractClientPlayerEntity player : players) {
            int color = Client.get().friendManager().isFriend(player.getName().getString())
                    ? ColorUtil.GREEN
                    : ColorUtil.fade(1);

            float realYaw = getAngle(player, partialTicks) - mc.gameRenderer.getCamera().getYaw();
            float rad = (float) Math.toRadians(realYaw);
            float cx = middleW - offset * (float) Math.sin(rad);
            float cy = middleH + offset * (float) Math.cos(rad);

            Client.get().render2D().getTexturePipeline().drawGlowTexture(
                    ARROW_TEX,
                    cx - size / 2f, cy - size / 2f, size, size,
                    0f, 0f, 1f, 1f,
                    new int[]{color, color, color, color},
                    new float[]{0f, 0f, 0f, 0f},
                    1f,
                    realYaw
            );
        }
    }

    private static boolean hasArmor(AbstractClientPlayerEntity player) {
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static float getAngle(Entity entity, float partialTicks) {
        double x = MathUtil.interpolate(entity.lastRenderX, entity.getX(), partialTicks)
                 - MathUtil.interpolate(mc.player.lastRenderX, mc.player.getX(), partialTicks);
        double z = MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), partialTicks)
                 - MathUtil.interpolate(mc.player.lastRenderZ, mc.player.getZ(), partialTicks);
        return (float) -(Math.atan2(x, z) * (180.0 / Math.PI));
    }
}
