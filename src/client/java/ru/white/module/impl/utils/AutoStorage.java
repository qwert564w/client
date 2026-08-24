package ru.white.module.impl.utils;


import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.StopGPT;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "Auto Storage",desc = "Данный модуль был сделан акарачком поэтому может работать через жопу", category = Category.OTHER)
public class AutoStorage extends Module {

    public final BindSetting bind = new BindSetting(this, "Бинд", -1);
    public final ModeSetting mode = new ModeSetting(this, "Режим", "Только ценные", "Все предметы");



    private enum State {
        IDLE,
        WAITING_FOR_OPEN,
        MOVING_ITEMS,
        CLOSING
    }

    private State state = State.IDLE;
    private final StopGPT timer = new StopGPT();
    public boolean triggeredByChestStealer = false;
    private long nextDelay = 0;

    @EventHandler
    public void onKey(EventKey e) {
        if (mc.player == null || mc.world == null)
            return;

        if (e.getKey() == bind.get()) {
            trigger();
        }
    }

    public void trigger() {
        if (mc.player == null)
            return;
        if (state != State.IDLE)
            return; // Prevent double trigger

        mc.player.networkHandler.sendChatCommand("clan storage");
        state = State.WAITING_FOR_OPEN;
        timer.reset();
        triggeredByChestStealer = false;
    }

    public boolean isWorking() {
        return state != State.IDLE;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) {
            state = State.IDLE;
            return;
        }

        switch (state) {
            case WAITING_FOR_OPEN -> {
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    state = State.MOVING_ITEMS;
                    timer.reset();
                    nextDelay = 0;
                } else if (timer.hasTimePassed(5000)) { // Timeout
                    state = State.IDLE;
                    if (triggeredByChestStealer) {
                        triggeredByChestStealer = false;
                    }
                }
            }
            case MOVING_ITEMS -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    state = State.IDLE;
                    return;
                }

                if (!timer.hasTimePassed(nextDelay))
                    return;

                processInventory(screen);
            }
            case CLOSING -> {
                if (mc.currentScreen != null) {
                    mc.currentScreen.close();
                    mc.setScreen(null);
                }
                state = State.IDLE;
                if (triggeredByChestStealer) {
                    triggeredByChestStealer = false;
                }
            }
        }
    }

    private void processInventory(GenericContainerScreen screen) {
        // Inventory slots are usually the last 36 slots in the container
        // But for moving TO container, we click our inventory slots.
        // Container slots: 0 to (rows * 9) - 1
        // Player inventory: (rows * 9) to (rows * 9) + 35

        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        int totalSlots = screen.getScreenHandler().slots.size();

        boolean movedItem = false;

        // Iterate player inventory slots
        for (int i = containerSize; i < totalSlots; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (slot.hasStack()) {
                boolean shouldMove = mode.is("Все предметы") || isValuableItem(slot.getStack());
                if (shouldMove) {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE,
                            mc.player);

                    nextDelay = (long) MathUtil.random(120, 150);
                    timer.reset();
                    movedItem = true;
                    return; // One item per delay tick to avoid kick
                }
            }
        }

        if (!movedItem) {
            state = State.CLOSING;
        }
    }

    public boolean isValuableItem(ItemStack stack) {
        if (stack.getName().getString().contains("★") && stack != Items.ARROW.getDefaultStack()) {
            return true;
        }
        Item item = stack.getItem();

        return item == Items.SPLASH_POTION ||
                item == Items.DRAGON_HEAD ||
                item == Items.VILLAGER_SPAWN_EGG ||
                item == Items.ZOMBIE_VILLAGER_SPAWN_EGG ||
                item == Items.DIAMOND ||
                item == Items.NETHERITE_SCRAP ||
                item == Items.NETHERITE_INGOT ||
                item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE ||
                item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.EMERALD_ORE ||
                item == Items.ENDER_EYE ||
                item == Items.TNT ||
                item == Items.NETHER_STAR ||
                item == Items.GOLDEN_APPLE ||
                item == Items.WITHER_SKELETON_SKULL ||
                item == Items.DIAMOND_BLOCK ||
                item == Items.NETHERITE_SWORD ||
                item == Items.NETHERITE_HELMET ||
                item == Items.NETHERITE_LEGGINGS ||
                item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_BOOTS ||
                item == Items.TRIPWIRE_HOOK ||
                item == Items.BEACON ||
                item == Items.SNOWBALL ||
                item == Items.IRON_NUGGET ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.ELYTRA ||
                item == Items.REINFORCED_DEEPSLATE;
    }

    @Override
    public void onDisable() {
        state = State.IDLE;
        super.onDisable();
    }
}
