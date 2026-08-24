package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.neuro.NeuroManager;
import ru.white.manager.neuro.NeuroModel;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.UAttack;
import ru.white.utils.math.MathUtil;

public class NeuroRotation implements RotationAura {

    private boolean initialized;
    private float lastCameraYaw;
    private float lastCameraPitch;
    private float prevAppliedYaw;
    private float prevAppliedPitch;
    private float lastHitX = 0.5F;
    private float lastHitY = 0.5F;
    private float lastHitZ = 0.5F;
    private float lastSmoothness = 1F;
    private float lastPeak = 0F;
    private int sinceAttack = 999;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        boolean attack = UAttack.shouldAttack(target, false, true, true, -100L, ranges);

        float cameraYaw = Rotation.cameraYaw();
        float cameraPitch = Rotation.cameraPitch();
        if (!initialized) {
            lastCameraYaw = cameraYaw;
            lastCameraPitch = cameraPitch;
            initialized = true;
        }

        float appliedYaw = MathHelper.wrapDegrees(cameraYaw - lastCameraYaw);
        float appliedPitch = cameraPitch - lastCameraPitch;
        Vec3d center = target.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        float centerYaw = yawTo(center);
        float centerPitch = pitchTo(center);
        float yawErr = MathHelper.wrapDegrees(centerYaw - cameraYaw);
        float pitchErr = centerPitch - cameraPitch;
        float distance = (float) mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());

        // контекст ударов: как давно был последний удар (для отвода камеры после удара)
        if (aura.justAttacked) {
            aura.justAttacked = false;
            sinceAttack = 0;
        } else {
            sinceAttack = Math.min(sinceAttack + 1, 999);
        }

        // ── приоритет: полная копия записанной траектории (подлёт/отвод как у игрока) ──
        float[] move = NeuroManager.get().nextMove(yawErr, pitchErr, Math.min(sinceAttack, 60));
        if (move != null) {
            float replayYaw = cameraYaw + move[0];
            float replayPitch = MathHelper.clamp(cameraPitch + move[1], -90F, 90F);
            RotationProcess.update(new Rotation(replayYaw, replayPitch),
                    Math.max(Math.abs(move[0]), 1F), Math.max(Math.abs(move[1]), 1F),
                    MathUtil.randomInt(900, 1424), MathUtil.randomInt(900, 1424),
                    MathUtil.randomInt(3, 5), 15, false);

            prevAppliedYaw = appliedYaw;
            prevAppliedPitch = appliedPitch;
            lastCameraYaw = cameraYaw;
            lastCameraPitch = cameraPitch;
            return;
        }

        // ── фолбэк: предсказание сети, когда похожей записи нет ──
        double[] features = NeuroModel.features(
                yawErr, pitchErr,
                appliedYaw, appliedPitch,
                prevAppliedYaw, prevAppliedPitch,
                lastHitX, lastHitY, lastHitZ,
                distance,
                lastSmoothness, lastPeak
        );
        NeuroModel.Prediction prediction = NeuroManager.get().predictDetailed(features);

        lastHitX = prediction.hitX;
        lastHitY = prediction.hitY;
        lastHitZ = prediction.hitZ;
        lastSmoothness = prediction.smoothness;
        lastPeak = prediction.peak;

        Vec3d aimPoint = pointFromHitbox(target.getBoundingBox(), prediction);
        Vec3d aim = aimPoint.subtract(mc.player.getEyePos());
        float baseYaw = yawTo(aim);
        float basePitch = pitchTo(aim);
        float aimYawDelta = MathHelper.wrapDegrees(baseYaw - cameraYaw);
        float aimPitchDelta = basePitch - cameraPitch;
        float aimError = (float) Math.hypot(aimYawDelta, aimPitchDelta);
        float styleScale = MathHelper.clamp(1F - aimError / 35F, 0.15F, 1F);
        // чем более дёрганым был игрок в этой ситуации (низкая smoothness) — тем сильнее тряска
        float jitterScale = 1F + (1F - prediction.smoothness) * 0.9F;
        float styleYaw = (prediction.yawShake + prediction.yawLead * 0.16F + prediction.yawAccel * 0.05F) * styleScale * jitterScale;
        float stylePitch = (prediction.pitchShake + prediction.pitchLead * 0.16F + prediction.pitchAccel * 0.05F) * styleScale * jitterScale;
        // реплей записанного почерка руки: реальные дёргания мыши под текущую скорость поворота
        float[] handJitter = NeuroManager.get().nextJitter((float) Math.hypot(appliedYaw, appliedPitch));
        float jitterYaw = MathHelper.clamp(handJitter[0], -8F, 8F);
        float jitterPitch = MathHelper.clamp(handJitter[1], -5F, 5F);

        float yaw = baseYaw + styleYaw + jitterYaw;
        float pitch = MathHelper.clamp(basePitch + stylePitch + jitterPitch, -90F, 90F);
        float yawDelta = MathHelper.wrapDegrees(yaw - cameraYaw);
        float pitchDelta = pitch - cameraPitch;
        // выученный пиковый рывок кратковременно ускоряет доводку, как резкое движение мыши
        float peakBoost = prediction.peak * 18F;
        float yawSpeed = MathHelper.clamp(Math.max(prediction.yawSpeed, Math.abs(aimYawDelta) * 0.6F ) + peakBoost, 0, 80);
        float pitchSpeed = MathHelper.clamp(Math.max(prediction.pitchSpeed, Math.abs(aimPitchDelta) * 0.03F ) + peakBoost * 0.4F, 0, 11);

        RotationProcess.update(new Rotation(yaw, pitch),
                yawSpeed, pitchSpeed,
                MathUtil.randomInt(900, 1424), MathUtil.randomInt(900, 1424),
                MathUtil.randomInt(3, 5), 15, false);

        prevAppliedYaw = appliedYaw;
        prevAppliedPitch = appliedPitch;
        lastCameraYaw = cameraYaw;
        lastCameraPitch = cameraPitch;
    }

    private Vec3d pointFromHitbox(Box box, NeuroModel.Prediction prediction) {
        return new Vec3d(
                MathHelper.lerp(prediction.hitX, box.minX, box.maxX),
                MathHelper.lerp(prediction.hitY, box.minY, box.maxY),
                MathHelper.lerp(prediction.hitZ, box.minZ, box.maxZ)
        );
    }

    private static float yawTo(Vec3d aim) {
        return (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
    }

    private static float pitchTo(Vec3d aim) {
        return (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90F, 90F);
    }
}
