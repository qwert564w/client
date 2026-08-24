package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.movement.Sprint;
import ru.white.utils.math.StopWatchP;
import ru.white.utils.notification.NotificationManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;


import java.util.stream.IntStream;


@ModuleInfo(
        name = "Auto Totem",
        desc = "Автоматически держит тотем в руке",
        category = Category.COMBAT
)
public class AutoTotem extends Module {

    private final BooleanSetting elytraHealthCheck = new BooleanSetting(this, "Здоровье с элитрами", true);
    private final BooleanSetting tntCheck = new BooleanSetting(this, "Динамит", true);
    private final BooleanSetting fallCheck = new BooleanSetting(this, "Падение", false);
    private final BooleanSetting crystalCheck = new BooleanSetting(this, "Эндер-кристалл", false);
    private final BooleanSetting notSwapifEat = new BooleanSetting("Не свап при еде", true);
    private final SliderSetting health = new SliderSetting(this,"Здоровье", 4.0F, 1.0F, 20.0F, 0.5F);
    private final SliderSetting elytraHealth = new SliderSetting(this,"Здоровье на элитре", 9.0F, 0.0F, 20.0F, 0.5F).setVisible(() -> elytraHealthCheck.getValue());
    private final SliderSetting crystalDistance = new SliderSetting(this,"Дистанция до крист", 4.0F, 1.0F, 10.0F, 1.0F).setVisible(() -> crystalCheck.getValue());
    private final SliderSetting tntDistance = new SliderSetting(this,"Дистанция до тнт", 30.0F, 3.0F, 50.0F, 1.0F).setVisible(() -> tntCheck.getValue());
    private final BooleanSetting noBall = new BooleanSetting(this,"Не свапать если шар", false);



    private int oldSlot = -1;
    private ItemStack oldOffhandItem = ItemStack.EMPTY;
    int nonEnchantedTotems;
    private final StopWatchP lockWatch = new StopWatchP();
    private boolean lockHeld;
    private int lastNotifiedTotemSlot = -1;

    @EventHandler
    public void update(EventUpdate eventUpdate) {
        if (mc.player == null || !mc.player.isAlive() || mc.world == null) {
            if (lockHeld) {
                lockHeld = false;
                resetSwapBack();
            }
            return;
        }

        this.nonEnchantedTotems = (int) IntStream.range(0, 36)
                .mapToObj((i) -> mc.player.getInventory().getStack(i))
                .filter((s) -> s.getItem() == Items.TOTEM_OF_UNDYING && !s.hasGlint())
                .count();




        if(!checkToAttack()) swap();
    }
    
    private boolean checkToAttack() {
        return (mc.player.isUsingItem() && notSwapifEat.getValue()
                && ( !(mc.player.getActiveItem().getItem() instanceof ShieldItem)));
    }

    private void swap() {
        int slot = this.findNonEnchantedTotemSlot();
        boolean totemInHand = isTotemInHands();

        if (this.canSwap()) {
            if (slot >= 0 && !totemInHand) {

                if (!lockHeld) {
                    setKey(false);
                    AttackAura.stoptick = 3;
                    Sprint.get().tick += 1;
                    lockHeld = true;
                    lockWatch.reset();
                }

                if (mc.currentScreen == null && !mc.player.isSprinting()) {
                    if (oldOffhandItem.isEmpty() && !mc.player.getOffHandStack().isEmpty()) {
                        oldOffhandItem = mc.player.getOffHandStack().copy();
                        oldSlot = slot;
                    }

                    if (slot != lastNotifiedTotemSlot) {
                        lastNotifiedTotemSlot = slot;
                        int invIdx = slot >= 36 ? slot - 36 : slot;
                        ItemStack totem = mc.player.getInventory().getStack(invIdx).copy();
                        if (!totem.isEmpty()) {
                            NotificationManager.send("Тотем подложен", NotificationManager.Type.MODULE, totem, 2000);
                        }
                    }

                    swapHand(slot, 40);
                    lockHeld = false;
                    setKey(true);
                    return;
                }

                if (lockHeld && lockWatch.isReached(250L)) {
                    lockHeld = false;
                    setKey(true);
                    resetSwapBack();
                }
            }
        } else if (oldSlot != -1 && !oldOffhandItem.isEmpty()) {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && !this.canSwap()) {

                if (!lockHeld) {
                    setKey(false);
                    AttackAura.stoptick = 3;
                    Sprint.get().tick += 1;
                    lockHeld = true;
                    lockWatch.reset();
                }

                if (mc.currentScreen == null && !mc.player.isSprinting()) {
                    swapHand(oldSlot, 40);
                    lockHeld = false;
                    resetSwapBack();
                    setKey(true);
                    return;
                }

                if (lockHeld && lockWatch.isReached(250L)) {
                    lockHeld = false;
                    setKey(true);
                    resetSwapBack();
                }
            } else {
                swapHand(oldSlot, 40);
                resetSwapBack();
            }
        } else {
            resetSwapBack();
        }
    }

    private void swapHand(int slot, int button) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, button, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
    }

    private void resetSwapBack() {
        oldOffhandItem = ItemStack.EMPTY;
        oldSlot = -1;
        lastNotifiedTotemSlot = -1;
    }

    private int findNonEnchantedTotemSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING && !stack.hasGlint()) {
                return i < 9 ? i + 36 : i;
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return i < 9 ? i + 36 : i;
            }
        }

        return -1;
    }

    public boolean isTotemInHands() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (mainHand.getItem() == Items.TOTEM_OF_UNDYING) {
            return !mainHand.hasGlint() || this.nonEnchantedTotems <= 0;
        }

        if (offHand.getItem() == Items.TOTEM_OF_UNDYING) {
            return !offHand.hasGlint() || this.nonEnchantedTotems <= 0;
        }

        return false;
    }


    private boolean canSwap() {
        boolean flag1 = this.elytraCheck();
        boolean flag2 = this.checkCrystal();
        boolean flag3 = this.checkTnt();
        boolean flag4 = this.checkFall();
        boolean flag6 = mc.player.getHealth() + this.getAbsorption() <= this.health.getValue();
        return flag1 || flag2 || flag3 || flag4 || flag6;
    }

    private boolean elytraCheck() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean elytra = chestStack.getItem() == Items.ELYTRA && elytraHealthCheck.getValue();
        return elytra && this.checkHealth();
    }

    private boolean checkFall() {
        if (!fallCheck.getValue()) {
            return false;
        } else {
            return (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.8F);
        }
    }

    private boolean checkHealth() {
        return mc.player.getHealth() + this.getAbsorption() <= this.elytraHealth.getValue();
    }

    private boolean checkCrystal() {
        if (!crystalCheck.getValue()) {
            return false;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && mc.player.distanceTo(entity) <= this.crystalDistance.getValue()) {
                return !(mc.player.getOffHandStack().getItem() instanceof PlayerHeadItem) || !this.noBall.getValue();
            }
        }
        return false;
    }

    private boolean checkTnt() {
        if (!tntCheck.getValue()) {
            return false;
        }

        for (Entity entity : mc.world.getEntities()) {
            float distance = mc.player.distanceTo(entity);
            if ((entity instanceof TntEntity || entity instanceof TntMinecartEntity) &&
                    distance <= this.tntDistance.getValue()) {
                return true;
            }
        }
        return false;
    }

    private float getAbsorption() {
        return mc.player.getAbsorptionAmount();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (lockHeld) {
            lockHeld = false;
        }
        resetSwapBack();
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


}
