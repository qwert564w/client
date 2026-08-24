package ru.white.module.impl.utils;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
        name = "Glass Farmer",
        desc = "Легитный фарм стекла: сначала наводится, затем с задержкой строит/ломает центр",
        category = Category.OTHER
)
public class GlassFarmer extends Module {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private BlockPos startPos = null;
    private State currentState = State.BUILDING;
    private int currentX = -1;
    private int currentY = 0;
    private int currentZ = -1;

    private long actionTimer = 0L;
    private boolean isRotated = false;
    private BlockPos lastTargetPos = null;


    private final long clickDelayMs = 35L;
    private final int buildDelayTicks = 3;
    private int buildTickCounter = 0;

    private enum State {
        BUILDING,
        DIGGING
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            this.toggle();
            return;
        }


        startPos = mc.player.getBlockPos();
        currentState = State.BUILDING;
        currentX = -1;
        currentY = 0;
        currentZ = -1;
        buildTickCounter = 0;
        actionTimer = 0L;
        isRotated = false;
        lastTargetPos = null;
    }

    @Override
    public void onDisable() {
        startPos = null;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        lastTargetPos = null;
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        if (currentState == State.BUILDING) {
            mc.options.attackKey.setPressed(false);


            if (buildTickCounter < buildDelayTicks) {
                buildTickCounter++;
                return;
            }
            doBuildingLogic();
        } else if (currentState == State.DIGGING) {
            mc.options.useKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);

            doDiggingLogic();
        }
    }

    private void doBuildingLogic() {
        int sandSlot = findItemInHotbar(Items.SAND);

        if (sandSlot == -1) {
            currentY--;
            currentState = State.DIGGING;
            return;
        }

        BlockPos targetPos = startPos.add(currentX, currentY, currentZ);

        if (mc.world.getBlockState(targetPos).getBlock() == Blocks.SAND) {
            advanceBuildPosition();
            resetActionState();
            return;
        }

        // Если нужно прыгнуть под себя
        if (currentX == 0 && currentZ == 0 && mc.player.getBlockPos().getY() <= targetPos.getY()) {
            if (mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
                return;
            } else {
                mc.options.jumpKey.setPressed(false);
            }
        }


        mc.player.getInventory().setSelectedSlot(sandSlot);
        lookAt(targetPos);


        if (!isRotated || !targetPos.equals(lastTargetPos)) {
            actionTimer = System.currentTimeMillis();
            isRotated = true;
            lastTargetPos = targetPos;
            return;
        }


        if (System.currentTimeMillis() - actionTimer < clickDelayMs) {
            return;
        }


        BlockHitResult hitResult = new BlockHitResult(
                new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5),
                Direction.UP,
                targetPos,
                false
        );

        mc.options.useKey.setPressed(true);

        buildTickCounter = 0;
        advanceBuildPosition();
        resetActionState();
    }

    private void doDiggingLogic() {
        int shovelSlot = findShovelInHotbar();
        if (shovelSlot == -1) {
            this.toggle();
            return;
        }

        BlockPos targetPos = startPos.add(0, currentY, 0);

        if (mc.world.getBlockState(targetPos).getBlock() == Blocks.SAND) {

            mc.player.getInventory().setSelectedSlot(shovelSlot);
            lookAt(targetPos);

            if (!isRotated || !targetPos.equals(lastTargetPos)) {
                actionTimer = System.currentTimeMillis();
                isRotated = true;
                lastTargetPos = targetPos;
                return; // Ждем доводки головы
            }

            // 2. Легитная микрозадержка перед началом ломания
            if (System.currentTimeMillis() - actionTimer < clickDelayMs) {
                return;
            }


            if (mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP)) {

                if(!mc.options.attackKey.isPressed()) {
                    mc.options.attackKey.setPressed(true);
                }

                resetActionState();
            }
        } else {
            currentY--;
            resetActionState();

            if (currentY < 0) {
                this.toggle();
            }
        }
    }

    private void resetActionState() {
        isRotated = false;
        actionTimer = 0L;
    }

    private void advanceBuildPosition() {
        currentX++;
        if (currentX > 1) {
            currentX = -1;
            currentZ++;
            if (currentZ > 1) {
                currentZ = -1;
                currentY++;
            }
        }
    }

    private int findItemInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private int findShovelInHotbar() {
        for (int i = 0; i < 9; i++) {
            String name = mc.player.getInventory().getStack(i).getItem().getTranslationKey();
            if (name.contains("shovel")) {
                return i;
            }
        }
        return -1;
    }

    private void lookAt(BlockPos pos) {
        double diffX = pos.getX() + 0.5 - mc.player.getX();
        double diffY = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double diffZ = pos.getZ() + 0.5 - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));

        // Используем твой RotationProcess.
        // Параметры скорости yaw/pitch (180, 180) можно снизить (например до 80, 80), чтобы голова двигалась еще плавнее и легитнее.
        RotationProcess.update(new Rotation(yaw, pitch), 140, 140, 6, 1);
    }
}