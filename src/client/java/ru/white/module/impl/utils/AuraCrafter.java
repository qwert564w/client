package ru.white.module.impl.utils;

import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import ru.white.module.impl.display.Hud;
import ru.white.module.impl.render.EntityEsp;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.potion.Potions;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

@ModuleInfo(
        name = "Aura Crafter",
        category = Category.OTHER,
        desc = "Крафт Божьей ауры, простая закупка и простая продажа по биндам."
)
public class AuraCrafter extends Module {

    private static final String AURA_NAME = "Божья аура";
    private static final int TICK_DELAY = 6;
    private static final int JOIN_AH_DELAY = 200;
    private static final int NETHERITE_SWITCH_TICKS = 1800;
    private static final int AH_SEARCH_RETRY_TICKS = 100;
    private static final int AUTO_INVIS_CHECK_TICKS = 80;
    private static final int INVIS_DRINK_TIMEOUT_TICKS = 60;
    private static final int BALANCE_REFRESH_CALLS = 55;
    private static final int MAX_MILK_BUCKET_USES = 8;
    private static final int STORAGE_REOPEN_TIMEOUT_TICKS = 180;
    private static final int STORAGE_EMPTY_SETTLE_TICKS = 60;
    private static final int PLAYER_PAUSE_RETURN_TICKS = 20 * 60 * 5;
    private static final String INVIS_SEARCH_QUERY = "инвиз";
    private static final double COW_INTERACT_RANGE = 4.5;

    public final BindSetting craftBind = new BindSetting(this, "Бинд крафта");
    public final BindSetting sellBind = new BindSetting(this, "Бинд продажи");
    public final BindSetting fullAutoBind = new BindSetting(this, "Бинд фулл авто");
    public final BindSetting buyNetheriteBind = new BindSetting(this, "Бинд незера");
    public final BindSetting buyDiamondsBind = new BindSetting(this, "Бинд алмазов");

    public final StringSetting buyItem = new StringSetting(this, "Что покупать", "Незеритовый слиток");
    public final StringSetting maxBuyPrice = new StringSetting(this, "Макс цена 1шт", "325000", true);
    public final StringSetting diamondStackPrice = new StringSetting(this, "Алмазы 64шт", "250000", true);
    public final StringSetting reserveMoney = new StringSetting(this, "Резерв денег", "3000000", true);
    public final StringSetting anarchyList = new StringSetting(this, "Анки закупа", "320,322,323");
    public final BooleanSetting clanInvest = new BooleanSetting(this, "Clan invest", false);
    public final StringSetting clanInvestAt = new StringSetting(this, "Invest порог", "10000000", true)
            .setVisible(() -> clanInvest.getValue());
    public final StringSetting clanInvestAmount = new StringSetting(this, "Invest сумма", "1000000", true)
            .setVisible(() -> clanInvest.getValue());
    public final BooleanSetting autoInvis = new BooleanSetting(this, "Авто инвиз", false);
    public final ModeSetting autoInvisType = new ModeSetting(this, "Тип инвиза", "3m", "8m", "8+")
            .setVisible(() -> autoInvis.getValue());

    public final StringSetting sellItem = new StringSetting(this, "Что продавать", AURA_NAME);
    public final StringSetting sellPrice = new StringSetting(this, "Цена продажи", "420000", true);
    public final BooleanSetting randomSellPrice = new BooleanSetting(this, "Рандом цены", false);
    public final StringSetting randomSellPriceRange = new StringSetting(this, "Разброс цены", "10000", true)
            .setVisible(() -> randomSellPrice.getValue());
    public final BooleanSetting autoPausePlayers = new BooleanSetting(this, "Авто пауза игроки", true);
    public final SliderSetting autoPauseRange = new SliderSetting(this, "Радиус игроков", 64, 8, 128, 1)
            .setVisible(() -> autoPausePlayers.getValue());
    public final SliderSetting sellSlots = new SliderSetting(this, "Слотов AH", 5, 1, 50, 1);
    public final DragSetting infoDrag = new DragSetting(this, "Инфо панель", new Vector2f(8, 145));

    private Mode mode = Mode.IDLE;
    private int waitTicks;
    private int listed;
    private int sellAttemptsLeft;
    private int lastScreenSyncId = -1;
    private int noScreenTicks;
    private int fullSellTicks;
    private boolean fullAuto;
    private String activeBuyName;
    private long activeMaxUnitPrice = -1;
    private Item activeBuyItem;
    private int activeBuyTarget;
    private int buySearchTicks;
    private int buyOpenTicks;
    private int ignoredSearchScreenSyncId = -1;
    private int balanceRefreshTicks;
    private long balance = -1;
    private int homeAnarchy = -1;
    private int targetAnarchy = -1;
    private int autoPauseAnarchy = -1;
    private int playerPauseTicks;
    private int playerPauseReturnTicks;
    private boolean playerPauseReturning;
    private Mode resumeModeAfterPlayerPause = Mode.IDLE;
    private String playerPauseName;
    private String lastPlayerPauseName;
    private int anarchyIndex;
    private JoinAction joinAction = JoinAction.NONE;
    private boolean allowAwayAnarchy;
    private int antiAfkTicks;
    private String pendingCommand;
    private String lastCommand;
    private int antiAfkStage;
    private boolean buyConfirmClicked;
    private int buyConfirmHoldTicks;
    private long selectedBuyPrice;
    private boolean balanceReservedForConfirm;
    private boolean afkBlocked;
    private String lastProgressSignature = "";
    private int stuckTicks;
    private Mode timedMode = Mode.IDLE;
    private int stateTicks;
    private String buyRejectReason = "";
    private boolean resumeCraftAfterDiamondBuy;
    private long sessionStartMillis;
    private long sessionStartBalance = -1;
    private long sessionSellEarned;
    private long sessionBuySpent;
    private long sessionClanInvested;
    private int investCooldownTicks;
    private boolean storageTookSellLot;
    private boolean checkedStorageForSell;
    private int emptyStorageChecks;
    private int storageTakeClicks;
    private boolean autoInvisWasEnabled;
    private int autoInvisAnarchy = -1;
    private int autoInvisCheckTicks;
    private Mode resumeModeAfterInvis = Mode.IDLE;
    private int resumeWaitAfterInvis;
    private int invisOpenTicks;
    private boolean invisConfirmClicked;
    private int invisConfirmHoldTicks;
    private boolean invisUseStarted;
    private int invisDrinkTicks;
    private int invisPreviousSlot = -1;
    private int humanMoveTicks;
    private int nextHumanMoveTicks;
    private int humanLookTicks;
    private int humanSneakTicks;
    private String delayedAhCommand;
    private int delayedAhTicks;
    private int inventoryActionTicks;
    private int tableAimTicks;
    private int cowAimTicks;

    private enum Mode {
        IDLE,
        PLAYER_PAUSE,
        AUTO_WAIT_JOIN,
        AUTO_WAIT_BUDGET,
        BUY_OPEN,
        BUY_READ,
        BUY_CONFIRM,
        CRAFT,
        CRAFT_MILK,
        CRAFT_OPEN_TABLE,
        CRAFT_FILL,
        INVIS_OPEN,
        INVIS_READ,
        INVIS_CONFIRM,
        INVIS_DRINK,
        SELL_PREPARE,
        SELL_SEND,
        STORAGE_OPEN,
        STORAGE_CLICK,
        STORAGE_TAKE,
        SELL
    }

    private enum JoinAction {
        NONE,
        SEARCH,
        CRAFT
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (mc.player == null || event.getKey() == -1) return;

        if (event.getKey() == craftBind.get()) {
            startCraft();
        } else if (event.getKey() == sellBind.get()) {
            startSell();
        } else if (event.getKey() == buyNetheriteBind.get()) {
            toggleNetheriteBuy();
        } else if (event.getKey() == buyDiamondsBind.get()) {
            toggleDiamondBuy();
        } else if (event.getKey() == fullAutoBind.get()) {
            if (fullAuto) {
                stopFullAuto();
            } else {
                startFullAuto();
            }
        }
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        tickAntiAfkCommand();
        tickClanInvest();
        tickHumanMovement();
        tickHumanLook();
        if (tickPlayerAutoPause()) return;
        if (tickDelayedAhCommand()) return;
        if (tickAutoInvis()) return;
        if (mode == Mode.IDLE) return;
        if (tickAhSearchOpenWatchdog()) return;
        tickWatchdog();
        if (fullAuto && homeAnarchy <= 0) {
            homeAnarchy = readCurrentAnarchy();
        }
        if (fullAuto && guardAnarchy()) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (mode) {
            case PLAYER_PAUSE -> tickPlayerPause();
            case AUTO_WAIT_JOIN -> tickAutoWaitJoin();
            case AUTO_WAIT_BUDGET -> tickAutoWaitBudget();
            case BUY_OPEN -> tickBuyOpen();
            case BUY_READ -> tickBuyRead();
            case BUY_CONFIRM -> tickBuyConfirm();
            case CRAFT -> tickCraft();
            case CRAFT_MILK -> tickMilk();
            case CRAFT_OPEN_TABLE -> tickOpenCraftingTable();
            case CRAFT_FILL -> tickCraftFill();
            case INVIS_OPEN -> tickInvisOpen();
            case INVIS_READ -> tickInvisRead();
            case INVIS_CONFIRM -> tickInvisConfirm();
            case INVIS_DRINK -> tickInvisDrink();
            case SELL_PREPARE -> tickSellPrepare();
            case SELL_SEND -> tickSellSend();
            case STORAGE_OPEN -> tickStorageOpen();
            case STORAGE_CLICK -> tickStorageClick();
            case STORAGE_TAKE -> tickStorageTake();
            case SELL -> tickSell();
            case IDLE -> {
            }
        }
    }

    @EventHandler
    public void onDisplay(EventDisplay event) {
        if (mc.player == null) return;

        renderInfoPanel(event);
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (mc.player == null || event.isSend()) return;
        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) return;

        String message = packet.content().getString();
        String lower = clean(message);
        String target = clean(sellItem.getValue());
        long money = extractDollarPrice(message);

        if ((lower.contains("ваш баланс") || lower.contains("баланс")) && money > 0) {
            balance = money;
            balanceRefreshTicks = 0;
        }

        if ((lower.contains("успешно купили") || lower.contains("вы успешно купили")) && money > 0 && balance > 0) {
            sessionBuySpent += money;
            if (!balanceReservedForConfirm) {
                balance = Math.max(0, balance - money);
            }
            balanceReservedForConfirm = false;
            balanceRefreshTicks = 0;
        }

        if (lower.contains("данная команда недоступна в режиме afk")) {
            afkBlocked = true;
            wakeForCommand(lastCommand);
            return;
        }

        if ((lower.contains("хаб") || lower.contains("lobby")) && fullAuto) {
            rejoinHome();
            return;
        }

        if (balanceReservedForConfirm && isBuyFailedMessage(lower)) {
            refundReservedBuyBalance();
            sendCommand("money");
        }

        if ((lower.contains("не хватает") || lower.contains("не хвататет")) && lower.contains("монет") && fullAuto) {
            refundReservedBuyBalance();
            finishAutoBuy();
            return;
        }

        if ((lower.contains("у вас купили") || lower.contains("купили")) && lower.contains(target)) {
            if (money > 0) {
                sessionSellEarned += money;
                if (balance > 0) {
                    balance += money;
                } else {
                    balance = money;
                }
            }
            if (fullAuto) {
                refreshBalanceNow(false);
            }
            listed = Math.max(0, listed - 1);
            fullSellTicks = 0;
            if (mode == Mode.SELL || mode == Mode.SELL_PREPARE || mode == Mode.SELL_SEND) {
                sellAttemptsLeft++;
            }
        }

        if ((lower.contains("выставлен") || lower.contains("выставили")) && lower.contains(target)) {
            listed++;
        }

        if (lower.contains("не удалось выставить") && lower.contains(target) && !isAuctionBalanceSellBlocked(lower)) {
            listed = sellSlots.getValue().intValue();
            startStorageRelist();
        }
    }

    @Override
    public void onDisable() {
        reset();
        autoInvisWasEnabled = false;
        autoInvisAnarchy = -1;
        super.onDisable();
    }

    private void startCraft() {
        reset();
        startSessionStats();
        mode = Mode.CRAFT;
        msg("§e[AuraCrafter] Крафт запущен.");
    }

    private void toggleNetheriteBuy() {
        if (!fullAuto && isBuying(Items.NETHERITE_INGOT)) {
            stopManualBuy();
            return;
        }
        reset();
        startSessionStats();
        beginBuy(buyItem.getValue().trim(), parsePrice(maxBuyPrice.getValue(), 325000), Items.NETHERITE_INGOT, 0);
        msg("§e[AuraCrafter] Закуп незера до $" + maxBuyPrice.getValue());
    }

    private void toggleDiamondBuy() {
        if (!fullAuto && isBuying(Items.DIAMOND)) {
            stopManualBuy();
            return;
        }
        reset();
        startSessionStats();
        long diamondUnit = Math.max(1, parsePrice(diamondStackPrice.getValue(), 250000) / 64);
        beginBuy("Алмаз", diamondUnit, Items.DIAMOND, 256);
        msg("§e[AuraCrafter] Закуп алмазов до $" + diamondStackPrice.getValue() + " за 64шт.");
    }

    private boolean isBuying(Item item) {
        return (mode == Mode.BUY_OPEN || mode == Mode.BUY_READ || mode == Mode.BUY_CONFIRM)
                && activeBuyItem == item;
    }

    private void stopManualBuy() {
        reset();
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        msg("§7[AuraCrafter] Закуп выключен.");
    }

    private void startSell() {
        if (!fullAuto && (mode == Mode.SELL || mode == Mode.SELL_PREPARE || mode == Mode.SELL_SEND
                || mode == Mode.STORAGE_OPEN || mode == Mode.STORAGE_CLICK || mode == Mode.STORAGE_TAKE)) {
            reset();
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            msg("§7[AuraCrafter] Продажа выключена.");
            return;
        }
        reset();
        startSessionStats();
        mode = Mode.SELL;
        sellAttemptsLeft = sellSlots.getValue().intValue();
        msg("§e[AuraCrafter] Продажа: " + sellItem.getValue() + " по $" + sellPrice.getValue() + sellPriceSuffix());
    }

    private void startFullAuto() {
        reset();
        fullAuto = true;
        homeAnarchy = readCurrentAnarchy();
        balance = readScoreboardMoney();
        startSessionStats();
        sendCommand("money");
        msg("§e[AuraCrafter] Фулл авто: анка " + homeAnarchy + ", старт цикла.");
        beginAutoCycle();
    }

    private void stopFullAuto() {
        reset();
        msg("§7[AuraCrafter] Фулл авто выключен.");
    }

    private void beginAutoCycle() {
        if (!fullAuto) return;
        allowAwayAnarchy = false;
        resumeCraftAfterDiamondBuy = false;

        if (countInventory(Items.NETHERITE_INGOT) > 0) {
            if (countInventory(Items.DIAMOND) < 4) {
                beginCraftDiamondBuy();
                return;
            }
            returnHomeForCraft();
            return;
        }

        if (countInventory(Items.DIAMOND) < 256) {
            long diamondUnit = Math.max(1, parsePrice(diamondStackPrice.getValue(), 250000) / 64);
            beginBuy("Алмаз", diamondUnit, Items.DIAMOND, 256);
            return;
        }
        beginNetheriteBuy();
    }

    private void beginNetheriteBuy() {
        resumeCraftAfterDiamondBuy = false;
        allowAwayAnarchy = true;
        if (fullAuto && !canBuyNetheriteAboveReserve(0)) {
            handleNoNetheriteBudget();
            return;
        }
        beginBuy(buyItem.getValue().trim(), parsePrice(maxBuyPrice.getValue(), 325000), Items.NETHERITE_INGOT, 0);
    }

    private void beginCraftDiamondBuy() {
        resumeCraftAfterDiamondBuy = true;
        allowAwayAnarchy = false;
        long diamondUnit = Math.max(1, parsePrice(diamondStackPrice.getValue(), 250000) / 64);
        beginBuy("Алмаз", diamondUnit, Items.DIAMOND, 256);
        msg("§e[AuraCrafter] Алмазы кончились, докупаю из резерва и вернусь к крафту.");
    }

    private boolean canBuyNetheriteAboveReserve(long nextPrice) {
        long currentBalance = knownBalance();
        if (currentBalance <= 0) return true;

        long reserve = parsePrice(reserveMoney.getValue(), 3000000);
        long needed = nextPrice > 0 ? nextPrice : Math.max(1, parsePrice(maxBuyPrice.getValue(), 325000));
        return currentBalance - needed >= reserve;
    }

    private long knownBalance() {
        if (balance <= 0) {
            long currentBalance = readScoreboardMoney();
            if (currentBalance > 0) {
                balance = currentBalance;
                balanceRefreshTicks = 0;
            }
        }
        return balance;
    }

    private void handleNoNetheriteBudget() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        refundReservedBuyBalance();
        activeBuyName = null;
        activeBuyItem = null;
        activeBuyTarget = 0;
        activeMaxUnitPrice = -1;
        buySearchTicks = 0;
        buyOpenTicks = 0;
        allowAwayAnarchy = false;

        if (countSellTargetInventory() > 0 || listed > 0) {
            mode = Mode.SELL;
            waitTicks = TICK_DELAY;
            return;
        }
        if (countInventory(Items.NETHERITE_INGOT) > 0 && countInventory(Items.DIAMOND) >= 4) {
            returnHomeForCraft();
            return;
        }

        mode = Mode.AUTO_WAIT_BUDGET;
        waitTicks = 40;
    }

    private void beginBuy(String name, long maxUnitPrice, Item item, int targetCount) {
        activeBuyName = name;
        activeMaxUnitPrice = maxUnitPrice;
        activeBuyItem = item;
        activeBuyTarget = targetCount;
        normalizeActiveBuyState();
        buySearchTicks = 0;
        buyRejectReason = "";
        lastScreenSyncId = -1;
        noScreenTicks = 0;
        sendActiveBuySearch();
        mode = Mode.BUY_OPEN;
        waitTicks = 20;
    }

    private void sendActiveBuySearch() {
        normalizeActiveBuyState();
        String name = activeBuyName == null ? buyItem.getValue().trim() : activeBuyName;
        closeCurrentSearchScreen();
        lastScreenSyncId = -1;
        noScreenTicks = 0;
        buyOpenTicks = 0;
        sendAhCommand("ah search " + name);
    }

    private void normalizeActiveBuyState() {
        if (activeBuyName == null) return;

        String name = clean(activeBuyName);
        boolean diamondSearch = name.contains("алмаз") || name.contains("diamond");
        if (diamondSearch) {
            activeBuyItem = Items.DIAMOND;
            if (activeBuyTarget <= 0) activeBuyTarget = 256;
            return;
        }

        if (activeBuyItem != Items.NETHERITE_INGOT || activeBuyTarget > 0) {
            activeBuyItem = Items.NETHERITE_INGOT;
            activeBuyTarget = 0;
            activeMaxUnitPrice = parsePrice(maxBuyPrice.getValue(), 325000);
            buyRejectReason = "";
        }
    }

    private void closeCurrentSearchScreen() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            ignoredSearchScreenSyncId = screen.getScreenHandler().syncId;
            mc.player.closeHandledScreen();
            return;
        }
        ignoredSearchScreenSyncId = -1;
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
    }

    private boolean isIgnoredSearchScreen() {
        return ignoredSearchScreenSyncId != -1
                && mc.currentScreen instanceof GenericContainerScreen screen
                && screen.getScreenHandler().syncId == ignoredSearchScreenSyncId;
    }

    private boolean tickAhSearchOpenWatchdog() {
        if (mode == Mode.INVIS_OPEN) {
            if (mc.currentScreen instanceof GenericContainerScreen) {
                if (isIgnoredSearchScreen()) {
                    closeCurrentSearchScreen();
                    waitTicks = 2;
                    return true;
                }
                ignoredSearchScreenSyncId = -1;
                invisOpenTicks = 0;
                return false;
            }
            if (++invisOpenTicks < AH_SEARCH_RETRY_TICKS) return false;

            sendInvisSearch();
            waitTicks = 20;
            return true;
        }

        if (mode != Mode.BUY_OPEN) return false;
        if (mc.currentScreen instanceof GenericContainerScreen) {
            if (isIgnoredSearchScreen()) {
                closeCurrentSearchScreen();
                waitTicks = 2;
                return true;
            }
            ignoredSearchScreenSyncId = -1;
            buyOpenTicks = 0;
            return false;
        }
        if (++buyOpenTicks < AH_SEARCH_RETRY_TICKS) return false;

        if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && !canBuyNetheriteAboveReserve(0)) {
            handleNoNetheriteBudget();
            return true;
        }
        sendActiveBuySearch();
        waitTicks = 20;
        return true;
    }

    private void tickAutoWaitJoin() {
        int current = readCurrentAnarchy();
        if (targetAnarchy > 0 && current != targetAnarchy) {
            sendCommand("an" + targetAnarchy);
            waitTicks = JOIN_AH_DELAY;
            return;
        }

        if (joinAction == JoinAction.SEARCH) {
            targetAnarchy = -1;
            sendActiveBuySearch();
            mode = Mode.BUY_OPEN;
            waitTicks = 20;
            return;
        }

        if (joinAction == JoinAction.CRAFT) {
            targetAnarchy = -1;
            mode = Mode.CRAFT;
            waitTicks = TICK_DELAY;
            return;
        }

        mode = Mode.IDLE;
    }

    private void tickAutoWaitBudget() {
        if (!fullAuto) {
            mode = Mode.IDLE;
            waitTicks = 0;
            return;
        }

        allowAwayAnarchy = false;
        refreshBalanceNow(false);

        if (countSellTargetInventory() > 0 || listed > 0) {
            mode = Mode.SELL;
            waitTicks = TICK_DELAY;
            return;
        }

        if (countInventory(Items.NETHERITE_INGOT) > 0) {
            if (countInventory(Items.DIAMOND) < 4) {
                beginCraftDiamondBuy();
            } else {
                returnHomeForCraft();
            }
            return;
        }

        if (countInventory(Items.DIAMOND) < 256) {
            long diamondUnit = Math.max(1, parsePrice(diamondStackPrice.getValue(), 250000) / 64);
            beginBuy("Алмаз", diamondUnit, Items.DIAMOND, 256);
            return;
        }

        if (canBuyNetheriteAboveReserve(0)) {
            beginNetheriteBuy();
            return;
        }

        waitTicks = 40;
    }

    private void tickBuyOpen() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            if (isIgnoredSearchScreen()) {
                closeCurrentSearchScreen();
                waitTicks = 2;
                return;
            }
            ignoredSearchScreenSyncId = -1;
            lastScreenSyncId = screen.getScreenHandler().syncId;
            noScreenTicks = 0;
            mode = Mode.BUY_READ;
            waitTicks = TICK_DELAY;
            return;
        }
        noScreenTicks++;
        if (lastScreenSyncId == -1 && noScreenTicks > 17) {
            sendActiveBuySearch();
            waitTicks = 20;
            return;
        }
        if (lastScreenSyncId != -1 && noScreenTicks > 20) {
            if (fullAuto) finishAutoBuy();
            else stop("§7[AuraCrafter] Закуп остановлен: AH закрыт.");
            return;
        }
        waitTicks = TICK_DELAY;
    }

    private void tickBuyRead() {
        normalizeActiveBuyState();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (fullAuto) {
                finishAutoBuy();
            } else {
                stop("§7[AuraCrafter] Закуп остановлен: AH закрыт.");
            }
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (lastScreenSyncId != -1 && handler.syncId != lastScreenSyncId) {
            lastScreenSyncId = handler.syncId;
        }

        if (isBuyConfirmScreen(screen, handler)) {
            mode = Mode.BUY_CONFIRM;
            waitTicks = TICK_DELAY;
            return;
        }

        if (activeBuyItem != null && activeBuyTarget > 0 && countInventory(activeBuyItem) >= activeBuyTarget) {
            finishAutoBuy();
            return;
        }

        if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT) {
            refreshBalanceNow(false);
            if (!canBuyNetheriteAboveReserve(0)) {
                if (countInventory(Items.NETHERITE_INGOT) > 0) {
                    finishAutoBuy();
                } else {
                    handleNoNetheriteBudget();
                }
                return;
            }
        }

        if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && balance <= 0) {
            if (balance <= 0) {
                sendCommand("money");
                waitTicks = 20;
                return;
            }
        }

        int slot = findBuySlot(handler);
        if (slot == -1) {
            if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && "резерв".equals(buyRejectReason)) {
                if (countInventory(Items.NETHERITE_INGOT) > 0) {
                    finishAutoBuy();
                    return;
                }
                handleNoNetheriteBudget();
                return;
            }
            if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && (buySearchTicks += 10) >= NETHERITE_SWITCH_TICKS) {
                switchBuyAnarchy();
                return;
            }
            clickRefresh(handler);
            waitTicks = 10;
            return;
        }

        if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && balance > 0
                && balance - selectedBuyPrice < parsePrice(reserveMoney.getValue(), 3000000)) {
            if (countInventory(Items.NETHERITE_INGOT) > 0) {
                finishAutoBuy();
                return;
            }
            handleNoNetheriteBudget();
            return;
        }

        clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE);
        mode = Mode.BUY_CONFIRM;
        waitTicks = 10;
    }

    private void tickBuyConfirm() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            refundReservedBuyBalance();
            buyConfirmClicked = false;
            buyConfirmHoldTicks = 0;
            mode = Mode.BUY_OPEN;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (!isBuyConfirmScreen(screen, handler)) {
            refundReservedBuyBalance();
            buyConfirmClicked = false;
            buyConfirmHoldTicks = 0;
            mode = Mode.BUY_READ;
            waitTicks = 10;
            return;
        }

        if (buyConfirmClicked) {
            if (++buyConfirmHoldTicks < 20) {
                waitTicks = 2;
                return;
            }
            refundReservedBuyBalance();
            buyConfirmClicked = false;
            buyConfirmHoldTicks = 0;
            mode = Mode.BUY_READ;
            waitTicks = 10;
            return;
        }

        int confirm = findConfirmSlot(handler);
        if (confirm >= 0) {
            clickSlot(handler.syncId, confirm, 0, SlotActionType.PICKUP);
            if (balance > 0 && selectedBuyPrice > 0) {
                balance = Math.max(0, balance - selectedBuyPrice);
                balanceReservedForConfirm = true;
            }
            buyConfirmClicked = true;
            buyConfirmHoldTicks = 0;
            waitTicks = 10;
            return;
        }

        mode = Mode.BUY_CONFIRM;
        waitTicks = TICK_DELAY;
    }

    private void tickCraft() {
        if (countInventory(Items.NETHERITE_INGOT) <= 0) {
            if (fullAuto) {
                startAutoSellOrBuy();
            } else {
                stop("§a[AuraCrafter] Крафт закончен: незеритовые слитки закончились.");
            }
            return;
        }

        if (countInventory(Items.DIAMOND) < 4) {
            if (fullAuto) {
                beginCraftDiamondBuy();
            } else {
                stop("§c[AuraCrafter] Нет алмазов для следующей Божки.");
            }
            return;
        }

        if (countInventory(Items.MILK_BUCKET) < milkBucketsTarget()) {
            if (countInventory(Items.BUCKET) <= 0) {
                stop("§c[AuraCrafter] Нет молока и пустых ведер.");
                return;
            }
            mode = Mode.CRAFT_MILK;
            waitTicks = TICK_DELAY;
            return;
        }

        mode = mc.currentScreen instanceof CraftingScreen ? Mode.CRAFT_FILL : Mode.CRAFT_OPEN_TABLE;
        waitTicks = TICK_DELAY;
    }

    private void tickMilk() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            waitTicks = 1;
            return;
        }

        if (countInventory(Items.MILK_BUCKET) >= milkBucketsTarget() || countInventory(Items.BUCKET) <= 0) {
            mode = Mode.CRAFT;
            waitTicks = TICK_DELAY;
            return;
        }

        int bucketSlot = findInventorySlot(Items.BUCKET);
        if (bucketSlot == -1) {
            mode = Mode.CRAFT;
            waitTicks = TICK_DELAY;
            return;
        }

        CowEntity cow = nearestCow();
        if (cow == null) {
            cowAimTicks = 0;
            if (fullAuto) {
                waitTicks = 40;
            } else {
                stop("§c[AuraCrafter] Коровы рядом нет.");
            }
            return;
        }

        lookAtCow(cow);
        if (cowAimTicks++ < 1) {
            waitTicks = 1;
            return;
        }

        if (!RayTraceUtil.rayTraceEntity(MathHelper.wrapDegrees(mc.player.getYaw()), mc.player.getPitch(), COW_INTERACT_RANGE, cow)) {
            waitTicks = 1;
            return;
        }

        moveToHotbar(bucketSlot, 0);
        mc.player.getInventory().setSelectedSlot(0);
        mc.interactionManager.interactEntity(mc.player, cow, Hand.MAIN_HAND);
        cowAimTicks = 0;
        waitTicks = 1;
    }

    private void tickOpenCraftingTable() {
        if (mc.currentScreen instanceof CraftingScreen) {
            mode = Mode.CRAFT_FILL;
            tableAimTicks = 0;
            waitTicks = randomTicks(1, 3);
            return;
        }

        BlockHitResult hit = mc.crosshairTarget instanceof BlockHitResult blockHit
                && mc.world.getBlockState(blockHit.getBlockPos()).isOf(Blocks.CRAFTING_TABLE)
                ? blockHit
                : null;
        BlockPos table = hit != null ? hit.getBlockPos() : findNearbyCraftingTable();
        if (table == null) {
            tableAimTicks = 0;
            if (fullAuto) {
                waitTicks = randomTicks(8, 16);
            } else {
                stop("§c[AuraCrafter] Наведись на верстак.");
            }
            return;
        }

        lookAtCraftingTable(table);
        if (hit == null && tableAimTicks++ < randomTicks(1, 2)) {
            waitTicks = 1;
            return;
        }

        if (hit == null) {
            Vec3d hitVec = new Vec3d(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
            hit = new BlockHitResult(hitVec, Direction.UP, table, false);
        }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        tableAimTicks = 0;
        waitTicks = randomTicks(2, 4);
    }

    private void tickCraftFill() {
        if (!(mc.currentScreen instanceof CraftingScreen)
                || !(mc.player.currentScreenHandler instanceof CraftingScreenHandler handler)) {
            mode = Mode.CRAFT_OPEN_TABLE;
            waitTicks = TICK_DELAY;
            return;
        }

        if (hasCraftResult(handler)) {
            clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE);
            waitTicks = randomTicks(1, 3);
            mode = Mode.CRAFT;
            return;
        }

        if (!placeRecipe(handler)) {
            clearCraftGrid(handler);
            mode = Mode.CRAFT;
            waitTicks = randomTicks(2, 4);
            return;
        }

        waitTicks = 1;
    }

    private boolean tickAutoInvis() {
        updateAutoInvisAnchor();
        if (isInvisMode()) return false;

        if (!autoInvis.getValue() || hasInvisibilityBuff()) {
            autoInvisCheckTicks = 0;
            return false;
        }

        if (++autoInvisCheckTicks < AUTO_INVIS_CHECK_TICKS) return false;
        autoInvisCheckTicks = 0;

        if (!canAutoInvisHere()) return false;

        startAutoInvisInterrupt();
        if (findInvisPotionInventorySlot() >= 0) {
            mode = Mode.INVIS_DRINK;
            waitTicks = 0;
        } else {
            sendInvisSearch();
            mode = Mode.INVIS_OPEN;
            waitTicks = 20;
        }
        return true;
    }

    private boolean tickPlayerAutoPause() {
        if (mode == Mode.PLAYER_PAUSE) {
            tickPlayerPause();
            return true;
        }

        if (!autoPausePlayers.getValue() || mode == Mode.IDLE || autoPauseAnarchy <= 0) return false;
        if (readCurrentAnarchy() != autoPauseAnarchy) return false;

        PlayerEntity player = findPauseThreatPlayer();
        if (player == null) return false;

        String name = player.getName().getString();
        if (lastPlayerPauseName != null && lastPlayerPauseName.equalsIgnoreCase(name)) {
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            releaseInvisUse();
            releaseHumanMovementKeys();
            releaseAntiAfkKeys();
            sendCommand("hub");
            msg("Â§c[AuraCrafter] Ð˜Ð³Ñ€Ð¾Ðº " + name + " ÑÐ½Ð¾Ð²Ð° Ñ€ÑÐ´Ð¾Ð¼, Ð²Ñ‹Ñ…Ð¾Ð¶Ñƒ Ð¸ Ð²Ñ‹ÐºÐ»ÑŽÑ‡Ð°ÑŽÑÑŒ.");
            setEnabled(false);
            return true;
        }

        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        releaseInvisUse();
        releaseHumanMovementKeys();
        releaseAntiAfkKeys();
        refundReservedBuyBalance();
        resumeModeAfterPlayerPause = mode;
        playerPauseName = name;
        lastPlayerPauseName = name;
        playerPauseTicks = 0;
        playerPauseReturnTicks = 0;
        playerPauseReturning = false;
        sendCommand("hub");
        mode = Mode.PLAYER_PAUSE;
        waitTicks = 0;
        msg("Â§e[AuraCrafter] Ð˜Ð³Ñ€Ð¾Ðº " + name + " Ñ€ÑÐ´Ð¾Ð¼, Ð¿Ð°ÑƒÐ·Ð° 5 Ð¼Ð¸Ð½ÑƒÑ‚.");
        return true;
    }

    private void tickPlayerPause() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }

        if (!playerPauseReturning) {
            if (++playerPauseTicks < PLAYER_PAUSE_RETURN_TICKS) return;
            if (autoPauseAnarchy > 0) {
                sendCommand("an" + autoPauseAnarchy);
            }
            playerPauseReturning = true;
            playerPauseReturnTicks = 0;
            return;
        }

        if (++playerPauseReturnTicks < JOIN_AH_DELAY) return;
        resumeAfterPlayerPause();
    }

    private void resumeAfterPlayerPause() {
        Mode resume = resumeModeAfterPlayerPause == null ? Mode.CRAFT : resumeModeAfterPlayerPause;
        playerPauseName = null;
        playerPauseTicks = 0;
        playerPauseReturnTicks = 0;
        playerPauseReturning = false;
        resumeModeAfterPlayerPause = Mode.IDLE;
        noScreenTicks = 0;
        stateTicks = 0;
        lastProgressSignature = "";
        stuckTicks = 0;

        switch (resume) {
            case BUY_OPEN, BUY_READ, BUY_CONFIRM -> {
                if (activeBuyName != null || activeBuyItem != null) {
                    if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && !canBuyNetheriteAboveReserve(0)) {
                        handleNoNetheriteBudget();
                        return;
                    }
                    sendActiveBuySearch();
                    mode = Mode.BUY_OPEN;
                    waitTicks = 20;
                } else {
                    mode = Mode.CRAFT;
                    waitTicks = TICK_DELAY;
                }
            }
            case STORAGE_OPEN, STORAGE_CLICK, STORAGE_TAKE -> startStorageRelist();
            case SELL, SELL_PREPARE, SELL_SEND -> {
                mode = Mode.SELL;
                waitTicks = TICK_DELAY;
            }
            case CRAFT, CRAFT_MILK, CRAFT_OPEN_TABLE, CRAFT_FILL -> {
                mode = Mode.CRAFT;
                waitTicks = TICK_DELAY;
            }
            default -> {
                mode = Mode.CRAFT;
                waitTicks = TICK_DELAY;
            }
        }
        msg("Â§a[AuraCrafter] ÐŸÐ°ÑƒÐ·Ð° Ð·Ð°ÐºÐ¾Ð½Ñ‡Ð¸Ð»Ð°ÑÑŒ, Ð²ÐµÑ€Ð½ÑƒÐ»ÑÑ Ð½Ð° Ð°Ð½ÐºÑƒ " + autoPauseAnarchy + ".");
    }

    private PlayerEntity findPauseThreatPlayer() {
        EntityEsp esp = EntityEsp.get();
        if (esp == null || !esp.isEnabled() || !esp.player.getValue()) return null;
        if (mc.world == null || mc.player == null) return null;

        double maxDistance = autoPauseRange.getValue().doubleValue();
        double maxSq = maxDistance * maxDistance;
        PlayerEntity best = null;
        double bestDistance = maxSq;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            String name = player.getName().getString();
            if (name == null || name.isEmpty()) continue;
            if (player.getCustomName() != null && player.getCustomName().getString().startsWith("Ghost_")) continue;
            if (Client.get().friendManager().isFriend(name)) continue;
            if (esp.ignoreNaked.getValue() && !EntityEsp.hasArmor(player)) continue;

            double distance = player.squaredDistanceTo(mc.player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void updateAutoInvisAnchor() {
        if (!autoInvis.getValue()) {
            autoInvisWasEnabled = false;
            autoInvisAnarchy = -1;
            return;
        }

        if (!autoInvisWasEnabled) {
            autoInvisWasEnabled = true;
            autoInvisAnarchy = readCurrentAnarchy();
        }
        if (autoInvisAnarchy <= 0) {
            autoInvisAnarchy = readCurrentAnarchy();
        }
    }

    private boolean canAutoInvisHere() {
        if (pendingCommand != null || mc.player.isUsingItem()) return false;
        if (allowAwayAnarchy || targetAnarchy > 0 || mode == Mode.AUTO_WAIT_JOIN || mode == Mode.AUTO_WAIT_BUDGET) return false;
        int current = readCurrentAnarchy();
        return autoInvisAnarchy > 0 && current == autoInvisAnarchy;
    }

    private void startAutoInvisInterrupt() {
        resumeModeAfterInvis = mode;
        resumeWaitAfterInvis = waitTicks;
        refundReservedBuyBalance();
        invisOpenTicks = 0;
        invisConfirmClicked = false;
        invisConfirmHoldTicks = 0;
        invisUseStarted = false;
        invisDrinkTicks = 0;
        invisPreviousSlot = -1;
        lastProgressSignature = "";
        stuckTicks = 0;

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        buyConfirmClicked = false;
        buyConfirmHoldTicks = 0;
    }

    private void sendInvisSearch() {
        closeCurrentSearchScreen();
        noScreenTicks = 0;
        invisOpenTicks = 0;
        sendAhCommand("ah search " + INVIS_SEARCH_QUERY);
    }

    private void tickInvisOpen() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            if (isIgnoredSearchScreen()) {
                closeCurrentSearchScreen();
                waitTicks = 2;
                return;
            }
            ignoredSearchScreenSyncId = -1;
            noScreenTicks = 0;
            invisOpenTicks = 0;
            mode = Mode.INVIS_READ;
            waitTicks = TICK_DELAY;
            return;
        }

        if (++noScreenTicks > 17) {
            sendInvisSearch();
            waitTicks = 20;
            return;
        }
        waitTicks = TICK_DELAY;
    }

    private void tickInvisRead() {
        if (findInvisPotionInventorySlot() >= 0) {
            mode = Mode.INVIS_DRINK;
            waitTicks = 0;
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            mode = Mode.INVIS_OPEN;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (isBuyConfirmScreen(screen, handler)) {
            mode = Mode.INVIS_CONFIRM;
            waitTicks = TICK_DELAY;
            return;
        }

        int slot = findInvisBuySlot(handler);
        if (slot == -1) {
            clickRefresh(handler);
            waitTicks = 10;
            return;
        }

        clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE);
        mode = Mode.INVIS_CONFIRM;
        waitTicks = 10;
    }

    private void tickInvisConfirm() {
        if (findInvisPotionInventorySlot() >= 0) {
            mode = Mode.INVIS_DRINK;
            waitTicks = 0;
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            mode = Mode.INVIS_DRINK;
            waitTicks = 4;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (!isBuyConfirmScreen(screen, handler)) {
            invisConfirmClicked = false;
            invisConfirmHoldTicks = 0;
            mode = Mode.INVIS_READ;
            waitTicks = 10;
            return;
        }

        if (invisConfirmClicked) {
            if (++invisConfirmHoldTicks < 20) {
                waitTicks = 2;
                return;
            }
            invisConfirmClicked = false;
            invisConfirmHoldTicks = 0;
            mode = Mode.INVIS_DRINK;
            waitTicks = 4;
            return;
        }

        int confirm = findConfirmSlot(handler);
        if (confirm >= 0) {
            clickSlot(handler.syncId, confirm, 0, SlotActionType.PICKUP);
            invisConfirmClicked = true;
            invisConfirmHoldTicks = 0;
            waitTicks = 10;
            return;
        }

        waitTicks = TICK_DELAY;
    }

    private void tickInvisDrink() {
        if (hasInvisibilityBuff()) {
            finishAutoInvis();
            return;
        }

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            waitTicks = 2;
            return;
        }

        int potionSlot = findInvisPotionInventorySlot();
        if (!invisUseStarted) {
            if (potionSlot == -1) {
                if (++invisDrinkTicks > 40) {
                    sendInvisSearch();
                    mode = Mode.INVIS_OPEN;
                    waitTicks = 20;
                } else {
                    waitTicks = 2;
                }
                return;
            }

            invisPreviousSlot = mc.player.getInventory().getSelectedSlot();
            int hotbarSlot = moveInvisPotionToHotbar(potionSlot);
            if (hotbarSlot < 0) {
                waitTicks = 5;
                return;
            }

            mc.player.getInventory().setSelectedSlot(hotbarSlot);
            mc.options.useKey.setPressed(true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            invisUseStarted = true;
            invisDrinkTicks = 0;
            waitTicks = 1;
            return;
        }

        mc.options.useKey.setPressed(true);
        if (++invisDrinkTicks <= INVIS_DRINK_TIMEOUT_TICKS) {
            waitTicks = 1;
            return;
        }

        releaseInvisUse();
        invisUseStarted = false;
        invisDrinkTicks = 0;
        if (findInvisPotionInventorySlot() == -1) {
            sendInvisSearch();
            mode = Mode.INVIS_OPEN;
            waitTicks = 20;
        } else {
            waitTicks = 4;
        }
    }

    private void tickSell() {
        if (listed >= sellSlots.getValue().intValue()) {
            if (++fullSellTicks >= 34) {
                startStorageRelist();
                return;
            }
            waitTicks = 2;
            return;
        }
        fullSellTicks = 0;

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            waitTicks = TICK_DELAY;
            return;
        }

        mode = Mode.SELL_PREPARE;
    }

    private void tickSellPrepare() {
        if (!prepareSingleSellItem()) {
            if (fullAuto) {
                beginAutoCycle();
            } else {
                if (checkedStorageForSell && !storageTookSellLot && listed <= 0) {
                    emptyStorageChecks++;
                    if (emptyStorageChecks >= 3) {
                        stop("§a[AuraCrafter] Продажа выключена: хранилище пустое 3 раза.");
                        return;
                    }
                }
                startStorageRelist();
            }
            return;
        }

        checkedStorageForSell = false;
        emptyStorageChecks = 0;
        mc.player.getInventory().setSelectedSlot(0);
        mode = Mode.SELL_SEND;
        waitTicks = randomTicks(2, 7);
    }

    private void tickSellSend() {
        mc.player.getInventory().setSelectedSlot(0);
        ItemStack hand = mc.player.getMainHandStack();
        if (!isSellTarget(hand) || hand.getCount() != 1) {
            mode = Mode.SELL_PREPARE;
            waitTicks = 3;
            return;
        }

        sendCommand("ah sell " + nextSellPrice());
        mode = Mode.SELL;
        waitTicks = randomTicks(8, 18);
    }

    private void renderInfoPanel(EventDisplay event) {
        int diamonds = countInventory(Items.DIAMOND);
        int netherite = countInventory(Items.NETHERITE_INGOT);
        int milk = countInventory(Items.MILK_BUCKET);
        int buckets = countInventory(Items.BUCKET);
        int possibleCrafts = Math.min(netherite, diamonds / 4);
        int milkTarget = milkBucketsTarget();
        int slotsMax = sellSlots.getValue().intValue();

        String[] left = {
                "Режим",
                "Авто",
                "Анка",
                "Баланс",
                "Крафтов",
                "Алмазы",
                "Незер",
                "Молоко",
                "Пустые",
                "Закуп",
                "Резерв",
                "Клан",
                "Фильтр",
                "Продажа",
                "Лоты"
        };
        String[] right = {
                modeName(),
                fullAuto ? "ON" : "OFF",
                (homeAnarchy > 0 ? String.valueOf(homeAnarchy) : "?") + (targetAnarchy > 0 ? " -> " + targetAnarchy : ""),
                balance > 0 ? "$" + fmt(balance) : "?",
                fmt(possibleCrafts),
                fmt(diamonds),
                fmt(netherite),
                fmt(milk) + "/" + fmt(milkTarget),
                fmt(buckets),
                (activeBuyName == null ? buyItem.getValue() : activeBuyName) + " <= $" + fmt(activeMaxUnitPrice > 0 ? activeMaxUnitPrice : parsePrice(maxBuyPrice.getValue(), 0)),
                "$" + fmt(parsePrice(reserveMoney.getValue(), 0)),
                clanInvest.getValue() ? fmt(parsePrice(clanInvestAmount.getValue(), 0)) + " от " + fmt(parsePrice(clanInvestAt.getValue(), 0)) : "OFF",
                buyRejectReason.isEmpty() ? "ok" : buyRejectReason,
                sellItem.getValue() + " $" + fmt(parsePrice(sellPrice.getValue(), 0)) + sellPriceSuffix(),
                listed + "/" + slotsMax
        };

        String[] statLeft = {"Время", "Заработал", "Продажи", "В клан", "Вёдра"};
        String[] statRight = {
                sessionElapsedText(),
                signedMoney(sessionProfit()),
                "$" + fmt(sessionSellEarned),
                "$" + fmt(sessionClanInvested),
                milkTarget + "/" + MAX_MILK_BUCKET_USES
        };

        float width = 130f;
        for (int i = 0; i < statLeft.length; i++) {
            width = Math.max(width, 16f + Fonts.sf_medium.getWidth(statLeft[i], 5) + Fonts.sf_medium.getWidth(statRight[i], 5) + 16f);
        }
        for (int i = 0; i < left.length; i++) {
            width = Math.max(width, 16f + Fonts.sf_medium.getWidth(left[i], 5) + Fonts.sf_medium.getWidth(right[i], 5) + 16f);
        }

        float rowH = 8;
        float height = 24 + (left.length + statLeft.length) * rowH ;
        float x = infoDrag.position.x;
        float y = infoDrag.position.y;
        infoDrag.size.set(width, height);

        if (Hud.getAlpha() != 1) {
            RenderUtil.Blur.glass(x, y, width, height, 1, 7, ColorUtil.getRectColor(Hud.getAlpha()), 15, 1, 1, 3);
        }


        String state = mode == Mode.IDLE ? "IDLE" : "ACTIVE";
        float stateW = Fonts.sf_medium.getWidth(state, 6.5F);
        Fonts.sf_medium.draw("Aura Crafter", x + 6.5F, y + 7, 6.5F, ColorUtil.getColor(255));
        Fonts.sf_medium.draw(state, x + width - 6.5F - stateW, y + 7, 6.5F,
                mode == Mode.IDLE ? ColorUtil.getColor(170) : ColorUtil.getColor(85, 255, 119, 1F));

        float sy = y + 18;
        for (int i = 0; i < statLeft.length; i++) {
            float rowY = sy + i * rowH;

          //  RenderUtil.Render2D.gradientRect(x + 5, rowY, width - 10, 12, new int[]{
          //          ColorUtil.getColor(255, 0.025F),
          //          ColorUtil.getColor(255, 0.0F),
          //          ColorUtil.getColor(255, 0.0F),
          //          ColorUtil.getColor(255, 0.025F)
          //  }, 3);

            Fonts.sf_medium.draw(statLeft[i], x + 6.5F, rowY + 2, 6, hudStatLabelColor(statLeft[i]));
            Fonts.sf_medium.draw(statRight[i], x + width - 6.5F
                            - Fonts.sf_medium.getWidth(statRight[i], 6), rowY + 2, 6,
                    hudStatValueColor(statLeft[i], statRight[i]));
        }

        sy += statLeft.length * rowH;
        for (int i = 0; i < left.length; i++) {
            float rowY = sy + i * rowH;

          // RenderUtil.Render2D.gradientRect(x + 5,rowY,width - 10,12,new int[]{
          //         ColorUtil.getColor(255,0.02F),
          //         ColorUtil.getColor(255,0.0F),
          //         ColorUtil.getColor(255,0.0F),
          //         ColorUtil.getColor(255,0.02F)
          // },3);

            int leftColor = i == 0 ? ColorUtil.getClientColor(255) : ColorUtil.getColor(180);
            int rightColor = i == 1 && fullAuto ? ColorUtil.getColor(85, 255, 119, 1F) : ColorUtil.getColor(220);
            Fonts.sf_medium.draw(left[i], x + 6.5F, rowY + 2, 6, leftColor);
            Fonts.sf_medium.draw(right[i], x + width - 6.5F - Fonts.sf_medium.getWidth(right[i], 6),
                    rowY + 2, 6, rightColor);
        }

        //float barY = y + height - 11;
        //drawBar(x + 8, barY, (width - 22) / 2f, 4, milk, Math.max(1, milkTarget), ColorUtil.getColor(85, 221, 255, 1F));
        //drawBar(x + 14 + (width - 22) / 2f, barY, (width - 22) / 2f, 4, listed, Math.max(1, slotsMax), ColorUtil.getColor(255, 209, 102, 1F));
    }

    private void drawBar(float x, float y, float w, float h, int value, int max, int color) {
        float fill = Math.max(0, Math.min(w, w * (value / (float) max)));
        RenderUtil.Render2D.rect(x, y, w, h, ColorUtil.getColor(0, 0.18F), 2);
        RenderUtil.Render2D.rect(x, y, fill, h, color, 2);
        RenderUtil.Render2D.outline(x, y, w, h, 0.5F, ColorUtil.getColor(255, 0.08F), 2);
    }

    private int hudStatLabelColor(String label) {
        if ("Время".equals(label)) return ColorUtil.getColor(135, 214, 255, 1F);
        if ("Заработал".equals(label)) return ColorUtil.getColor(159, 238, 188, 1F);
        if ("В клан".equals(label)) return ColorUtil.getColor(255, 220, 132, 1F);
        return ColorUtil.getColor(205);
    }

    private int hudStatValueColor(String label, String value) {
        if ("Заработал".equals(label)) {
            return value.startsWith("-") ? ColorUtil.getColor(255, 120, 120, 1F) : ColorUtil.getColor(105, 255, 156, 1F);
        }
        if ("Время".equals(label)) return ColorUtil.getColor(150, 225, 255, 1F);
        if ("В клан".equals(label)) return ColorUtil.getColor(255, 229, 148, 1F);
        if ("Вёдра".equals(label)) return ColorUtil.getColor(172, 210, 255, 1F);
        return ColorUtil.getColor(230);
    }

    private void startStorageRelist() {
        if (listed <= 0 && countSellTargetInventory() <= 0) {
            storageTookSellLot = false;
            checkedStorageForSell = true;
            storageTakeClicks = 0;
            if (fullAuto) {
                beginAutoCycle();
            } else {
                stop("§a[AuraCrafter] Продажа выключена: лотов и предметов для продажи нет.");
            }
            return;
        }
        storageTookSellLot = false;
        checkedStorageForSell = true;
        storageTakeClicks = 0;
        sendAhCommand("ah");
        mode = Mode.STORAGE_OPEN;
        noScreenTicks = 0;
        stateTicks = 0;
        waitTicks = 20;
    }

    private void tickStorageOpen() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            noScreenTicks = 0;
            mode = Mode.STORAGE_CLICK;
            waitTicks = 2;
            return;
        }

        if (++noScreenTicks > STORAGE_REOPEN_TIMEOUT_TICKS) {
            sendAhCommand("ah");
            noScreenTicks = 0;
            waitTicks = 20;
            return;
        }
        waitTicks = TICK_DELAY;
    }

    private void tickStorageClick() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            mode = Mode.STORAGE_OPEN;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int storage = findStorageSlot(handler);
        if (storage == -1) {
            if (++noScreenTicks > STORAGE_REOPEN_TIMEOUT_TICKS) {
                mc.player.closeHandledScreen();
                startStorageRelist();
                return;
            }
            waitTicks = 5;
            return;
        }

        noScreenTicks = 0;
        clickSlot(handler.syncId, storage, 0, SlotActionType.PICKUP);
        mode = Mode.STORAGE_TAKE;
        storageTakeClicks = 0;
        waitTicks = 6;
    }

    private void tickStorageTake() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            mode = Mode.SELL;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int slot = findStoredSellLot(handler);
        if (slot == -1) {
            if (storageTookSellLot) {
                if (listed <= 0 || ++noScreenTicks > 20) {
                    listed = 0;
                    mc.player.closeHandledScreen();
                    mode = Mode.SELL;
                    noScreenTicks = 0;
                    stateTicks = 0;
                    waitTicks = TICK_DELAY;
                } else {
                    waitTicks = 3;
                }
                return;
            }
            if (listed <= 0 || ++noScreenTicks > STORAGE_EMPTY_SETTLE_TICKS) {
                listed = 0;
                mc.player.closeHandledScreen();
                mode = Mode.SELL;
                waitTicks = TICK_DELAY;
            } else {
                waitTicks = 5;
            }
            return;
        }

        noScreenTicks = 0;
        stateTicks = 0;
        clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP);
        storageTookSellLot = true;
        emptyStorageChecks = 0;
        storageTakeClicks++;
        listed = Math.max(0, listed - 1);
        if (listed <= 0 || storageTakeClicks >= sellSlots.getValue().intValue() + 2) {
            mc.player.closeHandledScreen();
            mode = Mode.SELL;
            noScreenTicks = 0;
            stateTicks = 0;
            waitTicks = TICK_DELAY;
            return;
        }
        waitTicks = 2;
    }

    private boolean placeRecipe(CraftingScreenHandler handler) {
        clearWrongCraftSlots(handler);
        if (!putSlot(handler, 1, Items.DIAMOND)) return false;
        if (!putSlot(handler, 2, Items.MILK_BUCKET)) return false;
        if (!putSlot(handler, 3, Items.DIAMOND)) return false;
        if (!putSlot(handler, 4, Items.MILK_BUCKET)) return false;
        if (!putSlot(handler, 5, Items.NETHERITE_INGOT)) return false;
        if (!putSlot(handler, 6, Items.MILK_BUCKET)) return false;
        if (!putSlot(handler, 7, Items.DIAMOND)) return false;
        if (!putSlot(handler, 8, Items.MILK_BUCKET)) return false;
        return putSlot(handler, 9, Items.DIAMOND);
    }

    private boolean putSlot(CraftingScreenHandler handler, int craftSlot, Item item) {
        ItemStack current = handler.getSlot(craftSlot).getStack();
        if (current.getItem() == item) return true;
        if (!current.isEmpty()) return false;

        int invSlot = findInventorySlot(item);
        if (invSlot == -1) return false;

        int screenSlot = craftingPlayerSlot(invSlot);
        clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP);
        clickSlot(handler.syncId, craftSlot, 1, SlotActionType.PICKUP);
        clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP);
        return true;
    }

    private void clearWrongCraftSlots(CraftingScreenHandler handler) {
        for (int i = 1; i <= 9; i++) {
            Item expected = expectedRecipeItem(i);
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() != expected) {
                clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
            }
        }
    }

    private void clearCraftGrid(CraftingScreenHandler handler) {
        for (int i = 1; i <= 9; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
            }
        }
    }

    private Item expectedRecipeItem(int slot) {
        return switch (slot) {
            case 1, 3, 7, 9 -> Items.DIAMOND;
            case 2, 4, 6, 8 -> Items.MILK_BUCKET;
            case 5 -> Items.NETHERITE_INGOT;
            default -> Items.AIR;
        };
    }

    private boolean hasCraftResult(CraftingScreenHandler handler) {
        ItemStack result = handler.getSlot(0).getStack();
        return !result.isEmpty() && clean(result.getName().getString()).contains(AURA_NAME.toLowerCase());
    }

    private boolean isInvisMode() {
        return mode == Mode.INVIS_OPEN || mode == Mode.INVIS_READ
                || mode == Mode.INVIS_CONFIRM || mode == Mode.INVIS_DRINK;
    }

    private boolean hasInvisibilityBuff() {
        return mc.player != null && mc.player.hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    private int findInvisPotionInventorySlot() {
        int fallback = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesAutoInvisPotion(stack, true)) return i;
            if (fallback == -1 && matchesAutoInvisPotion(stack, false)) fallback = i;
        }
        return fallback;
    }

    private int findInvisBuySlot(GenericContainerScreenHandler handler) {
        int strict = findInvisBuySlot(handler, true);
        return strict >= 0 ? strict : findInvisBuySlot(handler, false);
    }

    private int findInvisBuySlot(GenericContainerScreenHandler handler, boolean strictPlus) {
        int containerSlots = handler.getRows() * 9;
        int bestSlot = -1;
        long bestPrice = Long.MAX_VALUE;

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            if (stack.getCount() != 1) continue;
            if (!matchesAutoInvisPotion(stack, strictPlus)) continue;

            long price = getLorePrice(stack);
            if (price <= 0) continue;
            if (price < bestPrice) {
                bestPrice = price;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean matchesAutoInvisPotion(ItemStack stack, boolean strictPlus) {
        if (stack.isEmpty() || stack.getItem() != Items.POTION) return false;

        String type = autoInvisType.getValue();
        String text = stackText(stack);
        boolean textLooksInvis = text.contains("инвиз") || text.contains("невид");
        boolean shortInvis = isPotionEntry(stack, Potions.INVISIBILITY);
        boolean longInvis = isPotionEntry(stack, Potions.LONG_INVISIBILITY);

        if ("3m".equalsIgnoreCase(type)) {
            return shortInvis || (textLooksInvis && text.contains("3") && !text.contains("8"));
        }
        if ("8+".equalsIgnoreCase(type)) {
            if (strictPlus) {
                return (longInvis || textLooksInvis) && isEightPlusInvis(stack);
            }
            return longInvis || (textLooksInvis && text.contains("8"));
        }
        return longInvis || (textLooksInvis && text.contains("8"));
    }

    private boolean isPotionEntry(ItemStack stack, net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> potion) {
        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return potionContents != null && potionContents.potion().isPresent()
                && potionContents.potion().get().equals(potion);
    }

    private boolean isEightPlusInvis(ItemStack stack) {
        String text = stackText(stack);
        boolean hasExtraDurationLine = text.matches(".*\\+\\s*\\d{1,2}\\s*[:：]\\s*\\d{2}.*");
        return hasExtraDurationLine || text.contains("8+") || text.contains("+8")
                || text.contains("усилен") || text.contains("plus");
    }

    private int moveInvisPotionToHotbar(int inventorySlot) {
        if (inventorySlot < 0) return -1;
        if (inventorySlot < 9) return inventorySlot;

        int hotbarSlot = findEmptyHotbarSlot();
        if (hotbarSlot == -1) hotbarSlot = 8;
        moveToHotbar(inventorySlot, hotbarSlot);
        return hotbarSlot;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private void releaseInvisUse() {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
    }

    private void finishAutoInvis() {
        Mode resume = resumeModeAfterInvis == null ? Mode.IDLE : resumeModeAfterInvis;
        int savedWait = resumeWaitAfterInvis;
        releaseInvisUse();
        if (invisPreviousSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(invisPreviousSlot);
        }

        resumeModeAfterInvis = Mode.IDLE;
        resumeWaitAfterInvis = 0;
        invisOpenTicks = 0;
        invisConfirmClicked = false;
        invisConfirmHoldTicks = 0;
        invisUseStarted = false;
        invisDrinkTicks = 0;
        invisPreviousSlot = -1;
        lastProgressSignature = "";
        stuckTicks = 0;

        resumeAfterInvis(resume, savedWait);
    }

    private void resumeAfterInvis(Mode resume, int savedWait) {
        switch (resume) {
            case BUY_OPEN, BUY_READ, BUY_CONFIRM -> {
                if (activeBuyName != null || activeBuyItem != null) {
                    if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && !canBuyNetheriteAboveReserve(0)) {
                        handleNoNetheriteBudget();
                        return;
                    }
                    sendActiveBuySearch();
                    mode = Mode.BUY_OPEN;
                    waitTicks = 20;
                } else {
                    mode = Mode.IDLE;
                    waitTicks = 0;
                }
            }
            case STORAGE_OPEN, STORAGE_CLICK, STORAGE_TAKE -> startStorageRelist();
            case SELL_PREPARE, SELL_SEND -> {
                mode = Mode.SELL;
                waitTicks = TICK_DELAY;
            }
            case CRAFT_MILK, CRAFT_OPEN_TABLE, CRAFT_FILL -> {
                mode = Mode.CRAFT;
                waitTicks = TICK_DELAY;
            }
            case INVIS_OPEN, INVIS_READ, INVIS_CONFIRM, INVIS_DRINK -> {
                mode = Mode.IDLE;
                waitTicks = 0;
            }
            default -> {
                mode = resume;
                waitTicks = Math.max(TICK_DELAY, savedWait);
            }
        }
    }

    private int findBuySlot(GenericContainerScreenHandler handler) {
        normalizeActiveBuyState();
        String target = clean(activeBuyName == null ? buyItem.getValue() : activeBuyName);
        long maxPrice = activeMaxUnitPrice > 0 ? activeMaxUnitPrice : parsePrice(maxBuyPrice.getValue(), Long.MAX_VALUE);
        int containerSlots = handler.getRows() * 9;
        int bestSlot = -1;
        long bestUnit = Long.MAX_VALUE;
        long bestPrice = 0;
        long reserve = parsePrice(reserveMoney.getValue(), 3000000);
        int seen = 0;
        int wrongItem = 0;
        int wrongName = 0;
        int noPrice = 0;
        int reserveBlocked = 0;
        int expensive = 0;

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            seen++;
            if (activeBuyItem != null && stack.getItem() != activeBuyItem) {
                wrongItem++;
                continue;
            }
            if (!clean(stack.getName().getString()).contains(target)) {
                wrongName++;
                continue;
            }

            long price = getLorePrice(stack);
            if (price <= 0) {
                noPrice++;
                continue;
            }
            if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && balance > 0 && balance - price < reserve) {
                reserveBlocked++;
                continue;
            }

            long unit = price / Math.max(1, stack.getCount());
            if (unit <= maxPrice && unit < bestUnit) {
                bestUnit = unit;
                bestSlot = i;
                bestPrice = price;
            } else {
                expensive++;
            }
        }

        selectedBuyPrice = bestPrice;
        buyRejectReason = bestSlot >= 0 ? "$" + fmt(bestPrice) + "/" + fmt(bestUnit)
                : seen == 0 ? "нет лотов"
                : wrongItem >= seen ? "item-id"
                : wrongName >= seen - wrongItem ? "имя"
                : noPrice > 0 ? "нет цены"
                : reserveBlocked > 0 ? "резерв"
                : expensive > 0 ? "дорого"
                : "нет слота";
        return bestSlot;
    }

    private void clickRefresh(GenericContainerScreenHandler handler) {
        int refresh = findStarSlot(handler);
        if (refresh >= 0) {
            clickSlot(handler.syncId, refresh, 0, SlotActionType.PICKUP);
        }
    }

    private boolean isBuyConfirmScreen(GenericContainerScreen screen, GenericContainerScreenHandler handler) {
        String title = clean(screen.getTitle().getString());
        return title.contains("подтверждение")
                || title.contains("подозрительная цена")
                || findConfirmGlassSlot(handler) >= 0;
    }

    private int findConfirmSlot(GenericContainerScreenHandler handler) {
        int suspicious = findSuspiciousPriceConfirmSlot(handler);
        if (suspicious >= 0) return suspicious;
        return findConfirmGlassSlot(handler);
    }

    private int findConfirmGlassSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE && stackText(stack).contains("купить")) {
                return i;
            }
        }
        return -1;
    }

    private int findSuspiciousPriceConfirmSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        int rows = Math.max(1, handler.getRows());
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 4; col++) {
                int i = row * 9 + col;
                if (i >= containerSlots) continue;
                Slot slot = handler.getSlot(i);
                if (slot.hasStack() && slot.getStack().getItem() == Items.GREEN_STAINED_GLASS_PANE) {
                    return i;
                }
            }
        }
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.GREEN_STAINED_GLASS_PANE) {
                return i;
            }
        }
        return -1;
    }

    private int findStarSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        int lastRowStart = Math.max(0, containerSlots - 9);
        for (int i = lastRowStart; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.NETHER_STAR) {
                return i;
            }
        }
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.NETHER_STAR) {
                return i;
            }
        }
        return -1;
    }

    private int findNamedSlot(GenericContainerScreenHandler handler, String name) {
        String needle = name.toLowerCase();
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && stackText(slot.getStack()).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private int findStorageSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.ENDER_CHEST) {
                return i;
            }
        }
        int named = findNamedSlot(handler, "Хранилище");
        if (named >= 0) return named;
        return -1;
    }

    private int findStoredSellLot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            if (isSellTarget(stack) && stackText(stack).contains("продается")) {
                return i;
            }
        }
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && isSellTarget(slot.getStack())) {
                return i;
            }
        }
        return -1;
    }

    private long getLorePrice(ItemStack stack) {
        var lore = stack.getComponents().get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore == null) return 0;
        for (Text line : lore.lines()) {
            String text = line.getString();
            if (text.contains("$") || text.toLowerCase().contains("цена")) {
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) return parsePrice(digits, 0);
            }
        }
        return 0;
    }

    private BlockPos findNearbyCraftingTable() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = -2; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) continue;
                    double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void lookAtCraftingTable(BlockPos pos) {
        Vec3d target = Vec3d.ofCenter(pos).add(randomDouble(-0.12, 0.12), randomDouble(-0.08, 0.10), randomDouble(-0.12, 0.12));
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, horizontal)), -90.0, 90.0);
        RotationProcess.update(
                interpolatedRotation(yaw, pitch, 0.62F, 0.86F),
                randomFloat(14.0F, 26.0F),
                randomFloat(12.0F, 22.0F),
                randomTicks(1, 2),
                2
        );
    }

    private Rotation interpolatedRotation(float targetYaw, float targetPitch, float minFactor, float maxFactor) {
        float factor = randomFloat(minFactor, maxFactor);
        float yaw = mc.player.getYaw() + MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()) * factor;
        float pitch = mc.player.getPitch() + (targetPitch - mc.player.getPitch()) * factor;
        return new Rotation(
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0F, 90.0F)
        );
    }

    private void lookAtCow(CowEntity cow) {
        Vec3d target = new Vec3d(cow.getX(), cow.getY(), cow.getZ()).add(
                randomDouble(-0.18, 0.18),
                MathHelper.clamp(cow.getHeight() * randomDouble(0.45, 0.78), 0.45, 1.35),
                randomDouble(-0.18, 0.18)
        );
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, horizontal)), -90.0, 90.0);
        RotationProcess.update(
                interpolatedRotation(yaw, pitch, 0.58F, 0.82F),
                randomFloat(13.0F, 24.0F),
                randomFloat(11.0F, 21.0F),
                randomTicks(1, 2),
                2
        );
    }

    private CowEntity nearestCow() {
        CowEntity best = null;
        double bestDistance = 36.0;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof CowEntity cow)) continue;
            double distance = cow.squaredDistanceTo(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cow;
            }
        }
        return best;
    }

    private int countInventory(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private int milkBucketsTarget() {
        int possibleCrafts = Math.min(countInventory(Items.NETHERITE_INGOT), countInventory(Items.DIAMOND) / 4);
        if (possibleCrafts <= 0) return 4;
        int buckets = countInventory(Items.BUCKET) + countInventory(Items.MILK_BUCKET);
        return Math.min(Math.min(buckets, possibleCrafts * 4), MAX_MILK_BUCKET_USES);
    }

    private void markInventoryAction() {
        inventoryActionTicks = Math.max(inventoryActionTicks, 8);
        releaseHumanMovementKeys();
        humanLookTicks = Math.max(humanLookTicks, 3);
    }

    private void clickSlot(int syncId, int slot, int button, SlotActionType actionType) {
        markInventoryAction();
        mc.interactionManager.clickSlot(syncId, slot, button, actionType, mc.player);
    }

    private void tickHumanMovement() {
        if (inventoryActionTicks > 0) {
            inventoryActionTicks--;
            releaseHumanMovementKeys();
            humanMoveTicks = 0;
            return;
        }
        if (pendingCommand != null) return;
        if (mode == Mode.IDLE || mode == Mode.PLAYER_PAUSE || mc.currentScreen != null || mc.player.isUsingItem()
                || mode == Mode.CRAFT_OPEN_TABLE || mode == Mode.CRAFT_FILL || mode == Mode.CRAFT_MILK) {
            if (humanMoveTicks > 0) releaseHumanMovementKeys();
            humanMoveTicks = 0;
            if (nextHumanMoveTicks <= 0) nextHumanMoveTicks = randomTicks(130, 380);
            return;
        }

        if (humanMoveTicks > 0) {
            humanMoveTicks--;
            if (humanMoveTicks <= 0) {
                releaseHumanMovementKeys();
                nextHumanMoveTicks = randomTicks(120, 520);
            }
            return;
        }

        if (nextHumanMoveTicks > 0) {
            nextHumanMoveTicks--;
            return;
        }

        releaseHumanMovementKeys();
        int pattern = randomTicks(0, 8);
        mc.options.forwardKey.setPressed(pattern == 0 || pattern == 4 || pattern == 6);
        mc.options.backKey.setPressed(pattern == 1);
        mc.options.leftKey.setPressed(pattern == 2 || pattern == 4 || pattern == 7);
        mc.options.rightKey.setPressed(pattern == 3 || pattern == 5 || pattern == 6);
        mc.options.jumpKey.setPressed(pattern == 7 && Math.random() < 0.35);
        mc.options.sneakKey.setPressed(pattern == 8 || Math.random() < 0.12);
        humanMoveTicks = randomTicks(5, 32);

        if (Math.random() < 0.45) {
            float targetYaw = mc.player.getYaw() + randomFloat(-14.0F, 14.0F);
            float targetPitch = MathHelper.clamp(mc.player.getPitch() + randomFloat(-4.5F, 4.5F), -75.0F, 75.0F);
            RotationProcess.update(
                    interpolatedRotation(targetYaw, targetPitch, 0.28F, 0.48F),
                    randomFloat(5.0F, 14.0F),
                    randomFloat(5.0F, 13.0F),
                    randomTicks(3, 7),
                    1
            );
        }
    }

    private void releaseHumanMovementKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private void tickHumanLook() {
        if (mode == Mode.IDLE || mode == Mode.PLAYER_PAUSE || mc.player == null) return;
        if (inventoryActionTicks > 0 && mode != Mode.CRAFT_MILK && mode != Mode.STORAGE_OPEN) return;

        if (humanSneakTicks > 0) {
            humanSneakTicks--;
            mc.options.sneakKey.setPressed(true);
        }

        if (humanLookTicks > 0) {
            humanLookTicks--;
            return;
        }

        if (!isAhLikeMode() && !(mc.currentScreen instanceof GenericContainerScreen)) return;
        humanLookTicks = randomTicks(5, mc.currentScreen instanceof GenericContainerScreen ? 12 : 26);
        float targetYaw = mc.player.getYaw() + randomFloat(-14.0F, 14.0F);
        float targetPitch = MathHelper.clamp(mc.player.getPitch() + randomFloat(-4.5F, 4.5F), -78.0F, 78.0F);
        RotationProcess.update(
                interpolatedRotation(targetYaw, targetPitch, 0.24F, 0.44F),
                randomFloat(4.0F, 12.0F),
                randomFloat(4.0F, 11.0F),
                randomTicks(3, 7),
                0
        );
    }

    private boolean tickDelayedAhCommand() {
        if (delayedAhCommand == null) return false;

        if (humanSneakTicks <= 0 && Math.random() < 0.35) {
            humanSneakTicks = randomTicks(3, 11);
        }

        if (delayedAhTicks-- > 0) {
            if (Math.random() < 0.18) {
                mc.options.sneakKey.setPressed(true);
            }
            return true;
        }

        String command = delayedAhCommand;
        delayedAhCommand = null;
        delayedAhTicks = 0;
        sendCommand(command);
        waitTicks = Math.max(waitTicks, randomTicks(8, 22));
        return true;
    }

    private void sendAhCommand(String command) {
        if (command == null || command.isEmpty()) return;
        if (delayedAhCommand != null) {
            delayedAhCommand = command;
            return;
        }
        delayedAhCommand = command;
        delayedAhTicks = randomTicks(4, 18);
        humanLookTicks = 0;
        humanSneakTicks = Math.random() < 0.65 ? randomTicks(3, 13) : 0;
    }

    private boolean isAhLikeMode() {
        return mode == Mode.BUY_OPEN || mode == Mode.BUY_READ || mode == Mode.BUY_CONFIRM
                || mode == Mode.INVIS_OPEN || mode == Mode.INVIS_READ || mode == Mode.INVIS_CONFIRM
                || mode == Mode.SELL || mode == Mode.SELL_PREPARE || mode == Mode.SELL_SEND
                || mode == Mode.STORAGE_OPEN || mode == Mode.STORAGE_CLICK || mode == Mode.STORAGE_TAKE;
    }

    private int findInventorySlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findInventorySlotByName(String name) {
        String needle = name.trim().toLowerCase();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && clean(stack.getName().getString()).contains(needle)) return i;
        }
        return -1;
    }

    private boolean prepareSingleSellItem() {
        String name = sellItem.getValue();
        ItemStack hotbar = mc.player.getInventory().getStack(0);
        if (isSellTarget(hotbar) && hotbar.getCount() == 1) {
            return true;
        }

        int syncId = mc.player.currentScreenHandler.syncId;
        int hotbarScreenSlot = playerScreenSlot(0);

        if (!hotbar.isEmpty()) {
            int emptyScreenSlot = findEmptyStorageScreenSlot();
            if (emptyScreenSlot == -1) return false;

            if (isSellTarget(hotbar) && hotbar.getCount() > 1) {
                clickSlot(syncId, hotbarScreenSlot, 0, SlotActionType.PICKUP);
                clickSlot(syncId, hotbarScreenSlot, 1, SlotActionType.PICKUP);
                clickSlot(syncId, emptyScreenSlot, 0, SlotActionType.PICKUP);
                return true;
            }

            clickSlot(syncId, hotbarScreenSlot, 0, SlotActionType.PICKUP);
            clickSlot(syncId, emptyScreenSlot, 0, SlotActionType.PICKUP);
        }

        int source = findInventorySlotByName(name);
        if (source == -1) return false;

        ItemStack stack = mc.player.getInventory().getStack(source);
        if (stack.getCount() == 1) {
            moveToHotbar(source, 0);
            return true;
        }

        int sourceScreenSlot = playerScreenSlot(source);
        clickSlot(syncId, sourceScreenSlot, 0, SlotActionType.PICKUP);
        clickSlot(syncId, hotbarScreenSlot, 1, SlotActionType.PICKUP);
        clickSlot(syncId, sourceScreenSlot, 0, SlotActionType.PICKUP);
        return true;
    }

    private int findEmptyStorageScreenSlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return playerScreenSlot(i);
        }
        for (int i = 1; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return playerScreenSlot(i);
        }
        return -1;
    }

    private void moveToHotbar(int inventorySlot, int hotbarSlot) {
        if (inventorySlot == hotbarSlot) return;
        int screenSlot = playerScreenSlot(inventorySlot);
        clickSlot(mc.player.currentScreenHandler.syncId, screenSlot, hotbarSlot, SlotActionType.SWAP);
    }

    private int playerScreenSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private int craftingPlayerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 37 : inventorySlot + 1;
    }

    private long parsePrice(String value, long fallback) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return fallback;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long nextSellPrice() {
        long base = parsePrice(sellPrice.getValue(), 1);
        if (!randomSellPrice.getValue()) return Math.max(1, base);

        long range = parsePrice(randomSellPriceRange.getValue(), 0);
        if (range <= 0) return Math.max(1, base);

        long min = Math.max(1, base - range);
        long max = Math.max(min, base + range);
        long spread = max - min + 1;
        return min + (long) Math.floor(Math.random() * spread);
    }

    private String sellPriceSuffix() {
        if (!randomSellPrice.getValue()) return "";
        long range = parsePrice(randomSellPriceRange.getValue(), 0);
        return range > 0 ? " ±$" + fmt(range) : "";
    }

    private int randomTicks(int min, int max) {
        if (max <= min) return min;
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }

    private float randomFloat(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }

    private double randomDouble(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private void startSessionStats() {
        sessionStartMillis = System.currentTimeMillis();
        sessionStartBalance = balance > 0 ? balance : readScoreboardMoney();
        autoPauseAnarchy = readCurrentAnarchy();
        playerPauseTicks = 0;
        playerPauseReturnTicks = 0;
        playerPauseReturning = false;
        resumeModeAfterPlayerPause = Mode.IDLE;
        playerPauseName = null;
        lastPlayerPauseName = null;
        sessionSellEarned = 0;
        sessionBuySpent = 0;
        sessionClanInvested = 0;
    }

    private long sessionProfit() {
        if (sessionStartBalance > 0 && balance >= 0) {
            return balance - sessionStartBalance + sessionClanInvested;
        }
        return sessionSellEarned - sessionBuySpent;
    }

    private String sessionElapsedText() {
        long started = sessionStartMillis <= 0 ? System.currentTimeMillis() : sessionStartMillis;
        long seconds = Math.max(0, (System.currentTimeMillis() - started) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%02d:%02d", minutes, secs);
    }

    private String signedMoney(long value) {
        return (value >= 0 ? "+$" : "-$") + fmt(Math.abs(value));
    }

    private String fmt(long value) {
        String raw = Long.toString(Math.max(0, value));
        StringBuilder out = new StringBuilder(raw);
        for (int i = out.length() - 3; i > 0; i -= 3) {
            out.insert(i, '.');
        }
        return out.toString();
    }

    private void tickWatchdog() {
        if (timedMode != mode) {
            timedMode = mode;
            stateTicks = 0;
            lastProgressSignature = "";
        }

        String signature = mode
                + "|s" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
                + "|sid" + (mc.player == null ? -1 : mc.player.currentScreenHandler.syncId)
                + "|l" + listed
                + "|d" + countInventory(Items.DIAMOND)
                + "|n" + countInventory(Items.NETHERITE_INGOT)
                + "|m" + countInventory(Items.MILK_BUCKET)
                + "|a" + countSellTargetInventory()
                + "|cmd" + pendingCommand;

        if (!signature.equals(lastProgressSignature)) {
            lastProgressSignature = signature;
            stuckTicks = 0;
            stateTicks = 0;
            return;
        }

        stateTicks++;
        int stateTimeout = modeTimeoutTicks(mode);
        if (stateTimeout > 0 && stateTicks > stateTimeout) {
            stateTicks = 0;
            recoverStuckState();
            return;
        }

        if (++stuckTicks < 1200) return;
        stuckTicks = 0;
        recoverStuckState();
    }

    private int modeTimeoutTicks(Mode mode) {
        return switch (mode) {
            case PLAYER_PAUSE -> 0;
            case STORAGE_OPEN, STORAGE_CLICK, STORAGE_TAKE -> STORAGE_REOPEN_TIMEOUT_TICKS + 40;
            case SELL_PREPARE, SELL_SEND -> 120;
            case BUY_CONFIRM, INVIS_CONFIRM -> 140;
            case INVIS_DRINK -> INVIS_DRINK_TIMEOUT_TICKS + 40;
            case CRAFT_OPEN_TABLE -> 140;
            case CRAFT_FILL -> 180;
            case BUY_OPEN, INVIS_OPEN -> AH_SEARCH_RETRY_TICKS + 60;
            default -> 0;
        };
    }

    private void recoverStuckState() {
        msg("§e[AuraCrafter] Слишком долго без прогресса, перезапускаю этап.");
        switch (mode) {
            case BUY_OPEN, BUY_READ, BUY_CONFIRM -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                buyConfirmClicked = false;
                buyConfirmHoldTicks = 0;
                if (fullAuto && activeBuyItem == Items.NETHERITE_INGOT && !canBuyNetheriteAboveReserve(0)) {
                    handleNoNetheriteBudget();
                    return;
                }
                sendActiveBuySearch();
                mode = Mode.BUY_OPEN;
                waitTicks = 20;
            }
            case STORAGE_OPEN, STORAGE_CLICK, STORAGE_TAKE -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                if (storageTookSellLot) {
                    listed = 0;
                    mode = Mode.SELL;
                    noScreenTicks = 0;
                    waitTicks = 10;
                } else {
                    startStorageRelist();
                }
            }
            case SELL, SELL_PREPARE, SELL_SEND -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                mode = Mode.SELL;
                waitTicks = 10;
            }
            case CRAFT, CRAFT_MILK, CRAFT_OPEN_TABLE, CRAFT_FILL -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                mode = Mode.CRAFT;
                waitTicks = 20;
            }
            case INVIS_OPEN, INVIS_READ, INVIS_CONFIRM, INVIS_DRINK -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                releaseInvisUse();
                invisConfirmClicked = false;
                invisConfirmHoldTicks = 0;
                invisUseStarted = false;
                invisDrinkTicks = 0;
                sendInvisSearch();
                mode = Mode.INVIS_OPEN;
                waitTicks = 20;
            }
            case PLAYER_PAUSE -> {
            }
            case AUTO_WAIT_JOIN -> {
                if (targetAnarchy > 0) sendCommand("an" + targetAnarchy);
                waitTicks = JOIN_AH_DELAY;
            }
            case AUTO_WAIT_BUDGET -> {
                refreshBalanceNow(true);
                waitTicks = 40;
            }
            case IDLE -> {
            }
        }
    }

    private void tickClanInvest() {
        if (investCooldownTicks > 0) {
            investCooldownTicks--;
            return;
        }
        if (!clanInvest.getValue() || mc.currentScreen != null) return;

        long currentBalance = readScoreboardMoney();
        if (currentBalance > 0) balance = currentBalance;
        if (balance <= 0) return;

        long threshold = parsePrice(clanInvestAt.getValue(), Long.MAX_VALUE);
        long amount = parsePrice(clanInvestAmount.getValue(), 0);
        if (amount <= 0 || balance < threshold || balance - amount < 0) return;

        sendCommand("clan invest " + amount);
        sessionClanInvested += amount;
        balance = Math.max(0, balance - amount);
        investCooldownTicks = 1200;
    }

    private void refreshBalanceNow(boolean forceCommand) {
        if (!forceCommand && balance <= 0) {
            long currentBalance = readScoreboardMoney();
            if (currentBalance > 0) {
                balance = currentBalance;
                balanceRefreshTicks = 0;
            }
        }

        if (forceCommand || ++balanceRefreshTicks >= BALANCE_REFRESH_CALLS + randomTicks(0, 18)) {
            sendCommand("money");
            balanceRefreshTicks = 0;
        }
    }

    private void refundReservedBuyBalance() {
        if (!balanceReservedForConfirm) return;
        if (balance >= 0 && selectedBuyPrice > 0) {
            balance += selectedBuyPrice;
        }
        balanceReservedForConfirm = false;
        selectedBuyPrice = 0;
    }

    private boolean isBuyFailedMessage(String lower) {
        return lower.contains("не удалось купить")
                || lower.contains("уже куп")
                || lower.contains("куплен")
                || lower.contains("истек")
                || lower.contains("нет в наличии");
    }

    private boolean isAuctionBalanceSellBlocked(String lower) {
        return lower.contains("баланс")
                && lower.contains("аукцион")
                && lower.contains("монет");
    }

    private long extractDollarPrice(String text) {
        if (text == null) return 0;
        int dollar = text.indexOf('$');
        if (dollar < 0) return 0;
        String tail = text.substring(dollar).replaceAll("[^0-9]", "");
        return parsePrice(tail, 0);
    }

    private long readScoreboardMoney() {
        if (mc.world == null) return -1;
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        long titleMoney = parseMoneyLine(objective.getDisplayName().getString());
        if (titleMoney > 0) return titleMoney;

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            String line = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
            long money = parseMoneyLine(line);
            if (money > 0) return money;
        }
        return -1;
    }

    private int readCurrentAnarchy() {
        int scoreboard = readScoreboardAnarchy();
        if (scoreboard > 0) return scoreboard;
        int cached = ServerUtil.getAnarchy();
        if (cached > 0) return cached;
        return -1;
    }

    private int readScoreboardAnarchy() {
        if (mc.world == null) return -1;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        int title = parseAnarchyLine(objective.getDisplayName().getString());
        if (title > 0) return title;

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            String line = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
            int anarchy = parseAnarchyLine(line);
            if (anarchy > 0) return anarchy;
        }
        return -1;
    }

    private int parseAnarchyLine(String line) {
        String text = clean(line);
        int index = text.indexOf("анархия");
        if (index < 0) return -1;
        String tail = text.substring(index).replaceAll("[^0-9]", "");
        return (int) parsePrice(tail, -1);
    }

    private long parseMoneyLine(String line) {
        String text = clean(line);
        if (!text.contains("монет") && !text.contains("баланс")) return -1;
        return parsePrice(text, -1);
    }

    private void finishAutoBuy() {
        normalizeActiveBuyState();
        if (!fullAuto) {
            reset();
            return;
        }
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }

        if (resumeCraftAfterDiamondBuy) {
            resumeCraftAfterDiamondBuy = false;
            activeBuyName = null;
            activeBuyItem = null;
            activeBuyTarget = 0;
            activeMaxUnitPrice = -1;
            returnHomeForCraft();
            return;
        }

        if (activeBuyItem == Items.DIAMOND) {
            beginNetheriteBuy();
            return;
        }

        returnHomeForCraft();
    }

    private void returnHomeForCraft() {
        allowAwayAnarchy = false;
        if (homeAnarchy <= 0) {
            homeAnarchy = readCurrentAnarchy();
        }
        int current = readCurrentAnarchy();
        if (homeAnarchy > 0 && current != homeAnarchy) {
            joinAnarchy(homeAnarchy, JoinAction.CRAFT);
            return;
        }
        mode = Mode.CRAFT;
        waitTicks = TICK_DELAY;
    }

    private void startAutoSellOrBuy() {
        if (countSellTargetInventory() > 0 || listed > 0) {
            mode = Mode.SELL;
            waitTicks = TICK_DELAY;
            return;
        }
        beginAutoCycle();
    }

    private int countSellTargetInventory() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isSellTarget(stack)) count += stack.getCount();
        }
        return count;
    }

    private void switchBuyAnarchy() {
        int next = nextBuyAnarchy();
        if (next <= 0) {
            buySearchTicks = 0;
            clickRefreshIfOpen();
            return;
        }
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        allowAwayAnarchy = true;
        msg("§e[AuraCrafter] Нету подходящих лотов, меняю анку на " + next + ".");
        joinAnarchy(next, JoinAction.SEARCH);
    }

    private int nextBuyAnarchy() {
        String[] parts = anarchyList.getValue().split("[,; ]+");
        for (int attempts = 0; attempts < parts.length; attempts++) {
            String part = parts[(anarchyIndex++ % Math.max(1, parts.length))].replaceAll("[^0-9]", "");
            if (part.isEmpty()) continue;
            int value = (int) parsePrice(part, -1);
            if (value > 0 && value != readScoreboardAnarchy()) return value;
        }
        return -1;
    }

    private void clickRefreshIfOpen() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            clickRefresh(screen.getScreenHandler());
        }
        waitTicks = 20;
    }

    private boolean guardAnarchy() {
        if (homeAnarchy <= 0 || allowAwayAnarchy || mode == Mode.AUTO_WAIT_JOIN) return false;
        int current = readCurrentAnarchy();
        if (current == homeAnarchy) return false;
        rejoinHome();
        return true;
    }

    private void rejoinHome() {
        if (homeAnarchy <= 0) homeAnarchy = readCurrentAnarchy();
        if (homeAnarchy <= 0) return;
        joinAnarchy(homeAnarchy, JoinAction.CRAFT);
    }

    private void joinAnarchy(int anarchy, JoinAction action) {
        targetAnarchy = anarchy;
        joinAction = action;
        buySearchTicks = 0;
        noScreenTicks = 0;
        sendCommand("an" + anarchy);
        mode = Mode.AUTO_WAIT_JOIN;
        waitTicks = JOIN_AH_DELAY;
    }

    private void tickAntiAfkCommand() {
        if (pendingCommand == null) {
            return;
        }

        antiAfkTicks++;
        mc.options.forwardKey.setPressed(antiAfkTicks < 10);
        mc.options.leftKey.setPressed(antiAfkTicks >= 10 && antiAfkTicks < 22);
        mc.options.rightKey.setPressed(antiAfkTicks >= 22 && antiAfkTicks < 34);
        mc.options.jumpKey.setPressed(antiAfkTicks == 8 || antiAfkTicks == 24);

        if (antiAfkTicks < 38) return;

        releaseAntiAfkKeys();
        lastCommand = pendingCommand;
        mc.player.networkHandler.sendChatCommand(pendingCommand.startsWith("/") ? pendingCommand.substring(1) : pendingCommand);
        pendingCommand = null;
        afkBlocked = false;
        antiAfkTicks = 0;
        antiAfkStage = 0;
    }

    private void wakeForCommand(String command) {
        if (command == null || command.isEmpty()) command = lastCommand;
        if (command == null || command.isEmpty()) return;
        if (command.equals(pendingCommand)) return;
        pendingCommand = command;
        antiAfkTicks = 0;
        antiAfkStage++;
        releaseAntiAfkKeys();
    }

    private void releaseAntiAfkKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private String modeName() {
        if (mode == Mode.PLAYER_PAUSE) return "пауза игрок";
        return switch (mode) {
            case PLAYER_PAUSE -> "пауза игрок";
            case IDLE -> "ожидает бинда";
            case AUTO_WAIT_JOIN -> "ждет анку";
            case AUTO_WAIT_BUDGET -> "ждет баланс";
            case BUY_OPEN, BUY_READ, BUY_CONFIRM -> "закупает";
            case CRAFT, CRAFT_MILK, CRAFT_OPEN_TABLE, CRAFT_FILL -> "крафтит";
            case INVIS_OPEN, INVIS_READ, INVIS_CONFIRM, INVIS_DRINK -> "инвиз";
            case SELL, SELL_PREPARE, SELL_SEND -> "продает";
            case STORAGE_OPEN, STORAGE_CLICK, STORAGE_TAKE -> "перевыставляет";
        };
    }

    private boolean isSellTarget(ItemStack stack) {
        return !stack.isEmpty() && clean(stack.getName().getString()).contains(sellItem.getValue().trim().toLowerCase());
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
        if (command == null || command.isEmpty()) return;
        lastCommand = command;
        if (afkBlocked) {
            wakeForCommand(command);
            return;
        }
        mc.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private void stop(String message) {
        msg(message);
        reset();
    }

    private void reset() {
        mode = Mode.IDLE;
        waitTicks = 0;
        listed = 0;
        sellAttemptsLeft = 0;
        lastScreenSyncId = -1;
        noScreenTicks = 0;
        fullSellTicks = 0;
        fullAuto = false;
        activeBuyName = null;
        activeMaxUnitPrice = -1;
        activeBuyItem = null;
        activeBuyTarget = 0;
        buySearchTicks = 0;
        buyOpenTicks = 0;
        ignoredSearchScreenSyncId = -1;
        balanceRefreshTicks = 0;
        homeAnarchy = -1;
        targetAnarchy = -1;
        autoPauseAnarchy = -1;
        playerPauseTicks = 0;
        playerPauseReturnTicks = 0;
        playerPauseReturning = false;
        resumeModeAfterPlayerPause = Mode.IDLE;
        playerPauseName = null;
        lastPlayerPauseName = null;
        anarchyIndex = 0;
        joinAction = JoinAction.NONE;
        allowAwayAnarchy = false;
        antiAfkTicks = 0;
        pendingCommand = null;
        lastCommand = null;
        antiAfkStage = 0;
        buyConfirmClicked = false;
        buyConfirmHoldTicks = 0;
        selectedBuyPrice = 0;
        balanceReservedForConfirm = false;
        afkBlocked = false;
        lastProgressSignature = "";
        stuckTicks = 0;
        timedMode = Mode.IDLE;
        stateTicks = 0;
        buyRejectReason = "";
        resumeCraftAfterDiamondBuy = false;
        investCooldownTicks = 0;
        storageTookSellLot = false;
        checkedStorageForSell = false;
        emptyStorageChecks = 0;
        storageTakeClicks = 0;
        autoInvisCheckTicks = 0;
        resumeModeAfterInvis = Mode.IDLE;
        resumeWaitAfterInvis = 0;
        invisOpenTicks = 0;
        invisConfirmClicked = false;
        invisConfirmHoldTicks = 0;
        invisUseStarted = false;
        invisDrinkTicks = 0;
        invisPreviousSlot = -1;
        humanMoveTicks = 0;
        nextHumanMoveTicks = 0;
        humanLookTicks = 0;
        humanSneakTicks = 0;
        delayedAhCommand = null;
        delayedAhTicks = 0;
        inventoryActionTicks = 0;
        tableAimTicks = 0;
        cowAimTicks = 0;
        releaseInvisUse();
        releaseHumanMovementKeys();
        releaseAntiAfkKeys();
    }

    private void msg(String text) {
        ChatUtils.addChatMessage(text);
    }
}
