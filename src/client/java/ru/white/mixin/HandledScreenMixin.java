package ru.white.mixin;

import ru.white.Client;
import ru.white.inventorypreset.InventoryPreset;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.module.impl.player.AuctionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    @Final
    protected ScreenHandler handler;

    @Shadow
    public abstract ScreenHandler getScreenHandler();

    @Unique @Mutable private Slot ahSlot1 = null;
    @Unique @Mutable private Slot ahSlot2 = null;
    @Unique @Mutable private Slot ahSlot3 = null;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        AuctionHelper mod = AuctionHelper.get();
        if (mod == null || !mod.isEnabled()) {
            ahSlot1 = ahSlot2 = ahSlot3 = null;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;

        String title = mc.currentScreen.getTitle().getString();
        if (!isAuctionScreen(title)) {
            ahSlot1 = ahSlot2 = ahSlot3 = null;
            return;
        }

        ahSlot1 = ahSlot2 = ahSlot3 = null;
        long p1 = Long.MAX_VALUE, p2 = Long.MAX_VALUE, p3 = Long.MAX_VALUE;

        for (Slot slot : getScreenHandler().slots) {
            if (!slot.hasStack()) continue;
            int total = AuctionHelper.getPrice(slot);
            if (total <= 0) continue;

            long perOne = (long) total / Math.max(slot.getStack().getCount(), 1);

            if (perOne < p1) {
                p3 = p2; ahSlot3 = ahSlot2;
                p2 = p1; ahSlot2 = ahSlot1;
                p1 = perOne; ahSlot1 = slot;
            } else if (perOne < p2) {
                p3 = p2; ahSlot3 = ahSlot2;
                p2 = perOne; ahSlot2 = slot;
            } else if (perOne < p3) {
                p3 = perOne; ahSlot3 = slot;
            }
        }
    }

    @Inject(
            method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At("HEAD")
    )
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AuctionHelper mod = AuctionHelper.get();
        if (mod == null || !mod.isEnabled()) return;

        if (slot == ahSlot1) mod.renderSlot(context, slot, 1);
        else if (slot == ahSlot2) mod.renderSlot(context, slot, 2);
        else if (slot == ahSlot3) mod.renderSlot(context, slot, 3);
    }

    /** Подсветка слотов активного пресета: зелёный — на месте, жёлтый — не тот слот, красный — нет предмета. */
    @Inject(
            method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At("TAIL")
    )
    private void onDrawPresetSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen) || Client.get() == null
                || Client.get().inventoryPresetManager() == null) return;

        InventoryPresetManager manager = Client.get().inventoryPresetManager();
        InventoryPreset preset = manager.activePreset().orElse(null);
        if (preset == null) return;

        int logical = InventoryPresetManager.screenToLogicalSlot(slot.id);
        if (logical < 0) return;

        InventoryPreset.Entry expected = preset.slot(logical);
        if (expected.isEmpty()) return;

        InventoryPresetManager.SlotState state = manager.stateFor(logical);
        int color = switch (state) {
            case CORRECT -> 0xFF42C96B;
            case WRONG_SLOT -> 0xFFE4B94F;
            case MISSING -> 0xFFE05C61;
            default -> 0;
        };

        if (state != InventoryPresetManager.SlotState.CORRECT) {
            ItemStack ghost = expected.toStack();
            if (!ghost.isEmpty()) {
                context.drawItem(ghost, slot.x, slot.y);
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x650A0A0A);
                context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, ghost, slot.x, slot.y);
            }
        }

        if (color != 0) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, color);
            context.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, color);
            context.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, color);
            context.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, color);
        }
    }

    @Unique
    private boolean isAuctionScreen(String title) {
        return title.contains("Аукцион") || title.contains("Auction")  || title.contains("Маркет") || title.contains("ꈁꀀꈂꌲꈂꀁ") || title.contains("Аукционы") || title.contains("Поиск:") || title.contains("Аукцион") || title.contains("Аукционы ") || title.equals("☃️ Аукцион") || title.startsWith("☃️ П:") || title.startsWith("☃️ Поиск:") || title.startsWith("0A2z") || title.contains("Поиск: ");
    }
}
