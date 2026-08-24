package ru.white.module.impl.player;

import ru.white.Client;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.impl.render.SwingAnimation;
import ru.white.utils.math.InvUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.math.StopGPT;
import ru.white.utils.math.StopWatchP;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

@ModuleInfo(name = "Elytra Helper", desc = "Помощник элитры: авто-свап и полёт", category = Category.PLAYER)
public class ElytraHelper extends Module {

    public final BindSetting swapChestKey = new BindSetting(this, "Свап");
    public final BindSetting use = new BindSetting(this, "Исп. фейерверк");
    public final BooleanSetting autoFly = new BooleanSetting(this, "AutoFly", true);
    public final BooleanSetting autoPvpSwap = new BooleanSetting(this, "AutoPvpSwap", false);

    public enum SwapState {
        IDLE, SWAPPING, SWAPPED, USING, RETURNING, CLOSING
    }

    private SwapState chestSwapState = SwapState.IDLE;
    private final StopGPT chestTimer = new StopGPT();
    public static StopWatchP stopWatch = new StopWatchP();
    private ItemStack currentStack = ItemStack.EMPTY;
    private boolean pendingFly = false;
    private final StopGPT flyTimer = new StopGPT();
    private Item prevChestItem = Items.AIR;

    private boolean wasPvp = false;
    private boolean pvpJustEnded = false;
    private long pvpEndTime = 0;

    public boolean isPvpJustEnded() {
        if (pvpJustEnded && System.currentTimeMillis() - pvpEndTime < 3000) {
            return true;
        }
        pvpJustEnded = false;
        return false;
    }

    public void clearPvpEnded() {
        pvpJustEnded = false;
    }

    private enum UseState {
        IDLE, PREPARE, SWAP, THROW, RESTORE, COOLDOWN
    }

    private int pearlSlot = -1;
    private int oldSlot = -1;
    private UseState currentState = UseState.IDLE;
    private final StopGPT timer = new StopGPT();

    @EventHandler
    private void onEventKey(EventKey e) {
        if (e.getKey() == swapChestKey.get() &&
                !(mc.player.isUsingItem() && mc.player.getOffHandStack().getItem() != Items.SHIELD)) {
            ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            int targetSlot = chestItem.getItem() == Items.ELYTRA ? getChestPlateSlot() : getItemSlot(Items.ELYTRA);
            if (targetSlot >= 0 && chestSwapState == SwapState.IDLE) {
                chestSwapState = SwapState.SWAPPING;
                currentStack = chestItem;
                chestTimer.reset();
                stopWatch.reset();
            }
        } else if (e.getKey() == use.get() && !mc.player.isOnGround()) {
            if (currentState == UseState.IDLE) {
                startPearlSwap();
            }
        }
    }


    @EventHandler
    private void onUpdate(EventUpdate e) {

        ItemStack currentChestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        Item currentItem = currentChestStack.getItem();
        if (currentItem == Items.ELYTRA && prevChestItem != Items.ELYTRA) {
            if (autoFly.getValue()) {
                pendingFly = true;
                flyTimer.reset();
            }
        }
        prevChestItem = currentItem;

        boolean currentPvp = ServerUtil.isPvp();
        if (wasPvp && !currentPvp) {
            pvpJustEnded = true;
            pvpEndTime = System.currentTimeMillis();
        }
        wasPvp = currentPvp;

        if (autoPvpSwap.getValue() && chestSwapState == SwapState.IDLE) {
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                boolean shouldSwap = false;

                if (ServerUtil.isPvp()) {
                    shouldSwap = true;
                }

                if (!shouldSwap) {
                    for (PlayerEntity player : mc.world.getPlayers()) {
                        if (player == mc.player)
                            continue;
                        if (!player.isAlive())
                            continue;
                        if (Client.get().friendManager().isFriend(player.getName().getString()))
                            continue;
                        double dist = mc.player.distanceTo(player);
                        if (dist <= 5) {
                            shouldSwap = true;
                            break;
                        }
                    }
                }

                if (shouldSwap) {
                    int chestplateSlot = getChestPlateSlot();
                    if (chestplateSlot >= 0
                            && !(mc.player.isUsingItem() && mc.player.getOffHandStack().getItem() != Items.SHIELD)) {
                        chestSwapState = SwapState.SWAPPING;
                        currentStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                        chestTimer.reset();
                        stopWatch.reset();
                    }
                }
            }
        }

        handleChestSwap();

        if (pendingFly && flyTimer.hasTimePassed(400)) {
            if (autoFly.getValue()) {
                mc.player.networkHandler.sendChatCommand("fly");
            }
            pendingFly = false;
        }

        if (mc.interactionManager == null || currentState == UseState.IDLE) {
            return;
        }

        setKey(false);

        switch (currentState) {
            case PREPARE -> {

                if (timer.hasTimePassed(50)) {
                    timer.reset();
                    currentState = UseState.SWAP;
                }
            }

            case SWAP -> {
                if (pearlSlot < 9) {
                    mc.player.getInventory().setSelectedSlot(pearlSlot);
                    mc.interactionManager.syncSelectedSlot();
                } else {
                    if (!mc.player.isSprinting()) {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, pearlSlot, oldSlot,
                                SlotActionType.SWAP, mc.player);
                    }
                }
                if (timer.hasTimePassed(30)) {
                    timer.reset();
                    currentState = UseState.THROW;
                }
            }

            case THROW -> {
                SwingAnimation.interactItem(Hand.MAIN_HAND);
                if (timer.hasTimePassed(30)) {
                    timer.reset();
                    currentState = UseState.RESTORE;
                }
            }

            case RESTORE -> {
                if (pearlSlot < 9) {
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                    mc.interactionManager.syncSelectedSlot();
                } else {
                    if (!mc.player.isSprinting()) {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, pearlSlot, oldSlot,
                                SlotActionType.SWAP, mc.player);
                    }
                    mc.player.networkHandler
                            .sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                }
                timer.reset();
                currentState = UseState.COOLDOWN;
            }

            case COOLDOWN -> {
                if (timer.hasTimePassed(70)) {
                    setKey(true);
                    currentState = UseState.IDLE;
                }
            }
        }
    }

    private void startPearlSwap() {
        if (mc.player == null)
            return;

        pearlSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                pearlSlot = i;
                break;
            }
        }

        if (pearlSlot == -1) {
            mc.player.sendMessage(Text.literal("Перл не найден!").formatted(Formatting.RED), true);
            return;
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();
        timer.reset();
        currentState = UseState.PREPARE;
    }

    private void handleChestSwap() {
        if (chestSwapState == SwapState.IDLE) return;

        if (mc.currentScreen != null) {
            chestSwapState = SwapState.IDLE;
            setKey(true);
            return;
        }

        setKey(false);

        switch (chestSwapState) {
            case SWAPPING -> {
                if (!chestTimer.hasTimePassed(50)) return;

                ItemStack itemStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                int elytraSlot = getItemSlot(Items.ELYTRA);
                int chestplateSlot = getChestPlateSlot();

                if (itemStack.getItem() != Items.ELYTRA && elytraSlot >= 0) {
                    swapToChestSlot(elytraSlot, 6);
                    chestTimer.reset();
                    chestSwapState = SwapState.CLOSING;
                } else if (itemStack.getItem() == Items.ELYTRA && chestplateSlot >= 0) {
                    swapToChestSlot(chestplateSlot, 6);
                    chestTimer.reset();
                    chestSwapState = SwapState.CLOSING;
                } else {
                    chestSwapState = SwapState.IDLE;
                    setKey(true);
                }
            }
            case CLOSING -> {
                if (!chestTimer.hasTimePassed(70)) return;

                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                    pendingFly = true;
                    flyTimer.reset();
                }
                chestSwapState = SwapState.IDLE;
                setKey(true);
            }
        }
    }

    private void swapToChestSlot(int slot, int chestSlot) {
        if (mc.currentScreen != null)
            return;
        int currentItemSlot = mc.player.getInventory().getSelectedSlot();
        if (slot >= 36 && slot <= 44) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, chestSlot, slot % 9,
                    SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, currentItemSlot,
                    SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, chestSlot, currentItemSlot,
                    SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, currentItemSlot,
                    SlotActionType.SWAP, mc.player);
        }
    }

    private int getChestPlateSlot() {
        if (mc.player == null)
            return -1;
        Item[] items = { Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE, Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE };

        for (Item item : items) {
            for (int i = 0; i < 36; ++i) {
                Item stack = mc.player.getInventory().getStack(i).getItem();
                if (stack == item) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    private int getItemSlot(Item input) {
        return InvUtil.find(input);
    }

    private void setKey(boolean state) {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
        };

        long handle = mc.getWindow().getHandle();

        for (KeyBinding keyBinding : movementKeys) {
            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);
        }
    }

    @Override
    public void onDisable() {
        stopWatch.reset();
        if (currentState != UseState.IDLE || chestSwapState != SwapState.IDLE) {
            setKey(true);
        }
        currentState = UseState.IDLE;
        chestSwapState = SwapState.IDLE;
        super.onDisable();
    }
}
