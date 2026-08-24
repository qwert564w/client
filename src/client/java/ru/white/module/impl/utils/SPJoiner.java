package ru.white.module.impl.utils;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

@ModuleInfo(
        name = "SP Joiner",
        desc = "Автоматические заходит на дуэли SpookyTime",
        category = Category.OTHER
)
public class SPJoiner extends Module {



    @EventHandler
    public void onEvent(EventUpdate event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Screen screen = mc.currentScreen;

        if (mc.player.getInventory().getSelectedSlot() == 0 && mc.player.getMainHandStack().getItem() == Items.COMPASS) {
            this.toggle();
            return;
        }


        if (screen instanceof HandledScreen<?> handledScreen) {
            ScreenHandler handler = handledScreen.getScreenHandler();

            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                ItemStack stack = slot.getStack();

                if (stack.isOf(Items.NETHERITE_SWORD)) {
                    mc.interactionManager.clickSlot(
                            handler.syncId,
                            i,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                    );
                    return;
                }
            }
        } else {
            selectCompass();
        }
    }



    public static void selectCompass() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        PlayerInventory inv = mc.player.getInventory();
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.COMPASS)) {
                slot = i;
                break;
            }
        }

        if (slot == -1) return;

        inv.setSelectedSlot(slot);

        mc.getNetworkHandler().sendPacket(
                new UpdateSelectedSlotC2SPacket(slot)
        );

        mc.interactionManager.interactItem(
                mc.player,
                Hand.MAIN_HAND
        );
    }
}
