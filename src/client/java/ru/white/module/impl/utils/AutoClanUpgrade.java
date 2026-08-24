package ru.white.module.impl.utils;


import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.InvUtil;
import ru.white.utils.other.TimerUtil;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@ModuleInfo(
        name = "Auto Clan Upgrade",
        desc = "Автоматическая прокачка клана через факела",
        category = Category.OTHER
)
public class AutoClanUpgrade extends Module {

    private final TimerUtil timer = new TimerUtil();

    private boolean slowBreak = false;

    @EventHandler
    public void onUpdate(EventUpdate event) {

        int slot = InvUtil.getItemInHotBar(Items.TORCH);

        if (mc.world.getRegistryKey().getValue().getPath().equals("lobby")) {
            if (mc.player.age % 200 == 0) {
                ChatUtils.addChatMessage("Тепнитесь на РТП");
            }
            return;
        }

        if (slot == -1) {
            if (mc.player.age % 200 == 0) {
                ChatUtils.addChatMessage("Нужен факел в хотбаре");
            }
            return;
        }

        BlockPos pos = mc.player.getBlockPos().down();
        BlockPos torchPos = pos.up();

        if (!mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)) return;


        if (mc.player.age % 1 == 0) {
            float yaw = 25;
            float pitch = 87.5f ;

            RotationProcess.update(new Rotation(yaw, pitch), 255, 255, 0, 100);
        }


        int delay = 5;
        if (!timer.hasReached(delay)) return;
        timer.reset();



        if (mc.world.getBlockState(torchPos).isAir()) {

            mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    new BlockHitResult(
                            pos.toCenterPos(),
                            Direction.UP,
                            pos,
                            false
                    )
            );

        }

        if (mc.world.getBlockState(torchPos).getBlock() == Blocks.TORCH) {
            mc.interactionManager.attackBlock(torchPos, Direction.WEST);
            mc.player.swingHand(Hand.MAIN_HAND);

        }


    }
}