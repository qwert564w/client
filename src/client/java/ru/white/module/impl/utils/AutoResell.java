package ru.white.module.impl.utils;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;

@ModuleInfo(
        name = "Auto Resell",
        desc = "МОДУЛЬ ПИСАЛ АКАР МОЖЕТ РОБОТАТЬ ЧЕРЕЗ ОЧКО И ЧУДА АЛЛАХА",
        category = Category.OTHER
)
public class AutoResell extends Module {
    private static final int PERIOD_TICKS = 20 * 60;
    private static final int OPEN_TIMEOUT_TICKS = 100;
    private static final int TICK_DELAY = 6;

    private State state = State.IDLE;
    private int cooldownTicks;
    private int waitTicks;
    private int noScreenTicks;

    private enum State {
        IDLE,
        OPEN_AH,
        CLICK_STORAGE,
        CLICK_RELIST,
        CLOSE
    }

    @Override
    protected void onEnable() {
        resetCycle();
    }

    @Override
    protected void onDisable() {
        state = State.IDLE;
        cooldownTicks = 0;
        waitTicks = 0;
        noScreenTicks = 0;
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle();
            case OPEN_AH -> tickOpenAh();
            case CLICK_STORAGE -> tickClickStorage();
            case CLICK_RELIST -> tickClickRelist();
            case CLOSE -> finishCycle();
        }
    }

    private void tickIdle() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        resetCycle();
    }

    private void resetCycle() {
        if (mc.player == null) {
            state = State.IDLE;
            return;
        }
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        sendCommand("ah");
        state = State.OPEN_AH;
        noScreenTicks = 0;
        waitTicks = 20;
    }

    private void tickOpenAh() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            noScreenTicks = 0;
            state = State.CLICK_STORAGE;
            waitTicks = 2;
            return;
        }

        if (++noScreenTicks >= OPEN_TIMEOUT_TICKS) {
            sendCommand("ah");
            noScreenTicks = 0;
            waitTicks = 20;
            return;
        }
        waitTicks = TICK_DELAY;
    }

    private void tickClickStorage() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = State.OPEN_AH;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (findRelistSlot(handler) >= 0) {
            state = State.CLICK_RELIST;
            waitTicks = 2;
            return;
        }

        int storage = findStorageSlot(handler);
        if (storage < 0) {
            waitTicks = TICK_DELAY;
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, storage, 0, SlotActionType.PICKUP, mc.player);
        state = State.CLICK_RELIST;
        waitTicks = 8;
    }

    private void tickClickRelist() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = State.OPEN_AH;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int relist = findRelistSlot(handler);
        if (relist < 0) {
            waitTicks = TICK_DELAY;
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, relist, 0, SlotActionType.PICKUP, mc.player);
        state = State.CLOSE;
        waitTicks = 6;
    }

    private void finishCycle() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
        cooldownTicks = PERIOD_TICKS;
        waitTicks = 0;
        noScreenTicks = 0;
    }

    private int findStorageSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.ENDER_CHEST) {
                return i;
            }
        }
        return findTextSlot(handler, "хранилище");
    }

    private int findRelistSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            String text = stackText(stack);
            if (stack.getItem() == Items.CLOCK && text.contains("перевыстав")) {
                return i;
            }
        }
        return findTextSlot(handler, "перевыставить предметы");
    }

    private int findTextSlot(GenericContainerScreenHandler handler, String needle) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && stackText(slot.getStack()).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private String stackText(ItemStack stack) {
        StringBuilder text = new StringBuilder(clean(stack.getName().getString()));
        var lore = stack.getComponents().get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                text.append(' ').append(clean(line.getString()));
            }
        }
        return text.toString();
    }

    private String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim().toLowerCase();
    }

    private void sendCommand(String command) {
        mc.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
