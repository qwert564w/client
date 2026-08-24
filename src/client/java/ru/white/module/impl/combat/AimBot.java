package ru.white.module.impl.combat;

import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventLook;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.aura.GCDUtil;
import ru.white.utils.aura.RayTraceUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.utils.aura.UBoxPoints;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        name = "Aim Assist",
        desc = "Автоматически наводит прицел на врагов с симуляцией движений человека",
        category = Category.COMBAT
)
public class AimBot extends Module {

    public ModeSetting mode = new ModeSetting(this, "Режим", "Обычный", "Нейро");


    public BooleanSetting player = new BooleanSetting(this, "Атаковать игроков", true);
    public BooleanSetting golyPlayer = new BooleanSetting(this, "Атаковать голых (без брони)", true);
    public BooleanSetting mobs = new BooleanSetting(this, "Атаковать мобов", false);
    public BooleanSetting friend = new BooleanSetting(this, "Атаковать друзей", false);


    public SliderSetting speed = new SliderSetting(this, "Плавность по горизонтали", 0.08F,
            0.01F, 1.0F, 0.01F)  .setVisible(() -> mode.is("Обычный"));

    public SliderSetting speed2 = new SliderSetting(this, "Плавность по вертикали", 0.08F,
            0.00F, 1.0F, 0.01F)  .setVisible(() -> mode.is("Обычный"));

    public BooleanSetting onlyHelpToInput = new BooleanSetting(this, "Умная остановка (не дергать при наведении)"
            , true)  .setVisible(() -> mode.is("Обычный"));


    public BooleanSetting DynamicSpeed = new BooleanSetting(this, "Умная скорость (Реализм)", true)  .setVisible(() -> mode.is("Обычный"));

    public SliderSetting attackSpeedBoost = new SliderSetting(this, "Доводка при ударе (Множитель)",
            1.5F, 1.0F, 3.0F, 0.1F)  .setVisible(() -> mode.is("Обычный") && DynamicSpeed.getValue());

    public SliderSetting speedRandomness = new SliderSetting(this, "Случайное изменение скорости",
            0.02F, 0.00F, 0.1F, 0.01F)  .setVisible(() -> mode.is("Обычный") && DynamicSpeed.getValue());


    public BooleanSetting useNoise = new BooleanSetting(this, "Дрожание прицела (Шум)", true)  .setVisible(() -> mode.is("Обычный"));

    public SliderSetting noiseStrength = new SliderSetting(this, "Сила дрожания", 0.5F, 0.1F,
            3.0F, 0.1F)  .setVisible(() -> mode.is("Обычный") &&useNoise.getValue());

    public SliderSetting noiseSpeed = new SliderSetting(this, "Скорость дрожания", 0.3F, 0.1F,
            1.0F, 0.05F)   .setVisible(() -> mode.is("Обычный") &&useNoise.getValue());

    public SliderSetting neuroHumanity = new SliderSetting(this, "Нейро человечность", 0.65F, 0.0F, 1.0F, 0.05F)
            .setVisible(() -> mode.is("Нейро"));
    public SliderSetting neuroReaction = new SliderSetting(this, "Нейро реакция", 120, 20, 300, 10)
            .setVisible(() -> mode.is("Нейро"));
    public SliderSetting neuroMaxMouse = new SliderSetting(this, "Нейро скорость мыши", 8.0F, 1.0F, 24.0F, 0.5F)
            .setVisible(() -> mode.is("Нейро"));

    public static LivingEntity target;
    private float noiseX = 0;
    private float noiseY = 0;
    private double mouseVelocityX = 0;
    private double mouseVelocityY = 0;
    private double aimOffsetX = 0;
    private double aimOffsetY = 0.5;
    private double aimOffsetZ = 0;
    private long nextAimPointUpdate = 0;
    private long reactionUntil = 0;
    private int lastTargetId = -1;
    private long neuroCurveStart = 0;
    private double neuroCurveSide = 1.0;
    private double neuroCurveStrength = 0.0;

    @EventHandler
    public void onEvent(EventUpdate eventUpdate) {
        if ((target == null || !isValidTarget(target))) {
            updateTarget();
        }

        if(AttackAura.get().typeRotation.is("TriggerBot") && AttackAura.get().isEnabled()) {
            target = AttackAura.target;
        }

        if (target == null || mc.player == null || mc.world == null) {
            target = null;

            return;
        }
    }

    @EventHandler
    public void onEvent(EventDisplay eventUpdate) {

        if (mode.is("Нейро")) {
            return;
        }

        if (target != null && mc.player != null && mc.world != null && (!onlyHelpToInput.getValue() || !RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), 3, target))) {
            Vec3d vec = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox(),false
            ).subtract(mc.player.getEyePos()).normalize();

            if (useNoise.getValue()) {
                float maxNoise = noiseStrength.getValue();
                float noiseStep = noiseSpeed.getValue();

                noiseX += (float) (ThreadLocalRandom.current().nextDouble(-noiseStep, noiseStep));
                noiseY += (float) (ThreadLocalRandom.current().nextDouble(-noiseStep, noiseStep));

                noiseX = MathHelper.clamp(noiseX, -maxNoise, maxNoise);
                noiseY = MathHelper.clamp(noiseY, -maxNoise, maxNoise);
            } else {
                noiseX = 0;
                noiseY = 0;
            }


            float finalSpeedX = this.speed.getValue();
            float finalSpeedY = this.speed2.getValue();

            if (DynamicSpeed.getValue()) {

                float randomFactor = (float) ThreadLocalRandom.current().nextDouble(-speedRandomness.getValue(), speedRandomness.getValue());
                finalSpeedX += randomFactor;
                finalSpeedY += randomFactor;


                if (mc.player.handSwingProgress > 0) {
                    finalSpeedX *= attackSpeedBoost.getValue();
                    finalSpeedY *= attackSpeedBoost.getValue();
                }


                finalSpeedX = MathHelper.clamp(finalSpeedX, 0.01F, 1.5F);
                finalSpeedY = MathHelper.clamp(finalSpeedY, 0.00F, 1.5F);
            }


            float targetYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z)) + noiseX;
            float currentYaw = mc.player.getYaw();
            float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float smoothYaw = currentYaw + yawDelta * finalSpeedX;

            mc.player.setYaw(GCDUtil.applyGCD(smoothYaw, currentYaw));

            float targetYaw2 = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90, 90) + noiseY;
            float currentYaw2 = mc.player.getPitch();
            float yawDelta2 = MathHelper.wrapDegrees(targetYaw2 - currentYaw2);
            float smoothYaw2 = currentYaw2 + yawDelta2 * finalSpeedY;

            mc.player.setPitch(GCDUtil.applyGCD(smoothYaw2, currentYaw2));
        }
    }

    @EventHandler
    public void onLook(EventLook event) {
        if (!mode.is("Нейро") || target == null || mc.player == null || mc.world == null) {
            resetNeuroMotion();
            return;
        }

        if (!isValidTarget(target)) {
            target = null;
            resetNeuroMotion();
            return;
        }

        if (onlyHelpToInput.getValue() && RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), 3, target)) {
            mouseVelocityX *= 0.55;
            mouseVelocityY *= 0.55;
            return;
        }

        long now = System.currentTimeMillis();
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            reactionUntil = now + neuroReaction.getValue().longValue()
                    + ThreadLocalRandom.current().nextLong(20L, 90L);
            nextAimPointUpdate = 0;
            neuroCurveStart = now;
            neuroCurveSide = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
            neuroCurveStrength = ThreadLocalRandom.current().nextDouble(0.55, 1.35);
            mouseVelocityX *= 0.25;
            mouseVelocityY *= 0.25;
        }

        updateNeuroAimPoint(target, now);
        if (now < reactionUntil) {
            return;
        }

        Vec3d vec = target.getEntityPos()
                .add(aimOffsetX, target.getHeight() * aimOffsetY, aimOffsetZ)
                .subtract(mc.player.getEyePos());

        float targetYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))),
                -90,
                90
        );

        double yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        double pitchDelta = targetPitch - mc.player.getPitch();
        double distance = Math.hypot(yawDelta, pitchDelta);
        if (distance < 0.08) {
            mouseVelocityX *= 0.62;
            mouseVelocityY *= 0.62;
            return;
        }

        double humanity = neuroHumanity.getValue();
        double sensitivityStep = Math.max(0.0001, GCDUtil.getGCDValue());
        double desiredMouseX = yawDelta / sensitivityStep;
        double desiredMouseY = pitchDelta / sensitivityStep;

        double ease = MathHelper.clamp(distance / 35.0, 0.18, 1.0);
        ease = ease * ease * (3.0 - 2.0 * ease);
        double closeBrake = MathHelper.clamp(distance / 8.0, 0.22, 1.0);
        double baseGain = (0.07 + 0.21 * ease) * closeBrake * (1.0 - humanity * 0.28);
        double maxMouse = neuroMaxMouse.getValue() * (0.45 + ease * 0.75);

        if (mc.player.handSwingProgress > 0) {
            baseGain *= attackSpeedBoost.getValue();
            maxMouse *= 1.25;
        }

        double waveTime = now / 1000.0;
        double tremor = useNoise.getValue() ? noiseStrength.getValue() * 0.035 * humanity : 0.0;
        double driftX = (Math.sin(waveTime * 7.1) + Math.cos(waveTime * 3.7)) * tremor;
        double driftY = (Math.cos(waveTime * 6.4) - Math.sin(waveTime * 2.9)) * tremor;

        double overshoot = MathHelper.clamp(distance / 90.0, 0.0, 1.0) * humanity;
        desiredMouseX += Math.signum(yawDelta) * overshoot * 0.65;
        desiredMouseY += Math.signum(pitchDelta) * overshoot * 0.35;

        double curveProgress = MathHelper.clamp((now - neuroCurveStart) / 520.0, 0.0, 1.0);
        double curveEase = Math.sin(curveProgress * Math.PI);
        double curveMouse = curveEase * neuroCurveStrength * humanity * MathHelper.clamp(distance / 22.0, 0.0, 1.0);
        if (distance > 0.001) {
            desiredMouseX += (-pitchDelta / distance) * curveMouse * neuroCurveSide;
            desiredMouseY += (yawDelta / distance) * curveMouse * 0.42 * neuroCurveSide;
        }

        double accel = 0.24 + ease * 0.30 + Math.sin((now - neuroCurveStart) / 95.0) * 0.035 * humanity;
        mouseVelocityX += (desiredMouseX * baseGain - mouseVelocityX) * accel;
        mouseVelocityY += (desiredMouseY * baseGain - mouseVelocityY) * (accel * 0.88);

        mouseVelocityX += ThreadLocalRandom.current().nextDouble(-speedRandomness.getValue(), speedRandomness.getValue()) * humanity;
        mouseVelocityY += ThreadLocalRandom.current().nextDouble(-speedRandomness.getValue(), speedRandomness.getValue()) * humanity;

        double addX = MathHelper.clamp(mouseVelocityX + driftX, -maxMouse, maxMouse);
        double addY = MathHelper.clamp(mouseVelocityY + driftY, -maxMouse, maxMouse);

        event.setYaw(event.getYaw() + addX);
        event.setPitch(event.getPitch() + addY);
    }

    private void updateNeuroAimPoint(LivingEntity entity, long now) {
        if (now < nextAimPointUpdate) {
            return;
        }

        double humanity = neuroHumanity.getValue();
        double halfWidth = entity.getWidth() * 0.5;
        aimOffsetX = ThreadLocalRandom.current().nextDouble(-halfWidth, halfWidth) * 0.55 * humanity;
        aimOffsetY = ThreadLocalRandom.current().nextDouble(0.38, 0.82);
        aimOffsetZ = ThreadLocalRandom.current().nextDouble(-halfWidth, halfWidth) * 0.55 * humanity;
        nextAimPointUpdate = now + ThreadLocalRandom.current().nextLong(140L, 360L);
    }

    private void resetNeuroMotion() {
        mouseVelocityX = 0;
        mouseVelocityY = 0;
        lastTargetId = -1;
        nextAimPointUpdate = 0;
        reactionUntil = 0;
        neuroCurveStart = 0;
        neuroCurveStrength = 0;
    }

    private void updateTarget() {
        LivingEntity bestTarget = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            Vec3d targetPos = living.getEntityPos().add(0, living.getHeight() * 0.5, 0);
            Vec3d toTarget = targetPos.subtract(eyePos).normalize();
            double angle = Math.acos(MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0));
            if (angle < bestAngle) {
                bestAngle = angle;
                bestTarget = living;
            }
        }
        target = bestTarget;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof ClientPlayerEntity) return false;
        if (mc.player.distanceTo(entity) > 6) return false;
        if (!mc.player.canSee(entity)) return false;

        if (entity instanceof PlayerEntity p) {
            if (Client.get().friendManager().isFriend(p.getName().getString()) && !friend.getValue()) return false;
        }
        if (entity instanceof PlayerEntity && !player.getValue()) return false;
        if (entity instanceof PlayerEntity && entity.getArmorVisibility() <= 0 && !golyPlayer.getValue()) return false;
        if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) return false;

        if ((entity instanceof Monster || entity instanceof SlimeEntity || entity instanceof VillagerEntity || entity instanceof AnimalEntity)
                && !mobs.getValue()) return false;

        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }
}
