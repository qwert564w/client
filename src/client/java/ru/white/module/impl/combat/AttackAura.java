package ru.white.module.impl.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.Client;
import ru.white.manager.rotation.RotationProcess;
import ru.white.utils.animation.Easings;
import ru.white.manager.event_impl.*;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.impl.combat.aura.RotationType;
import ru.white.module.impl.utils.FakePlayer;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.screen.Menu;
import ru.white.utils.aura.AttackUtil;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.aura.UAttack;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import ru.white.utils.render.RenderUtil;

import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.util.Hand.MAIN_HAND;


@ModuleInfo(
        name = "Attack Aura",
        desc = "Автоматический наводится и атакует цель",
        category = Category.COMBAT,
        key = GLFW.GLFW_KEY_R
)
public class AttackAura extends Module {

    public static AttackAura get() {
        return Instance.get(AttackAura.class);
    }


    public SliderSetting attackRange = new SliderSetting(this, "Радиус атаки", 3.0F, 2.5F, 6, 0.1F);
    public SliderSetting preRange = new SliderSetting(this, "Радиус обнаружения", 1.0F, 0.0F, 3, 0.1F);

    public MultiBooleanSetting targets = new MultiBooleanSetting(this, "Кого атаковать",
            new BooleanSetting("Игроков", true),
            new BooleanSetting("Голых игроков", true),
            new BooleanSetting("Мобов", false),
            new BooleanSetting("Друзей", false));

    public ModeSetting typeRotation = new ModeSetting(this,"Тип наведения", "FunTime","SpookyTime","Default","Snap","HvH");
    public ModeSetting typeSnap = new ModeSetting(this,"Режим снапа", "360","Fov").setVisible(() -> typeRotation.is("Snap"));
    public SliderSetting fov = new SliderSetting(this, "Fov", 50.0F, 25.0F, 90.0F, 1.0F).setVisible(() -> typeRotation.is("Snap") && typeSnap.is("Fov"));

    public BooleanSetting fovRender = new BooleanSetting(this,"Отображать Fov",false).setVisible(() -> typeRotation.is("Snap") && typeSnap.is("Fov"));

    public ModeSetting typeSprint = new ModeSetting(this,"Тип спринта", "Packet","Silent","Legit");
    public ModeSetting typeMove = new ModeSetting(this,"Коррекция движения","Сфокусированная","Свободная");

    public ModeSetting typeTargetESP = new ModeSetting(this,"Отображение таргета","Картинка","Духи","Кольцо","Кубики","Куб","Не отображать");

    public SliderSetting cubeSize = new SliderSetting(this,"Размер куба",1.15F,0.6F,2.5F,0.05F).setVisible(() -> typeTargetESP.is("Куб"));
    public SliderSetting cubeRotateSpeed = new SliderSetting(this,"Скорость вращения куба",1.2F,0.2F,4.0F,0.05F).setVisible(() -> typeTargetESP.is("Куб"));
    public SliderSetting cubeThickness = new SliderSetting(this,"Толщина куба",2.2F,0.6F,5.0F,0.1F).setVisible(() -> typeTargetESP.is("Куб"));
    public BooleanSetting cubeColorOnHit = new BooleanSetting(this,"Цвет куба при ударе",true).setVisible(() -> typeTargetESP.is("Куб"));
    public BooleanSetting cubeGlow = new BooleanSetting(this,"Свечение куба",true).setVisible(() -> typeTargetESP.is("Куб"));

    public MultiBooleanSetting others = new MultiBooleanSetting(this, "Доп. проверки",
            new BooleanSetting("Умные криты", false),
            new BooleanSetting("Бить через блоки", false));

    public MultiBooleanSetting noattackto = new MultiBooleanSetting(this, "Не бить если",
            new BooleanSetting("Используешь еду", false),
            new BooleanSetting("Открыт контейнер", false));




    public static LivingEntity target = null;

    public float[] getRanges() {
        return new float[]{attackRange.getValue(), preRange.getValue()};
    }


    @EventHandler
    
    public void onEvent(EventUpdate e) {


        LivingEntity prevTarget = target;
        if ((target == null || !isValidTarget(target))) {
            updateTarget();
        }

        if(stoptick != 0) {
            stoptick--;
        }

        if (!checkToAttack() && target != null) {
            attackEntity();
        }


    }
    private boolean checkToAttack() {

        boolean baseCheck = mc.player.isUsingItem() && noattackto.getValue("Используешь еду");

        boolean screenCheck = noattackto.getValue("Открыт контейнер") && mc.currentScreen != null  && !(mc.currentScreen instanceof Menu);

        return baseCheck ||  screenCheck;
    }

    public void attackEntity() {
        if (AuraUtil.getStrictDistance(target) >= attackRange.getValue()) {
            return;
        }
        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};

        if (target == null) {


            return;
        }

        UAttack.antiMissesHittingUpdate(target, false, true, false);


        boolean canAttack = UAttack.shouldAttack(target, !typeRotation.is("HvH"), true, true, 0L, ranges);


        if (canAttack) {

            final Runnable[] shieldBreak = UAttack.hitShieldBreakTaskForUse(target, true),
                    shieldPressBypass = UAttack.resetShieldSilentTaskForUse(true),
                    skipSilentSprint = UAttack.skipSilentSprintingTaskForUse((typeSprint.is("Packet") || typeSprint.is("Silent")));
            final Runnable preHitSendCodeSingleTick = () -> {
                skipSilentSprint[0].run();
                shieldPressBypass[0].run();
                shieldBreak[0].run();
            }, postHitSendCodeSingleTick = () -> {
                shieldBreak[1].run();
                shieldPressBypass[1].run();
                skipSilentSprint[1].run();
            };




             UAttack.useEntity(target, preHitSendCodeSingleTick, postHitSendCodeSingleTick, MAIN_HAND,
                             false);


            justAttacked = true;
            attackFlickAt = System.currentTimeMillis() + 250;

            count = (count + 1) % 2;

            hitCount++;
            if (hitCount >= pitchFlickThreshold) {
                hitCount = 0;
                pitchFlickThreshold = ThreadLocalRandom.current().nextInt(15,19);
                pitchFlickActive = true;
                pitchFlickEndTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(50, 70);
            }

        }


    }

    public static int stoptick = 0;

    @EventHandler
    private void setCorrection(EventMoveInput eventMoveInput) {

        if (target != null && mc.player != null && mc.world != null) {
            if (UAttack.resetSprintTick(target, getRanges()) && target != null && !AttackUtil.hasMovementRestrictions() && typeSprint.is("Legit")) {
                eventMoveInput.setForward(0);
                eventMoveInput.setStrafe(0);
            }
        }
        if (target != null && mc.player != null && mc.world != null && typeMove.is("Свободная")) {


            MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), mc.gameRenderer.getCamera().getYaw());
        }


            if (target != null && mc.player != null && mc.world != null && typeMove.is("Сфокусированная") && ServerUtil.isFunTime()) {
                if (!mc.player.isSubmergedInWater()) {
                    if (ServerUtil.isCopyTime()) {

                        Vec3d vec3d = AuraUtil.getVector2(target);
                        float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
                        MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);


                    } else {
                        Vec3d vec = target.getEntityPos().subtract(mc.player.getEyePos()).normalize();

                        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                        MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);

                    }
                }
            }
            if (target != null && mc.player != null && mc.world != null && typeMove.is("Сфокусированная") && !ServerUtil.isFunTime()) {

                if (ServerUtil.isCopyTime()) {

                    Vec3d vec3d = AuraUtil.getVector2(target);
                    float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
                    MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);


                } else {
                    Vec3d vec = target.getEntityPos().subtract(mc.player.getEyePos()).normalize();

                    float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);

                }

        }
    }


public static long lastLookUpTime = 0;
public static long nextLookUpDelay = ThreadLocalRandom.current().nextLong(90000, 180000);
public static boolean isLookingUp = false;
public static long lookUpStartTime = 0;
public static int lookUpDuration = 0;
    public static long lastAttackTime = 0L;


    public float tick = 0;
    public static float lastYaw;
    public static float lastPitch;
    public Vec3d targetPoint;
    public Vec3d currentPoint;
    private int count;
    public boolean justAttacked = false;
    public long attackFlickAt = 0;

    private int hitCount = 0;
    private int pitchFlickThreshold = ThreadLocalRandom.current().nextInt(14,19);
    public boolean pitchFlickActive = false;
    public long pitchFlickEndTime = 0;

    public TimerUtil timeSped1 = new TimerUtil();
    public TimerUtil timeSped2 = new TimerUtil();


    @EventHandler
    
    public void onRotate(EventTick e) {
        if (target == null || mc.player == null || mc.world == null) {
            return;
        }
        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
        boolean canAttack = UAttack.shouldAttack(target, false, true, true, -350, ranges);

        for (RotationType type : RotationType.values()) {
            if (typeRotation.is(type.getName())) {
                type.getRotation().onRotation(this, target, ranges, canAttack);
                break;
            }
        }
        if (typeRotation.is("FunTime")) {

            Vec3d vec3d = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox(), false).add(0.2F * Math.sin(System.currentTimeMillis() / 250D), (target.getHeight() / 6)  * Math.cos(System.currentTimeMillis() / 850D), 0).subtract(mc.player.getEyePos());




            long currentTime = System.currentTimeMillis();

            if (!isLookingUp &&
                    currentTime - lastLookUpTime >= nextLookUpDelay) {

                isLookingUp = true;
                lookUpStartTime = currentTime;
                lookUpDuration = ThreadLocalRandom.current().nextInt(300, 400);
                lastLookUpTime = currentTime;
                nextLookUpDelay =
                        ThreadLocalRandom.current().nextLong(9100, 11200);
            }

            boolean fastspeed = false;
            if (isLookingUp && currentTime - lookUpStartTime >= lookUpDuration) {
                isLookingUp = false;
            }
            if(currentTime - lookUpStartTime >= lookUpDuration + 70L) {
                fastspeed =  true;
            }



            float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
            float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec3d.y, Math.hypot(vec3d.x, vec3d.z))), -90, 90);

            float speedYAW =  MathUtil.randomLerp(44,66);
            float speedPit = !fastspeed ?  MathUtil.randomLerp(120,170) : MathUtil.randomLerp(4,7   );


            if(canAttack && fastspeed) {
                tick= MathUtil.random(0,2);
            }
            boolean attack = false;
            if(tick > 0) {
                attack = true;
                tick --;
            }
            float randomAttackShift = 0;
            float waveA = (float) Math.cos(System.currentTimeMillis() / 70D);
            float waveB = (float) Math.sin(System.currentTimeMillis() / 110D);

            if(attack) {
                lastYaw = yaw;
                lastPitch = pitch;
            }


            float yawJitter = waveA *  MathUtil.randomLerp(6, 15) ;
            float pitchJitter = waveB *  MathUtil.randomLerp(6,12) ;

            float finalPitch = isLookingUp ? -MathUtil.randomLerp(85,90) : (lastPitch);

            RotationProcess.update(new Rotation(lastYaw + yawJitter,finalPitch + pitchJitter), speedYAW,
                    speedPit, MathUtil.randomInt(35,45), MathUtil.randomInt(19,45), MathUtil.randomInt(0,3), 15, false);


        }
        
        
    }



    @EventHandler
    private void onResetOnWorld(WorldLoadEvent event) {
        if(mc.player != null && mc.gameRenderer.getCamera() != null && mc.world != null) {
            lastPitch = mc.gameRenderer.getCamera().getPitch();
            lastYaw = mc.gameRenderer.getCamera().getYaw();

        }
        target = null;
        targetPoint = null;
        currentPoint = null;
        hitCount = 0;
        pitchFlickActive = false;
        resetCubeState();

    }


    @Override
    
    public void onDisable() {
        super.onDisable();
        if(mc.player != null && mc.gameRenderer.getCamera() != null && mc.world != null) {
            lastPitch = mc.gameRenderer.getCamera().getPitch();
            lastYaw = mc.gameRenderer.getCamera().getYaw();
        }
        target = null;
        targetPoint = null;
        currentPoint = null;
        hitCount = 0;
        pitchFlickActive = false;
        resetCubeState();

    }




    private void updateTarget() {
        if (FakePlayer.fakePlayer != null
                && FakePlayer.fakePlayer.isAlive()
                && mc.player.distanceTo(FakePlayer.fakePlayer) <= auraDist()) {
            target = FakePlayer.fakePlayer;
            return;
        }

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


    private float auraDist() {
        return attackRange.getValue() + preRange.getValue();
    }

    public double getTargetFov(LivingEntity entity) {
        Vec3d targetPos = entity.getEntityPos().add(0, entity.getHeight() * 0.5, 0);
        Vec3d vec = targetPos.subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        float yawDelta = MathHelper.wrapDegrees(yaw - Rotation.cameraYaw());
        float pitchDelta = pitch - Rotation.cameraPitch();
        return Math.hypot(yawDelta, pitchDelta);
    }


    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof ClientPlayerEntity) return false;

        if (mc.player.distanceTo(entity) > auraDist()) return false;

        if (!others.getValue("Бить через блоки")) {
            if (!mc.player.canSee(entity)) return false;
        }

        if (entity instanceof PlayerEntity p) {
            if (AntiBot.getInstance().isBot(p)) return false;
            if (Client.get().friendManager().isFriend(p.getName().getString()) && !targets.getValue("Друзей")) return false;
        }
        if (entity instanceof PlayerEntity && !targets.getValue("Игроков")) return false;

        if (entity instanceof PlayerEntity && entity.getArmorVisibility() <= 0 && !targets.getValue("Голых игроков")) return false;

        if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) return false;

        if ((entity instanceof Monster
                || entity instanceof SlimeEntity
                || entity instanceof VillagerEntity
                || entity instanceof AnimalEntity
                || entity instanceof FishEntity
                || entity instanceof SchoolingFishEntity
                || entity instanceof CodEntity
                || entity instanceof SalmonEntity
                || entity instanceof TropicalFishEntity
                || entity instanceof PufferfishEntity
                || entity instanceof SquidEntity
                || entity instanceof GlowSquidEntity
                || entity instanceof DolphinEntity
                || entity instanceof TurtleEntity
                || entity instanceof AxolotlEntity)
                && !targets.getValue("Мобов")) {
            return false;
        }


        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }

    @EventHandler
    public void onDisplay(VisualRotEvent e) {


    }

    @EventHandler
    public void onDisplay(EventDisplay e) {

        if ((mc.player != null && mc.world != null && target != null)) {



            if (!(typeRotation.is("Snap") && typeSnap.is("Fov") &&  fovRender.getValue() ) || mc.player == null || mc.world == null) return;


            float targetScale = 2F;
            float currentScale = (float) mc.getWindow().getScaleFactor();
            float scaleFix = targetScale / currentScale;

            int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
            int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

            float screenW = screenWidth;
            float screenH = screenHeight;
            float cx = screenW / 2f;
            float cy = screenH / 2f;

            float m00 = RenderUtil.Render3D.lastProjMat.m00();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 10D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F));
            float pixelRadius = (float) (Math.tan(Math.toRadians(fov.getValue() - 15)) * (screenW / 2f) * m00);
            float diameter = pixelRadius * 2f ;
            float x = cx - pixelRadius;
            float y = cy - pixelRadius;

            boolean hasTarget = target != null;
            int baseAlpha = 255;


            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.getColor(255), 255), redColor, hurtPC);


            RenderUtil.Render2D.outline(x, y, diameter, diameter, 1, ColorUtil.replAlpha(color, 0.1F), pixelRadius);
        }
    }

    // ─────────────────────────── таргет-есп «Куб» ───────────────────────────

    private static final RenderPipeline CUBE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/aura_target_cube"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer CUBE_LAYER = RenderLayer.of(
            "aura_target_cube",
            RenderSetup.builder(CUBE_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 14)
                    .build()
    );

    private float cubeRotationY;
    private float cubeRotationX;
    private float cubeTargetY;
    private float cubeTargetX;
    private float cubeHitFlash;
    private int cubePrevHurtTime;
    private long cubeLastUpdate = System.currentTimeMillis();
    private LivingEntity cubeTrackedTarget;
    private Vec3d cubeLastCenter;
    private float cubeLastHalf = 0.6F;
    private double cubeLastFeetY;
    private boolean cubeShattered;
    private final java.util.List<CubeFragment> cubeFragments = new java.util.ArrayList<>();
    private final java.util.Random cubeRandom = new java.util.Random();
    private Matrix4f cubeMatrix;

    @EventHandler
    public void onCubeTick(EventTick event) {
        if (!typeTargetESP.is("Куб")) return;

        updateCubeFragments();

        LivingEntity trackedTarget = cubeTrackedTarget;
        if (trackedTarget == null) {
            return;
        }

        boolean dead = trackedTarget.isRemoved()
                || trackedTarget.getHealth() <= 0.0F
                || trackedTarget.isDead();
        if (dead) {
            if (!cubeShattered && cubeLastCenter != null) {
                shatterCube();
            }
            cubeShattered = true;
            cubeTrackedTarget = null;
            cubePrevHurtTime = 0;
            return;
        }

        int hurt = trackedTarget.hurtTime;
        if (hurt > cubePrevHurtTime) {
            int quartersY = 1 + cubeRandom.nextInt(3);
            int quartersX = cubeRandom.nextInt(2);
            float directionY = cubeRandom.nextBoolean() ? 1.0F : -1.0F;
            float directionX = cubeRandom.nextBoolean() ? 1.0F : -1.0F;
            cubeTargetY += directionY * 90.0F * quartersY;
            cubeTargetX += directionX * 90.0F * quartersX;
            cubeHitFlash = 1.0F;
        }
        cubePrevHurtTime = hurt;
    }

    public void resetCubeState() {
        cubeRotationY = 0.0F;
        cubeRotationX = 0.0F;
        cubeTargetY = 0.0F;
        cubeTargetX = 0.0F;
        cubeHitFlash = 0.0F;
        cubePrevHurtTime = 0;
        cubeLastUpdate = System.currentTimeMillis();
        cubeTrackedTarget = null;
        cubeLastCenter = null;
        cubeLastHalf = 0.6F;
        cubeLastFeetY = 0.0D;
        cubeShattered = false;
        cubeFragments.clear();
        cubeMatrix = null;
    }

    private float[][] buildCubeArms(float half) {
        float armLength = half * 0.46F;
        float inner = half - armLength;
        float[][] arms = new float[24][];
        int index = 0;
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    float cornerX = sideX * half;
                    float cornerY = sideY * half;
                    float cornerZ = sideZ * half;
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, sideX * inner, cornerY, cornerZ};
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, cornerX, sideY * inner, cornerZ};
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, cornerX, cornerY, sideZ * inner};
                }
            }
        }
        return arms;
    }

    public void renderTargetCube(EventRender3D event, LivingEntity renderTarget, float alphaProgress,
                                 VertexConsumerProvider.Immediate immediate) {
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d feetPos = renderTarget.getLerpedPos(event.getTickDelta());
        float entityHeight = renderTarget.getHeight();
        float entityWidth = renderTarget.getWidth();

        if (renderTarget.isAlive()) {
            if (cubeTrackedTarget != renderTarget) {
                cubePrevHurtTime = renderTarget.hurtTime;
            }
            cubeTrackedTarget = renderTarget;
            cubeShattered = false;
        }

        float appear = (float) Easings.SINE_IN_OUT.ease(MathHelper.clamp(alphaProgress, 0.0F, 1.0F));
        if (appear <= 0.001F) {
            return;
        }

        float half = (entityWidth * 0.5F + 0.08F)
                * cubeSize.getValue()
                * (0.55F + 0.45F * appear);
        Vec3d center = feetPos.add(0.0D, entityHeight * 0.5D, 0.0D);
        cubeLastCenter = center;
        cubeLastHalf = half;
        cubeLastFeetY = feetPos.y;

        long now = System.currentTimeMillis();
        float deltaTime = MathHelper.clamp((now - cubeLastUpdate) / 1000.0F, 0.0F, 0.1F);
        cubeLastUpdate = now;
        float ease = 1.0F - (float) Math.exp(-(6.0F + cubeRotateSpeed.getValue() * 3.0F) * deltaTime);
        cubeRotationY += (cubeTargetY - cubeRotationY) * ease;
        cubeRotationX += (cubeTargetX - cubeRotationX) * ease;
        if (Math.abs(cubeTargetY - cubeRotationY) < 0.05F) {
            cubeRotationY = cubeTargetY;
        }
        if (Math.abs(cubeTargetX - cubeRotationX) < 0.05F) {
            cubeRotationX = cubeTargetX;
        }
        cubeHitFlash = Math.max(0.0F, cubeHitFlash - deltaTime * 2.2F);

        int baseColor = getCubeEspColor();
        int color = baseColor;
        if (cubeColorOnHit.getValue() && cubeHitFlash > 0.0F) {
            color = blendCubeColor(baseColor, ColorUtil.getColor(255, 25, 25, 255), cubeHitFlash);
        }
        color = multiplyCubeAlpha(replaceCubeAlpha(color, 190), appear);

        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(cubeRotationY))
                .rotateX((float) Math.toRadians(cubeRotationX));

        float coreHalf = getCubeDistanceScale(cameraPos, center.x, center.y, center.z)
                * cubeThickness.getValue() * 0.5F;
        VertexConsumer consumer = immediate.getBuffer(CUBE_LAYER);
        double relativeX = center.x - cameraPos.x;
        double relativeY = center.y - cameraPos.y;
        double relativeZ = center.z - cameraPos.z;

        cubeMatrix = event.getMatrixStack().peek().getPositionMatrix();
        for (float[] arm : buildCubeArms(half)) {
            drawCubeEdgeBar(consumer, rotation, relativeX, relativeY, relativeZ, arm, coreHalf, color);
        }
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    drawCornerCube(consumer, rotation, relativeX, relativeY, relativeZ,
                            sideX * half, sideY * half, sideZ * half, coreHalf, color);
                }
            }
        }
        immediate.draw(CUBE_LAYER);

        if (cubeGlow.getValue()) {
            renderCubeGlow(event, rotation, relativeX, relativeY, relativeZ,
                    half, coreHalf, baseColor, appear, immediate);
        }
    }

    private void renderCubeGlow(EventRender3D event, Quaternionf rotation,
                                double relativeX, double relativeY, double relativeZ,
                                float half, float coreHalf, int baseColor, float appear,
                                VertexConsumerProvider.Immediate immediate) {
        RenderLayer glowLayer = RotationProcess.ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png"));
        VertexConsumer glowConsumer = immediate.getBuffer(glowLayer);
        MatrixStack matrices = event.getMatrixStack();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();
        int glowColor = multiplyCubeAlpha(replaceCubeAlpha(baseColor, 55), 0.45F * appear);
        float glowSize = coreHalf * 5.0F;

        for (float[] arm : buildCubeArms(half)) {
            for (int index = 0; index <= 2; index++) {
                float progress = index * 0.5F;
                float localX = arm[0] + (arm[3] - arm[0]) * progress;
                float localY = arm[1] + (arm[4] - arm[1]) * progress;
                float localZ = arm[2] + (arm[5] - arm[2]) * progress;
                Vector3f point = rotation.transform(new Vector3f(localX, localY, localZ));
                drawCubeGlowSprite(glowConsumer, matrices, cameraRotation,
                        relativeX + point.x, relativeY + point.y, relativeZ + point.z,
                        glowSize, glowColor);
            }
        }
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    Vector3f point = rotation.transform(
                            new Vector3f(sideX * half, sideY * half, sideZ * half)
                    );
                    drawCubeGlowSprite(glowConsumer, matrices, cameraRotation,
                            relativeX + point.x, relativeY + point.y, relativeZ + point.z,
                            glowSize * 1.2F, glowColor);
                }
            }
        }
        immediate.draw(glowLayer);
    }

    private void drawCubeGlowSprite(VertexConsumer consumer, MatrixStack matrices,
                                    Quaternionf cameraRotation, double x, double y, double z,
                                    float size, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(cameraRotation);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        cubeGlowVertex(consumer, matrix, -size, -size, 0.0F, 0.0F, 1.0F, color);
        cubeGlowVertex(consumer, matrix, size, -size, 0.0F, 1.0F, 1.0F, color);
        cubeGlowVertex(consumer, matrix, size, size, 0.0F, 1.0F, 0.0F, color);
        cubeGlowVertex(consumer, matrix, -size, size, 0.0F, 0.0F, 0.0F, color);
        matrices.pop();
    }

    private void cubeGlowVertex(VertexConsumer consumer, Matrix4f matrix,
                                float x, float y, float z, float u, float v, int color) {
        consumer.vertex(matrix, x, y, z)
                .color(color)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F);
    }

    private void drawCornerCube(VertexConsumer consumer, Quaternionf rotation,
                                double relativeX, double relativeY, double relativeZ,
                                float centerX, float centerY, float centerZ,
                                float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f n000 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY - thickness, centerZ - thickness));
        Vector3f n100 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY - thickness, centerZ - thickness));
        Vector3f n110 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY + thickness, centerZ - thickness));
        Vector3f n010 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY + thickness, centerZ - thickness));
        Vector3f n001 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY - thickness, centerZ + thickness));
        Vector3f n101 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY - thickness, centerZ + thickness));
        Vector3f n111 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY + thickness, centerZ + thickness));
        Vector3f n011 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY + thickness, centerZ + thickness));

        faceCubeQuad(consumer, cubeMatrix, n000, n100, n110, n010, color);
        faceCubeQuad(consumer, cubeMatrix, n001, n011, n111, n101, color);
        faceCubeQuad(consumer, cubeMatrix, n000, n001, n101, n100, color);
        faceCubeQuad(consumer, cubeMatrix, n010, n110, n111, n011, color);
        faceCubeQuad(consumer, cubeMatrix, n000, n010, n011, n001, color);
        faceCubeQuad(consumer, cubeMatrix, n100, n101, n111, n110, color);
    }

    private void drawCubeEdgeBar(VertexConsumer consumer, Quaternionf rotation,
                                 double relativeX, double relativeY, double relativeZ,
                                 float[] arm, float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f start = new Vector3f(arm[0], arm[1], arm[2]);
        Vector3f end = new Vector3f(arm[3], arm[4], arm[5]);
        Vector3f direction = new Vector3f(end).sub(start);

        Vector3f firstNormal;
        Vector3f secondNormal;
        if (Math.abs(direction.x) > 1.0E-5F) {
            firstNormal = new Vector3f(0.0F, thickness, 0.0F);
            secondNormal = new Vector3f(0.0F, 0.0F, thickness);
        } else if (Math.abs(direction.y) > 1.0E-5F) {
            firstNormal = new Vector3f(thickness, 0.0F, 0.0F);
            secondNormal = new Vector3f(0.0F, 0.0F, thickness);
        } else {
            firstNormal = new Vector3f(thickness, 0.0F, 0.0F);
            secondNormal = new Vector3f(0.0F, thickness, 0.0F);
        }

        Vector3f start00 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).sub(firstNormal).sub(secondNormal));
        Vector3f start10 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).add(firstNormal).sub(secondNormal));
        Vector3f start11 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).add(firstNormal).add(secondNormal));
        Vector3f start01 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).sub(firstNormal).add(secondNormal));
        Vector3f end00 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).sub(firstNormal).sub(secondNormal));
        Vector3f end10 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).add(firstNormal).sub(secondNormal));
        Vector3f end11 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).add(firstNormal).add(secondNormal));
        Vector3f end01 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).sub(firstNormal).add(secondNormal));

        emitCubeBox(consumer, start00, start10, start11, start01, end00, end10, end11, end01, color);
    }

    private Vector3f cubeLocalToRelative(Quaternionf rotation,
                                         double relativeX, double relativeY, double relativeZ,
                                         Vector3f local) {
        rotation.transform(local);
        local.add((float) relativeX, (float) relativeY, (float) relativeZ);
        return local;
    }

    private void emitCubeBox(VertexConsumer consumer,
                             Vector3f start00, Vector3f start10, Vector3f start11, Vector3f start01,
                             Vector3f end00, Vector3f end10, Vector3f end11, Vector3f end01,
                             int color) {
        faceCubeQuad(consumer, cubeMatrix, start00, end00, end10, start10, color);
        faceCubeQuad(consumer, cubeMatrix, start10, end10, end11, start11, color);
        faceCubeQuad(consumer, cubeMatrix, start11, end11, end01, start01, color);
        faceCubeQuad(consumer, cubeMatrix, start01, end01, end00, start00, color);
    }

    private void faceCubeQuad(VertexConsumer consumer, Matrix4f matrix,
                              Vector3f first, Vector3f second, Vector3f third, Vector3f fourth,
                              int color) {
        consumer.vertex(matrix, first.x, first.y, first.z).color(color);
        consumer.vertex(matrix, second.x, second.y, second.z).color(color);
        consumer.vertex(matrix, third.x, third.y, third.z).color(color);
        consumer.vertex(matrix, fourth.x, fourth.y, fourth.z).color(color);
    }

    private void shatterCube() {
        float half = cubeLastHalf;
        Vec3d center = cubeLastCenter;
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(cubeRotationY))
                .rotateX((float) Math.toRadians(cubeRotationX));
        double groundY = cubeLastFeetY + 0.05D;

        for (float[] arm : buildCubeArms(half)) {
            Vector3f start = rotation.transform(new Vector3f(arm[0], arm[1], arm[2]));
            Vector3f end = rotation.transform(new Vector3f(arm[3], arm[4], arm[5]));
            Vec3d worldStart = center.add(start.x, start.y, start.z);
            Vec3d worldEnd = center.add(end.x, end.y, end.z);
            Vec3d fragmentCenter = worldStart.add(worldEnd).multiply(0.5D);
            Vector3f offset = new Vector3f(
                    (float) (worldEnd.x - fragmentCenter.x),
                    (float) (worldEnd.y - fragmentCenter.y),
                    (float) (worldEnd.z - fragmentCenter.z)
            );

            Vec3d outward = fragmentCenter.subtract(center);
            if (outward.lengthSquared() < 1.0E-6D) {
                outward = new Vec3d(cubeRandom.nextDouble() - 0.5D, 0.0D,
                        cubeRandom.nextDouble() - 0.5D);
            }
            outward = outward.normalize();
            double speed = 0.12D + cubeRandom.nextDouble() * 0.14D;
            Vec3d velocity = outward.multiply(speed).add(
                    (cubeRandom.nextDouble() - 0.5D) * 0.06D,
                    0.08D + cubeRandom.nextDouble() * 0.16D,
                    (cubeRandom.nextDouble() - 0.5D) * 0.06D
            );

            Vector3f axis = new Vector3f(
                    cubeRandom.nextFloat() - 0.5F,
                    cubeRandom.nextFloat() - 0.5F,
                    cubeRandom.nextFloat() - 0.5F
            );
            if (axis.lengthSquared() < 1.0E-6F) {
                axis.set(0.0F, 1.0F, 0.0F);
            }
            axis.normalize();
            float angularSpeed = 0.12F + cubeRandom.nextFloat() * 0.28F;
            int maxAge = 50 + cubeRandom.nextInt(30);
            cubeFragments.add(new CubeFragment(fragmentCenter, offset, velocity, axis,
                    angularSpeed, groundY, maxAge));
        }
    }

    private void updateCubeFragments() {
        java.util.Iterator<CubeFragment> iterator = cubeFragments.iterator();
        while (iterator.hasNext()) {
            CubeFragment fragment = iterator.next();
            fragment.previousCenter = fragment.center;
            fragment.previousOffset = new Vector3f(fragment.offset);
            fragment.center = fragment.center.add(fragment.velocity);
            fragment.velocity = new Vec3d(
                    fragment.velocity.x * 0.98D,
                    fragment.velocity.y - 0.028D,
                    fragment.velocity.z * 0.98D
            );

            if (fragment.center.y <= fragment.groundY && fragment.velocity.y < 0.0D) {
                fragment.center = new Vec3d(fragment.center.x, fragment.groundY, fragment.center.z);
                fragment.velocity = new Vec3d(
                        fragment.velocity.x * 0.55D,
                        -fragment.velocity.y * 0.38D,
                        fragment.velocity.z * 0.55D
                );
                fragment.angularSpeed *= 0.6F;
            }

            Quaternionf spin = new Quaternionf().fromAxisAngleRad(
                    fragment.axis.x, fragment.axis.y, fragment.axis.z, fragment.angularSpeed
            );
            fragment.offset = spin.transform(new Vector3f(fragment.offset));
            fragment.age++;
            if (fragment.age >= fragment.maxAge) {
                iterator.remove();
            }
        }
    }

    /** Осколки живут после смерти цели, поэтому рисуются отдельно от самого куба. */
    public void renderTargetCubeFragments(EventRender3D event, VertexConsumerProvider.Immediate immediate) {
        if (cubeFragments.isEmpty()) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        int baseColor = getCubeEspColor();
        VertexConsumer consumer = immediate.getBuffer(CUBE_LAYER);
        cubeMatrix = event.getMatrixStack().peek().getPositionMatrix();
        float delta = event.getTickDelta();

        for (CubeFragment fragment : cubeFragments) {
            float life = 1.0F - (float) fragment.age / Math.max(1, fragment.maxAge);
            float fade = life > 0.3F ? 1.0F : Math.max(0.0F, life / 0.3F);
            if (fade <= 0.0F) {
                continue;
            }

            Vec3d center = new Vec3d(
                    MathHelper.lerp(delta, fragment.previousCenter.x, fragment.center.x),
                    MathHelper.lerp(delta, fragment.previousCenter.y, fragment.center.y),
                    MathHelper.lerp(delta, fragment.previousCenter.z, fragment.center.z)
            );
            Vector3f offset = new Vector3f(fragment.previousOffset).lerp(fragment.offset, delta);
            double relativeX = center.x - cameraPos.x;
            double relativeY = center.y - cameraPos.y;
            double relativeZ = center.z - cameraPos.z;
            float coreHalf = getCubeDistanceScale(cameraPos, center.x, center.y, center.z)
                    * cubeThickness.getValue() * 0.5F;
            int color = multiplyCubeAlpha(replaceCubeAlpha(baseColor, 255), fade);

            drawCubeFragmentBar(consumer,
                    (float) (relativeX - offset.x), (float) (relativeY - offset.y),
                    (float) (relativeZ - offset.z),
                    (float) (relativeX + offset.x), (float) (relativeY + offset.y),
                    (float) (relativeZ + offset.z),
                    coreHalf, color);
        }
        immediate.draw(CUBE_LAYER);
    }

    private void drawCubeFragmentBar(VertexConsumer consumer,
                                     float startX, float startY, float startZ,
                                     float endX, float endY, float endZ,
                                     float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f start = new Vector3f(startX, startY, startZ);
        Vector3f end = new Vector3f(endX, endY, endZ);
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() < 1.0E-10F) {
            return;
        }
        direction.normalize();
        Vector3f reference = Math.abs(direction.y) < 0.99F
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f firstNormal = new Vector3f();
        direction.cross(reference, firstNormal).normalize().mul(thickness);
        Vector3f secondNormal = new Vector3f();
        direction.cross(firstNormal, secondNormal).normalize().mul(thickness);

        Vector3f start00 = new Vector3f(start).sub(firstNormal).sub(secondNormal);
        Vector3f start10 = new Vector3f(start).add(firstNormal).sub(secondNormal);
        Vector3f start11 = new Vector3f(start).add(firstNormal).add(secondNormal);
        Vector3f start01 = new Vector3f(start).sub(firstNormal).add(secondNormal);
        Vector3f end00 = new Vector3f(end).sub(firstNormal).sub(secondNormal);
        Vector3f end10 = new Vector3f(end).add(firstNormal).sub(secondNormal);
        Vector3f end11 = new Vector3f(end).add(firstNormal).add(secondNormal);
        Vector3f end01 = new Vector3f(end).sub(firstNormal).add(secondNormal);
        emitCubeBox(consumer, start00, start10, start11, start01,
                end00, end10, end11, end01, color);
    }

    private float getCubeDistanceScale(Vec3d cameraPos, double worldX, double worldY, double worldZ) {
        double deltaX = worldX - cameraPos.x;
        double deltaY = worldY - cameraPos.y;
        double deltaZ = worldZ - cameraPos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        return (float) Math.max(0.1D, distance * 0.007D);
    }

    private int getCubeEspColor() {
        int color = ColorUtil.getClientColor1(1);
        if (((color >> 24) & 0xFF) == 0) {
            color |= 0xFF000000;
        }
        return color;
    }

    private int multiplyCubeAlpha(int color, float multiplier) {
        int alpha = MathHelper.clamp((int) (((color >> 24) & 0xFF) * multiplier), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int replaceCubeAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private int blendCubeColor(int first, int second, float factor) {
        float clampedFactor = MathHelper.clamp(factor, 0.0F, 1.0F);
        int firstRed = (first >> 16) & 0xFF;
        int firstGreen = (first >> 8) & 0xFF;
        int firstBlue = first & 0xFF;
        int firstAlpha = (first >> 24) & 0xFF;
        int secondRed = (second >> 16) & 0xFF;
        int secondGreen = (second >> 8) & 0xFF;
        int secondBlue = second & 0xFF;
        int secondAlpha = (second >> 24) & 0xFF;
        int red = (int) (firstRed + (secondRed - firstRed) * clampedFactor);
        int green = (int) (firstGreen + (secondGreen - firstGreen) * clampedFactor);
        int blue = (int) (firstBlue + (secondBlue - firstBlue) * clampedFactor);
        int alpha = (int) (firstAlpha + (secondAlpha - firstAlpha) * clampedFactor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static class CubeFragment {
        private Vec3d previousCenter;
        private Vec3d center;
        private Vector3f previousOffset;
        private Vector3f offset;
        private Vec3d velocity;
        private final Vector3f axis;
        private float angularSpeed;
        private final double groundY;
        private final int maxAge;
        private int age;

        private CubeFragment(Vec3d center, Vector3f offset, Vec3d velocity, Vector3f axis,
                             float angularSpeed, double groundY, int maxAge) {
            this.previousCenter = center;
            this.center = center;
            this.previousOffset = new Vector3f(offset);
            this.offset = offset;
            this.velocity = velocity;
            this.axis = axis;
            this.angularSpeed = angularSpeed;
            this.groundY = groundY;
            this.maxAge = maxAge;
        }
    }
}
