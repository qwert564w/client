package ru.white.module.impl.combat;

import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import static ru.white.utils.annotation.IMinecraft.mc;

public class TripleWheelScreen extends Screen {

    private final AutoSwap module;

    public   float  OUTER_R = 100;
    public   float  INNER_R = 60;
    public float  MID_R   = (OUTER_R + INNER_R) / 2f;
    public double GAP     = Math.toRadians(1);
    public double STEP    = Math.toRadians(0.5); // 1° per slice = smooth

    private static final double[] CENTERS = {
        -Math.PI / 2,
         Math.PI / 6,
         5 * Math.PI / 6
    };

    public static ru.white.utils.animation.satoshi.Animation alpha = new EaseInOutQuad(250, 1);

    private float scaleFix = 1F;
    private boolean exit        = false;
    private int     hoveredSlot = -1;
    private long openTime;
    private boolean selectionAnimating;
    private boolean closingAfterSelection;
    private int selectionSlot = -1;
    private Runnable selectionAction;
    private Runnable closeAction;

    private final Animation selectionAnimation = new Animation();
    private final float[] hoverProgress = new float[3];
    private final float[] pressProgress = new float[3];

    public TripleWheelScreen(AutoSwap module) {
        super(Text.literal("Auto Swap"));
        this.module = module;
    }

    @Override
    protected void init() {
        exit = false;
        openTime = System.currentTimeMillis();
        selectionAnimating = false;
        closingAfterSelection = false;
        selectionSlot = -1;
        selectionAction = null;
        closeAction = null;
        selectionAnimation.set(0);
        for (int i = 0; i < 3; i++) {
            hoverProgress[i] = 0f;
            pressProgress[i] = 0f;
        }
        alpha.setDuration(300);
        alpha.reset();
        alpha.setDirection(Direction.FORWARDS);
    }

    private float cx() { return width  / 2f; }
    private float cy() { return height / 2f; }

    public int getHoveredSlot() { return hoveredSlot; }

    public boolean isSelectionAnimating() {
        return selectionAnimating || closingAfterSelection || exit;
    }

    public void startSelectionAnimation(int slot, Runnable action) {
        if (selectionAnimating || closingAfterSelection || exit) return;
        selectionAnimating = true;
        selectionSlot = slot;
        selectionAction = action;
        closeAction = action;
        selectionAnimation.set(0);
        selectionAnimation.run(1, 0.55, Easings.SINE_OUT);
    }

    public void startCloseAnimation(Runnable action) {
        if (closingAfterSelection || exit) return;
        closeAction = action;
        closingAfterSelection = true;
        alpha.setDuration(700);
        alpha.setDirection(Direction.BACKWARDS);
    }

    private int sectorForAngle(double angle) {
        double half = Math.PI / 3 - GAP;
        for (int i = 0; i < 3; i++) {
            double diff = angle - CENTERS[i];
            while (diff >  Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;
            if (Math.abs(diff) <= half) return i;
        }
        return -1;
    }

    // Sector selected purely by angle — works from anywhere on screen
    private int computeHoveredSlot(double mx, double my) {
        double dx = mx - cx();
        double dy = my - cy();
        if (Math.hypot(dx, dy) < 12 * scaleFix) return -1; // tiny dead zone at exact center
        return sectorForAngle(Math.atan2(dy, dx));
    }

    private boolean isInHand(int i) {
        if (mc.player == null) return false;
        ItemStack cfg = module.tripleSlotItems[i];
        if (cfg == null || cfg.isEmpty()) return false;
        ItemStack offhand = mc.player.getOffHandStack();
        return !offhand.isEmpty() && offhand.getItem() == cfg.getItem();
    }

    private static int argb(int r, int g, int b, float a) {
        return (Math.round(clamp(a, 0f, 1f) * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothOut(float value) {
        value = clamp(value, 0f, 1f);
        return 1f - (float) Math.pow(1f - value, 3);
    }

    private float openProgress(int slot) {
        if (!module.isTripleAnimationsEnabled()) return 1f;
        float part = (System.currentTimeMillis() - openTime - slot * 85L) / 520f;
        return smoothOut(part);
    }

    private static float approach(float value, float target, float speed) {
        if (Math.abs(value - target) < 0.001f) return target;
        return value + (target - value) * speed;
    }

    // Draw one arc sector as a strip of rotated thin rects via Matrix3x2fStack
    private void drawSector(DrawContext ctx, float cx, float cy,
                             float innerR, float outerR,
                             double startAngle, double endAngle,
                             int color) {
        Matrix3x2fStack ms = ctx.getMatrices();
        double range = endAngle - startAngle;
        int steps = Math.max(1, (int) Math.ceil(range / STEP));
        double actualStep = range / steps;
        int hw = Math.max(1, (int)(outerR * Math.sin(actualStep / 2) + 1.5));

        for (int i = 0; i < steps; i++) {
            double mid = startAngle + (i + 0.5) * actualStep;
            ms.pushMatrix();
            ms.translate(cx, cy);
            ms.rotate((float) mid);
            ctx.fill((int) innerR, -hw, (int) outerR + 1, hw + 1, color);
            ms.popMatrix();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        scaleFix = 2F / (float) mc.getWindow().getScaleFactor();
        hoveredSlot = computeHoveredSlot(mouseX, mouseY);

        if ((exit || closingAfterSelection) && alpha.isDone()) {
            Runnable action = closeAction;
            closeAction = null;
            exit = false;
            closingAfterSelection = false;
            mc.setScreen(null);
            if (action != null) action.run();
            return;
        }

        float a  = alpha.getOutput();
        float cx = cx(), cy = cy();
        boolean animations = module.isTripleAnimationsEnabled();

        if (selectionAnimating) {
            selectionAnimation.update();
            if (selectionAnimation.isFinished()) {
                selectionAnimating = false;
                selectionAction = null;
                closingAfterSelection = true;
                alpha.setDuration(700);
                alpha.setDirection(Direction.BACKWARDS);
            }
        }
        float selectionProgress = selectionAnimating || closingAfterSelection
                ? MathHelper.clamp(selectionAnimation.get(), 0f, 1f)
                : 0f;

        for (int i = 0; i < 3; i++) {
            boolean hov = hoveredSlot == i && !closingAfterSelection && !exit;
            boolean pressed = (selectionAnimating || closingAfterSelection) && selectionSlot == i;
            hoverProgress[i] = approach(hoverProgress[i], hov ? 1f : 0f, 0.08f);
            pressProgress[i] = approach(pressProgress[i], pressed ? 1f : 0f, 0.08f);
        }

        for (int order = 0; order < 3; order++) {
            int i = order;
            if (hoveredSlot >= 0) {
                if (order == 2) {
                    i = hoveredSlot;
                } else {
                    int n = 0;
                    for (int candidate = 0; candidate < 3; candidate++) {
                        if (candidate == hoveredSlot) continue;
                        if (n == order) {
                            i = candidate;
                            break;
                        }
                        n++;
                    }
                }
            }

            double half  = Math.PI / 3 - GAP;
            double start = CENTERS[i] - half;
            double end   = CENTERS[i] + half;

            boolean inHand = isInHand(i);
            boolean dimmedBySelection = (selectionAnimating || closingAfterSelection) && selectionSlot != i;

            float open = openProgress(i);
            float hp = animations ? hoverProgress[i] : (hoveredSlot == i ? 1f : 0f);
            float pp = animations ? pressProgress[i] : 0f;
            float selectFade = dimmedBySelection ? 1f - 0.82f * selectionProgress : 1f;
            float visible = a * open * selectFade;
            float openScale = animations ? (0.42f + open * 0.58f) : 1f;
            float innerR = (INNER_R * openScale - hp * 3f - pp * 5f) * scaleFix;
            float outerR = (OUTER_R * openScale + hp * 5f + pp * 8f) * scaleFix;
            float midR = (innerR + outerR) / 2f;
            float shift = ((animations ? (1f - open) * -16f : 0f) + hp * 3f + pp * 5f) * scaleFix;
            float sectorCx = cx + (float) (Math.cos(CENTERS[i]) * shift);
            float sectorCy = cy + (float) (Math.sin(CENTERS[i]) * shift);

            float baseR = inHand ? 210f : 150f;
            float baseG = inHand ? 50f : 150f;
            float baseB = inHand ? 40f : 150f;
            float baseA = inHand ? 0.55f : 0.20f;
            float hoverR = 210f, hoverG = 195f, hoverB = 125f, hoverA = 0.38f;
            float pressR = 238f, pressG = 210f, pressB = 85f, pressA = 0.48f;

            float colorR = baseR + (hoverR - baseR) * hp;
            float colorG = baseG + (hoverG - baseG) * hp;
            float colorB = baseB + (hoverB - baseB) * hp;
            float colorA = baseA + (hoverA - baseA) * hp;
            colorR += (pressR - colorR) * pp;
            colorG += (pressG - colorG) * pp;
            colorB += (pressB - colorB) * pp;
            colorA += (pressA - colorA) * pp;

            int color = argb(Math.round(colorR), Math.round(colorG), Math.round(colorB), colorA * visible);

            drawSector(context, sectorCx, sectorCy, innerR, outerR, start, end, color);

            ItemStack item = module.tripleSlotItems[i];
            if (item != null && !item.isEmpty()) {
                float iconX = sectorCx + (float)(midR * Math.cos(CENTERS[i]));
                float iconY = sectorCy + (float)(midR * Math.sin(CENTERS[i]));
                float iconScale = (0.82f + hp * 0.08f + pp * 0.12f) * open;
                ItemRender.drawItemCenteredWithContext(context, item, iconX, iconY, iconScale, visible);
            }
        }

        String hint;
        if (hoveredSlot >= 0) {
            ItemStack item = module.tripleSlotItems[hoveredSlot];
            if (item != null && !item.isEmpty())
                hint = item.getName().getString() + "  —  отпусти для свапа  |  ПКМ — изменить";
            else
                hint = "Пусто  —  ПКМ чтобы назначить предмет";
        } else {
            hint = "Двигай мышку в нужную сторону  |  ПКМ — назначить";
        }
        Fonts.sf_regular.drawCentered(hint, cx / scaleFix, cy / scaleFix + OUTER_R + 16f, 8.5f,
                argb(255, 255, 255, 0.55f * a));

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 1) {
            int slot = computeHoveredSlot(click.x(), click.y());
            if (slot >= 0) {
                mc.setScreen(new TriplePickerScreen(module, slot, this));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (!exit) {
            alpha.setDuration(700);
            alpha.setDirection(Direction.BACKWARDS);
            exit = true;
        }
        return false;
    }

    @Override
    public boolean shouldPause() { return false; }
}
