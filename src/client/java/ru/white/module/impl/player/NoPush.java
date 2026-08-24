package ru.white.module.impl.player;


import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.util.math.BlockPos;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "No Push",
        desc = "Не даёт другим игрокам и воде толкать вас",
        category = Category.PLAYER
)
public class NoPush extends Module {

    public static NoPush get() {
        return Instance.get(NoPush.class);
    }

    public BooleanSetting block = new BooleanSetting(this,"Блоки",true);

    public BooleanSetting entity = new BooleanSetting(this,"Сущности",true);

    public BooleanSetting xyiOblamova = new BooleanSetting(this,"Вода [пузырьки]",false);

    @EventHandler
    public void onEvent(MotionEvent event) {
if (mc.player != null && mc.world != null && mc.player.isSubmergedInWater() && xyiOblamova.getValue()) {


        BlockPos pos = mc.player.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);


        boolean isUpwardBubbles = state.getBlock() == Blocks.BUBBLE_COLUMN && !state.get(BubbleColumnBlock.DRAG);

      if (isUpwardBubbles) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
            boolean isSneaking = mc.options.sneakKey.isPressed();
            boolean isJumping = mc.options.jumpKey.isPressed();
            float motionSpeed = 0.6F;

            if (isSneaking) {
                mc.player.setVelocity(mc.player.getVelocity().x,-motionSpeed,mc.player.getVelocity().z);
            } else if (isJumping) {
                mc.player.setVelocity(mc.player.getVelocity().x,motionSpeed,mc.player.getVelocity().z);
            }
        }
    }
}


}
