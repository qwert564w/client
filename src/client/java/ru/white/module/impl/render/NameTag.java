package ru.white.module.impl.render;

import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.TextFactoryEvent;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.events.orbit.EventPriority;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.impl.display.Hud;
import ru.white.module.impl.display.InterFace;
import ru.white.module.impl.player.WorldTracker;
import ru.white.module.impl.utils.NameProtect;
import ru.white.module.impl.utils.ReportHelper;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.Projection;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.ScreenBlur;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4d;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "Name Tag",
        category = Category.RENDER,
        desc = "Отображает ники, хп и броню сущностей"
)
public class NameTag extends Module {

    public static NameTag get() {
        return Instance.get(NameTag.class);
    }

    public BooleanSetting player       = new BooleanSetting(this, "Отображения игроков", true);
    public BooleanSetting ignoreNaked  = new BooleanSetting(this, "Игнорировать голых", false)
            .setVisible(() -> player.getValue());
    public BooleanSetting handItems    = new BooleanSetting(this, "Предметы в руках", false)
            .setVisible(() -> player.getValue());
    public BooleanSetting mobs         = new BooleanSetting(this, "Отображения мобов", true);
    public BooleanSetting items        = new BooleanSetting(this, "Отображения предметов", true);


    private final List<Entity> entities = new ArrayList<>();

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        entities.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        entities.clear();
        if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity p) {
                    if (player.getValue()
                            && (p.getCustomName() == null || !p.getCustomName().getString().startsWith("Ghost_"))
                            && (!ignoreNaked.getValue() || hasArmor(p))) {
                        entities.add(entity);
                    }
                } else if (entity instanceof LivingEntity) {
                    if (mobs.getValue()) entities.add(entity);
                } else if (entity instanceof ItemEntity) {
                    if (items.getValue()) entities.add(entity);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTick(EventDisplay e) {
        DrawContext context = e.getDrawContext();
        float tickDelta = e.getPartialTicks();


        ScreenBlur.capture();

        if (mc.world == null || mc.player == null) return;

        for (Entity entity : entities) {
            Vector4d vec4d = Projection.getVector4D(entity, tickDelta);
            if (Projection.cantSee(vec4d)) continue;

            float distance = (float) mc.gameRenderer.getCamera().getCameraPos().distanceTo(entity.getBoundingBox().getCenter());
            if (distance < 1) continue;

            float x = (float) Projection.centerX(vec4d);
            float y = (float) vec4d.y - 12;


            String displayName = "";
            boolean isFriend = false;

            if (entity instanceof PlayerEntity p) {
                isFriend = Client.get().friendManager().isFriend(p.getName().getString());
                String playerName = Client.get().moduleManager().get(NameProtect.class).isEnabled() &&
                        Client.get().moduleManager().get(NameProtect.class).friends.getValue() &&
                        Client.get().friendManager().isFriend(p.getNameForScoreboard()) ? "Friend" :
                        toColoredString(p.getDisplayName()).replace("⚡", "");
                displayName = playerName.replace(mc.player.getName().getString(), "nightix.fun") + " " + Formatting.RED  + (entity.isInvisible() ? "null " : (int) getHealth(p)) + "hp";
            } else if (entity instanceof LivingEntity le) {
                displayName = le.getType().getName().getString() + Formatting.GRAY + " / " + Formatting.WHITE + (int) getHealth(le) + Formatting.GRAY + "hp";
            } else if (entity instanceof ItemEntity item) {
                ItemStack stack = item.getStack();
                displayName = stack.getFormattedName().getString() + Formatting.GRAY + " x" + Formatting.WHITE + item.getStack().getCount();
            }

            renderTag(context, entity, displayName, x, y, isFriend, tickDelta);

            if (entity instanceof PlayerEntity handPlayer && player.getValue() && handItems.getValue()) {
                renderHandItems(context, handPlayer, x, (float) vec4d.w);
            }

            // кулдауны хилок над ником — модуль сам решает, включён ли он
            if (entity instanceof PlayerEntity tracked && player.getValue()) {
                WorldTracker.get().render(context, tracked, x, y - 3);
            }
        }
    }

    private void renderHandItems(DrawContext context, PlayerEntity entity, float centerX, float feetY) {
        ItemStack mainHand = entity.getMainHandStack();
        ItemStack offHand = entity.getOffHandStack();

        List<ItemStack> hands = new ArrayList<>();
        if (!mainHand.isEmpty()) hands.add(mainHand);
        if (!offHand.isEmpty()) hands.add(offHand);
        if (hands.isEmpty()) return;

        float fontSize = 6;
        float iconSize = 8;
        float padding = 4;
        float spacing = 2;
        float rowH = 13;
        float gap = 2;

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;


        int bgColor = Client.get().friendManager().isFriend(entity.getName().getString()) ? ColorUtil.getColor(0, 255, 0) : ColorUtil.background();
        if (ReportHelper.isReported(entity)) {
            bgColor = ReportHelper.getReportColor(bgColor);
        }
        float y = feetY + 2;
        for (ItemStack stack : hands) {
            String name = toColoredString(stack.getFormattedName());
            float textWidth = Fonts.sf_regular.getWidth(name, fontSize);
            float wr = padding * 2 + iconSize + spacing + textWidth;
            float bgX = centerX - wr / 2f;



            RenderUtil.Blur.blur(bgX, y - 0.75F, wr, rowH,1,4,ColorUtil.replAlpha(
                    bgColor, InterFace.getInstance().alphaHUD.getValue() * 0.5F));



            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate((bgX  + padding + iconSize / 2f) * scaleFix, (y + rowH / 2f - 1) * scaleFix);
            matrices.scale(0.5F, 0.5F);
            ItemRender.drawItemWithContext(context, stack, -8, -8, 1F, 1.0F);
            matrices.popMatrix();

            float textX = bgX + padding + iconSize + spacing;
            float textY = y + rowH / 2f - 4.8F;
            Fonts.sf_regular.draw(name, textX, textY, fontSize, ColorUtil.WHITE);

            y += rowH + gap;
        }
    }

    private void renderTag(DrawContext context, Entity entity, String name, float x, float y, boolean isFriend, float tickDelta) {
        TextFactoryEvent nameEvent = new TextFactoryEvent(name);
        nameEvent.hook();
        name = nameEvent.getText();

        float fontSize = 6;
        float padding = 4;
        float spacing = 4;



        List<ItemStack> armorItems = new ArrayList<>();
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack stack = living.getEquippedStack(slot);
                if (!stack.isEmpty()) armorItems.add(stack);
            }
        }

        float itemStep = 10;
        float iconSize = 8;
        float itemsWidth = armorItems.isEmpty() ? 0 : spacing + (armorItems.size() - 1) * itemStep + iconSize;

        float textWidth = Fonts.sf_regular.getWidth(name, fontSize);
        float contentWidth = textWidth + itemsWidth;
        float wr = contentWidth + (padding * 2) ;
        float h = 15;

        float bgX = x - (wr / 2);
        float bgY = y - 3;

        int bgColor = isFriend ? ColorUtil.getColor(0, 255, 0) :  ColorUtil.background();
        if (ReportHelper.isReported(entity)) {
            bgColor = ReportHelper.getReportColor(bgColor);
        }


            RenderUtil.Blur.blur(bgX, bgY, wr, h,1,4,ColorUtil.replAlpha(
                    bgColor, InterFace.getInstance().alphaHUD.getValue() * 0.5F));


        Client.get().render2D().flushAll();

        float textX = bgX + padding;
        float textY = bgY + (h / 2f) - Fonts.sf_regular.getHeight(fontSize) / 2 - 0.1F;
        Fonts.sf_regular.draw(name, textX, textY, fontSize, ColorUtil.WHITE);

        if (!armorItems.isEmpty()) {
            float targetScale = 2F;
            float currentScale = (float) mc.getWindow().getScaleFactor();
            float scaleFix = targetScale / currentScale;

            float itemX = textX + textWidth + spacing;
            float itemCenterY = bgY + (h / 2f) - 0.5F;

            float currentItemOffset = 0;
            for (ItemStack stack : armorItems) {
                Matrix3x2fStack matrix3x2f = context.getMatrices();
                matrix3x2f.pushMatrix();
                matrix3x2f.translate((itemX + currentItemOffset + iconSize / 2f) * scaleFix, itemCenterY * scaleFix);
                matrix3x2f.scale(0.5F, 0.5F);
                ItemRender.drawItemWithContext(context, stack, -8, -8, 1F, 1.0F);
                matrix3x2f.popMatrix();

                currentItemOffset += itemStep;
            }
        }
    }

    public static boolean hasArmor(PlayerEntity p) {
        return !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private float getHealth(LivingEntity entity) {
        float hp = entity.getHealth() + entity.getAbsorptionAmount();
        if (entity instanceof PlayerEntity player && mc.world != null) {
            ScoreboardObjective scoreBoard = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
            if (scoreBoard != null) {
                MutableText text = ReadableScoreboardScore.getFormattedScore(
                        mc.world.getScoreboard().getScore(player, scoreBoard),
                        scoreBoard.getNumberFormatOr(StyledNumberFormat.EMPTY)
                );
                try {
                    hp = Float.parseFloat(ColorUtil.removeFormatting(text.getString()));
                } catch (Exception ignored) {}
            }
        }
        return ServerUtil.isCopyTime() ? entity.getHealth() : MathHelper.clamp(hp, 0, entity.getMaxHealth() + entity.getAbsorptionAmount());
    }

    private static String toColoredString(Text text) {
        StringBuilder sb = new StringBuilder();
        text.visit((style, str) -> {
            TextColor tc = style.getColor();
            if (tc != null) sb.append("§#").append(String.format("%06X", tc.getRgb()));
            sb.append(str);
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        return sb.toString();
    }
}
