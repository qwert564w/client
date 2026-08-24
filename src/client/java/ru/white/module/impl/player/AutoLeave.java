package ru.white.module.impl.player;


import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.StopGPT;
import ru.white.utils.other.Instance;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "Auto Leave",desc = "Данный модуль был сделан акарачком поэтому может работать через жопу", category = Category.PLAYER)
public class AutoLeave extends Module {

    public static AutoLeave get() {
        return Instance.get(AutoLeave.class);
    }

    public final ModeSetting leaveMode = new ModeSetting(this, "Команда", "/hub", "/spawn", "/darena");
    public final BooleanSetting onPlayerNearby = new BooleanSetting(this, "Игрок рядом", true);
    public final SliderSetting playerRange = new SliderSetting(this, "Дистанция игрока", 20, 10, 50, 1);
    public final BooleanSetting onPvpEnd = new BooleanSetting(this, "Конец PvP", false);

    private final StopGPT antiSpamTimer = new StopGPT();
    private final StopGPT darenaMenuTimer = new StopGPT();

    private boolean waitingForDarenaMenu = false;
    private boolean leaveTriggered = false;
    private boolean didLeave = false;
    private int darenaClickTicks = 0;

    private static final long ANTI_SPAM_DELAY = 5000;
    private static final long POST_LEAVE_COOLDOWN = 10000;

    private long lastLeaveTime = 0;

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null)
            return;

        if (didLeave && !leaveMode.is("/darena")) {
            double x = Math.abs(mc.player.getX());
            double z = Math.abs(mc.player.getZ());
            if (x <= 5 && z <= 5) {
                ChatUtils.addChatMessage("§e[AutoLeave] §aТелепортация успешна, выключаюсь.");
                didLeave = false;
                setEnabled(false);
                return;
            }
        }

        if (waitingForDarenaMenu) {
            darenaClickTicks++;
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                GenericContainerScreenHandler handler = screen.getScreenHandler();
                int containerSlots = handler.getRows() * 9;
                for (int i = 0; i < containerSlots; i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot.hasStack() && slot.getStack().getItem() == Items.PUFFERFISH) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0,
                                SlotActionType.PICKUP, mc.player);
                        waitingForDarenaMenu = false;
                        darenaClickTicks = 0;
                        return;
                    }
                }
                if (darenaClickTicks > 40) {
                    waitingForDarenaMenu = false;
                    darenaClickTicks = 0;
                }
            } else if (darenaClickTicks > 60) {
                waitingForDarenaMenu = false;
                darenaClickTicks = 0;
            }
            return;
        }

        if (System.currentTimeMillis() - lastLeaveTime < POST_LEAVE_COOLDOWN)
            return;

        if (!antiSpamTimer.hasTimePassed(ANTI_SPAM_DELAY))
            return;

        boolean shouldLeave = false;
        String reason = "";

        if (onPlayerNearby.getValue()) {
            float maxRange = playerRange.getValue();
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player)
                    continue;
                if (!player.isAlive())
                    continue;

                double dist = mc.player.distanceTo(player);
                if (dist <= maxRange) {
                    shouldLeave = true;
                    reason = "Игрок " + player.getName().getString() + " рядом (" + (int) dist + "м)";
                    break;
                }
            }
        }

        if (!shouldLeave && onPvpEnd.getValue()) {
            try {
                ElytraHelper elytraHelper = Instance.get(ElytraHelper.class);
                if (elytraHelper != null && elytraHelper.isEnabled() && elytraHelper.isPvpJustEnded()) {
                    shouldLeave = true;
                    reason = "PvP закончился";
                    elytraHelper.clearPvpEnded();
                }
            } catch (Exception ignored) {
            }
        }

        if (shouldLeave) {
            executeLeave(reason);
        }
    }

    private void executeLeave(String reason) {
        antiSpamTimer.reset();
        lastLeaveTime = System.currentTimeMillis();

        String mode = leaveMode.getValue();
        ChatUtils.addChatMessage("§e[AutoLeave] §c" + reason + " §7→ §a" + mode);

        switch (mode) {
            case "/hub" -> {
                mc.player.networkHandler.sendChatCommand("hub");
                didLeave = true;
            }
            case "/spawn" -> {
                mc.player.networkHandler.sendChatCommand("spawn");
                didLeave = true;
            }
            case "/darena" -> {
                mc.player.networkHandler.sendChatCommand("darena");
                waitingForDarenaMenu = true;
                darenaMenuTimer.reset();
                darenaClickTicks = 0;
            }
        }
    }

    @Override
    public void onDisable() {
        waitingForDarenaMenu = false;
        darenaClickTicks = 0;
        leaveTriggered = false;
        didLeave = false;
        super.onDisable();
    }
}
