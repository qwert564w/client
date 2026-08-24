package ru.white.module.impl.combat;

import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.FreeLookUtil;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.Instance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
        name = "Hit Boxes",
        desc = "Увеличивает хит бокс игрока",
        category = Category.COMBAT
)
public class HitBoxes extends Module {

    public static HitBoxes get() {
        return Instance.get(HitBoxes.class);
    }

    public SliderSetting size = new SliderSetting(this, "Размер хит бокса", 0.3F, 0.1F, 1, 0.1F);
    public ModeSetting type = new ModeSetting(this, "Режим", "Обычный", "Ротация");
    public ModeSetting typeRot = new ModeSetting(this, "Режим ротации", "Постоянный", "При ударе").setVisible(() -> type.is("Ротация"));

    public static LivingEntity pendingTarget = null;
    public static int attackTimer = 0;

    @EventHandler
    public void onEvent(AttackEvent event) {
        if (typeRot.is("При ударе")) {
            pendingTarget = (LivingEntity) event.getTarget();
            attackTimer = 0;
        }
    }



    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null || !type.is("Ротация")) return;

        if (typeRot.is("Постоянный")) {
            HitResult hitResult = mc.crosshairTarget;


            if (!(hitResult instanceof EntityHitResult entityHitResult)) {
                pendingTarget = null;
                return;
            }

            Entity entity = entityHitResult.getEntity();

            if (!(entity instanceof LivingEntity target) || mc.player.distanceTo(target) > 4) {
                pendingTarget = null;
                return;
            }


            if (target instanceof PlayerEntity playerEntity) {
                pendingTarget = playerEntity;
                onHitBox(playerEntity);
            } else {
                pendingTarget = null;
            }
        }

        if (typeRot.is("При ударе") && pendingTarget != null) {
            if (!pendingTarget.isAlive() || mc.player.distanceTo(pendingTarget) > 4.0F) {
                pendingTarget = null;
                return;
            }

            onHitBox(pendingTarget);

            attackTimer++;
            if (attackTimer >= 3) {
                pendingTarget = null;
                attackTimer = 0;
            }
        }
    }

    public void onHitBox(LivingEntity target) {
        if (typeRot.is("Постоянный")) {
            Vec3d vec3d = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox(), false).subtract(mc.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
            float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec3d.y, Math.hypot(vec3d.x, vec3d.z))), -90, 90);

            float waveA = (float) Math.cos(System.currentTimeMillis() / 40D);
            float waveB = (float) Math.sin(System.currentTimeMillis() / 70D);

            float yawJitter = waveA * MathUtil.randomLerp(1, 2);
            float pitchJitter = waveB * MathUtil.randomLerp(1, 2);

            Rotation newRotation = new Rotation(yaw + yawJitter, FreeLookUtil.freePitch + pitchJitter);
            RotationProcess.update(newRotation, MathUtil.randomLerp(12,25),
                    MathUtil.randomLerp(22, 25), MathUtil.randomLerp(24, 26), MathUtil.randomLerp(24, 26), 0, 15, false);
        }

        if (typeRot.is("При ударе")) {
            Vec3d vec3d = target.getEntityPos().add(0, target.getHeight() / 2, 0).subtract(mc.player.getEyePos()).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
            float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec3d.y, Math.hypot(vec3d.x, vec3d.z))), -90, 90);

            float waveA = (float) Math.cos(System.currentTimeMillis() / 180D);
            float waveB = (float) Math.sin(System.currentTimeMillis() / 180D);

            float yawJitter = waveA * MathUtil.randomLerp(2, 4);
            float pitchJitter = waveB * MathUtil.randomLerp(2, 4);

            Rotation newRotation = new Rotation(yaw + yawJitter, pitch + pitchJitter);
            RotationProcess.update(newRotation, MathUtil.randomLerp(152, 255),
                    MathUtil.randomLerp(125, 255), MathUtil.randomLerp(152, 255), MathUtil.randomLerp(152, 255), 0, 15, false);
        }
    }
}