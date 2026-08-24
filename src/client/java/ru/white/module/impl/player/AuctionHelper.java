package ru.white.module.impl.player;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.StopGPT;
import ru.white.utils.other.Instance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "Auction Helper",
        category = Category.PLAYER,
        desc = "Подсвечивает 3 самых дешёвых лота. Поиск и автопродажа по биндам."
)
public class AuctionHelper extends Module {

    public static AuctionHelper get() {
        return Instance.get(AuctionHelper.class);
    }

    public final BindSetting    searchKey    = new BindSetting(this, "Клавиша поиска");
    public final BindSetting    sellKey      = new BindSetting(this, "Клавиша продажи");
    public final BooleanSetting autoNextPage = new BooleanSetting(this, "Листать страницы", true);
    public final SliderSetting  maxPagesSetting = new SliderSetting(this, "Макс. страниц", 50, 1, 200, 1)
            .setVisible(() -> autoNextPage.getValue());

    private enum SellState { IDLE, WAITING_AH_OPEN, READING_PRICES, NEXT_PAGE, SEND }

    private SellState sellState = SellState.IDLE;
    private final StopGPT timer = new StopGPT();
    private String savedItemName  = "";
    private int    savedItemCount = 1;
    private long   savedSellPrice = 0;
    private int    waitTicks      = 0;
    private List<String> handEnchants = new ArrayList<>();
    // vanilla enchantment component (null = item not enchanted or only custom enchants)
    private ItemEnchantmentsComponent handEnchantComp = null;
    // -1 = no durability check (new item or has Mending)
    private float handDurabilityPercent = -1f;
    private static final float DURABILITY_TOLERANCE = 0.15f;
    private Item savedItem   = null;
    private int nextPageSlot = -1;
    private int pagesScanned = 0;


    @EventHandler
    public void onKey(EventKey e) {
        if (mc.player == null || e.getKey() == -1) return;
        if (sellState != SellState.IDLE) return;

        if (e.getKey() == searchKey.get()) performSearch();
        else if (e.getKey() == sellKey.get()) startSell();
    }

    private void performSearch() {
        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty()) { actionbar("§cНет предмета!"); return; }

        String name = cleanItemName(hand.getName().getString());
        if (name.isEmpty()) return;

        mc.player.networkHandler.sendChatCommand("ah search " + name);
        actionbar("§aПоиск: " + name);
    }

    private void startSell() {
        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty()) { actionbar("§cНет предмета!"); return; }

        savedItemName  = cleanItemName(hand.getName().getString());
        savedItemCount = hand.getCount();
        savedItem      = hand.getItem();
        handEnchants   = getEnchantNames(hand);


        ItemEnchantmentsComponent comp = hand.get(DataComponentTypes.ENCHANTMENTS);
        handEnchantComp = (comp != null && !comp.isEmpty()) ? comp : null;

        if (savedItemName.isEmpty()) return;

        if (hasMending(hand)) {
            handDurabilityPercent = -1f;
        } else {
            float dur = getDurabilityPercent(hand);
            handDurabilityPercent = (dur < 0.999f) ? dur : -1f;
        }

        mc.player.networkHandler.sendChatCommand("ah search " + savedItemName);

        if (handEnchantComp != null || !handEnchants.isEmpty()) {
            String enchList = handEnchants.isEmpty()
                    ? "ванильные зачары"
                    : String.join(", ", handEnchants);
            chat("§e[AH] Зачарования: §f" + enchList);
        }
        if (handDurabilityPercent >= 0) {
            chat("§e[AH] Прочность: §f" + (int)(handDurabilityPercent * 100) + "%");
        }
        actionbar("§e[AH] Анализ цен...");

        sellState = SellState.WAITING_AH_OPEN;
        timer.reset();
        waitTicks = 0;
    }


    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || sellState == SellState.IDLE) return;

        switch (sellState) {

            case WAITING_AH_OPEN -> {

                if (++waitTicks > 10) { chat("§c[AH] Аукцион не открылся."); resetState(); return; }
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    if (waitTicks < 4) return;
                    sellState = SellState.READING_PRICES;
                    waitTicks = 0;
                }
            }

            case READING_PRICES -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) { resetState(); return; }

                GenericContainerScreenHandler handler = screen.getScreenHandler();
                int containerSlots = handler.getRows() * 9;
                long cheapestPerOne = Long.MAX_VALUE;
                int found = 0;


                float closestDurDiff = Float.MAX_VALUE;
                long closestCheapest = Long.MAX_VALUE;

                for (int i = 0; i < containerSlots; i++) {
                    Slot slot = handler.getSlot(i);
                    int price = getPrice(slot);
                    if (price <= 0) continue;


                    if (savedItem != null && slot.getStack().getItem() != savedItem) continue;


                    if (handEnchantComp != null) {
                        ItemEnchantmentsComponent slotComp = slot.getStack().get(DataComponentTypes.ENCHANTMENTS);
                        if (!enchantCompMatch(handEnchantComp, slotComp)) continue;
                    } else if (!handEnchants.isEmpty()) {
                        try {
                            if (!enchantsMatch(handEnchants, getEnchantNames(slot.getStack()))) continue;
                        } catch (Exception ignored) { continue; }
                    }

                    long perOne = (long) price / Math.max(slot.getStack().getCount(), 1);


                    if (handDurabilityPercent >= 0) {
                        float slotDur = getDurabilityPercent(slot.getStack());
                        float diff = Math.abs(slotDur - handDurabilityPercent);


                        if (diff < closestDurDiff || (diff == closestDurDiff && perOne < closestCheapest)) {
                            closestDurDiff = diff;
                            closestCheapest = perOne;
                        }

                        if (diff > DURABILITY_TOLERANCE) continue;
                    }

                    found++;
                    if (perOne < cheapestPerOne) cheapestPerOne = perOne;
                }

                if (found == 0 && handDurabilityPercent >= 0 && closestCheapest != Long.MAX_VALUE) {
                    chat("§e[AH] Точной прочности нет, ближайшая (" + (int)(closestDurDiff * 100) + "% разница)");
                    cheapestPerOne = closestCheapest;
                    found = 1;
                }

                if (found == 0 || cheapestPerOne == Long.MAX_VALUE) {
                    int npSlot = autoNextPage.getValue() ? findNextPageSlot(handler, containerSlots) : -1;
                    if (npSlot >= 0 && pagesScanned < maxPagesSetting.getValue().intValue()) {
                        nextPageSlot = npSlot;
                        pagesScanned++;
                        sellState = SellState.NEXT_PAGE;
                        waitTicks = 0;
                        return;
                    }
                    chat("§c[AH] Нет подходящих лотов (просмотрено страниц: " + pagesScanned + ").");
                    mc.player.closeHandledScreen();
                    resetState();
                    return;
                }

                long totalPrice = roundPrice(cheapestPerOne * savedItemCount);
                savedSellPrice  = totalPrice;

                chat("§a[AH] Нашёл " + found + " лотов. Продаю за §l$" + formatNumber(totalPrice));

                mc.player.closeHandledScreen();
                sellState = SellState.SEND;
                waitTicks = 0;
            }

            case NEXT_PAGE -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) { resetState(); return; }
                if (waitTicks == 0) {
                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    mc.interactionManager.clickSlot(handler.syncId, nextPageSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                if (++waitTicks >= 6) {
                    sellState = SellState.READING_PRICES;
                    waitTicks = 0;
                }
            }

            case SEND -> {

                if (++waitTicks < 4) return;
                if (savedSellPrice > 0) {
                    mc.player.networkHandler.sendChatCommand("ah sell " + savedSellPrice);
                }
                resetState();
            }
        }
    }


    public void renderSlot(DrawContext context, Slot slot, int rank) {
        int fill = switch (rank) {
            case 1 -> 0x6055FF55;
            case 2 -> 0x60FFFF55;
            default -> 0x60FFAA00;
        };
        int outline = switch (rank) {
            case 1 -> 0xFF00DD00;
            case 2 -> 0xFFDDDD00;
            default -> 0xFFFF8800;
        };
        int x = slot.x, y = slot.y;
        context.fill(x, y, x + 16, y + 16, fill);
        context.fill(x,      y,      x + 16, y + 1,  outline);
        context.fill(x,      y + 15, x + 16, y + 16, outline);
        context.fill(x,      y,      x + 1,  y + 16, outline);
        context.fill(x + 15, y,      x + 16, y + 16, outline);
    }

    public static int getPrice(Slot slot) {
        if (!slot.hasStack()) return 0;
        LoreComponent lore = slot.getStack().getComponents().get(DataComponentTypes.LORE);
        if (lore == null) return 0;
        for (Text line : lore.lines()) {
            String text = line.getString();
            if (text.contains("$") || text.toLowerCase().contains("цена")) {
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try { return Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0;
    }


    private long roundPrice(long price) {
        if (price <= 0) return 1;

        if (price >= 1000) {
            long thousands = price / 1000;      // 350321 -> 350
            long lastDigit = (price % 1000) / 100; // первая цифра последних 3

            // если последние 3 цифры >= 500 -> добавляем 500
            if (price % 1000 >= 500) {
                return thousands * 1000 + 500;
            }

            return thousands * 1000;
        }

        // для маленьких чисел
        return (price / 10) * 10;
    }

    public static List<String> getEnchantNames(ItemStack stack) {
        List<String> result = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return result;
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore == null) return result;
        for (Text line : lore.lines()) {
            String raw = line.getString().trim();
            if (raw.isEmpty() || raw.contains("$") || raw.toLowerCase().contains("цена")
                    || raw.contains("Продавец") || raw.contains("Истекает")
                    || raw.contains("Нажмите") || raw.contains("Починка")) continue;
            if (raw.matches(".*[IVX1-9]+$") || isKnownEnchant(raw)) result.add(raw);
        }
        return result;
    }

    private static boolean isKnownEnchant(String s) {
        return s.contains("Острота") || s.contains("Эффективность") || s.contains("Прочность")
                || s.contains("Удача") || s.contains("Добыча") || s.contains("Небесная кара")
                || s.contains("Заговор огня") || s.contains("Бич") || s.contains("Разящий")
                || s.contains("Вампиризм") || s.contains("Окисление") || s.contains("Яд")
                || s.contains("Детекция") || s.contains("Бульдозер") || s.contains("Магнит")
                || s.contains("Авто-Плавка") || s.contains("Паутина") || s.contains("Пингер")
                || s.contains("Опытный") || s.contains("Шёлковое") || s.contains("Шелковое")
                || s.contains("Подводная") || s.contains("Защита") || s.contains("Отделка")
                || s.contains("Улучшение") || s.contains("Шипы") || s.contains("Легкость")
                || s.contains("Книга");
    }

    // сравниваем по ванильному компоненту — каждое зачарование и его уровень должны совпасть
    private boolean enchantCompMatch(ItemEnchantmentsComponent hand, ItemEnchantmentsComponent slot) {
        if (slot == null || slot.isEmpty()) return false;
        for (RegistryEntry<Enchantment> entry : hand.getEnchantments()) {
            if (hand.getLevel(entry) != slot.getLevel(entry)) return false;
        }
        return true;
    }

    private boolean enchantsMatch(List<String> hand, List<String> slot) {
        if (hand.isEmpty()) return true;
        if (slot.isEmpty()) return false;
        for (String he : hand) {
            String base = extractBase(he);
            int lvl = extractLevel(he);
            boolean found = false;
            for (String se : slot) {
                if (extractBase(se).contains(base) || base.contains(extractBase(se))) {
                    if (extractLevel(se) == lvl) { found = true; break; }
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private String extractBase(String s) { return s.replaceAll("\\s+[IVX0-9]+$", "").trim(); }

    private int extractLevel(String s) {
        String trimmed = s.trim();
        int sp = trimmed.lastIndexOf(' ');
        if (sp == -1) return 1;
        String suf = trimmed.substring(sp + 1);
        int r = parseRoman(suf);
        if (r > 0) return r;
        try { return Integer.parseInt(suf); } catch (NumberFormatException ignored) {}
        return 1;
    }

    private int parseRoman(String r) {
        return switch (r) {
            case "I"    -> 1;  case "II"   -> 2;  case "III"  -> 3;
            case "IV"   -> 4;  case "V"    -> 5;  case "VI"   -> 6;
            case "VII"  -> 7;  case "VIII" -> 8;  case "IX"   -> 9;
            case "X"    -> 10; case "XI"   -> 11; case "XII"  -> 12;
            case "XIII" -> 13; case "XIV"  -> 14; case "XV"   -> 15;
            default -> 0;
        };
    }

    private String cleanItemName(String name) {
        if (name == null || name.isEmpty()) return "";
        name = name.replaceAll("§.", "");
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '_')
                sb.append(c);
        }
        return sb.toString().trim().replace("Пузырёк опыта 15 Ур", "Пузырек с уровнем 15")
                .replace("Пузырёк опыта 30 Ур", "Пузырек с уровнем 30")
                .replace("Пузырёк опыта 45 Ур", "Пузырек с уровнем 45")
                .replace("Пузырёк опыта 50 Ур", "Пузырек с уровнем 50")
                .replace("xxx", "").replace("TNT - TIER WHITE", "Таер вайт")
                .replace("TNT - TIER BLACK", "Таер блэк")
                .replace("Динамит", "Динамит обычный")
                .replace("Яйцо призыва крестьянина-зомби", "яйцо зомби-крестьянина")
                .replace("Снежок заморозка", "Снежок заморозка");
    }

    private float getDurabilityPercent(ItemStack stack) {
        Integer damage    = stack.get(DataComponentTypes.DAMAGE);
        Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (damage == null || maxDamage == null || maxDamage == 0) return 1.0f;
        return (float)(maxDamage - damage) / maxDamage;
    }

    private boolean hasMending(ItemStack stack) {
        ItemEnchantmentsComponent comp = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (comp != null && !comp.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : comp.getEnchantments()) {
                if (entry.getIdAsString().contains("mending")) return true;
            }
        }
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().contains("Починка")) return true;
            }
        }
        return false;
    }

    private int findNextPageSlot(GenericContainerScreenHandler handler, int containerSlots) {

        int lastRowStart = containerSlots - 9;
        for (int i = lastRowStart; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            String name = slot.getStack().getName().getString().replaceAll("§.", "");
            if (name.contains("Следующая страница")) return i;
        }
        return -1;
    }

    private String formatNumber(long n) { return String.format("%,d", n).replace(',', '.'); }

    private void actionbar(String msg) { ChatUtils.addChatMessage(msg); }
    private void chat(String msg)      { ChatUtils.addChatMessage(msg); }

    private void resetState() {
        sellState     = SellState.IDLE;
        waitTicks     = 0;
        savedItemName = "";
        savedItemCount = 1;
        savedSellPrice = 0;
        handEnchants.clear();
        handEnchantComp = null;
        handDurabilityPercent = -1f;
        savedItem     = null;
        nextPageSlot  = -1;
        pagesScanned  = 0;
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }
}
