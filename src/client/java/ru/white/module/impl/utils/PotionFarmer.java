package ru.white.module.impl.utils;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.math.ChatUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "Potion Farmer",
        desc = "Автоматически варит зелье [не роботает временно]",
        category = Category.OTHER
)
public class PotionFarmer extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<BlockPos> orderedChests = new ArrayList<>();
    private BlockPos brewingStandPos = null;
    private int currentStage = 0; // 0 - нарост, 1 - морковь, 2 - глаз, 3 - редстоун

    // Ингредиенты для варки
    private final Item[] INGREDIENTS = {
            Items.NETHER_WART,
            Items.GOLDEN_CARROT,
            Items.FERMENTED_SPIDER_EYE,
            Items.REDSTONE
    };

    @Override
    public void onEnable() {
        super.onEnable();
        orderedChests.clear();
        brewingStandPos = null;
        currentStage = 0;

        findBlocks();



      //  if (orderedChests.size() < 3 || brewingStandPos == null) {
      //      System.out.println("[PotionFarmer] Ошибка: Нужно 3 сундука друг на друге и 1 стойка!");
      //      this.toggle();
      //  }
    }

    private void findBlocks() {
        if (mc.player == null || mc.world == null) return;
        BlockPos playerPos = mc.player.getBlockPos();
        List<BlockPos> foundChests = new ArrayList<>();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    var block = mc.world.getBlockState(pos).getBlock();
                    if (block == Blocks.CHEST) {
                        foundChests.add(pos);
                    } else if (block == Blocks.BREWING_STAND) {
                        brewingStandPos = pos;
                    }
                }
            }
        }

        // Сортируем сундуки по высоте (Y): 0 - нижний, 1 - средний, 2 - верхний
        if (foundChests.size() >= 3) {
            foundChests.sort((b1, b2) -> Integer.compare(b1.getY(), b2.getY()));
            orderedChests.addAll(foundChests.subList(0, 3));
        }
    }

    // Вызывать этот метод в твоем OnTick / OnUpdate эвенте
    @EventHandler
    public void onUpdate(EventUpdate eventUpdate) {

        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        this.toggle();
        ChatUtils.addChatMessageDev("[PotionFarmer] Ошибка: Модуль временно не роботает!");
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler) {
            handleBrewingStand((BrewingStandScreenHandler) mc.player.currentScreenHandler);
        } else if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            handleChest((GenericContainerScreenHandler) mc.player.currentScreenHandler);
        } else {
            openRequiredContainer();
        }
    }

    private void openRequiredContainer() {
        // Если нет бутылочек с водой — открываем средний сундук (1)
        if (!hasWaterBottleInInventory()) {
            interactWithBlock(orderedChests.get(1));
            return;
        }
        // Если нет нужного ингредиента — открываем нижний сундук (0)
        if (currentStage < 4 && !hasItemInInventory(INGREDIENTS[currentStage])) {
            interactWithBlock(orderedChests.get(0));
            return;
        }
        // Если инвентарь забит готовыми зельями 8 мин — открываем верхний сундук (2)
        if (hasReadyPotionInInventory()) {
            interactWithBlock(orderedChests.get(2));
            return;
        }
        // Иначе открываем варочную стойку
        interactWithBlock(brewingStandPos);
    }

    private void handleBrewingStand(BrewingStandScreenHandler stand) {
        ItemStack ingredientSlot = stand.getSlot(3).getStack();
        ItemStack fuelSlot = stand.getSlot(4).getStack();

        // 1. Проверяем топливо (порошок ифрита)
        if (fuelSlot.isEmpty() && hasItemInInventory(Items.BLAZE_POWDER)) {
            shiftClickItem(Items.BLAZE_POWDER, stand);
            return;
        }

        // 2. Проверяем наличие бутылочек внизу (слоты 0, 1, 2)
        boolean hasBottles = !stand.getSlot(0).getStack().isEmpty() ||
                !stand.getSlot(1).getStack().isEmpty() ||
                !stand.getSlot(2).getStack().isEmpty();

        if (!hasBottles) {
            shiftClickWaterBottle(stand);
            return;
        }

        // 3. Кладем ровно 1 нужный ингредиент
        if (ingredientSlot.isEmpty() && currentStage < 4) {
            int invSlot = findItemSlotInInventory(INGREDIENTS[currentStage]);
            if (invSlot != -1) {
                // В BrewingStandScreenHandler слоты игрока начинаются после слотов стойки (с 5-го слота)
                int serverSlot = invSlot + 5;

                // Нажимаем правой кнопкой мыши по стаку в инвентаре, чтобы взять 1 штуку
                mc.interactionManager.clickSlot(stand.syncId, serverSlot, 1, SlotActionType.PICKUP, mc.player);
                // Кладим в слот для ингредиентов (слот 3)
                mc.interactionManager.clickSlot(stand.syncId, 3, 0, SlotActionType.PICKUP, mc.player);
                // Возвращаем остатки обратно в инвентарь
                mc.interactionManager.clickSlot(stand.syncId, serverSlot, 0, SlotActionType.PICKUP, mc.player);

                currentStage++;
                if (currentStage > 3) currentStage = 0; // Сброс цикла варки
            }
        }

        // 4. Забираем готовые зелья невидимости (8 мин) обратно в инвентарь
        for (int i = 0; i < 3; i++) {
            ItemStack potion = stand.getSlot(i).getStack();
            if (isLongInvisibilityPotion(potion)) {
                mc.interactionManager.clickSlot(stand.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
        }
    }

    private void handleChest(GenericContainerScreenHandler chest) {
        // Если в инвентаре есть готовые зелья — скидываем их в ВЕРХНИЙ сундук
        if (hasReadyPotionInInventory()) {
            for (int i = 0; i < chest.slots.size(); i++) {
                ItemStack stack = chest.getSlot(i).getStack();
                if (isLongInvisibilityPotion(stack) && i >= chest.getRows() * 9) { // Проверяем, что предмет в инвентаре игрока
                    mc.interactionManager.clickSlot(chest.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                }
            }
            return;
        }

        // Если открыт нижний сундук (ингредиенты) — добираем то, чего не хватает
        for (Item ingredient : INGREDIENTS) {
            if (!hasItemInInventory(ingredient)) {
                takeItemFromChest(chest, ingredient, 1);
                return;
            }
        }
        // Проверяем топливо
        if (!hasItemInInventory(Items.BLAZE_POWDER)) {
            takeItemFromChest(chest, Items.BLAZE_POWDER, 2);
            return;
        }

        // Если открыт средний сундук (бутылки) — берем воду
        if (!hasWaterBottleInInventory()) {
            takeWaterBottleFromChest(chest, 3);
        }
    }

    // --- Методы проверки предметов через Data Components (1.21+) ---

    private boolean isLongInvisibilityPotion(ItemStack stack) {
        if (stack.getItem() != Items.POTION) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null && potionContents.potion().isPresent()) {
            // Получаем RegistryEntry из опционала
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> entry = potionContents.potion().get();

            // В Yarn у RegistryEntry есть метод matchesKey(), но ему нужно передавать ключ,
            // либо можно сравнить напрямую с самой записью Potions.LONG_INVISIBILITY через метод entry.equals()
            return entry.equals(Potions.LONG_INVISIBILITY);
        }
        return false;
    }

    private boolean isWaterBottle(ItemStack stack) {
        if (stack.getItem() != Items.POTION) return false;

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null && potionContents.potion().isPresent()) {
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> entry = potionContents.potion().get();

            // Сравниваем запись напрямую с Potions.WATER
            return entry.equals(Potions.WATER);
        }
        return false;
    }

    private boolean hasWaterBottleInInventory() {
        if (mc.player == null) return false;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (isWaterBottle(inv.getStack(i))) return true;
        }
        return false;
    }

    private boolean hasReadyPotionInInventory() {
        if (mc.player == null) return false;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (isLongInvisibilityPotion(inv.getStack(i))) return true;
        }
        return false;
    }

    private boolean hasItemInInventory(Item item) {
        return findItemSlotInInventory(item) != -1;
    }

    private int findItemSlotInInventory(Item item) {
        if (mc.player == null) return -1;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    // --- Взаимодействие с контейнерами (1.21+) ---

    private void interactWithBlock(BlockPos pos) {
        if (pos == null || mc.interactionManager == null || mc.player == null) return;
        BlockHitResult hitResult = new BlockHitResult(
                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                Direction.UP, pos, false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
    }

    private void shiftClickItem(Item item, BrewingStandScreenHandler stand) {
        for (int i = 5; i < stand.slots.size(); i++) { // Пропускаем слоты стойки
            if (stand.getSlot(i).getStack().getItem() == item) {
                mc.interactionManager.clickSlot(stand.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                break;
            }
        }
    }

    private void shiftClickWaterBottle(BrewingStandScreenHandler stand) {
        for (int i = 5; i < stand.slots.size(); i++) {
            if (isWaterBottle(stand.getSlot(i).getStack())) {
                mc.interactionManager.clickSlot(stand.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
        }
    }

    private void takeItemFromChest(GenericContainerScreenHandler chest, Item item, int amount) {
        int chestSize = chest.getRows() * 9;
        int taken = 0;
        for (int i = 0; i < chestSize; i++) {
            if (chest.getSlot(i).getStack().getItem() == item) {
                mc.interactionManager.clickSlot(chest.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                taken++;
                if (taken >= amount) break;
            }
        }
    }

    private void takeWaterBottleFromChest(GenericContainerScreenHandler chest, int amount) {
        int chestSize = chest.getRows() * 9;
        int taken = 0;
        for (int i = 0; i < chestSize; i++) {
            if (isWaterBottle(chest.getSlot(i).getStack())) {
                mc.interactionManager.clickSlot(chest.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                taken++;
                if (taken >= amount) break;
            }
        }
    }
}