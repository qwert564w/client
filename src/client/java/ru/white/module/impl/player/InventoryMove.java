package ru.white.module.impl.player;

import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.ScreenCloseEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.impl.combat.TriplePickerScreen;
import ru.white.module.impl.combat.TripleWheelScreen;
import ru.white.module.impl.utils.ReportHelper;
import ru.white.screen.Menu;
import ru.white.utils.other.TimerUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

@ModuleInfo(
        name = "Inventory Move",
        desc = "Позволяет двигаться с открытым инвентарём",
        category = Category.MOVEMENT
)
public class InventoryMove extends Module {

    public final ModeSetting mode = new ModeSetting(this, "Тип", "FunTime","Vanilla");

    private final List<Packet<?>> packetQueue = new ArrayList<>();
    private final Set<Packet<?>> sendingPackets = new HashSet<>();
    private final Queue<ClickSlotC2SPacket> windowClickPacketQueue = new LinkedList<>();

    private final TimerUtil timer = new TimerUtil();

    public boolean stop = false;
    private boolean pendingClose = false;
    private long closeTime = 0;

    // Переменные для обновленного режима FunTime_2
    private int stopTicksOut;
    private boolean stoppedStatus;
    private boolean previousStoppedStatus;
    public int ticksPostOnStop = 1;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null) return;

        if(mode.is("Vanilla") && !ReportHelper.isAutoListActive()) {
            KeyBinding[] movementKeys = {
                    mc.options.forwardKey,
                    mc.options.backKey,
                    mc.options.leftKey,
                    mc.options.rightKey,
                    mc.options.jumpKey
            };


            if (mc.currentScreen instanceof ChatScreen) {

            } else if (mc.currentScreen instanceof InventoryScreen ||
                    mc.currentScreen instanceof TripleWheelScreen ||    mc.currentScreen instanceof Menu&& !Menu.searchActive ||
                    mc.currentScreen instanceof TriplePickerScreen ||
                    mc.currentScreen instanceof GenericContainerScreen) {

                updateKeyBindingState(movementKeys);
            }
        }

        // Логика тиков остановки для FunTime_2
        if (mode.is("FunTime_2")) {
            if (this.previousStoppedStatus && this.stoppedStatus && this.stopTicksOut > 0) {
                this.useAccumulatedPackets();
            }
            this.previousStoppedStatus = this.stoppedStatus;

            if (this.stopTicksOut > 0) {
                --this.stopTicksOut;
            }
            this.stoppedStatus = this.stopTicksOut > 0;
            ++this.ticksPostOnStop;
            if (this.stoppedStatus) {
                this.ticksPostOnStop = 0;
            }
        }

        // Логика закрытия контейнеров для FunTime
        if (pendingClose && System.currentTimeMillis() >= closeTime) {
            pendingClose = false;
            for (Packet<?> p : packetQueue) {
                sendingPackets.add(p);
                mc.getNetworkHandler().getConnection().send(p, null);
            }
            packetQueue.clear();

            if (mc.player != null) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        setKey(true);
                        mc.player.closeHandledScreen();
                    }
                });
            }
        }

        handleBypassMode();
    }

    public boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().y != 0 || mc.player.input.getMovementInput().x != 0;
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (e.getPacket() instanceof CraftRequestC2SPacket) return;
        if (ReportHelper.isAutoListActive()) return;



        // Блокируем пакет закрытия окна от сервера в FunTime_2
        if (e.getPacket() instanceof CloseScreenS2CPacket) {
            if (mode.is("FunTime_2")) {
                e.cancel();
            }
            return;
        }

        if (!(e.getPacket() instanceof ClickSlotC2SPacket packet)) return;

        // Пропускаем пакеты, отправленные самой функцией useAccumulatedPackets
        if (sendingPackets.contains(packet)) {
            sendingPackets.remove(packet);
            return;
        }

        boolean isTargetScreen = mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof GenericContainerScreen;

        if (e.isSend() && isTargetScreen) {
            // Новая логика кликов для FunTime_2
            if (mode.is("FunTime_2")) {
                if (this.canStoppingOnWindowClick() && packet.slot() != -1) {
                    this.setStop(packet.actionType());
                    if (!this.stoppedStatus) {
                        if (!this.windowClickPacketQueue.contains(packet)) {
                            this.windowClickPacketQueue.add(packet);
                        }
                        this.ticksPostOnStop = 0;
                        e.cancel();
                    }
                }
            }

            // Старая логика кликов для FunTime
            if (mode.is("FunTime") && (isMoving() || mc.options.jumpKey.isPressed())) {
                packetQueue.add(packet);
                e.cancel();
            }
        }
    }

    @EventHandler
    public void onClose(ScreenCloseEvent e) {
        if (ReportHelper.isAutoListActive()) return;
        if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof GenericContainerScreen) {
            if (mode.is("FunTime")) {
                e.cancel();
                timer.reset();
                closeTime = System.currentTimeMillis() + 60;
                pendingClose = true;
            }
            if (mode.is("FunTime_2")) {
                e.cancel();
                timer.reset();
                closeTime = System.currentTimeMillis() + 50;
                pendingClose = true;
            }
        }
    }

    private void handleBypassMode() {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey
        };

        if (ReportHelper.isAutoListActive()) {
            setKey(false);
            return;
        }

        if (!timer.isReached(60)) {
            setKey(false);
            return;
        }

        if (mode.is("FunTime_2") && stoppedStatus) {
            setKey(false);
            return;
        }

        if (mc.currentScreen instanceof ChatScreen) {
            // В чате не прожимаем клавиши движения
        } else if (mc.currentScreen instanceof InventoryScreen ||
                mc.currentScreen instanceof TripleWheelScreen ||    mc.currentScreen instanceof Menu && !Menu.searchActive||
                mc.currentScreen instanceof TriplePickerScreen ||
                mc.currentScreen instanceof GenericContainerScreen) {

            updateKeyBindingState(movementKeys);
        }
    }

    private void updateKeyBindingState(KeyBinding[] keyBindings) {
        for (KeyBinding keyBinding : keyBindings) {
            int keyCode = keyBinding.getDefaultKey().getCode();
            boolean isKeyPressed = InputUtil.isKeyPressed(mc.getWindow(), keyCode);
            keyBinding.setPressed(isKeyPressed);
        }
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

    // Вспомогательные методы для логики FunTime_2
    private boolean canStoppingOnWindowClick() {
        return (isMoving() || mc.options.jumpKey.isPressed()) || this.stoppedStatus;
    }

    private void setStop(SlotActionType actionType) {
        this.stopTicksOut = this.ticksWindowClickOffset(actionType == null ? SlotActionType.PICKUP : actionType) + 1;
    }

    private int ticksWindowClickOffset(SlotActionType actionType) {
        return 1;
    }

    private void useAccumulatedPackets() {
        if (this.windowClickPacketQueue.isEmpty()) return;

        while (!windowClickPacketQueue.isEmpty()) {
            ClickSlotC2SPacket packet = windowClickPacketQueue.poll();
            if (packet != null) {
                sendingPackets.add(packet);
                mc.getNetworkHandler().getConnection().send(packet, null);
            }
        }
        this.ticksPostOnStop = 0;
    }

    @Override
    public void onDisable() {
        stop = false;
        pendingClose = false;
        this.useAccumulatedPackets();
        this.ticksPostOnStop = 1;
        packetQueue.clear();
        windowClickPacketQueue.clear();
        sendingPackets.clear();
        super.onDisable();
    }

    @Override
    public void onEnable() {
        this.useAccumulatedPackets();
        this.windowClickPacketQueue.clear();
        this.ticksPostOnStop = 1;
    }
}