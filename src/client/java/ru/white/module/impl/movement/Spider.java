package ru.white.module.impl.movement;


import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.other.TimerUtil;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@ModuleInfo(
        name = "Spider",
        desc = "Позволяет лазить по стенам",
        category = Category.MOVEMENT
)
public class Spider extends Module {

    public ModeSetting modeSpider = new ModeSetting(this,"Режим","Matrix");

    private final TimerUtil timerUtil = new TimerUtil();
    TimerUtil placeTimer = new TimerUtil();

    @EventHandler
    public void onMotion(MotionEvent e) {
        Direction facing = mc.player.getHorizontalFacing();



        BlockPos posInFront = BlockPos.ofFloored(mc.player.getEntityPos()).offset(facing);
        BlockState stateInFront = mc.world.getBlockState(posInFront);
        Block blockInFront = stateInFront.getBlock();

        BlockPos posBelow = BlockPos.ofFloored(mc.player.getEntityPos());
        BlockState stateBelow = mc.world.getBlockState(posBelow);
        Block blockBelow = stateBelow.getBlock();

        boolean penisF = blockInFront instanceof TrapdoorBlock
                && Boolean.TRUE.equals(stateInFront.get(Properties.OPEN))
                && stateInFront.contains(Properties.HORIZONTAL_FACING);

        boolean penisB = blockBelow instanceof TrapdoorBlock
                && Boolean.TRUE.equals(stateBelow.get(Properties.OPEN))
                && stateBelow.contains(Properties.HORIZONTAL_FACING);

        boolean xuiF = blockInFront instanceof FenceBlock
                || stateInFront.isIn(BlockTags.WALLS)
                || blockInFront instanceof FenceGateBlock
                || blockInFront instanceof LanternBlock
                || blockInFront instanceof LightningRodBlock
                || penisF;

        boolean glubgseB = blockBelow instanceof FenceBlock
                || stateBelow.isIn(BlockTags.WALLS)
                || blockBelow instanceof FenceGateBlock
                || blockBelow instanceof LanternBlock
                || blockBelow instanceof LightningRodBlock
                || penisB;
        if (modeSpider.is("Matrix")) {

            ;

            if (timerUtil.finished(70) && hozColl()) {
                e.ground(true);
                mc.player.setOnGround(true);
                mc.player.jump();
                mc.player.fallDistance = 0;
                timerUtil.reset();
            }
        }
        if (modeSpider.is("FT - 2 2") && hozColl() && !mc.player.isOnGround()) {


            RotationProcess.update(new Rotation(mc.gameRenderer.getCamera().getYaw(), 60), 255, 255, 0, 50);


            BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitState = mc.world.getBlockState(hitPos);

            boolean isTopSide = hitResult.getSide() == Direction.UP;
            boolean isSolidBlockOrRod = !hitState.isReplaceable();
            boolean isSpaceFree = mc.world.getBlockState(hitPos.up()).isReplaceable();


            if (placeTimer.hasTimeElapsed(70)) {

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);

                placeTimer.reset();

                if (timerUtil.finished(90)) {
                    e.ground(true);
                    mc.player.setOnGround(true);
                    mc.player.jump();
                    mc.player.fallDistance = 0;
                    timerUtil.reset();
                }
            }
        }
        if (modeSpider.is("FT - 2") && hozColl() && !mc.player.isOnGround()) {
            int rodSlot = findLightningRodBlockInHotBar();

            if (rodSlot != -1) {


               // RotationProcess.update(new Rotation(mc.gameRenderer.getCamera().getYaw(),60),255,255,0,50);


                BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
                BlockPos hitPos = hitResult.getBlockPos();
                BlockState hitState = mc.world.getBlockState(hitPos);

                boolean isTopSide = hitResult.getSide() == Direction.UP;
                boolean isSolidBlockOrRod = !hitState.isReplaceable();
                boolean isSpaceFree = mc.world.getBlockState(hitPos.up()).isReplaceable();



                if (placeTimer.hasTimeElapsed(120)) {
                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(rodSlot);

                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    mc.player.getInventory().setSelectedSlot(oldSlot);
                    placeTimer.reset();


                }
                if (timerUtil.finished(150)) {
                    e.ground(true);
                    mc.player.setOnGround(true);
                    mc.player.jump();
                    mc.player.fallDistance = 0;
                    timerUtil.reset();
                }
            }
        }

    }    private int findLightningRodBlockInHotBar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.COPPER_TRAPDOOR) {
                return i;
            }
        }
        return -1;
    }

    public boolean hozColl() {
        return mc.player.horizontalCollision;
    }
}
