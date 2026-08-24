package ru.white.module.impl.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.other.TimerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ModuleInfo(
        name = "Auto Duel",
        desc = "Автоматически отправляет вызовы на дуэли (ReallyWorld)",
        category = Category.OTHER
)
public class AutoDuel extends Module {

    private static final Pattern pattern = Pattern.compile("^\\w{3,16}$");

    public final ModeSetting mode = new ModeSetting(this, "Режим",
            "Шары", "Щит", "Шипы 3", "Незеритка", "Читерский рай", "Лук", "Классик", "Тотемы", "Нодебафф");

    private double lastPosX, lastPosY, lastPosZ;

    private final List<String> sent = new ArrayList<>();
    private int currentPlayerIndex = 0;

    private final TimerUtil counter = new TimerUtil();
    private final TimerUtil counter2 = new TimerUtil();
    private final TimerUtil counterChoice = new TimerUtil();
    private final TimerUtil counterTo = new TimerUtil();

    private static final String MENU_KIT = "kit";
    private static final String MENU_SETUP = "setup";

    private final Map<Integer, String> pendingMenus = new HashMap<>();

    @Override
    protected void onDisable() {
        pendingMenus.clear();
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        final List<String> players = getOnlinePlayers();

        double distance = Math.sqrt(Math.pow(lastPosX - mc.player.getX(), 2) +
                Math.pow(lastPosY - mc.player.getY(), 2) +
                Math.pow(lastPosZ - mc.player.getZ(), 2));

        if (distance > 500) {
            toggle();
        }

        lastPosX = mc.player.getX();
        lastPosY = mc.player.getY();
        lastPosZ = mc.player.getZ();

        if (counter2.isReached(800L * players.size())) {
            sent.clear();
            currentPlayerIndex = 0;
            counter2.reset();
        }

        handleContainer();

        if (!players.isEmpty()) {
            if (counter.isReached(1000)) {
                if (currentPlayerIndex >= players.size()) {
                    currentPlayerIndex = 0;
                }

                String player = players.get(currentPlayerIndex);
                if (!sent.contains(player) && !player.equals(getSelfName())) {
                    sendCommand("duel " + player);
                    sent.add(player);
                }
                currentPlayerIndex++;
                counter.reset();
            }
        }
    }

    private void handleContainer() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            pendingMenus.clear();
            return;
        }

        String menu = pendingMenus.get(handler.syncId);
        if (menu == null) {
            return;
        }

        if (mc.currentScreen != null) {
            mc.setScreen(null);
        }

        if (MENU_KIT.equals(menu)) {
            if (counterChoice.isReached(150)) {
                int slot = getKitSlot();
                if (slot >= 0) {
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                }
                counterChoice.reset();
            }
        } else if (MENU_SETUP.equals(menu)) {
            if (counterTo.isReached(150)) {
                mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                counterTo.reset();
            }
        }
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend()) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (packet instanceof OpenScreenS2CPacket open) {
            String title = open.getName().getString();
            if (title.contains("Выбор набора (1/1)")) {
                pendingMenus.put(open.getSyncId(), MENU_KIT);
            } else if (title.contains("Настройка поединка")) {
                pendingMenus.put(open.getSyncId(), MENU_SETUP);
            }
        }

        if (packet instanceof GameMessageS2CPacket chat) {
            final String text = chat.content().getString().toLowerCase();
            if ((text.contains("начало") && text.contains("через") && text.contains("секунд!")) ||
                    text.isEmpty()) {
                toggle();
            }
        }
    }

    private int getKitSlot() {
        if (mode.is("Щит")) return 0;
        if (mode.is("Шипы 3")) return 1;
        if (mode.is("Лук")) return 2;
        if (mode.is("Тотемы")) return 3;
        if (mode.is("Нодебафф")) return 4;
        if (mode.is("Шары")) return 5;
        if (mode.is("Классик")) return 6;
        if (mode.is("Читерский рай")) return 7;
        if (mode.is("Незеритка")) return 8;
        return -1;
    }

    private String getSelfName() {
        return mc.getSession().getUsername();
    }

    private void sendCommand(String command) {
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        mc.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private List<String> getOnlinePlayers() {
        List<String> names = new ArrayList<>();
        if (mc.player == null || mc.player.networkHandler == null) {
            return names;
        }
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (profile == null) {
                continue;
            }
            String name = profile.name();
            if (name != null && pattern.matcher(name).matches()) {
                names.add(name);
            }
        }
        return names;
    }
}
