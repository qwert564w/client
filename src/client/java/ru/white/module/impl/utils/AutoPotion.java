package ru.white.module.impl.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.TimerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        name = "Auto Potion",
        desc = "Смотрит вниз и кидает все нужные зелья сразу (Сила, Скорость, Огнестойкость), когда эффекта нет",
        category = Category.OTHER
)
public class AutoPotion extends Module {

    public final MultiBooleanSetting potions = new MultiBooleanSetting(this, "Зелья",
            new BooleanSetting("Сила", true),
            new BooleanSetting("Скорость", true),
            new BooleanSetting("Огнестойкость", true));

    public final SliderSetting delay = new SliderSetting(this, "Задержка", 500, 100, 2000, 50);

    private final TimerUtil throwCooldown = new TimerUtil();
    private int oldSlot = -1;

    @Override
    protected void onDisable() {
        restoreSlot();
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.networkHandler == null) {
            return;
        }

        if (mc.currentScreen != null || mc.player.isUsingItem()) {
            return;
        }

        List<Integer> slots = collectPotionSlots();
        if (slots.isEmpty()) {
            restoreSlot();
            return;
        }

        RotationProcess.update(new Rotation(mc.player.getYaw(), 90f), 180, 180, 2, 90);

        if (mc.player.getPitch() < 88f) {
            return;
        }

        if (!throwCooldown.isReached((long) delay.getValue().floatValue())) {
            return;
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();

        for (int slot : slots) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        restoreSlot();
        throwCooldown.reset();
    }

    private void restoreSlot() {
        if (oldSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
            }
            oldSlot = -1;
        }
    }

    private List<Integer> collectPotionSlots() {
        List<Integer> slots = new ArrayList<>();
        Set<String> queued = new HashSet<>();

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.SPLASH_POTION) {
                continue;
            }

            List<String> eligible = getEligibleEffects(stack);
            if (eligible.isEmpty()) {
                continue;
            }

            boolean useful = false;
            for (String key : eligible) {
                if (!queued.contains(key)) {
                    useful = true;
                    break;
                }
            }

            if (useful) {
                slots.add(i);
                queued.addAll(eligible);
            }
        }

        return slots;
    }

    private List<String> getEligibleEffects(ItemStack stack) {
        List<String> keys = new ArrayList<>();
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return keys;
        }
        for (StatusEffectInstance instance : contents.getEffects()) {
            RegistryEntry<StatusEffect> type = instance.getEffectType();
            String key = effectKey(type);
            if (key == null || !potions.getValue(key)) {
                continue;
            }
            if (!mc.player.hasStatusEffect(type)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String effectKey(RegistryEntry<StatusEffect> type) {
        if (type.value() == StatusEffects.STRENGTH.value()) {
            return "Сила";
        }
        if (type.value() == StatusEffects.SPEED.value()) {
            return "Скорость";
        }
        if (type.value() == StatusEffects.FIRE_RESISTANCE.value()) {
            return "Огнестойкость";
        }
        return null;
    }
}
