package ru.white.manager.rotation;


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.events.orbit.EventPriority;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.rotation.FunTimeRotation;
import ru.white.module.impl.player.ClickHelper;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.aura.GCDUtil;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UAttack;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.player.MoveUtil;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class RotationProcess extends Component {

    public static RotationTask currentTask = RotationTask.IDLE;
    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;
    public static int currentPriority;
    public static int currentTimeout;
    public static int idleTicks;
    public static Rotation targetRotation;

    public static boolean isRotating() {
        return !currentTask.equals(currentTask.IDLE);
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);


        if (ServerUtil.isHolyWorld()) {
            stopRotation();
        } else {
            if (updateRotation(targetRotation, currentYawReturnSpeed, currentPitchReturnSpeed)) {
                stopRotation();
            }
        }
    }

    public static void resetParentTimeout() {
        currentTimeout = 0;
        currentTask = RotationTask.IDLE;
        currentPriority = 0;

        FreeLookUtil.setActive(false);
    }


    @EventHandler
    public void onEventMovement(EventMoveInput eventMoveInput) {

        if (currentTask.equals(RotationTask.RESET)) {
            MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), mc.gameRenderer.getCamera().getYaw());
        }

    }


    @EventHandler
    public void onEvent(EventTick event) {

        System.out.print("Мы переходим в новый проект https://t.me/LuminasMinecraft \n");

        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
            currentTask = (RotationTask.RESET);
        }


        if (currentTask.equals(RotationTask.RESET)) {
            if (ServerUtil.isHolyWorld() || ServerUtil.isCopyTime()) {
                stopRotation();
            } else {
                resetRotation();
            }
        }
        idleTicks++;
    }



    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed,
                              float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        if (currentPriority > priority) {
            return;
        }

        if (currentTask.equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookUtil.active = true;
        }

        currentYawSpeed = yawSpeed;
        currentPitchSpeed = pitchSpeed;
        currentYawReturnSpeed = yawReturnSpeed;
        currentPitchReturnSpeed = pitchReturnSpeed;
        currentTimeout = timeout;
        currentPriority = priority;
        currentTask = RotationTask.AIM;
        targetRotation = target;

        updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }


    static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null)
            return false;

        Rotation currentRotation = new Rotation(mc.player);


        float pitchDelta = targetRotation.pitch - currentRotation.pitch;
        float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);


        mc.player.setYaw(
                mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

        mc.player.setPitch(MathHelper.clamp(
                mc.player.getPitch()
                        + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                -90F, 90F));


        idleTicks = 0;
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }

    public void stopRotation() {
        currentTask = (RotationTask.IDLE);
        currentPriority = (0);
        FreeLookUtil.setActive(false);

    }


    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }

    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
    }
    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;
    public LivingEntity target = null;
    public Animation alpha = new Animation();
    public Animation alpha_2 = new Animation();

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );
    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });
    private static final RenderPipeline RING_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderLayer RING_FILL_LAYER = RenderLayer.of("ring_esp_fill",
            RenderSetup.builder(RING_FILL_PIPELINE).expectedBufferSize(1 << 16).build());
    private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("ring_esp_line",
            RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());



    private interface JitterPreset {
        float getYaw(long time);
        float getPitch(long time);
    }
    private final JitterPreset[] jitterPresets = new JitterPreset[] {

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 80D) + Math.cos(t / 35D) * 0.35) * 13);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 95D) + Math.sin(t / 42D) * 0.4) * 11);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 55D + Math.sin(t / 350D)) * 15);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 65D + Math.cos(t / 280D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 42D) * 0.7 + Math.sin(t / 120D)) * 17);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 58D) * 0.5 + Math.cos(t / 140D)) * 12);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 25D) * (9 + Math.sin(t / 300D) * 4));
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 48D) * (13 + Math.cos(t / 250D) * 3));
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 70D) + Math.sin(t / 17D) * 0.25) * 16);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 90D) + Math.cos(t / 23D) * 0.2) * 13);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 110D) * 0.8 + Math.sin(t / 38D) * 0.6) * 12);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 100D) * 0.7 + Math.cos(t / 29D) * 0.4) * 15);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 170D + 1.2) * 21);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 52D) + Math.sin(t / 240D)) * 10);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 36D) * 0.45 + Math.sin(t / 145D)) * 18);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 44D) * 0.35 + Math.cos(t / 180D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 60D + Math.cos(t / 180D)) * 14);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 73D + Math.sin(t / 210D)) * 17);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 48D) * 0.9 + Math.cos(t / 140D) * 0.5) * 15);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 84D) * 0.8 + Math.sin(t / 32D) * 0.3) * 12);
                }
            },
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

    private float currentSpeedYaw = 0;
    private float currentSpeedPitch = 0;

    float jitterIntensity = 0;


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

    int tick;


  /*  @EventHandler
    public void onTick(EventTick eventTick) {
        if (AttackAura.get().typeRotation.is("FunTime Snap")) {
            if (mc.player != null && mc.world != null) {

                LivingEntity target = AttackAura.target;

                boolean isActive = AttackAura.get().isEnabled() && target != null ;

                float rawYaw;
                float rawPitch;

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


                float activeSpeedYaw = MathUtil.randomLerp(30,35);
                float activeSpeedPitch = MathUtil.randomLerp(8,14);


                lastYawSpeed += (targetYawSpeed - lastYawSpeed) * 0.5F;
                lastPitchSpeed += (targetPitchSpeed - lastPitchSpeed) * 0.5F;

                float speed = lastYawSpeed + MathUtil.randomLerp(-2.5F, 2.5F);
                float speed2 = lastPitchSpeed + MathUtil.randomLerp(-1.0F, 1.0F);



                activeSpeedYaw = speed;
                activeSpeedPitch = speed2;

                AttackAura aura = AttackAura.get();



                if (isActive) {
                    FreeLookUtil.active = true;

                    jitterIntensity = Math.min(1.0f, jitterIntensity + 0.1f);
                } else {

                    jitterIntensity = Math.max(0.0f, jitterIntensity - 0.05f);
                }






                if (aura.justAttacked && System.currentTimeMillis() >= aura.attackFlickAt) {
                    aura.justAttacked = false;
                }

                if (isActive) {

                    Vec3d vec = target.getEntityPos().add(0.15F * Math.sin(System.currentTimeMillis() / 250D),target.getHeight() / 2 + (target.getHeight() /  4) *
                                            Math.cos(System.currentTimeMillis() / 200D),
                                    0.15F * Math.cos(System.currentTimeMillis() / 250D))
                            .subtract(mc.player.getEyePos())
                            .normalize();

                    rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    rawPitch = (float) MathHelper.clamp(
                            -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))),
                            -90F, 90F
                    );


                    float[] ranges = aura.getRanges();
                    ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
                    boolean canAttack = UAttack.shouldAttack(target, false, true, true, -MathUtil.randomInt(400,450), ranges);

                    activeSpeedYaw *= 1.4f;
                    activeSpeedPitch *= 2.7F;


                    if(canAttack) {
                        tick += MathUtil.randomInt(2,3);
                    }

                    boolean canAttackSnap = false;

                    if(tick != 0) {
                        canAttackSnap = true;
                        tick--;
                    }

                    if(canAttackSnap) {
                       AttackAura.lastPitch = rawPitch;
                       AttackAura.lastYaw = rawYaw;
                    }
                    if(!canAttackSnap) {
                        AttackAura.lastYaw = FreeLookUtil.freeYaw;
                        AttackAura.lastPitch = FreeLookUtil.freePitch;
                    }


                } else {



                    AttackAura.lastYaw = FreeLookUtil.freeYaw;
                    AttackAura.lastPitch = FreeLookUtil.freePitch;
                }


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

                lastYawJitter += (yawJitter - lastYawJitter) * 0.12F;
                lastPitchJitter += (pitchJitter - lastPitchJitter) * 0.12F;

                yawJitter = lastYawJitter * jitterIntensity;
                pitchJitter = lastPitchJitter *jitterIntensity;

                Rotation targetRotation = new Rotation(   AttackAura.lastYaw + yawJitter, AttackAura.lastPitch  + pitchJitter);
                Rotation currentRotation = new Rotation(mc.player);

                float pitchDelta = targetRotation.pitch - currentRotation.pitch;
                float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

                if (!isActive && jitterIntensity <= 0.0f && Math.abs(yawDelta) < 0.5f && Math.abs(pitchDelta) < 0.5f) {
                    FreeLookUtil.active = false;

                    return;
                }


                float clampedYaw = Math.min(Math.abs(yawDelta), activeSpeedYaw);
                float clampedPitch = Math.min(Math.abs(pitchDelta), activeSpeedPitch);


                mc.player.setYaw(
                        mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

                mc.player.setPitch(MathHelper.clamp(
                        mc.player.getPitch()
                                + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                        -90F, 90F));
            }
        }
       if (AttackAura.get().typeRotation.is("FunTime")) {
            if (mc.player != null && mc.world != null) {

                LivingEntity target = AttackAura.target;

                boolean isActive = AttackAura.get().isEnabled() && target != null ;

                float rawYaw;
                float rawPitch;

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


                float activeSpeedYaw = MathUtil.randomLerp(30,35);
                float activeSpeedPitch = MathUtil.randomLerp(8,14);


                lastYawSpeed += (targetYawSpeed - lastYawSpeed) * 0.5F;
                lastPitchSpeed += (targetPitchSpeed - lastPitchSpeed) * 0.5F;

                float speed = lastYawSpeed + MathUtil.randomLerp(-2.5F, 2.5F);
                float speed2 = lastPitchSpeed + MathUtil.randomLerp(-1.0F, 1.0F);

                activeSpeedYaw = speed;
                activeSpeedPitch = speed2;

                AttackAura aura = AttackAura.get();





                if (isActive) {
                    FreeLookUtil.active = true;

                    jitterIntensity = Math.min(1.0f, jitterIntensity + 0.1f);
                } else {

                    jitterIntensity = Math.max(0.0f, jitterIntensity - 0.05f);
                }


                if (aura.pitchFlickActive) {
                    if (System.currentTimeMillis() > aura.pitchFlickEndTime) {
                        aura.pitchFlickActive = false;
                    } else {
                        AttackAura.lastYaw += MathUtil.random(-15, 15);
                        AttackAura.lastPitch = -MathUtil.random(85, 90);
                    }
                }




                if (aura.justAttacked && System.currentTimeMillis() >= aura.attackFlickAt) {
                    aura.justAttacked = false;
                }

                if (isActive) {

                    activeSpeedYaw *= 1.8f;
                    activeSpeedPitch *= 1.6F;

                    Vec3d vec = target.getEntityPos().add(0.15F * Math.sin(System.currentTimeMillis() / 250D),target.getHeight() / 2 + (target.getHeight() /  4) *
                                    Math.cos(System.currentTimeMillis() / 200D),
                                    0.15F * Math.cos(System.currentTimeMillis() / 250D))
                            .subtract(mc.player.getEyePos())
                            .normalize();

                    rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    rawPitch = (float) MathHelper.clamp(
                            -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))),
                            -90F, 90F
                    );


                    float[] ranges = aura.getRanges();
                    ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
                    boolean canAttack = UAttack.shouldAttack(target, false, true, true, (long) -MathUtil.random(100,200), ranges) && !aura.pitchFlickActive;

                    if(canAttack) {
                        if (!aura.pitchFlickActive) AttackAura.lastPitch = rawPitch;
                        if (!aura.pitchFlickActive) AttackAura.lastYaw = rawYaw;
                    }


                } else {



                    AttackAura.lastYaw = FreeLookUtil.freeYaw;
                    AttackAura.lastPitch = FreeLookUtil.freePitch;
                }


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

                lastYawJitter += (yawJitter - lastYawJitter) * 0.9F;
                lastPitchJitter += (pitchJitter - lastPitchJitter) * 0.9F;

                float waveA = (float) Math.cos(System.currentTimeMillis() / 40D);
                float waveB = (float) Math.sin(System.currentTimeMillis() / 70D);

                float yawJitter2 = waveA * MathUtil.randomLerp(9, 17);
                float pitchJitter2 = waveB * MathUtil.randomLerp(4, 13);

                yawJitter = yawJitter2 * jitterIntensity;
                pitchJitter = pitchJitter2 *jitterIntensity;

                Rotation targetRotation = new Rotation(   AttackAura.lastYaw + yawJitter, AttackAura.lastPitch  + pitchJitter);
                Rotation currentRotation = new Rotation(mc.player);

                float pitchDelta = targetRotation.pitch - currentRotation.pitch;
                float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

                if (!isActive && jitterIntensity <= 0.0f && Math.abs(yawDelta) < 0.5f && Math.abs(pitchDelta) < 0.5f) {
                    FreeLookUtil.active = false;

                    return;
                }


                float clampedYaw = Math.min(Math.abs(yawDelta), activeSpeedYaw);
                float clampedPitch = Math.min(Math.abs(pitchDelta), activeSpeedPitch);


                mc.player.setYaw(
                        mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

                mc.player.setPitch(MathHelper.clamp(
                        mc.player.getPitch()
                                + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                        -90F, 90F));


            }
        }
    } */
    private void renderCorner(MatrixStack matrices,
                              VertexConsumer consumer,
                              float x,
                              float y,
                              float rotation,
                              float scale,
                              int c1,
                              int c2,
                              int c3,
                              int c4,
                              float alpha) {

        matrices.push();

        matrices.translate(x, y, 0);

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        matrices.scale(scale * 0.6F , scale * 0.6F , 1F);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        drawGradientQuad(
                consumer,
                mat,
                c1,
                c2,
                c3,
                c4,
                (int)(255 * alpha)
        );

        matrices.pop();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEventMovement(EventRender3D e) {
        AttackAura aura = AttackAura.get();

        alpha.update();

        LivingEntity currentTarget = AttackAura.target;

        if (currentTarget != null) {
            target = currentTarget;
        }

        if (mc.world == null || mc.player == null) return;

        alpha.run(currentTarget != null ? 1 : 0, 0.2F, Easings.SINE_OUT);
        float alphaPC = alpha.get();



        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);
        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Картинка")) {

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));

            alpha_2.update();
            alpha_2.run(hurtPC,0.12F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(185, 80, 80, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, alpha_2.get());
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor, alpha_2.get());
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, alpha_2.get());
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor,alpha_2.get());



            MatrixStack matrices = e.getMatrixStack();

            VertexConsumer consumer = immediate
                    .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/union.png")));


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            double x = lerpedPos.x;
            double y = lerpedPos.y;
            double z = lerpedPos.z;

            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

            long currentTimeMillis = System.currentTimeMillis();
            float rotate = (float) MathUtil.clamps(0, 360 * 2,
                    ((Math.sin(currentTimeMillis / (1000D)) + 1F) / 2F) * 360 * 2);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));



            Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
            float size = (0.7F );
            float offset = 0.3F * size;

            float hitAnim = alpha_2.get();

            long time = System.currentTimeMillis();
            float rotation = 0;

            float sizeA = 0.1F;

            float xan = 0.2F - 0.2F * alphaPC;


            renderCorner(matrices, consumer, offset + sizeA * hitAnim + xan, offset + sizeA * hitAnim + xan, 135, size, color, color2, color3, color4, alphaPC);      // ↖
            renderCorner(matrices, consumer, -offset - sizeA * hitAnim - xan, offset + sizeA * hitAnim + xan, -135   , size, color, color2, color3, color4, alphaPC); // ↗
            renderCorner(matrices, consumer, -offset - sizeA * hitAnim - xan, -offset - sizeA * hitAnim - xan, -45 , size, color, color2, color3, color4, alphaPC);// ↘
            renderCorner(matrices, consumer, offset + sizeA * hitAnim + xan, -offset - sizeA * hitAnim - xan, 45 , size, color, color2, color3, color4, alphaPC); // ↙






            matrices.pop();
        }



        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Кольцо")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));
            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0F * alphaPC));

            double duration = 1200;
            double elapsed  = System.currentTimeMillis() % duration;
            boolean side    = elapsed > duration / 2.0;
            double raw      = elapsed / (duration / 2.0);
            raw = side ? (raw - 1.0) : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height2 = target.getHeight() ;
            double eased  = (height2 / 1.7) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1) /0.7;

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float radius = (target.getWidth() - 0.1F) + 0.35F - 0.35F * alphaPC;
            float yBase  = (float)(height2 * progress);
            float yTop   = (float)(height2 * progress + eased);


            VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c0 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(seg ), alphaPC), redColor, hurtPC);
                int c1 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade((seg  )), alphaPC), redColor, hurtPC);
                float x0 = (float)(Math.cos(a0) * radius), z0 = (float)(Math.sin(a0) * radius);
                float x1 = (float)(Math.cos(a1) * radius), z1 = (float)(Math.sin(a1) * radius);
                fillBuf.vertex(matrix, x0, yBase, z0).color(ColorUtil.replAlpha(c0, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yBase, z1).color(ColorUtil.replAlpha(c1, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yTop,  z1).color(ColorUtil.replAlpha(c1, 0));
                fillBuf.vertex(matrix, x0, yTop,  z0).color(ColorUtil.replAlpha(c0, 0));
            }

            VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c =ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(1),0.7F),redColor,hurtPC), alphaPC);
                lineBuf.vertex(matrix, (float)(Math.cos(a0) * radius), yBase, (float)(Math.sin(a0) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
                lineBuf.vertex(matrix, (float)(Math.cos(a1) * radius), yBase, (float)(Math.sin(a1) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Духи")) {


            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  700;
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);


            int n2 = 3;
            int n3 = 12;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.1F;
                    float f3 = 0.6F;
                    float f4 = 0.4F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.3F * Math.sin(animationNurik + (float) j * 0.2F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.005F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;


                    int n7 = -20;
                    int n8 = 35;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);



                    n7 = (int) (-20  - 20 * 1.5F);
                    n8 = (int) (35 + 40 *1.5F);

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC*0.1F))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();

        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Кубики")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0f * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

            long speed_f = 40 ;

            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / speed_f;
            }
            currentTimeSpirits = currentTime;

            float time =animationNurik - 12 * alpha_2.get();
            int cound = 12;
            float width = target.getWidth() * 1.5f ;
            float sizeFI = (1f - 0.3F * alpha_2.get()) * alphaPC;

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(camera.getRotation());
                float gs = 0.6f * sizeFI;
                matrices.scale(gs, gs, gs);
                drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png"))),
                        matrices.peek().getPositionMatrix(),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        (int)(alphaPC * 0.35f * 255));
                matrices.pop();
            }

            // Pass 2: cube fills
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeFillTESP(immediate.getBuffer(RING_FILL_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 0.2f * 255)));
                matrices.pop();
            }

            // Pass 3: cube outlines
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeOutlineTESP(immediate.getBuffer(RING_LINE_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 255)));
                matrices.pop();
            }
        }

        if (aura.typeTargetESP.is("Куб")) {
            // осколки переживают смерть цели, поэтому рисуются вне проверки на таргет
            aura.renderTargetCubeFragments(e, immediate);

            if (alphaPC > 0.001f && target != null) {
                aura.renderTargetCube(e, target, alphaPC, immediate);
            }
        }


        immediate.draw();

    }

    private static void drawCubeFillTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // +Y
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Y
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // +X
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        // -X
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // +Z
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Z
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
    }

    private static void drawCubeOutlineTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // bottom ring
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m,  s, -s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m, -s, -s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // top ring
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s, -s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        buf.vertex(m, -s,  s,  s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        // verticals
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
    }


    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix,int color,int color2,int color3,int color4, int alpha) {

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color, alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color2, alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color3, alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color4, alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

}
