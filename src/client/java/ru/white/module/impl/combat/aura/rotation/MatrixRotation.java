package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;

import java.util.concurrent.ThreadLocalRandom;

public class MatrixRotation implements RotationAura {

    private float currentSpeedYaw = 34f;
    private float currentSpeedPitch = 12F;


    @Override
    
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;


            Vec3d vec = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox()).subtract(mc.player.getEyePos()).normalize();


            float rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
            float rawPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

            float targetSpeedYaw = MathUtil.randomLerp(50, 80);
            float targetSpeedPitch = MathUtil.randomLerp(7, 12);


            if (RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), aura.attackRange.getValue(), target)) {
                targetSpeedYaw = MathUtil.randomLerp(3, 4);
                targetSpeedPitch = MathUtil.randomLerp(1, 2);
            }


            float smoothFactor = 0.3f;

            currentSpeedYaw += (targetSpeedYaw - currentSpeedYaw) * smoothFactor;

            currentSpeedPitch += (targetSpeedPitch - currentSpeedPitch) * smoothFactor;
            RotationProcess.update(new Rotation((float) (rawYaw   ), (float) (rawPitch   )),
                    currentSpeedYaw,   currentSpeedPitch, MathUtil.random(360, 390), MathUtil.random(360, 390), (int)
                            MathUtil.random(3,5), 1, false);


    }

}