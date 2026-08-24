package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.math.InvUtil;
import ru.white.utils.math.StopWatchShadow;
import ru.white.utils.notification.NotificationManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;


@ModuleInfo(
        name = "Auto Swap",
        category = Category.COMBAT,
        desc = "Свап предметов по клавише."
)
public class AutoSwap extends Module {


    private final ModeSetting mode = new ModeSetting(this, "Режим", "Двойной", "Тройной");

    private final ModeSetting firstItemSetting  = new ModeSetting(this, "Первый предмет",
            "Шар", "Золотое яблоко", "Щит", "Тотем")
            .setVisible(() -> mode.is("Двойной"));
    private final ModeSetting secondItemSetting = new ModeSetting(this, "Второй предмет",
            "Шар 2", "Золотое яблоко 2", "Щит 2", "Тотем 2")
            .setVisible(() -> mode.is("Двойной"));


    private final BindSetting    bind          = new BindSetting(this,    "Клавиша свапа",       -1);
    private final BooleanSetting swaprender    = new BooleanSetting(this, "Уведомления о свапе", true);
    private final BooleanSetting onlyEnchanted = new BooleanSetting(this, "Только Зач. Тотем",   false)
            .setVisible(() -> mode.is("Двойной"));


    private final BooleanSetting tripleAnimations = new BooleanSetting(this, "Анимации тройного", true)
            .setVisible(() -> mode.is("РўСЂРѕР№РЅРѕР№"));

    private boolean swap;
    private boolean hand;
    private final StopWatchShadow swapWatch = new StopWatchShadow();
    private boolean bypassActive;
    private boolean bypassSwapped;
    private int     bypassSlot    = -1;
    private String  bypassItemName = "";


    ItemStack[] tripleSlotItems = new ItemStack[]{null, null, null};

    
    private boolean isBindHeld() {
        int k = bind.get();
        if (k == -1) return false;
        long handle = mc.getWindow().getHandle();
        if (k >= 0 && k <= 7) {
            return GLFW.glfwGetMouseButton(handle, k) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(mc.getWindow(), k);
    }

    @EventHandler
    public void update(EventTick event) {
        if (!mc.player.isAlive()) {
            setKey(true);
        }

        if (mode.getValue().equals("Тройной")
                && mc.currentScreen instanceof TripleWheelScreen ws) {
            if (!isBindHeld() && !ws.isSelectionAnimating()) {
                int hov = ws.getHoveredSlot();
                if (tripleAnimations.getValue()) {
                    if (hov < 0) {
                        ws.startCloseAnimation(null);
                        return;
                    }
                    ws.startSelectionAnimation(hov, () -> {
                        executeTripleSwap(hov);
                    });
                } else {
                    mc.setScreen(null);
                    if (hov >= 0) executeTripleSwap(hov);
                }
            }
            return;
        }

        ScreenHandler screenHandler = mc.player.currentScreenHandler;
        if (bypassActive) {
            if (!bypassSwapped && bypassSlot != -1 && !mc.player.isSprinting()) {
                int invSlot = bypassSlot >= 36 ? bypassSlot - 36 : bypassSlot;
                ItemStack swappedItem = mc.player.getInventory().getStack(invSlot).copy();

                mc.interactionManager.clickSlot(
                        screenHandler.syncId,
                        bypassSlot < 9 ? bypassSlot + 36 : bypassSlot,
                        40,
                        SlotActionType.SWAP,
                        mc.player
                );
                mc.player.networkHandler.sendPacket(
                        new CloseHandledScreenC2SPacket(screenHandler.syncId)
                );
                setKey(true);

                if (swaprender.getValue()) {
                    String name = !bypassItemName.isEmpty()
                            ? bypassItemName
                            : swappedItem.isEmpty() ? "предмет" : swappedItem.getName().getString();
                    if (!swappedItem.isEmpty()) {
                        NotificationManager.send("Свапнул на " + name,
                                NotificationManager.Type.INFO, swappedItem.copy(), 2000);
                    } else {
                        NotificationManager.send("Свапнул на " + name,
                                NotificationManager.Type.INFO, 2000);
                    }
                }
                bypassSwapped = true;
            }
            if (bypassSwapped || swapWatch.hasTimeElapsed(500)) {
                bypassActive = false;
                bypassSwapped = false;
                bypassSlot = -1;
                bypassItemName = "";
            }
            return;
        }


        if (mode.getValue().equals("Двойной")) {
            if (this.swap && this.hand) {
                if (this.firstItemSetting.getValue().equals("Шар")) {
                    this.swap(Items.PLAYER_HEAD, "Шар", false);
                } else if (this.firstItemSetting.getValue().equals("Тотем")) {
                    this.swap(Items.TOTEM_OF_UNDYING, "Тотем", this.onlyEnchanted.getValue());
                } else if (this.firstItemSetting.getValue().equals("Золотое яблоко")) {
                    this.swap(Items.GOLDEN_APPLE, "Золотое яблоко", false);
                } else if (this.firstItemSetting.getValue().equals("Щит")) {
                    this.swap(Items.SHIELD, "Щит", false);
                }
                this.hand = false;
            }
            if (this.swap) {
                if (this.secondItemSetting.getValue().equals("Шар 2")) {
                    this.swap(Items.PLAYER_HEAD, "Шар", false);
                } else if (this.secondItemSetting.getValue().equals("Золотое яблоко 2")) {
                    this.swap(Items.GOLDEN_APPLE, "Золотое яблоко", false);
                } else if (this.secondItemSetting.getValue().equals("Тотем 2")) {
                    this.swap(Items.TOTEM_OF_UNDYING, "Тотем", this.onlyEnchanted.getValue());
                } else if (this.secondItemSetting.getValue().equals("Щит 2")) {
                    this.swap(Items.SHIELD, "Щит", false);
                }
                this.hand = true;
            }
        }
    }


    @EventHandler
    public void input(EventKey event) {
        if (mc.currentScreen != null || bind.get() == -1) return;
        if (event.getKey() != bind.get()) return;

        if (mode.getValue().equals("Двойной")) {
            this.swap = true;
        } else if (mode.getValue().equals("Тройной")) {

            mc.execute(() -> mc.setScreen(new TripleWheelScreen(AutoSwap.this)));
        }
    }


    void executeTripleSwap(int slotIndex) {
        if (mc.player == null) return;
        ItemStack target = tripleSlotItems[slotIndex];
        if (target == null || target.isEmpty()) return;

        int slot = findSlotByStack(target);
        if (slot != -1) {
            setKey(false);
            AttackAura.stoptick  = 3;
            bypassActive   = true;
            bypassSwapped  = false;
            bypassSlot     = slot;
            bypassItemName = target.getName().getString();
            swapWatch.reset();
            this.swap = false;
        }
    }

    boolean isTripleAnimationsEnabled() {
        return tripleAnimations.getValue();
    }

    private int findSlotByStack(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(s, target)) {
                return i < 9 ? i + 36 : i;
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == target.getItem()) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }


    private void swap(Item item, String itemName, boolean onlyEnchanted) {
        int slot = item == Items.TOTEM_OF_UNDYING
                ? findTotemSlot(onlyEnchanted)
                : InvUtil.find(item);

        if (slot != -1) {
            setKey(false);
            AttackAura.stoptick  = 3;
            bypassActive   = true;
            bypassSwapped  = false;
            bypassSlot     = slot;
            bypassItemName = itemName;
            swapWatch.reset();
        }
        this.swap = false;
    }

    private int findTotemSlot(boolean onlyEnchanted) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.TOTEM_OF_UNDYING && s.hasGlint())
                return i < 9 ? i + 36 : i;
        }
        if (!onlyEnchanted) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.getItem() == Items.TOTEM_OF_UNDYING)
                    return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    @Override
    
    public void onDisable() {

        if (mc.currentScreen instanceof TripleWheelScreen
         || mc.currentScreen instanceof TriplePickerScreen) {
            mc.execute(() -> mc.setScreen(null));
        }
        super.onDisable();
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
