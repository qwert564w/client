package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.StopWatchP;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;


@ModuleInfo(
        name = "Crystal Aura",
        desc = "Автоматическая аура под кристалы",
        category = Category.COMBAT
)
public class CrystalAura extends Module {

    private final SliderSetting range = new SliderSetting(this, "Дальность", 5.0F, 1.0F, 10.0F, 0.5F);
    private final SliderSetting placeDelay = new SliderSetting(this, "Задержка постановки (мс)", 200.0F, 50.0F, 1000.0F, 50.0F);
    private final SliderSetting breakDelay = new SliderSetting(this, "Задержка взрыва (мс)", 100.0F, 50.0F, 1000.0F, 50.0F);
    private final BooleanSetting rotateToBlock = new BooleanSetting(this, "Наведение на блок", true);
    private final BooleanSetting onlyNearEnemy = new BooleanSetting(this, "Только возле врага", true);
    private final SliderSetting enemyRange = new SliderSetting(this, "Дальность врага", 6.0F, 1.0F, 15.0F, 0.5F)
            .setVisible(() -> onlyNearEnemy.getValue());
    private final BooleanSetting obsidianOnly = new BooleanSetting(this, "Только обсидиан", false);

    private final StopWatchP placeTimer = new StopWatchP();
    private final StopWatchP breakTimer = new StopWatchP();
    private int prevSlot = -1;

    @EventHandler
    public void update(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        breakNearbyCrystals();

        BlockPos targetBlock = findNearestObsidian();
        if (targetBlock == null) {
            restoreSlot();
            return;
        }

        int crystalSlot = findCrystalSlot();
        if (crystalSlot == -1) {
            restoreSlot();
            return;
        }

        if (rotateToBlock.getValue()) {
            aimAtBlock(targetBlock);
        }

        if (placeTimer.isReached((long) placeDelay.getValue().longValue())) {
            selectSlot(crystalSlot);
            placeCrystal(targetBlock);
            placeTimer.reset();
        }
    }

    private void breakNearbyCrystals() {
        if (!breakTimer.isReached((long) breakDelay.getValue().longValue())) return;

        EndCrystalEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            double dist = mc.player.getEntityPos().distanceTo(crystal.getEntityPos());
            if (dist > range.getValue()) continue;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = crystal;
            }
        }

        if (nearest != null) {
            mc.interactionManager.attackEntity(mc.player, nearest);
            mc.player.swingHand(Hand.MAIN_HAND);
            breakTimer.reset();
        }
    }

    private BlockPos findNearestObsidian() {
        int r = (int) range.getValue().intValue();
        BlockPos playerPos = mc.player.getBlockPos();
        int playerY = playerPos.getY();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);


                    var block = mc.world.getBlockState(pos).getBlock();
                    boolean isObsidian = block == Blocks.OBSIDIAN;
                    boolean isBedrock = !obsidianOnly.getValue() && block == Blocks.BEDROCK;
                    if (!isObsidian && !isBedrock) continue;

                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    if (!mc.world.getBlockState(pos.up(2)).isAir()) continue;

                    if (onlyNearEnemy.getValue() && !hasEnemyNear(pos)) continue;

                    double dist = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean hasEnemyNear(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        float maxDist = enemyRange.getValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.getEntityPos().distanceTo(center) <= maxDist) return true;
        }
        return false;
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.END_CRYSTAL) return i;
        }
        return -1;
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (prevSlot == -1) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
            }
            mc.player.getInventory().setSelectedSlot(slot);
            mc.interactionManager.syncSelectedSlot();
        }
    }
    
    private void restoreSlot() {
        if (prevSlot != -1) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            mc.interactionManager.syncSelectedSlot();
            prevSlot = -1;
        }
    }

    
    private void aimAtBlock(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 1.0 - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dxz)));
        RotationProcess.update(new Rotation(yaw + MathUtil.random(-1.12481248F,1.21481248F), pitch + MathUtil.random(-1.12481248F,1.21481248F)), 140, 140, 1, 1);
    }
    
    private void placeCrystal(BlockPos pos) {
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    
    protected void onDisable() {
        restoreSlot();
        placeTimer.reset();
        breakTimer.reset();
    }
}
