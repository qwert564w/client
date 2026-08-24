package ru.white.module.impl.player;

import org.apache.logging.log4j.core.pattern.AbstractStyleNameConverter;
import ru.white.Client;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.FreeLookUtil;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.display.Hud;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.Keyboard;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import java.util.ArrayList;
import java.util.List;

import ru.white.module.impl.render.SwingAnimation;
import ru.white.utils.math.StopGPT;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        name = "Click Helper",
        desc = "Помогает кликать по клавише (автокликер)",
        category = Category.PLAYER
)
public class ClickHelper extends Module {




    public BindSetting friend = new BindSetting(this, "Клавиша добавления в друзья", -1);
    public BindSetting perka = new BindSetting(this, "Клавиша кидание Эндер жемчуга", -1);
    public BindSetting coordsKey = new BindSetting(this, "Клавиша координат", -1);

    public final BindSetting chorusKey = new BindSetting(this, "Клавиша хоруса", -1);
    public final BindSetting expKey = new BindSetting(this, "Клавиша опыта", -1);

    public ModeSetting type = new ModeSetting(this,"Режим","FunTime","HolyWorld");
    

    public final BindSetting eyeKey = new BindSetting(this, "Клавиша дезки", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting sugarKey = new BindSetting(this, "Клавиша явки", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting snowKey = new BindSetting(this, "Клавиша снежка", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting plastKey = new BindSetting(this, "Клавиша пласта", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting trapKey = new BindSetting(this, "Клавиша трапки", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting windKey = new BindSetting(this, "Клавиша з.ветра", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting bojAurakey = new BindSetting(this, "Клавиша бож.ауры", -1).setVisible(() -> type.is("FunTime"));


    public final BindSetting keySerka = new BindSetting(this, "Клавиша радейки", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting keyHilka = new BindSetting(this, "Клавиша бож.воды", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting palladinKey = new BindSetting(this, "Клавиша Палладина", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting assassinKey = new BindSetting(this, "Клавиша Ассасина", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting sleepKey = new BindSetting(this, "Клавиша Снотворного", -1).setVisible(() -> type.is("FunTime"));
    public final BindSetting angerKey = new BindSetting(this, "Клавиша Гнева", -1).setVisible(() -> type.is("FunTime"));



    public final BindSetting holyStunKey = new BindSetting(this, "Клавиша стана", -1).setVisible(() -> type.is("HolyWorld"));
    public final BindSetting holyTrapKey = new BindSetting(this, "Клавиша трапки", -1).setVisible(() -> type.is("HolyWorld"));
    public final BindSetting holyExplosiveTrapKey = new BindSetting(this, "Клавиша взрывной трапки", -1).setVisible(() -> type.is("HolyWorld"));
    public final BindSetting holyExplosiveKey = new BindSetting(this, "Клавиша взрывной штуки", -1).setVisible(() -> type.is("HolyWorld"));
    public final BindSetting holySnowKey = new BindSetting(this, "Клавиша кома снега", -1).setVisible(() -> type.is("HolyWorld"));

    private boolean expHeld = false;
    private int expSlot = -1;
    private int expOldSlot = -1;
    private int expStep = 0;
    private final StopGPT expTimer = new StopGPT();

    public static ClickHelper getInstance() {
        return Instance.get(ClickHelper.class);
    }


    private int oldSlot = -1;

    private int step = 0;
    private final StopGPT timer = new StopGPT();

    private boolean chorusHeld = false;
    private int chorusSlot = -1;
    private int chorusOldSlot = -1;
    private int chorusStep = 0;
    private final StopGPT chorusTimer = new StopGPT();


    @EventHandler
    public void onKey(EventKey e) {
        if (mc.player == null || mc.world == null) return;


        handleBind_2(e.getKey());
    }


    private void startPearlSwap() {

        itemSlot = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ENDER_PEARL) {
                itemSlot = i;
                break;
            }
        }

        if (itemSlot == -1) {
            mc.player.sendMessage(Text.literal("Перл не найден!").formatted(Formatting.RED), true);
            return;
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();

        timer.reset();
        step = 1;
    }

    private void setKey(boolean state) {


        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
        };

        long handle = mc.getWindow().getHandle();

        for (KeyBinding keyBinding : movementKeys) {

            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);

        }
    }

    private void sendCoords() {
        if (mc.player == null) return;
        mc.player.networkHandler.sendChatMessage("/cc " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
    }


    @Override
    public void onDisable() {
        step = 0;
        if (chorusStep != 0) abortChorus();
        chorusHeld = false;
        super.onDisable();
    }

    private void handleFriendAction() {

        if (AttackAura.target != null)
            return;

        HitResult hitResult = mc.crosshairTarget;

        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY)
            return;

        Entity entity = ((EntityHitResult) hitResult).getEntity();

        if (!(entity instanceof PlayerEntity player))
            return;

        String name = player.getName().getString();

        if (!Client.get().friendManager().isFriend(name)) {

            Client.get().friendManager().add(name);
            //SoundUtil.playSound_wav("bind_success", 0.25f);

        } else {

            Client.get().friendManager().remove(name);
            //SoundUtil.playSound_wav("bind_remove", 0.3f);

        }
    }



    private int itemSlot = -1;
    private Item targetItem = null;
    private String targetName = null;


    private final Map<Item, Float> animations = new HashMap<>();
    // cdTracker: item -> [startTick, durationTicks] (-1 until computed on 2nd tick)
    private final Map<Item, long[]> cdTracker = new ConcurrentHashMap<>();

    private static final int HUD_COLS = 20, HUD_CW = 24, HUD_CH = 24, HUD_GAP = 1;
    private static final float BADGE_PAD_X = 4, BADGE_PAD_Y = 3, BADGE_FONT = 5F;

    /** Плавная высота красной заливки кулдауна по предметам. */
    private final Map<Item, Animation> cdAnimations = new HashMap<>();

    private static final Item[] CD_ITEMS = {
            Items.ENDER_EYE, Items.SUGAR, Items.SNOWBALL, Items.DRIED_KELP,
            Items.NETHERITE_SCRAP, Items.WIND_CHARGE, Items.PHANTOM_MEMBRANE,
            Items.NETHER_STAR, Items.POPPED_CHORUS_FRUIT, Items.PRISMARINE_SHARD, Items.FIRE_CHARGE
    };

    @EventHandler
    public void onCooldownTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        for (Item item : CD_ITEMS) {
            boolean isCD = mc.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack());
            long[] info = cdTracker.get(item);
            if (isCD) {
                if (info == null) {
                    cdTracker.put(item, new long[]{mc.world.getTime(), -1L});
                } else if (info[1] < 0) {
                    float progress = mc.player.getItemCooldownManager().getCooldownProgress(item.getDefaultStack(), 0f);
                    long elapsed = mc.world.getTime() - info[0];
                    if (elapsed > 0 && progress > 0f && progress < 1f) {
                        long total = Math.round(elapsed / (1f - progress));
                        if (total > 0) info[1] = total;
                    }
                }
            } else if (info != null) {
                cdTracker.remove(item);
            }
        }
    }
    private void handleBind_2(int key) {


        if (key == friend.get() && key != -1) {
            handleFriendAction();
        }
        if (key == -1 || step != 0 || chorusSlot != -1) return;

        Item toUse = null;

        if (key == perka.get()) {

            toUse = Items.ENDER_PEARL;
        }



        if(type.is("FunTime")) {


            if (key == sugarKey.get()) toUse = Items.SUGAR;
            else if (key == eyeKey.get()) toUse = Items.ENDER_EYE;
            else if (key == snowKey.get()) toUse = Items.SNOWBALL;
            else if (key == plastKey.get()) toUse = Items.DRIED_KELP;
            else if (key == trapKey.get()) toUse = Items.NETHERITE_SCRAP;
            else if (key == windKey.get()) toUse = Items.WIND_CHARGE;
            else if (key == bojAurakey.get()) toUse = Items.PHANTOM_MEMBRANE;


            if (key == keyHilka.get()) {
                RotationProcess.update(new Rotation(mc.player.getYaw() + MathUtil.randomValue(-3, 3), 80 + MathUtil.randomValue(2, 8)), 360, 360, 2, 200);
                startActionByName("Святая вода");
                return;
            }
            if (key == keySerka.get()) {
                startActionByName("Зелье Радиации");
                return;
            }

            if (key == palladinKey.get()) {
                RotationProcess.update(new Rotation(mc.player.getYaw() + MathUtil.randomValue(-3, 3), 80 + MathUtil.randomValue(2, 8)), 360, 360, 2, 200);
                startActionByName("Зелье Палладина");
                return;
            }
            if (key == assassinKey.get()) {
                RotationProcess.update(new Rotation(mc.player.getYaw() + MathUtil.randomValue(-3, 3), 80 + MathUtil.randomValue(2, 8)), 360, 360, 2, 200);
                startActionByName("Зелье Ассасина");
                return;
            }
            if (key == sleepKey.get()) {
                startActionByName("Снотворное");
                return;
            }
            if (key == angerKey.get()) {
                RotationProcess.update(new Rotation(mc.player.getYaw() + MathUtil.randomValue(-3, 3), 80 + MathUtil.randomValue(2, 8)), 360, 360, 2, 200);
                startActionByName("Зелье Гнева");
                return;
            }
            if (toUse != null) {

                startAction(toUse);
            }
        } else if(type.is("HolyWorld")) {
            if (key == holyStunKey.get()) toUse = Items.NETHER_STAR;
            else if (key == holyTrapKey.get()) toUse = Items.POPPED_CHORUS_FRUIT;
            else if (key == holyExplosiveTrapKey.get()) toUse = Items.PRISMARINE_SHARD;
            else if (key == holyExplosiveKey.get()) toUse = Items.FIRE_CHARGE;
            else if (key == holySnowKey.get()) toUse = Items.SNOWBALL;

            if (toUse != null) {
                startAction(toUse);
            }
        }
    }

    private void handleExpKey() {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        int ek = expKey.get();
        if (ek == -1) {
            if (expStep != 0) abortExp();
            expHeld = false;
            return;
        }

        boolean nowPressed = InputUtil.isKeyPressed(mc.getWindow(), ek);

        // Начало нажатия
        if (!expHeld && nowPressed && step == 0 && expStep == 0) {
            int slot = -1;
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.EXPERIENCE_BOTTLE) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) {
                mc.player.sendMessage(Text.literal("Опыт не найден!").formatted(Formatting.RED), true);
            } else {
                expSlot = slot;
                expOldSlot = mc.player.getInventory().getSelectedSlot();
                expStep = 1;
                expTimer.reset();
            }
        }

        expHeld = nowPressed;

        // Когда отпустили клавишу
        if (expStep == 2 && !nowPressed) {
            expStep = 3;
            expTimer.reset();
        }

        switch (expStep) {
            case 1 -> { // Свап предмета
                if (expSlot > 9) setKey(false);
                if (expTimer.hasTimePassed(50)) {
                    if (expSlot < 9) {
                        mc.player.getInventory().setSelectedSlot(expSlot);
                        mc.interactionManager.syncSelectedSlot();
                    } else {
                        setKey(true);
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, expSlot, expOldSlot, SlotActionType.SWAP, mc.player);
                    }
                    expStep = 2;
                }
            }
            case 2 -> { // Зажатие ПКМ
                mc.options.useKey.setPressed(true);
                RotationProcess.update(new Rotation(FreeLookUtil.freeYaw,90),90,90,2,25);
            }
            case 3 -> { // Возврат слота
                mc.options.useKey.setPressed(false);
                if (expSlot > 9) setKey(false);
                if (expTimer.hasTimePassed(50)) {
                    if (expSlot < 9) {
                        mc.player.getInventory().setSelectedSlot(expOldSlot);
                        mc.interactionManager.syncSelectedSlot();
                    } else {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, expSlot, expOldSlot, SlotActionType.SWAP, mc.player);
                        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                        mc.player.closeHandledScreen();
                        setKey(true);
                    }
                    expSlot = -1;
                    expStep = 0;
                }
            }
        }
    }
    private void abortExp() {
        if (mc.player != null && expSlot != -1) {
            if (expSlot < 9) {
                mc.player.getInventory().setSelectedSlot(expOldSlot);
                mc.interactionManager.syncSelectedSlot();
            } else {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, expSlot, expOldSlot, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                mc.player.closeHandledScreen();
            }
            setKey(true);
        }
        expSlot = -1;
        expStep = 0;
    }

    private void startAction(Item item) {
        if (mc.player == null) return;
        targetName = null;
        targetItem = item;
        findSlotAndStart();
    }


    private void startActionByName(String name) {
        if (mc.player == null) return;
        targetItem = null;
        targetName = name.toLowerCase();
        findSlotAndStart();
    }

    private void findSlotAndStart() {
        itemSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (targetName != null) {

                String stackName = stack.getName().getString().toLowerCase();
                if (stackName.contains(targetName)) {
                    itemSlot = i;
                    break;
                }
            } else if (targetItem != null) {
                if (stack.getItem() == targetItem) {
                    itemSlot = i;
                    break;
                }
            }
        }

        if (itemSlot == -1) {
            String msg = targetName != null ? targetName : targetItem.getName().getString();
            mc.player.sendMessage(Text.literal("Предмет [" + msg + "] не найден!").formatted(Formatting.RED), true);
            return;
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();
        timer.reset();
        step = 1;
    }

    private BindSetting[] hudKeys() {
        return type.is("HolyWorld")
                ? new BindSetting[]{perka, holyStunKey, holyTrapKey, holyExplosiveTrapKey, holyExplosiveKey, holySnowKey, chorusKey}
                : new BindSetting[]{perka, eyeKey, sugarKey, snowKey, plastKey, trapKey, windKey, bojAurakey, chorusKey};
    }

    private Item[] hudItems() {
        return type.is("HolyWorld")
                ? new Item[]{Items.ENDER_PEARL, Items.NETHER_STAR, Items.POPPED_CHORUS_FRUIT,
                        Items.PRISMARINE_SHARD, Items.FIRE_CHARGE, Items.SNOWBALL, Items.CHORUS_FRUIT}
                : new Item[]{Items.ENDER_PEARL, Items.ENDER_EYE, Items.SUGAR, Items.SNOWBALL, Items.DRIED_KELP,
                        Items.NETHERITE_SCRAP, Items.WIND_CHARGE, Items.PHANTOM_MEMBRANE, Items.CHORUS_FRUIT};
    }

    private List<Integer> hudActive(BindSetting[] keys) {
        List<Integer> active = new ArrayList<>();

        for (int i = 0; i < keys.length; i++) {
            if (keys[i].get() != -1) active.add(i);
        }

        return active;
    }

    /** Ширина плашки с названием клавиши — она может быть шире самой ячейки. */
    private float badgeWidth(int key) {
        return Fonts.sf_regular.getWidth(Keyboard.keyName(key), BADGE_FONT) + BADGE_PAD_X * 2;
    }

    /**
     * X-смещения ячеек: если у соседних биндов длинные названия, шаг между предметами растёт,
     * чтобы плашки клавиш не наезжали друг на друга.
     */
    private float[] hudOffsets(List<Integer> active, BindSetting[] keys) {
        float[] xs = new float[active.size()];
        float x = 0;

        for (int n = 0; n < active.size(); n++) {
            if (n % HUD_COLS == 0) {
                x = 0;
            } else {
                float prev = badgeWidth(keys[active.get(n - 1)].get());
                float cur = badgeWidth(keys[active.get(n)].get());

                x += Math.max(HUD_CW, (prev + cur) / 2F) + HUD_GAP;
            }

            xs[n] = x;
        }

        return xs;
    }

    /** реальный размер худа биндов — той же логикой, что и renderHud */
    public org.joml.Vector2f getHudSize() {
        BindSetting[] keys = hudKeys();

        List<Integer> active = hudActive(keys);
        if (active.isEmpty()) return new org.joml.Vector2f(0, 0);

        // Получаем текущее значение ползунка размера
        float S = ru.white.module.impl.display.InterFace.getInstance().sizeHud.getValue();

        float[] xs = hudOffsets(active, keys);

        // Масштабируем базовые константы
        float scaledHUD_CW = HUD_CW * S;
        float scaledHUD_CH = HUD_CH * S;
        float scaledHUD_GAP = HUD_GAP * S;

        float width = 0;
        for (int n = 0; n < active.size(); n++) {
            // Умножаем badgeWidth и отступ на S, так же как мы делали при рендере
            float bw = badgeWidth(keys[active.get(n)].get()) * S;
            float scaledX = xs[n] * S;

            width = Math.max(width, scaledX + Math.max(scaledHUD_CW, scaledHUD_CW / 2F + bw / 2F));
        }

        int rows = (active.size() + HUD_COLS - 1) / HUD_COLS;

        // Вычисляем итоговую высоту с учетом отмасштабированных ячеек и отступов
        return new org.joml.Vector2f(width, rows * scaledHUD_CH + (rows - 1) * scaledHUD_GAP);
    }
    public void renderHud(DrawContext ctx, float sx, float sy) {
        if (mc.player == null || mc.world == null) return;

        BindSetting[] keys = hudKeys();
        Item[] its = hudItems();

        List<Integer> active = hudActive(keys);
        if (active.isEmpty()) return;

        // Получаем текущее значение ползунка размера
        float S = InterFace.getInstance().sizeHud.getValue();

        // Масштабируем константы ячеек
        final float CW = 20f * S;
        final float CH = 20f * S;
        final float TH = 0f * S;
        final float GAP = HUD_GAP * S;

        float[] xs = hudOffsets(active, keys);

        for (int n = 0; n < active.size(); n++) {
            int  idx  = active.get(n);
            int  key  = keys[idx].get();
            Item item = its[idx];
            int  row  = n / HUD_COLS;

            // Умножаем xs[n] на S (если внутри hudOffsets еще нет масштабирования)
            float cx  = sx + xs[n] * S;
            float cy  = sy + row * (CH + TH + GAP);

            RenderUtil.Render2D.glow(cx, cy, CW, CH - 0.5F * S, ColorUtil.getColor(0, 0.1F), 5f * S, 12f * S, 1);
            RenderUtil.Blur.blur(cx, cy, CW, CH, 1, 5f * S, ColorUtil.replAlpha(ColorUtil.background(),InterFace.getInstance().alphaHUD.getValue()));

            // красная заливка опускается вместе с кулдауном
            Animation cdAnim = cdAnimations.computeIfAbsent(item, i -> new Animation());
            cdAnim.update();
            cdAnim.run(mc.player.getItemCooldownManager().getCooldownProgress(item.getDefaultStack(), 0F), 0.12F, Easings.LINEAR);

            float fill = MathUtil.clamp(cdAnim.get(), 0F, 1F);

            if (fill > 0.001F) {
                float fh = CH * fill;

                // верхние углы округляются только когда заливка дошла до края ячейки
                float top = 5f * S * MathUtil.clamp((fill - 0.85F) / 0.15F, 0F, 1F);

                RenderUtil.Render2D.rect(cx, cy + CH - fh, CW, fh,
                        ColorUtil.getColor(80, 10, 10, 0.7F), top, top, 5f * S, 5f * S);
            }

            float targetScale = 2F;
            float currentScale = (float) mc.getWindow().getScaleFactor();
            float scaleFix = targetScale / currentScale;

            ctx.getMatrices().pushMatrix();
            // Применяем масштаб S к сдвигу и итоговому размеру предмета
            ctx.getMatrices().translate((cx + CW / 2f) * scaleFix, (cy + CH / 2f - 1f * S) * scaleFix);
            ctx.getMatrices().scale(scaleFix * 0.5F * S, scaleFix * 0.5F * S);
            ctx.drawItem(new ItemStack(item), -8, -9);
            ctx.getMatrices().popMatrix();

            String cnt  = "x" + countItem(item);
            float cntSize = 5f * S;
            float cntW = Fonts.sf_regular.getWidth(cnt, cntSize);
            Fonts.sf_regular.draw(cnt, cx + CW - cntW - 3f * S, cy + CH - 8f * S, cntSize, ThemeColor.getTextColor());

            // timer cell
            float ty = cy + CH + GAP;
            RenderUtil.Render2D.rect(cx, ty, CW, TH, 0xCC181920, 2f * S);

            boolean onCD   = mc.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack());
            float   remain = 0f;
            float   maxSec = 0f;
            if (onCD) {
                long[] info = cdTracker.get(item);
                if (info != null && info[1] > 0) {
                    float progress = mc.player.getItemCooldownManager().getCooldownProgress(item.getDefaultStack(), 0f);
                    maxSec = info[1] / 20f;
                    remain = progress * maxSec;
                }
            }

            String keyText = Keyboard.keyName(key);

            float scaledBadgeFont = BADGE_FONT * S;
            float textHeight = Fonts.sf_regular.getHeight(scaledBadgeFont);

            // ширина считается тем же badgeWidth, умноженным на S
            float CW2 = badgeWidth(key) * S;
            float CH2 = textHeight + (BADGE_PAD_Y * 2f * S);

            float rectX = cx - (CW2 / 2.0F) + CW / 2F;
            float rectY = cy - (CH2 / 2.0F) - 9f * S;

            RenderUtil.Render2D.glow(rectX, rectY, CW2, CH2 - 0.5F * S, ColorUtil.getColor(0, 0.1F), 4f * S, 7f * S, 1);
            RenderUtil.Blur.blur(rectX, rectY, CW2, CH2, 1, 4f * S, ColorUtil.replAlpha(ColorUtil.background(),InterFace.getInstance().alphaHUD.getValue()));

            Fonts.sf_regular.drawCentered(keyText, cx + CW / 2F, cy - (textHeight / 2.0F) - 9f * S, scaledBadgeFont, ThemeColor.getTextColor());
        }
    }

    private String keyShortName(int key) {
        if (key <= 0) return "";
        try {
            String name = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
            return (name.length() <= 3 ? name : String.valueOf(name.charAt(0))).toUpperCase();
        } catch (Exception ignored) { return "?"; }
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        return n;
    }



    private void handleChorusKey() {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        int ck = chorusKey.get();
        if (ck == -1) {
            if (chorusStep != 0) abortChorus();
            chorusHeld = false;
            return;
        }

        boolean nowPressed = InputUtil.isKeyPressed(mc.getWindow(), ck);

        if (!chorusHeld && nowPressed && step == 0 && chorusStep == 0) {
            int slot = -1;
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.CHORUS_FRUIT) { slot = i; break; }
            }
            if (slot == -1) {
                mc.player.sendMessage(Text.literal("Хорус не найден!").formatted(Formatting.RED), true);
            } else {
                chorusSlot = slot;
                chorusOldSlot = mc.player.getInventory().getSelectedSlot();
                chorusStep = 1;
                chorusTimer.reset();
            }
        }

        chorusHeld = nowPressed;

        if (chorusStep == 2 && !nowPressed) {
            chorusStep = 3;
            chorusTimer.reset();
        }

        switch (chorusStep) {
            case 1 -> {
                if (chorusSlot > 9) {
                    setKey(false);
                }
                if (chorusTimer.hasTimePassed(50)) {
                    if (chorusSlot < 9) {
                        mc.player.getInventory().setSelectedSlot(chorusSlot);
                        mc.interactionManager.syncSelectedSlot();
                    } else {
                        setKey(true);
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, chorusSlot, chorusOldSlot, SlotActionType.SWAP, mc.player);
                    }
                    chorusStep = 2;

                }
            }
            case 2 -> {mc.options.useKey.setPressed(true);}
            case 3 -> {
                mc.options.useKey.setPressed(false);
                if (chorusSlot > 9) {
                    setKey(false);
                }
                if (chorusTimer.hasTimePassed(50)) {
                    if (chorusSlot < 9) {
                        mc.player.getInventory().setSelectedSlot(chorusOldSlot);
                        mc.interactionManager.syncSelectedSlot();
                    } else {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, chorusSlot, chorusOldSlot, SlotActionType.SWAP, mc.player);
                        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                        mc.player.closeHandledScreen();
                        setKey(true);
                    }
                    chorusSlot = -1;
                    chorusStep = 0;

                }
            }
        }
    }

    private void abortChorus() {
        if (mc.player != null && chorusSlot != -1) {
            if (chorusSlot < 9) {
                mc.player.getInventory().setSelectedSlot(chorusOldSlot);
                mc.interactionManager.syncSelectedSlot();
            } else {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, chorusSlot, chorusOldSlot, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                mc.player.closeHandledScreen();
            }
            setKey(true);
        }
        chorusSlot = -1;
        chorusStep = 0;
    }

    @EventHandler
    public void onUpdate2(EventUpdate e) {

        if (mc.player == null || mc.interactionManager == null) return;

        handleChorusKey();
        handleExpKey();

        if (step == 0) return;

        setKey(false);

        AttackAura.stoptick = 3;

        if (!timer.hasTimePassed(50)) return;
        timer.reset();

        switch (step) {
            case 1 -> {

                RotationProcess.update(new Rotation(mc.gameRenderer.getCamera().getYaw(), mc.gameRenderer.getCamera().getPitch()), MathUtil.randomLerp(400, 900), MathUtil.randomLerp(400, 900), (int) MathUtil.randomLerp(4, 7), 100);

                if (itemSlot < 9) {
                    mc.player.getInventory().setSelectedSlot(itemSlot);
                    mc.interactionManager.syncSelectedSlot();
                } else {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, itemSlot, oldSlot, SlotActionType.SWAP, mc.player);
                }
                step = 2;
            }
            case 2 -> {
                //mc.player.swingHand(Hand.MAIN_HAND);
                //mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);



                SwingAnimation.interactItem(Hand.MAIN_HAND);



                step = 3;
            }
            case 3 -> {
                if (itemSlot < 9) {
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                    mc.interactionManager.syncSelectedSlot();
                } else {

                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, itemSlot, oldSlot, SlotActionType.SWAP, mc.player);

                }
                step = 4;
            }
            case 4 -> {
                if (itemSlot >= 9) {
                    mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
                    mc.player.closeHandledScreen();
                }
                setKey(true);
                step = 0;
                targetItem = null;
                targetName = null;
            }
        }

    }

}
