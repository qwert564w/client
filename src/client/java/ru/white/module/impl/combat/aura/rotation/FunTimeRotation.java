package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UAttack;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;

import java.util.concurrent.ThreadLocalRandom;

public class FunTimeRotation implements RotationAura {

    private long jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
    private long nextJitterSwitch = System.currentTimeMillis() + jitterSwitchTime;
    private int currentJitterPreset = 0;
    private int nextJitterPreset = 1;

    private long speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
    private long nextSpeedSwitch = System.currentTimeMillis() + speedSwitchTime;
    private int currentSpeedPreset = 0;
    private int nextSpeedPreset = 1;


    private static final long JITTER_SWITCH_TIME = 1000L;

    private float lastYawJitter;
    private float lastPitchJitter;

    private static final long SPEED_SWITCH_TIME = 2500L;

    private float lastYawSpeed;
    private float lastPitchSpeed;

    @Override
    
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {

        if (!mc.player.isSubmergedInWater()) {
            Vec3d vec = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox()).subtract(mc.player.getEyePos()).normalize();


            float rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
            float rawPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

            float randomBoga = (float) (MathUtil.randomLerp(-1,1) * MathUtil.random(-1,1) * MathUtil.randomInt(-2,2) * ThreadLocalRandom.current().nextDouble(-4.3535F,3.3553F));


            long currentTime = System.currentTimeMillis();
            int presetIndex = (int) ((currentTime / JITTER_SWITCH_TIME) % jitterPresets.length);

            if (currentTime >= nextJitterSwitch) {
                currentJitterPreset = nextJitterPreset;
                nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                while (nextJitterPreset == currentJitterPreset)
                    nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
                nextJitterSwitch = currentTime + jitterSwitchTime;
            }

            JitterPreset currentPreset = jitterPresets[currentJitterPreset];
            JitterPreset nextPreset = jitterPresets[nextJitterPreset];

            float blend = 1F - (nextJitterSwitch - currentTime) / (float) jitterSwitchTime;
            blend = MathHelper.clamp(blend, 0F, 1F);
            blend = blend * blend * (3F - 2F * blend);

            blend = blend * blend * (3F - 2F * blend);

            float currentYaw = currentPreset.getYaw(currentTime);
            float nextYaw = nextPreset.getYaw(currentTime);

            float currentPitch = currentPreset.getPitch(currentTime);
            float nextPitch = nextPreset.getPitch(currentTime);

            float yawJitter = MathHelper.lerp(blend, currentYaw, nextYaw);
            float pitchJitter = MathHelper.lerp(blend, currentPitch, nextPitch);

            lastYawJitter += (yawJitter - lastYawJitter) * 0.6F;
            lastPitchJitter += (pitchJitter - lastPitchJitter) * 0.6F;

            yawJitter = lastYawJitter;
            pitchJitter = lastPitchJitter;

            canAttack = UAttack.shouldAttack(target, false, true, true, (long) -MathUtil.random(150,250), ranges);

            long speedTime = System.currentTimeMillis();

            if (speedTime >= nextSpeedSwitch) {
                currentSpeedPreset = nextSpeedPreset;
                nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                while (nextSpeedPreset == currentSpeedPreset)
                    nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
                nextSpeedSwitch = speedTime + speedSwitchTime;
            }

            SpeedPreset currentSpeed = speedPresets[currentSpeedPreset];
            SpeedPreset nextSpeed = speedPresets[nextSpeedPreset];

            float speedBlend = 1F - (nextSpeedSwitch - speedTime) / (float) speedSwitchTime;
            speedBlend = MathHelper.clamp(speedBlend, 0F, 1F);
            speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);


            speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);

            float targetYawSpeed = MathHelper.lerp(
                    speedBlend,
                    currentSpeed.getYawSpeed(),
                    nextSpeed.getYawSpeed()
            );

            float targetPitchSpeed = MathHelper.lerp(
                    speedBlend,
                    currentSpeed.getPitchSpeed(),
                    nextSpeed.getPitchSpeed()
            );


            lastYawSpeed += (targetYawSpeed - lastYawSpeed) * 0.5F;
            lastPitchSpeed += (targetPitchSpeed - lastPitchSpeed) * 0.5F;

            float speed = lastYawSpeed + MathUtil.randomLerp(-2.5F, 2.5F);
            float speed2 = lastPitchSpeed + MathUtil.randomLerp(-1.0F, 1.0F);

            if (aura.pitchFlickActive) {
                if (System.currentTimeMillis() > aura.pitchFlickEndTime) {
                    aura.pitchFlickActive = false;
                } else {
                    AttackAura.lastYaw += MathUtil.random(-60, 60);
                }
            }


            if(canAttack) {
                if (!aura.pitchFlickActive) AttackAura.lastPitch = rawPitch;
                if (!aura.pitchFlickActive) AttackAura.lastYaw = rawYaw;
            }

            if (aura.justAttacked && System.currentTimeMillis() >= aura.attackFlickAt) {
                aura.justAttacked = false;
            }

            RotationProcess.update(new Rotation((float) (AttackAura.lastYaw + yawJitter), (float) (AttackAura.lastPitch + pitchJitter)),
                    speed,
                  speed2,
                    MathUtil.randomLerp(10.12481248124F, 20.412841824F),
                        MathUtil.randomLerp(10.12481248124F, 20.412841824F), 0, 1, false);
        }
    }

    private interface JitterPreset {
        float getYaw(long time);
        float getPitch(long time);
    }
    private final JitterPreset[] jitterPresets = new JitterPreset[] {

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 90D) * 12); }
                public float getPitch(long t) { return (float) (Math.sin(t / 90D) * 16); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 70D) * 12); }
                public float getPitch(long t) { return (float) (Math.cos(t / 80D) * 11); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 65D) * 14); }
                public float getPitch(long t) { return (float) (Math.sin(t / 90D) * 10); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 70D) * 12); }
                public float getPitch(long t) { return (float) (Math.sin(t / 111D) * 13); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 66D) * 12); }
                public float getPitch(long t) { return (float) (Math.cos(t / 66D) * 12); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 125D ) * 13.5F); }
                public float getPitch(long t) { return (float) (Math.cos(t / 135D) * 13.5F); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 90D) * 12); }
                public float getPitch(long t) { return (float) (Math.cos(t / 70D) * 7); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 240D) * 14); }
                public float getPitch(long t) { return (float) (Math.cos(t / 240D) * 14); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 133D) * 11); }
                public float getPitch(long t) { return (float) (Math.sin(t / 142D) * 11); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 35D) * 8); }
                public float getPitch(long t) { return (float) (Math.cos(t / 110D) * 18); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 130D + 0.8) * 20); }
                public float getPitch(long t) { return (float) (Math.sin(t / 95D) * 8); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 65D + 2.1) * 16); }
                public float getPitch(long t) { return (float) (Math.cos(t / 45D) * 14); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 42D) * 9); }
                public float getPitch(long t) { return (float) (Math.sin(t / 38D) * 17); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 160D) * 22); }
                public float getPitch(long t) { return (float) (Math.cos(t / 140D) * 5); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 48D + 1.2) * 13); }
                public float getPitch(long t) { return (float) (Math.sin(t / 68D + 0.4) * 15); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 28D) * 10); }
                public float getPitch(long t) { return (float) (Math.cos(t / 85D) * 20); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 100D) * 7); }
                public float getPitch(long t) { return (float) (Math.sin(t / 55D) * 22); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 52D + 2.7) * 17); }
                public float getPitch(long t) { return (float) (Math.cos(t / 92D + 1.3) * 11); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.cos(t / 74D) * 14); }
                public float getPitch(long t) { return (float) (Math.sin(t / 33D) * 13); }
            },

            new JitterPreset() {
                public float getYaw(long t) { return (float) (Math.sin(t / 115D + 0.5) * 19); }
                public float getPitch(long t) { return (float) (Math.cos(t / 58D + 2.4) * 16); }
            }
    };

    private interface SpeedPreset {
        float getYawSpeed();
        float getPitchSpeed();
    }

    private final SpeedPreset[] speedPresets = new SpeedPreset[]{

            new SpeedPreset() {
                public float getYawSpeed() { return 40F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 55F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 30F; }
                public float getPitchSpeed() { return 10F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 48F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 60F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 35F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 50F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 44F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 58F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 32F; }
                public float getPitchSpeed() { return 19F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 46F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 53F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 37F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 42F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 57F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 34F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 49F; }
                public float getPitchSpeed() { return 20F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 62F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 39F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 52F; }
                public float getPitchSpeed() { return 12F; }
            }
    };

}
