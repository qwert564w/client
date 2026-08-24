package ru.white.module.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.math.StopGPT;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;

@ModuleInfo(
        name = "Auto Tool",
        desc = "Автоматически выбирает лучший инструмент",
        category = Category.PLAYER
)
public class AutoTool extends Module {

    private static final long SWAP_STOP_MS = 60L;

    private State state = State.IDLE;
    private final StopGPT timer = new StopGPT();
    private int toolSlot = -1;
    private int previousHotbarSlot = -1;

    @EventHandler
    public void onUpdate(EventUpdate event) {



        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            state = State.IDLE;
            toolSlot = -1;
            previousHotbarSlot = -1;
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        switch (state) {
            case IDLE -> tryStart();
            case PREPARE_SWAP -> prepareSwap();
            case MINING -> continueMining();
            case PREPARE_RESTORE -> prepareRestore();
        }
    }

    @Override
    public void onDisable() {
        abort();
        super.onDisable();
    }

    private void tryStart() {
        BlockState blockState = getTargetState();
        if (blockState == null || !mc.options.attackKey.isPressed()) {
            return;
        }

        int bestSlot = findBestTool(blockState);
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (bestSlot == -1 || bestSlot == selectedSlot) {
            return;
        }

        toolSlot = bestSlot;
        previousHotbarSlot = selectedSlot;

        if (bestSlot < 9) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
            mc.interactionManager.syncSelectedSlot();
            state = State.MINING;
        } else {
            timer.reset();
            state = State.PREPARE_SWAP;
        }
    }

    private void prepareSwap() {
        if (!mc.options.attackKey.isPressed() || getTargetState() == null) {
            reset();
            return;
        }

        setKey(false);
        if (!timer.hasTimePassed(SWAP_STOP_MS)) {
            return;
        }

        setKey(true);
        swapInventoryTool();
        state = State.MINING;
    }

    private void continueMining() {
        BlockState blockState = getTargetState();

        if (mc.options.attackKey.isPressed() && blockState != null) {
            // цель сменилась — переключаемся на другой инструмент из хотбара на лету
            if (toolSlot < 9) {
                int bestSlot = findBestTool(blockState);
                if (bestSlot != -1 && bestSlot < 9 && bestSlot != mc.player.getInventory().getSelectedSlot()) {
                    toolSlot = bestSlot;
                    mc.player.getInventory().setSelectedSlot(bestSlot);
                    mc.interactionManager.syncSelectedSlot();
                }
            }
            return;
        }

        if (toolSlot < 9) {
            restoreSelectedSlot();
            reset();
        } else {
            timer.reset();
            state = State.PREPARE_RESTORE;
        }
    }

    private void prepareRestore() {
        setKey(false);
        if (!timer.hasTimePassed(SWAP_STOP_MS)) {
            return;
        }

        swapInventoryTool();
        closeInventorySilent();
        reset();
    }

    private void swapInventoryTool() {
        if (toolSlot < 9 || previousHotbarSlot < 0 || previousHotbarSlot > 8) {
            return;
        }
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                toolSlot,
                previousHotbarSlot,
                SlotActionType.SWAP,
                mc.player
        );
    }

    private void closeInventorySilent() {
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
        mc.player.closeHandledScreen();
    }

    private void restoreSelectedSlot() {
        if (previousHotbarSlot >= 0 && previousHotbarSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(previousHotbarSlot);
            mc.interactionManager.syncSelectedSlot();
        }
    }

    private BlockState getTargetState() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return mc.world.getBlockState(hit.getBlockPos());
    }

    private int findBestTool(BlockState blockState) {
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        int bestSlot = isUsable(mc.player.getInventory().getStack(selectedSlot))
                ? selectedSlot
                : -1;
        float bestSpeed = bestSlot == -1
                ? 1.0F
                : mc.player.getInventory().getStack(bestSlot).getMiningSpeedMultiplier(blockState);
        boolean bestCanHarvest = bestSlot != -1
                && (!blockState.isToolRequired()
                || mc.player.getInventory().getStack(bestSlot).isSuitableFor(blockState));

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isUsable(stack)) {
                continue;
            }

            float speed = stack.getMiningSpeedMultiplier(blockState);
            boolean canHarvest = !blockState.isToolRequired() || stack.isSuitableFor(blockState);
            if ((canHarvest && !bestCanHarvest)
                    || (canHarvest == bestCanHarvest && speed > bestSpeed)) {
                bestSlot = slot;
                bestSpeed = speed;
                bestCanHarvest = canHarvest;
            }
        }
        return bestSlot;
    }

    private boolean isUsable(ItemStack stack) {
        return !stack.isEmpty()
                && (!stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() > 1);
    }

    /** мягкий сброс после нормального возврата предмета */
    private void reset() {
        setKey(true);
        state = State.IDLE;
        toolSlot = -1;
        previousHotbarSlot = -1;
    }

    /** аварийный сброс (выключение модуля) — возвращаем предмет, если успели свапнуть */
    private void abort() {
        if (mc.player != null && mc.interactionManager != null) {
            if (state == State.MINING || state == State.PREPARE_RESTORE) {
                if (toolSlot >= 9) {
                    swapInventoryTool();
                    closeInventorySilent();
                } else {
                    restoreSelectedSlot();
                }
            }
            setKey(true);
        }
        state = State.IDLE;
        toolSlot = -1;
        previousHotbarSlot = -1;
    }

    private enum State {
        IDLE,
        PREPARE_SWAP,
        MINING,
        PREPARE_RESTORE
    }

    private void setKey(boolean state) {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
        };


        for (KeyBinding keyBinding : movementKeys) {
            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);
        }
    }
}
