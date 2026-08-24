package ru.white.module.impl.utils;


import ru.white.Client;

import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.math.StopGPT;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(name = "Chest Stealer",desc = "Данный модуль был сделан акарачком поэтому может работать через жопу", category = Category.OTHER)
public class ChestStealer extends Module {

    public final ModeSetting mode = new ModeSetting(this, "Режим", "Normal", "Warden", "Danj");
    public final BooleanSetting autoClan = new BooleanSetting(this, "AutoClanStorage", false);
    public final BooleanSetting autoEc = new BooleanSetting(this, "Авто /ec", false);
    public final BooleanSetting closeEmpty = new BooleanSetting(this, "Закрывать пустой", true);
    public final BooleanSetting autoOpen = new BooleanSetting(this, "AutoOpen", true);

    private final Set<BlockPos> openedChests = new HashSet<>();
    private final StopGPT timer = new StopGPT();
    private final StopGPT openTimer = new StopGPT();
    private final StopGPT ecTimer = new StopGPT();
    private final StopGPT closeTimer = new StopGPT();
    private long nextDelay = 0;
    private BlockPos lastOpenedChest = null;
    private boolean waitingForLoot = false;

    private enum EcState {
        IDLE, WAITING_EC, MOVING_TO_EC, CLOSING_EC
    }

    private EcState ecState = EcState.IDLE;

    @Override
    public void onEnable() {
        super.onEnable();
        openedChests.clear();
        timer.reset();
        openTimer.reset();
        ecState = EcState.IDLE;
        closeTimer.reset();
        lastOpenedChest = null;
        waitingForLoot = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        openedChests.clear();
        ecState = EcState.IDLE;
        lastOpenedChest = null;
        waitingForLoot = false;
    }

    @EventHandler
    public void onLoadWorld(WorldLoadEvent event) {
        openedChests.clear();
        openTimer.reset();
        timer.reset();
    }

    private String getContainerTitle() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            return screen.getTitle().getString();
        }
        return "";
    }

    private boolean isValidContainer() {
        String title = getContainerTitle();
        if (mode.is("Warden")) {
            return title.contains("Сундук");
        } else if (mode.is("Danj")) {
            return title.contains("Бочка");
        }
        return true;
    }

    private boolean isValidStorageContainer() {
        String title = getContainerTitle();
        if (autoClan.getValue()) {
            return title.contains("Клан: Хранилище");
        }
        return true;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null)
            return;

        // === ЛОГИКА ПОСТОЯННОГО ОТКРЫТИЯ/ЗАКРЫТИЯ ДЛЯ WARDEN/DUNGEON ===
        // Когда таймер = 0:00 и сундук пуст или есть таймер - постоянно открывать/закрывать
        if ((mode.is("Warden") || mode.is("Danj")) && !autoOpen.getValue() &&
                (mc.currentScreen instanceof GenericContainerScreen screen)) {

            if (closeTimer.hasTimePassed(500)) {
                String title = screen.getTitle().getString();
                // Если таймер 0:00 или сундук пустой
                boolean hasZeroTimer = title.contains("0:00") || title.contains("0.00");
                boolean isContainerEmpty = isContainerEmpty(screen);

                if (hasZeroTimer || (isContainerEmpty && waitingForLoot)) {
                    mc.player.closeHandledScreen();
                    closeTimer.reset();
                    waitingForLoot = true;
                    return;
                }

                // Если залутали - перестаём ждать
                if (!isContainerEmpty) {
                    waitingForLoot = false;
                }
            }
        }

        if (autoClan.getValue() && autoEc.getValue()) {
            autoEc.set(false);
        }

        if (ecState != EcState.IDLE) {
            handleEcState();
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            nextDelay = 0;
            return;
        }

        AutoStorage autoStorage = Client.get().moduleManager().get(AutoStorage.class);
        if (autoStorage != null && autoStorage.isWorking()) {
            return;
        }

        if (mode.is("Warden") || mode.is("Danj")) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            boolean inXZ = Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
            if (!inXZ) {
                return;
            }

            if (!isValidContainer()) {
                return;
            }

            if (screen.getScreenHandler().getRows() == 6) {
                return;
            }
        }

        if (!timer.hasTimePassed(nextDelay)) {
            return;
        }

        processChest(screen);
    }

    private void processChest(GenericContainerScreen screen) {
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;

        boolean foundItemToSteal = false;
        boolean chestIsEmpty = true;

        for (int i = 0; i < containerSize; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (slot.hasStack()) {
                chestIsEmpty = false;

                boolean shouldSteal = true;
                if (mode.is("Warden") || mode.is("Danj")) {
                    shouldSteal = isValuableItem(slot.getStack());
                }

                if (shouldSteal) {
                    foundItemToSteal = true;
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE,
                            mc.player);
                    timer.reset();
                    nextDelay = 0;
                    return;
                }
            }
        }

        if (!foundItemToSteal && (chestIsEmpty || closeEmpty.getValue())) {
            mc.player.closeHandledScreen();

            if (autoEc.getValue()) {
                ecState = EcState.WAITING_EC;
                ecTimer.reset();
                mc.player.networkHandler.sendChatCommand("ec");
            } else if (autoClan.getValue() && !ServerUtil.isPvp()) {
                AutoStorage storage = Client.get().moduleManager().get(AutoStorage.class);
                if (storage != null) {
                    if (!storage.isEnabled()) {
                        storage.setEnabled(true);
                    }
                    storage.trigger();
                    storage.triggeredByChestStealer = true;
                }
            }
        }
    }

    private void handleEcState() {
        switch (ecState) {
            case WAITING_EC -> {
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    ecState = EcState.MOVING_TO_EC;
                    ecTimer.reset();
                    nextDelay = (long) MathUtil.random(120, 150);
                } else if (ecTimer.hasTimePassed(5000)) {
                    ecState = EcState.IDLE;
                }
            }
            case MOVING_TO_EC -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    ecState = EcState.IDLE;
                    return;
                }
                if (!ecTimer.hasTimePassed(nextDelay))
                    return;

                int rows = screen.getScreenHandler().getRows();
                int containerSize = rows * 9;
                int totalSlots = screen.getScreenHandler().slots.size();

                boolean moved = false;
                for (int i = containerSize; i < totalSlots; i++) {
                    Slot slot = screen.getScreenHandler().getSlot(i);
                    if (slot.hasStack() && isValuableItem(slot.getStack())) {
                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0,
                                SlotActionType.QUICK_MOVE, mc.player);
                        nextDelay = (long) MathUtil.random(120, 150);
                        ecTimer.reset();
                        moved = true;
                        return;
                    }
                }
                if (!moved) {
                    ecState = EcState.CLOSING_EC;
                }
            }
            case CLOSING_EC -> {
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                }
                ecState = EcState.IDLE;
            }
        }
    }

    public boolean isValuableItem(ItemStack stack) {
        Item item = stack.getItem();
        String name = stack.getName().getString();

        if (item == Items.ARROW || name.contains("Стрела"))
            return false;

        if (name.contains("Хлопушка"))
            return false;

        if (name.contains("★")) {
            return true;
        }

        return item == Items.SPLASH_POTION ||
                item == Items.DRAGON_HEAD ||
                item == Items.VILLAGER_SPAWN_EGG ||
                item == Items.ZOMBIE_VILLAGER_SPAWN_EGG ||
                item == Items.DIAMOND ||
                item == Items.NETHERITE_SCRAP ||
                item == Items.NETHERITE_INGOT ||
                item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE ||
                item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.EMERALD_ORE ||
                item == Items.ENDER_EYE ||
                item == Items.TNT ||
                item == Items.NETHER_STAR ||
                item == Items.GOLDEN_APPLE ||
                item == Items.WITHER_SKELETON_SKULL ||
                item == Items.DIAMOND_BLOCK ||
                item == Items.NETHERITE_SWORD ||
                item == Items.NETHERITE_HELMET ||
                item == Items.NETHERITE_LEGGINGS ||
                item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_BOOTS ||
                item == Items.TRIPWIRE_HOOK ||
                item == Items.BEACON ||
                item == Items.SNOWBALL ||
                item == Items.IRON_NUGGET ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.ELYTRA ||
                item == Items.REINFORCED_DEEPSLATE;
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null)
            return;
        if (!mode.is("Warden") && !mode.is("Danj"))
            return;
        if (!autoOpen.getValue())
            return;
        if (mc.currentScreen instanceof GenericContainerScreen)
            return;

        double x = mc.player.getX();
        double z = mc.player.getZ();
        boolean inXZ = Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
        if (!inXZ) {
            return;
        }

        BlockPos target = findNearestChest();
        if (target != null) {
            float[] rots = getRotations(target);

            float newYaw = smoothRotation(mc.player.getYaw(), rots[0], 15.0f);
            float newPitch = smoothRotation(mc.player.getPitch(), rots[1], 15.0f);

            RotationProcess.update(new Rotation(newYaw, newPitch), 400, 400, 1, 1);

            if (openTimer.hasTimePassed(200)) {
                double maxDistance = 16.0;
                if (mc.player.squaredDistanceTo(target.toCenterPos()) < maxDistance) {
                    BlockHitResult hit = new BlockHitResult(target.toCenterPos(), Direction.UP, target, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    openedChests.add(target);
                    openTimer.reset();
                }
            }
        }
    }

    private BlockPos findNearestChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = 36.0;

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    boolean isTargetBlock = false;
                    if (mode.is("Warden")) {
                        isTargetBlock = mc.world.getBlockState(pos).getBlock() == Blocks.CHEST
                                || mc.world.getBlockState(pos).getBlock() == Blocks.TRAPPED_CHEST;
                    } else if (mode.is("Danj")) {
                        isTargetBlock = mc.world.getBlockState(pos).getBlock() == Blocks.BARREL;
                    } else {
                        isTargetBlock = mc.world.getBlockState(pos).getBlock() == Blocks.CHEST
                                || mc.world.getBlockState(pos).getBlock() == Blocks.TRAPPED_CHEST
                                || mc.world.getBlockState(pos).getBlock() == Blocks.BARREL;
                    }

                    if (isTargetBlock) {
                        double dist = pos.toCenterPos().squaredDistanceTo(mc.player.getEntityPos());
                        if (dist > 36.0)
                            continue;

                        if (openedChests.contains(pos))
                            continue;

                        if (hasTimerHologram(pos))
                            continue;

                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isContainerEmpty(GenericContainerScreen screen) {
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        for (int i = 0; i < containerSize; i++) {
            if (screen.getScreenHandler().getSlot(i).hasStack()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasTimerHologram(BlockPos pos) {
        Box box = new Box(pos).expand(0, 2, 0);
        List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(ArmorStandEntity.class, box, val -> true);
        for (ArmorStandEntity stand : stands) {
            if (stand.hasCustomName()) {
                String name = stand.getCustomName().getString();
                if (name.matches(".*\\d.*") || name.contains(":")) {
                    return true;
                }
            }
        }
        return false;
    }

    private float[] getRotations(BlockPos pos) {
        Vec3d eyesPos = mc.player.getEyePos();
        Vec3d targetPos = pos.toCenterPos();
        double dX = targetPos.x - eyesPos.x;
        double dY = targetPos.y - eyesPos.y;
        double dZ = targetPos.z - eyesPos.z;
        double dist = Math.sqrt(dX * dX + dZ * dZ);

        float yaw = (float) (Math.atan2(dZ, dX) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dY, dist) * 180.0 / Math.PI));
        return new float[] { yaw, pitch };
    }

    private float smoothRotation(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > maxStep)
            delta = maxStep;
        if (delta < -maxStep)
            delta = -maxStep;
        return current + delta;
    }
}
