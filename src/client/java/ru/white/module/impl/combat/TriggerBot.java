package ru.white.module.impl.combat;

import ru.white.Client;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.aura.TriggerUAttack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


import static net.minecraft.util.Hand.MAIN_HAND;

@ModuleInfo(
        name = "TriggerBot",
        desc = "Автоматический атакует цель на которую вы навились",
        category = Category.COMBAT
)
public class TriggerBot extends Module {

    public SliderSetting attackRange  = new SliderSetting(this, "Радиус атаки",       3.0F, 2.5F, 5.0F, 0.1F);
    public SliderSetting preRange     = new SliderSetting(this, "Радиус обнаружения", 1.0F, 0.0F, 3.0F, 0.1F);

    public BooleanSetting player      = new BooleanSetting(this, "Атаковать игроков", true);
    public BooleanSetting golyPlayer  = new BooleanSetting(this, "Атаковать голых",   true);
    public BooleanSetting mobs        = new BooleanSetting(this, "Атаковать мобов",   false);
    public BooleanSetting friend      = new BooleanSetting(this, "Атаковать друзей",  false);

    public ModeSetting typeSprint     = new ModeSetting(this, "Тип спринта", "Packet", "Silent", "Legit");

    public BooleanSetting smartCrit   = new BooleanSetting(this, "Умные криты",       false);
    public BooleanSetting hitOfBlock  = new BooleanSetting(this, "Бить через блоки",  false);
    public BooleanSetting nohitead    = new BooleanSetting(this, "Не бить при еде",   false);


    public static LivingEntity targets = null;

    @EventHandler
    
    public void onUpdate(EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) return;

        if (nohitead.getValue() && mc.player.isUsingItem()) return;

        LivingEntity target = resolveTarget();
        if (target == null) return;

        if (!isValidTarget(target)) return;

        targets = target;

        attackEntity(target);
    }
    public float[] getRanges() {
        return new float[]{attackRange.getValue(), preRange.getValue()};
    }

    public void attackEntity(LivingEntity target) {
        if (AuraUtil.getStrictDistance(target) >= attackRange.getValue()) {
            return;
        }
        float[] ranges = new float[]{attackRange.getValue(), preRange.getValue(), attackRange.getValue() + preRange.getValue()};

        TriggerUAttack.antiMissesHittingUpdate(target, false, true, false);

        boolean canAttack = TriggerUAttack.shouldAttack(target, true, true, true, 0L, ranges);

        if (canAttack) {

            final Runnable[] shieldBreak = TriggerUAttack.hitShieldBreakTaskForUse(target, true),
                    shieldPressBypass = TriggerUAttack.resetShieldSilentTaskForUse(true),
                    skipSilentSprint = TriggerUAttack.skipSilentSprintingTaskForUse((typeSprint.is("Packet") || typeSprint.is("Silent")));
            final Runnable preHitSendCodeSingleTick = () -> {
                skipSilentSprint[0].run();
                shieldPressBypass[0].run();
                shieldBreak[0].run();
            }, postHitSendCodeSingleTick = () -> {
                shieldBreak[1].run();
                shieldPressBypass[1].run();
                skipSilentSprint[1].run();
            };

            TriggerUAttack.useEntity(target, preHitSendCodeSingleTick, postHitSendCodeSingleTick, MAIN_HAND,
                    false);
        }
    }


    private LivingEntity resolveTarget() {
        if (!hitOfBlock.getValue()) {

            HitResult hitResult = mc.crosshairTarget;
            if (!(hitResult instanceof EntityHitResult ehr)) return null;
            Entity e = ehr.getEntity();
            return e instanceof LivingEntity living ? living : null;
        }


        Vec3d eyePos  = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();

        LivingEntity best      = null;
        double       bestAngle = Double.MAX_VALUE;

        final double MAX_ANGLE_RAD = Math.toRadians(10.0);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity instanceof ClientPlayerEntity) continue;
            if (!entity.isAlive()) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist > attackRange.getValue()) continue;

            Vec3d toTarget = entity.getEyePos().subtract(eyePos).normalize();
            double cosAngle = MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0);
            double angle    = Math.acos(cosAngle);

            if (angle <= MAX_ANGLE_RAD && angle < bestAngle) {
                bestAngle = angle;
                best      = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target instanceof PlayerEntity playerEntity) {
            if (!player.getValue()) return false;
            if (Client.get().friendManager().isFriend(playerEntity.getName().getString()) && !friend.getValue()) return false;
            if (playerEntity.getArmorVisibility() <= 0 && !golyPlayer.getValue()) return false;
            if (playerEntity.isCreative()) return false;
        }
        if (target instanceof MobEntity && !mobs.getValue()) return false;
        return true;
    }
}