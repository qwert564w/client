package ru.white.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import ru.white.Client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique private List<String> nightix$suggestions = Collections.emptyList();
    @Unique private int nightix$selected = 0;

    // геометрия попапа (для кликов) — заполняется в render
    @Unique private int nightix$boxX, nightix$boxY, nightix$boxW, nightix$boxCount;
    @Unique private static final int NIGHTIX_LINE_H = 12;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void interceptMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.isEmpty()) return;

        char prefix = Client.get().commandManager().getPrefix();
        if (message.charAt(0) != prefix) return;
        ci.cancel();
        Client.get().commandManager().handleMessage(message);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void nightix$renderSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        nightix$boxCount = 0;

        String text = chatField.getText();
        char prefix = Client.get().commandManager().getPrefix();
        if (text.isEmpty() || text.charAt(0) != prefix) {
            nightix$suggestions = Collections.emptyList();
            return;
        }

        nightix$suggestions = Client.get().commandManager().getSuggestions(text);
        if (nightix$suggestions.isEmpty()) return;

        if (nightix$selected >= nightix$suggestions.size()) nightix$selected = 0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int count = Math.min(nightix$suggestions.size(), 10);

        int width = 0;
        for (int i = 0; i < count; i++) {
            width = Math.max(width, tr.getWidth(nightix$suggestions.get(i)));
        }
        width += 6;

        int boxH = count * NIGHTIX_LINE_H;
        int x = chatField.getX() - 2;
        int y = chatField.getY() - boxH - 1;

        nightix$boxX = x;
        nightix$boxY = y;
        nightix$boxW = width;
        nightix$boxCount = count;

        context.fill(x, y, x + width, y + boxH, 0xE6000000);
        context.fill(x, y, x + width, y + 1, 0x40FFFFFF);

        for (int i = 0; i < count; i++) {
            int ly = y + i * NIGHTIX_LINE_H;
            boolean sel = i == nightix$selected;
            if (sel) context.fill(x, ly, x + width, ly + NIGHTIX_LINE_H, 0x55FFFFFF);
            context.drawText(tr, nightix$suggestions.get(i), x + 3, ly + 2,
                    sel ? 0xFFFFFF55 : 0xFFBBBBBB, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void nightix$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (nightix$suggestions.isEmpty()) return;
        int n = nightix$suggestions.size();

        switch (input.getKeycode()) {
            case GLFW.GLFW_KEY_TAB -> {
                nightix$apply(nightix$suggestions.get(Math.min(nightix$selected, n - 1)));
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_UP -> {
                nightix$selected = (nightix$selected - 1 + n) % n;
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                nightix$selected = (nightix$selected + 1) % n;
                cir.setReturnValue(true);
            }
            default -> {}
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nightix$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {

        int button = click.button();

        double mouseX = click.x();
        double mouseY = click.y();

        if (nightix$suggestions.isEmpty() || nightix$boxCount == 0 || button != 0) return;

        if (mouseX >= nightix$boxX && mouseX <= nightix$boxX + nightix$boxW
                && mouseY >= nightix$boxY && mouseY <= nightix$boxY + nightix$boxCount * NIGHTIX_LINE_H) {
            int idx = (int) ((mouseY - nightix$boxY) / NIGHTIX_LINE_H);
            if (idx >= 0 && idx < nightix$boxCount && idx < nightix$suggestions.size()) {
                nightix$apply(nightix$suggestions.get(idx));
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void nightix$apply(String suggestion) {
        chatField.setText(suggestion);
        chatField.setCursorToEnd(false);
        nightix$selected = 0;
    }
}
