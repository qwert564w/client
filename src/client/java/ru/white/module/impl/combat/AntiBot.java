package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.other.Instance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "Anti Bot",
        desc = "Не дает Attack Aura ударить бота",
        category = Category.COMBAT
)
public class AntiBot extends Module {

    public static AntiBot getInstance() {
        return Instance.get(AntiBot.class);
    }

    private final List<PlayerEntity> bots = new ArrayList<>();

    @EventHandler
    public void onTick(EventUpdate event) {
        reallyWorldCheck();
    }

    private void reallyWorldCheck() {
        if (mc.player == null || mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player) continue;

            boolean armorCheck = false;
            boolean enchantCheck = false;
            boolean armorDamagedCheck = false;
            boolean offHandCheck = player.getOffHandStack().getItem() == Items.AIR;
            boolean equipCheck =
                    player.getEquippedStack(EquipmentSlot.FEET).getItem() == Items.LEATHER_BOOTS ||
                    player.getEquippedStack(EquipmentSlot.FEET).getItem() == Items.IRON_BOOTS ||
                    player.getEquippedStack(EquipmentSlot.LEGS).getItem() == Items.LEATHER_LEGGINGS ||
                    player.getEquippedStack(EquipmentSlot.LEGS).getItem() == Items.IRON_LEGGINGS ||
                    player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.LEATHER_CHESTPLATE ||
                    player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.IRON_CHESTPLATE ||
                    player.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.LEATHER_HELMET ||
                    player.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.IRON_HELMET;
            boolean mainHandCheck = player.getMainHandStack().getItem() != Items.AIR;
            boolean foodCheck = player.getHungerManager().getFoodLevel() == 20;

            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.FEET,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.HEAD
            }) {
                ItemStack armorPiece = player.getEquippedStack(slot);

                if (!armorCheck && armorPiece.getItem() != Items.AIR) {
                    armorCheck = true;
                }

                if (!enchantCheck && armorPiece.isEnchantable()) {
                    enchantCheck = true;
                }

                if (!armorDamagedCheck && !armorPiece.isDamaged()) {
                    armorDamagedCheck = true;
                }
            }

            if (armorCheck && enchantCheck && armorDamagedCheck && offHandCheck &&
                    equipCheck && mainHandCheck && foodCheck) {
                if (!bots.contains(player)) {
                    bots.add(player);
                }
            } else {
                bots.remove(player);
            }
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        bots.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        bots.clear();
    }

    public boolean isBot(Entity entity) {
        return entity instanceof PlayerEntity player && bots.contains(player);
    }

    public boolean isBot(LivingEntity entity) {
        return entity instanceof PlayerEntity player && bots.contains(player);
    }

    public boolean checkBot(Entity entity) {
        return isBot(entity);
    }
}
