package ru.white.module.impl.combat;

import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.GCDUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        name = "Aim Bot",
        desc = "Автоприцел для лука и трезубца с предиктом позиции цели и компенсацией гравитации",
        category = Category.COMBAT
)
public class ProjectileAimBot extends Module {

    public BooleanSetting onlyWhenUsing = new BooleanSetting(this, "Только при зарядке", true);
    public BooleanSetting playersSet = new BooleanSetting(this, "Атаковать игроков", true);
    public BooleanSetting mobsSet = new BooleanSetting(this, "Атаковать мобов", false);
    public BooleanSetting friendsSet = new BooleanSetting(this, "Атаковать друзей", false);

    public SliderSetting range = new SliderSetting(this, "Дальность", 30f, 5f, 80f, 1f);
    public SliderSetting smoothH = new SliderSetting(this, "Плавность горизонталь", 0.15f, 0.01f, 1.0f, 0.01f);
    public SliderSetting smoothV = new SliderSetting(this, "Плавность вертикаль", 0.15f, 0.01f, 1.0f, 0.01f);
    public SliderSetting predictMult = new SliderSetting(this, "Сила предикта", 1.0f, 0.0f, 2.0f, 0.05f);

    private final Map<UUID, Vec3d[]> posHistory = new HashMap<>();
    private LivingEntity target;

    @Override
    public void onDisable() {
        target = null;
        posHistory.clear();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // Track positions of nearby entities for velocity calculation
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof ClientPlayerEntity) continue;
            UUID id = entity.getUuid();
            Vec3d pos = entity.getEntityPos();
            Vec3d[] hist = posHistory.computeIfAbsent(id, k -> new Vec3d[]{pos, pos, pos});
            hist[2] = hist[1];
            hist[1] = hist[0];
            hist[0] = pos;
        }

        if (onlyWhenUsing.getValue() && !isUsingProjectileItem()) {
            target = null;
            return;
        }

        if (target == null || !isValid(target)) {
            selectTarget();
        }
    }

    @EventHandler
    public void onDisplay(EventDisplay event) {
        if (mc.player == null || mc.world == null || target == null) return;
        if (onlyWhenUsing.getValue() && !isUsingProjectileItem()) return;
        if (!isValid(target)) {
            target = null;
            return;
        }

        float speed = getProjectileSpeed();
        if (speed <= 0f) return;

        Vec3d eyePos = mc.player.getEyePos();
        // Aim at upper body (~60% height from feet)
        Vec3d targetFeet = target.getEntityPos();
        Vec3d targetBase = targetFeet.add(0, target.getHeight() * 0.2, 0);
        Vec3d vel = getSmoothedVelocity(target);

        // Iterative convergence: predict where target will be when projectile arrives
        Vec3d predicted = predictPosition(eyePos, targetBase, vel, speed);

        // MC adds player velocity to the projectile when it spawns — compensate for that drift
        predicted = compensatePlayerVelocity(eyePos, predicted, speed);

        float newYaw = calcYaw(eyePos, predicted);
        AimResult aim = calcAim(eyePos, predicted, speed);
        if (aim == null) return; // target unreachable

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = MathHelper.wrapDegrees(newYaw - currentYaw);
        float pitchDelta = aim.pitch - currentPitch;

        Rotation rotation = new Rotation(GCDUtil.applyGCD(currentYaw + yawDelta * smoothH.getValue(), currentYaw),GCDUtil.applyGCD(currentPitch + pitchDelta * smoothV.getValue(), currentPitch));


        RotationProcess.update(rotation,500,500,0,20);

    }

    // Iterative prediction: shoot → measure flight time → move target by velocity → repeat
    private Vec3d predictPosition(Vec3d from, Vec3d targetBase, Vec3d vel, float speed) {
        Vec3d predicted = targetBase;
        float mult = predictMult.getValue();
        for (int i = 0; i < 10; i++) {
            AimResult aim = calcAim(from, predicted, speed);
            if (aim == null) break;
            Vec3d next = targetBase.add(vel.multiply(aim.ticks * mult));
            // stop when converged
            if (next.squaredDistanceTo(predicted) < 0.001) break;
            predicted = next;
        }
        return predicted;
    }

    // ── Core aim calculation ──────────────────────────────────────────────────

    private static final class AimResult {
        final float pitch;
        final int ticks;
        AimResult(float pitch, int ticks) { this.pitch = pitch; this.ticks = ticks; }
    }

    /**
     * Binary-search for the MC pitch angle that makes the projectile hit `to`
     * from `from` at the given `speed`, simulating real MC arrow physics
     * (gravity 0.05/tick, drag 0.99/tick).
     * Returns null if the target is out of range.
     */
    private AimResult calcAim(Vec3d from, Vec3d to, float speed) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double D = Math.sqrt(dx * dx + dz * dz); // horizontal distance

        // Horizontal unit vector (direction to shoot in XZ plane)
        double ux = D > 0.001 ? dx / D : 0.0;
        double uz = D > 0.001 ? dz / D : 1.0;

        // If target is almost directly above/below, special case
        if (D < 0.3) {
            float pitch = dy > 0 ? -89f : 89f;
            return new AimResult(pitch, 5);
        }

        // Binary search: find elevation angle (standard: positive = upward)
        // such that projectile's Y when it reaches horizontal distance D equals to.y
        double lo = -Math.PI / 2 + 0.01;
        double hi = Math.PI / 2 - 0.01;

        // Sanity check: at maximum useful elevation, can we still reach horizontal distance D?
        if (simYAtDist(from.y, ux, uz, speed, Math.toRadians(75), D) == Double.MIN_VALUE) {
            // even 75° elevation can't reach D → out of range
            return null;
        }

        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) * 0.5;
            double simY = simYAtDist(from.y, ux, uz, speed, mid, D);
            if (simY == Double.MIN_VALUE) {
                // Shot doesn't reach D at this elevation — need more upward angle
                lo = mid;
            } else if (simY < to.y) {
                lo = mid; // too low → aim higher
            } else {
                hi = mid; // too high → reduce elevation
            }
        }

        double elev = (lo + hi) * 0.5;

        // Validate result: simulated Y should be within 0.5 of target
        double finalY = simYAtDist(from.y, ux, uz, speed, elev, D);
        if (finalY == Double.MIN_VALUE || Math.abs(finalY - to.y) > 2.0) return null;

        float mcPitch = MathHelper.clamp((float) -Math.toDegrees(elev), -90f, 90f);
        int ticks = simFlightTicks(ux, uz, speed, elev, D);
        return new AimResult(mcPitch, ticks);
    }

    /**
     * Simulate projectile with MC physics (gravity + drag).
     * Returns the Y coordinate of the projectile when its XZ distance from
     * origin equals targetD. Returns Double.MIN_VALUE if it never reaches targetD.
     */
    private double simYAtDist(double fromY, double ux, double uz, float speed,
                               double elevRad, double targetD) {
        double cosPitch = Math.cos(elevRad);
        double sinPitch = Math.sin(elevRad); // positive = upward

        double vx = ux * cosPitch * speed;
        double vy = sinPitch * speed;
        double vz = uz * cosPitch * speed;

        double px = 0.0, py = fromY, pz = 0.0;
        double prevHD = 0.0;

        for (int t = 0; t < 120; t++) {
            double npx = px + vx;
            double npy = py + vy;
            double npz = pz + vz;

            // MC arrow physics: gravity first, then drag
            vy -= 0.05;
            vx *= 0.99; vy *= 0.99; vz *= 0.99;

            double hd = Math.sqrt(npx * npx + npz * npz);

            if (hd >= targetD) {
                // Linearly interpolate Y at exact targetD
                double frac = prevHD < hd ? (targetD - prevHD) / (hd - prevHD) : 1.0;
                return py + (npy - py) * frac;
            }

            px = npx; py = npy; pz = npz;
            prevHD = hd;

            // Shot is falling and going away → won't reach
            if (vy < -3.0 && hd < prevHD + 0.01) break;
        }
        return Double.MIN_VALUE;
    }

    /** Returns how many ticks the projectile takes to reach horizontal distance D. */
    private int simFlightTicks(double ux, double uz, float speed, double elevRad, double targetD) {
        double cosPitch = Math.cos(elevRad);
        double sinPitch = Math.sin(elevRad);

        double vx = ux * cosPitch * speed;
        double vy = sinPitch * speed;
        double vz = uz * cosPitch * speed;

        double px = 0.0, pz = 0.0;

        for (int t = 1; t <= 120; t++) {
            px += vx; pz += vz;
            vy -= 0.05;
            vx *= 0.99; vy *= 0.99; vz *= 0.99;

            if (Math.sqrt(px * px + pz * pz) >= targetD) return t;
        }
        return 40;
    }

    // ── Player velocity compensation ─────────────────────────────────────────

    /**
     * MC ProjectileEntity.setVelocity() adds shooter.getVelocity() to the arrow's
     * initial velocity when it spawns. Without compensation, if the player is
     * running sideways the arrow drifts and misses.
     *
     * Fix: subtract the accumulated drift (player vel × drag sum over flight time)
     * from the aim point so the arrow still ends up at the target.
     *
     * Drift per axis = playerVel * sum(0.99^t, t=0..T-1) = playerVel * (1 - 0.99^T) / 0.01
     */
    private Vec3d compensatePlayerVelocity(Vec3d from, Vec3d to, float speed) {
        AimResult rough = calcAim(from, to, speed);
        if (rough == null) return to;

        int T = rough.ticks;
        // Geometric series: sum of 0.99^t for t = 0 .. T-1
        double dragSum = T > 0 ? (1.0 - Math.pow(0.99, T)) / 0.01 : 0.0;

        Vec3d pv = mc.player.getVelocity();
        double driftX = pv.x * dragSum;
        // Vertical: MC only adds player Y velocity when player is NOT on the ground
        double driftY = mc.player.isOnGround() ? 0.0 : pv.y * dragSum;
        double driftZ = pv.z * dragSum;

        // Arrow will drift by (driftX, driftY, driftZ) from where we aimed →
        // aim at (target - drift) so the arrow arrives exactly at target
        return to.subtract(driftX, driftY, driftZ);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float calcYaw(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /**
     * Smoothed velocity from last 2 ticks of position history.
     * Using 2-tick delta reduces single-tick noise.
     */
    private Vec3d getSmoothedVelocity(LivingEntity entity) {
        Vec3d[] hist = posHistory.get(entity.getUuid());
        if (hist == null) return entity.getVelocity();
        // hist[0] = newest, hist[2] = 2 ticks ago → average velocity per tick
        return hist[0].subtract(hist[2]).multiply(0.5);
    }

    private float getProjectileSpeed() {
        if (mc.player == null || !mc.player.isUsingItem()) return 0f;
        var item = mc.player.getActiveItem().getItem();
        int useTicks = mc.player.getItemUseTime();

        if (item instanceof BowItem) {
            float charge = (float) useTicks / 20.0f;
            charge = (charge * charge + charge * 2.0f) / 3.0f;
            charge = Math.min(charge, 1.0f);
            return Math.max(charge, 0.2f) * 3.0f;
        }
        if (item instanceof CrossbowItem) return 3.15f;
        if (item instanceof TridentItem) return 2.5f;
        return 0f;
    }

    private boolean isUsingProjectileItem() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        var item = mc.player.getActiveItem().getItem();
        return item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem;
    }

    private void selectTarget() {
        LivingEntity best = null;
        double bestFov = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f).normalize();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValid(living)) continue;
            Vec3d center = living.getEntityPos().add(0, living.getHeight() * 0.2, 0);
            double fov = Math.acos(MathHelper.clamp(lookVec.dotProduct(center.subtract(eyePos).normalize()), -1.0, 1.0));
            if (fov < bestFov) { bestFov = fov; best = living; }
        }
        target = best;
    }

    private boolean isValid(LivingEntity entity) {
        if (entity instanceof ClientPlayerEntity) return false;
        if (!entity.isAlive() || entity.isInvulnerable()) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity.squaredDistanceTo(mc.player) > range.getValue() * range.getValue()) return false;

        if (entity instanceof PlayerEntity p) {
            if (!playersSet.getValue()) return false;
            if (p.isCreative() || p.isSpectator()) return false;
            if (!friendsSet.getValue() && Client.get().friendManager().isFriend(p.getName().getString())) return false;
        } else if (entity instanceof Monster || entity instanceof SlimeEntity || entity instanceof AnimalEntity) {
            if (!mobsSet.getValue()) return false;
        } else {
            return false;
        }
        return true;
    }
}
