package ru.white.module.impl.combat;

import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static ru.white.utils.annotation.IMinecraft.mc;

/**
 * Compact item-picker: shows only the unique item types the player currently has
 * in their inventory. Opens when the player clicks an empty wheel slot.
 */
public class TriplePickerScreen extends Screen {

    private float scaleFix = 1F;

    private final AutoSwap module;
    private final int      slotIndex;
    /** Screen to return to when this picker closes (null → return to game). */
    private final Screen   parent;

    // Layout
    private static final float CELL_SIZE = 20F;
    private static final float CELL_GAP  = 4F;
    private static final float PAD       = 5F;
    private static final float TITLE_H   = 14f;
    private static final float BTN_H     = 14f;
    private static final float BTN_W     = 44f;
    private static final int   MAX_COLS  = 7;


    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    /** All non-empty item stacks found in inventory this frame. */
    private final List<ItemStack> items = new ArrayList<>();

    public TriplePickerScreen(AutoSwap module, int slotIndex) {
        this(module, slotIndex, null);
    }

    public TriplePickerScreen(AutoSwap module, int slotIndex, Screen parent) {
        super(Text.literal("Выбор предмета"));
        this.module    = module;
        this.slotIndex = slotIndex;
        this.parent    = parent;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void refreshItems() {
        items.clear();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty()) items.add(s);
        }
    }

    private int cols() { return Math.min(MAX_COLS, Math.max(1, items.size())); }
    private int rows() { return items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / cols()); }

    private float gridW() { return cols() * CELL_SIZE + (cols() - 1) * CELL_GAP; }
    private float gridH() { return rows() * CELL_SIZE + Math.max(0, rows() - 1) * CELL_GAP; }

    private float panelW() { return Math.max(gridW(), BTN_W) + PAD * 2; }
    private float panelH() { return PAD + TITLE_H + 4 + (items.isEmpty() ? 20 : gridH()) + 6 + BTN_H + PAD; }
    private float panelX() { return (width  / scaleFix - panelW()) / 2f; }
    private float panelY() { return (height / scaleFix - panelH()) / 2f; }

    private float gridX() { return panelX() + (panelW() - gridW()) / 2f; }
    private float gridY() { return panelY() + PAD + TITLE_H + 4; }

    /** Screen-space rect for a cell at linear index i. */
    private float cellX(int i) { return gridX() + (i % cols()) * (CELL_SIZE + CELL_GAP); }
    private float cellY(int i) { return gridY() + (i / cols()) * (CELL_SIZE + CELL_GAP); }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        scaleFix = 2F / (float) mc.getWindow().getScaleFactor();
        mouseX = (int)(mouseX / scaleFix);
        mouseY = (int)(mouseY / scaleFix);
        refreshItems();


        float px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        RenderUtil.Blur.blur(px, py, pw, ph,1,8,0);
        RenderUtil.Render2D.rect(px, py, pw, ph, ColorUtil.getColor(0, 0.7f), 8f);

        String title = "Слот " + (slotIndex + 1) + " — выберите предмет";
        float tw = Fonts.sf_regular.getWidth(title, 7);
        Fonts.sf_regular.draw(title, px + (pw - tw) / 2f, py + PAD + 2, 7, ColorUtil.getColor(255, 1F));

        // Items grid
        if (items.isEmpty()) {
            String empty = "Инвентарь пуст";
            float ew = Fonts.sf_regular.getWidth(empty, 7);
            Fonts.sf_regular.draw(empty, px + (pw - ew) / 2f, gridY() + 4, 7, ColorUtil.getColor(255, 1F));
        } else {
            ItemStack selectedItem = module.tripleSlotItems[slotIndex];

            for (int idx = 0; idx < items.size(); idx++) {
                ItemStack stack = items.get(idx);
                float cx    = cellX(idx);
                float cy    = cellY(idx);
                boolean hover    = mouseX >= cx && mouseX <= cx + CELL_SIZE
                                && mouseY >= cy && mouseY <= cy + CELL_SIZE;
                boolean selected = selectedItem != null && ItemStack.areItemsAndComponentsEqual(stack, selectedItem);


                int bg = selected ? ColorUtil.getColor(255, 255, 255, 0.15f)
                       : hover    ? ColorUtil.getColor(255, 255, 255, 0.12f)
                                  : ColorUtil.getColor(255, 255, 255, 0.05f);
                RenderUtil.Render2D.rect(cx, cy, CELL_SIZE, CELL_SIZE, bg, 4f);

                if (selected || hover) {
                    RenderUtil.Render2D.outline(cx, cy, CELL_SIZE, CELL_SIZE,
                            selected ? 0.6f : 0.5f,
                            ColorUtil.getColor(255, 255, 255, selected ? 0.3f : 0.2f), 4f);
                }

                ItemRender.drawItemCenteredWithContext(context, stack,
                        (cx + CELL_SIZE / 2f) * scaleFix, (cy + CELL_SIZE / 2f) * scaleFix, 0.85f, 1.0f);

                // Tooltip: item name below the grid row when hovered
                if (hover) {
                    String name = stack.getName().getString();
                    float nw = Fonts.sf_bold.getWidth(name, 7);
                    Fonts.sf_bold.draw(name,
                            px + (pw - nw) / 2f, gridY() + gridH() + 30F,
                            7, ColorUtil.getColor(255, 1F));
                }
            }
        }

        // Clear button
        float btnX = px + (pw - BTN_W) / 2f;
        float btnY = gridY() + (items.isEmpty() ? 20 : gridH()) + 6;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + BTN_W
                      && mouseY >= btnY && mouseY <= btnY + BTN_H;

        RenderUtil.Render2D.rect(btnX, btnY, BTN_W, BTN_H,
                ColorUtil.getColor(255, 255, 255, btnHov ? 0.12f : 0.06f), 4f);
        RenderUtil.Render2D.outline(btnX, btnY, BTN_W, BTN_H, 0.6f,
                ColorUtil.getColor(255, 255, 255, btnHov ? 0.30f : 0.10f), 4f);
        float clw = Fonts.sf_regular.getWidth("Сброс", 6f);
        Fonts.sf_regular.draw("Сброс", btnX + (BTN_W - clw) / 2f, btnY + 3.5f,
                6f, ColorUtil.getColor(255, btnHov ? 0.90f : 0.45f));

        super.render(context, mouseX, mouseY, delta);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x() / scaleFix;
        double mouseY = click.y() / scaleFix;

        if (click.button() != 0) return super.mouseClicked(click, doubled);

        float px = panelX(), py = panelY();

        // Item cells
        for (int idx = 0; idx < items.size(); idx++) {
            float cx = cellX(idx);
            float cy = cellY(idx);
            if (mouseX >= cx && mouseX <= cx + CELL_SIZE
             && mouseY >= cy && mouseY <= cy + CELL_SIZE) {
                module.tripleSlotItems[slotIndex] = items.get(idx).copy();
                this.close();
                return true;
            }
        }

        // Clear button
        float btnX = px + (panelW() - BTN_W) / 2f;
        float btnY = gridY() + (items.isEmpty() ? 20 : gridH()) + 6;
        if (mouseX >= btnX && mouseX <= btnX + BTN_W
         && mouseY >= btnY && mouseY <= btnY + BTN_H) {
            module.tripleSlotItems[slotIndex] = null;
            this.close();
            return true;
        }

        // Click outside panel → close
        if (mouseX < px || mouseX > px + panelW()
         || mouseY < py || mouseY > py + panelH()) {
            this.close();
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        // Return to the wheel screen (or close completely if no parent)
        mc.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
