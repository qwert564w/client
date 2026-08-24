package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;

import java.util.concurrent.ThreadLocalRandom;

public class SpookyTimeRotation implements RotationAura {

    private float currentSpeedYaw = 24;
    private float currentSpeedPitch = 6;

    private float currentSpeedYawf = 0;
    private float currentSpeedPitchf = 0;

    @Override
    
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;


        Vec3d vec = AuraUtil.getVector2(target);

        float yawJitter = (float) (Math.cos(System.currentTimeMillis() / 200D) * MathUtil.randomLerp(6,8));
        float pitchJitter = (float) (Math.sin(System.currentTimeMillis() /200D) *MathUtil.randomLerp(6,8));


        float targetSpeedYaw = MathUtil.randomLerp(35, 40);
        float targetSpeedPitch = MathUtil.randomLerp(4, 8);







        float rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z)) ;
        float rawPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F) ;

        float smoothFactor = 0.3f;

        if(RayTraceUtil.rayTraceEntity(mc.player.getYaw(),mc.player.getPitch(),5,target)) {
            targetSpeedYaw = 0;
            targetSpeedPitch = 0;
        }

        currentSpeedYaw += (targetSpeedYaw - currentSpeedYaw) * smoothFactor;
        currentSpeedPitch += (targetSpeedPitch - currentSpeedPitch) * smoothFactor;


        RotationProcess.update(new Rotation((float) (rawYaw ), (float) (rawPitch )),
                currentSpeedYaw, currentSpeedPitch, MathUtil.random(360, 390), MathUtil.random(360, 390), (int)
                        MathUtil.random(3, 5), 1, false);


    }
}