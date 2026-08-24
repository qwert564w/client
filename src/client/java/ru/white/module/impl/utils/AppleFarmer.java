package ru.white.module.impl.utils;


import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.FreeLookUtil;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.ChatUtils;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Apple Farmer", category = Category.OTHER)
public class AppleFarmer extends Module {

    public final SliderSetting range = new SliderSetting(this,"Дистанция", 
            4.5f, 3f, 4.5f, 0.1f);

    private enum FarmState {
        FIND_SPOT,
        PLACE,
        BONEMEAL,
        SCAN_TREE,
        BREAKING
    }

    private FarmState currentState = FarmState.FIND_SPOT;
    private BlockPos targetPos = null;
    private final List<BlockPos> blocksToMine = new ArrayList<>();
    private static final int PLANT_DISTANCE = 2;
    private static final int TREE_SCAN_RADIUS = 4;
    private static final int TREE_SCAN_HEIGHT = 8;
    private Direction farmFacing = Direction.NORTH;
    private BlockPos breakingBlock = null;
    private int timer = 0;
    private int refillTimer = 0;

    public AppleFarmer() {
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentState = FarmState.FIND_SPOT;
        targetPos = null;
        blocksToMine.clear();
        breakingBlock = null;
        refillTimer = 0;
        if (mc.player != null) {
            farmFacing = mc.player.getHorizontalFacing();
        }
        timer = 0;
    }

    @Override
    public void onDisable() {
        RotationProcess.currentTask = RotationProcess.RotationTask.IDLE;
        RotationProcess.currentPriority = 0;
        RotationProcess.targetRotation = null;
        FreeLookUtil.active = false;
        breakingBlock = null;
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // if (PlayerHelper.isAutoUseActive()) {
            //     return;
            // }

        refillTimer++;
        if (refillTimer > 4) {
            handleAutoRefill();
            refillTimer = 0;
        }

        timer++;

        if (currentState != FarmState.BREAKING && timer < 2) return;

        switch (currentState) {
            case FIND_SPOT:
                handleFindSpot();
                break;
            case PLACE:
                handlePlace();
                break;
            case BONEMEAL:
                handleBonemeal();
                break;
            case SCAN_TREE:
                handleScanTree();
                break;
            case BREAKING:
                handleBreaking();
                break;
        }
    }

    private void handleFindSpot() {
        farmFacing = mc.player.getHorizontalFacing();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos gapPos = playerPos.offset(farmFacing);
        BlockPos plantPos = playerPos.offset(farmFacing, PLANT_DISTANCE);
        BlockState plantState = mc.world.getBlockState(plantPos);

        if (!isSpaceClear(gapPos) || !isSpaceClear(gapPos.up())) {
            ChatUtils.addChatMessage("§c[AppleFarmer] §fМежду вами и местом посадки должен быть свободный блок");
            this.toggle();
            timer = 0;
            return;
        }

        if (plantState.getBlock() == Blocks.OAK_SAPLING) {
            targetPos = plantPos;
            currentState = FarmState.BONEMEAL;
            timer = 0;
            return;
        }

        if (isFarmLog(plantState)) {
            targetPos = plantPos;
            currentState = FarmState.SCAN_TREE;
            timer = 0;
            return;
        }

        BlockPos soilPos = plantPos.down();
        if (isValidSoil(soilPos) && plantState.isReplaceable()) {
            targetPos = soilPos.up();
            currentState = FarmState.PLACE;
        } else {
              ChatUtils.addChatMessage("§c[AppleFarmer] §fВстаньте напротив места посадки: земля должна быть через один блок перед вами");
            this.toggle();
        }
        timer = 0;
    }

    private void handlePlace() {
        if (targetPos == null) {
            currentState = FarmState.FIND_SPOT;
            return;
        }

        BlockState state = mc.world.getBlockState(targetPos);
        if (state.getBlock() == Blocks.OAK_SAPLING) {
            currentState = FarmState.BONEMEAL;
            timer = 0;
            return;
        }
        if (!state.isReplaceable()) {
            currentState = FarmState.FIND_SPOT;
            timer = 0;
            return;
        }

        int saplingSlot = findItemSlot(Items.OAK_SAPLING);
        if (saplingSlot == -1) {
              ChatUtils.addChatMessage("§c[AppleFarmer] §fНет саженцев");
            this.toggle();
            return;
        }

        if (!rotateTo(targetPos.down())) {
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(saplingSlot);
        interactBlock(targetPos.down());
        mc.player.getInventory().setSelectedSlot(oldSlot);

        currentState = FarmState.BONEMEAL;
        timer = 0;
    }

    private void handleBonemeal() {
        if (targetPos == null) return;

        BlockState state = mc.world.getBlockState(targetPos);
        if (isFarmLog(state)) {
            currentState = FarmState.SCAN_TREE;
            return;
        }
        if (state.isReplaceable()) {
            currentState = FarmState.PLACE;
            return;
        }
        if (state.getBlock() != Blocks.OAK_SAPLING) {
            currentState = FarmState.FIND_SPOT;
            return;
        }

        int boneMealSlot = findItemSlot(Items.BONE_MEAL);

        if (boneMealSlot == -1) {
            if (pullBoneMealFromInventory()) {
                boneMealSlot = mc.player.getInventory().getSelectedSlot();
            } else {
                  ChatUtils.addChatMessage("§c[AppleFarmer] §fНет костной муки");
                this.toggle();
                return;
            }
        }

        if (!rotateTo(targetPos)) {
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(boneMealSlot);
        interactBlock(targetPos);

        mc.player.getInventory().setSelectedSlot(oldSlot);
        timer = 0;
    }

    private void handleScanTree() {
        blocksToMine.clear();
        BlockPos root = targetPos;

        for (int x = -TREE_SCAN_RADIUS; x <= TREE_SCAN_RADIUS; x++) {
            for (int y = 0; y <= TREE_SCAN_HEIGHT; y++) {
                for (int z = -TREE_SCAN_RADIUS; z <= TREE_SCAN_RADIUS; z++) {
                    BlockPos p = root.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);

                    if (isFarmTreeBlock(s) && isWithinReach(p)) {
                        blocksToMine.add(p);
                    }
                }
            }
        }

        if (blocksToMine.isEmpty()) {
            currentState = FarmState.PLACE;
            return;
        }

        blocksToMine.sort(this::compareTreeBlocks);
        breakingBlock = null;

        currentState = FarmState.BREAKING;
    }

    private void handleBreaking() {
        blocksToMine.removeIf(pos -> !isFarmTreeBlock(mc.world.getBlockState(pos)) || !isWithinReach(pos));

        if (blocksToMine.isEmpty()) {
            currentState = FarmState.PLACE;
            breakingBlock = null;
            return;
        }

        BlockPos currentBlock = findNextMineableBlock();
        if (currentBlock == null) {
            currentState = FarmState.SCAN_TREE;
            breakingBlock = null;
            timer = 0;
            return;
        }

        BlockState state = mc.world.getBlockState(currentBlock);
        BlockHitResult hit = raycastTarget(currentBlock);
        if (hit == null) {
            breakingBlock = null;
            return;
        }

        boolean isLeaves = isFarmLeaf(state);
        boolean isLog = isFarmLog(state);

        if (isLog) switchToBestTool(true);
        else if (isLeaves) switchToBestTool(false);

        Rotation targetRotation = calculateRotation(hit.getPos());
        RotationProcess.update(targetRotation, 65f, 65f, 65f, 65f, 2, 20, false);
        if (new Rotation(mc.player).getDelta(targetRotation) > 6.0F) {
            return;
        }

        if (!currentBlock.equals(breakingBlock)) {
            mc.interactionManager.attackBlock(currentBlock, hit.getSide());
            breakingBlock = currentBlock;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(currentBlock, hit.getSide());
        }
        mc.player.swingHand(Hand.MAIN_HAND);

    }

    private void interactBlock(BlockPos pos) {
        Vec3d hitVec = getHitVec(pos, Direction.UP);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean rotateTo(BlockPos pos) {
        Rotation targetRotation = calculateRotation(getHitVec(pos, Direction.UP));
        RotationProcess.update(targetRotation, 65f, 65f, 65f, 65f, 2, 20, false);
        return new Rotation(mc.player).getDelta(targetRotation) <= 6.0F;
    }

    private Vec3d getHitVec(BlockPos pos, Direction side) {
        return new Vec3d(
                pos.getX() + 0.5 + side.getOffsetX() * 0.5,
                pos.getY() + 0.5 + side.getOffsetY() * 0.5,
                pos.getZ() + 0.5 + side.getOffsetZ() * 0.5
        );
    }

    private Rotation calculateRotation(BlockPos pos) {
        return calculateRotation(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    private Rotation calculateRotation(Vec3d target) {
        if (mc.player == null) return new Rotation(0, 0);
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new Rotation(yaw, pitch);
    }

    private boolean isValidSoil(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL;
    }

    private boolean isSpaceClear(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.isReplaceable();
    }

    private boolean isFarmTreeBlock(BlockState state) {
        return isFarmLog(state) || isFarmLeaf(state);
    }

    private boolean isFarmLog(BlockState state) {
        return state.getBlock() == Blocks.OAK_LOG;
    }

    private boolean isFarmLeaf(BlockState state) {
        return state.getBlock() == Blocks.OAK_LEAVES;
    }

    private boolean isWithinReach(BlockPos pos) {
        double reach = Math.min(range.getValue(), 4.5f);
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    private int compareTreeBlocks(BlockPos first, BlockPos second) {
        BlockState firstState = mc.world.getBlockState(first);
        BlockState secondState = mc.world.getBlockState(second);
        int firstGroup = getBreakGroup(first, firstState);
        int secondGroup = getBreakGroup(second, secondState);

        if (firstGroup != secondGroup) {
            return Integer.compare(firstGroup, secondGroup);
        }

        if (isFarmLog(firstState) && isFarmLog(secondState)) {
            return Integer.compare(first.getY(), second.getY());
        }

        int firstDepth = getDepthFromPlayerSide(first);
        int secondDepth = getDepthFromPlayerSide(second);
        if (firstDepth != secondDepth) {
            return Integer.compare(secondDepth, firstDepth);
        }

        int yCompare = Integer.compare(first.getY(), second.getY());
        if (yCompare != 0) {
            return yCompare;
        }

        double firstDistance = first.getSquaredDistance(mc.player.getEntityPos());
        double secondDistance = second.getSquaredDistance(mc.player.getEntityPos());
        return Double.compare(firstDistance, secondDistance);
    }

    private int getBreakGroup(BlockPos pos, BlockState state) {
        if (isFarmLog(state)) {
            return 3;
        }

        int depth = getDepthFromPlayerSide(pos);
        if (depth > 0) {
            return 0;
        }
        if (depth == 0) {
            return 1;
        }
        return 2;
    }

    private int getDepthFromPlayerSide(BlockPos pos) {
        if (targetPos == null) return 0;
        Direction playerSide = farmFacing.getOpposite();
        int dx = pos.getX() - targetPos.getX();
        int dz = pos.getZ() - targetPos.getZ();
        return dx * playerSide.getOffsetX() + dz * playerSide.getOffsetZ();
    }

    private BlockPos findNextMineableBlock() {
        for (BlockPos pos : blocksToMine) {
            if (raycastTarget(pos) != null) {
                return pos;
            }
        }
        return null;
    }

    private BlockHitResult raycastTarget(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double[] offsets = {0.5, 0.2, 0.8};
        for (double x : offsets) {
            for (double y : offsets) {
                for (double z : offsets) {
                    Vec3d target = new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private void switchToBestTool(boolean isAxe) {
        int bestSlot = -1;
        ItemStack current = mc.player.getMainHandStack();

        if (isAxe && current.getItem() instanceof AxeItem) return;
        if (!isAxe && current.getItem() instanceof HoeItem) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (isAxe && stack.getItem() instanceof AxeItem) {
                bestSlot = i;
                break;
            }
            if (!isAxe && stack.getItem() instanceof HoeItem) {
                bestSlot = i;
                break;
            }
        }

        if (bestSlot != -1) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }
    private boolean pullBoneMealFromInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.BONE_MEAL) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i,
                        mc.player.getInventory().getSelectedSlot(),
                        SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    private void handleAutoRefill() {
        for (int i = 0; i < 9; i++) {
            ItemStack stackInHotbar = mc.player.getInventory().getStack(i);

            boolean isTargetItem = stackInHotbar.getItem() == Items.BONE_MEAL || stackInHotbar.getItem() == Items.OAK_SAPLING;

            if (isTargetItem && stackInHotbar.getCount() < 64) {

                int bestSlot = -1;
                int bestCount = stackInHotbar.getCount();

                for (int invSlot = 9; invSlot < 36; invSlot++) {
                    ItemStack stackInInv = mc.player.getInventory().getStack(invSlot);

                    if (stackInInv.getItem() == stackInHotbar.getItem() && stackInInv.getCount() > bestCount) {
                        bestSlot = invSlot;
                        bestCount = stackInInv.getCount();

                        if (bestCount == 64) break;
                    }
                }

                if (bestSlot != -1) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                            bestSlot,
                            i,
                            SlotActionType.SWAP,
                            mc.player);

                    refillTimer = 0;
                    return;
                }
            }
        }
    }

}
