package ru.white.module.impl.player;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import ru.white.Client;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.UseCooldowns;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * То же, что Use Tracker, но не по одной цели, а по всем игрокам в мире.
 * Кулдауны рисуются строкой над ником — вызывается из {@link ru.white.module.impl.render.NameTag}.
 */
@ModuleInfo(
        name = "Use World Tracker",
        desc = "Кулдауны хилок всех игроков в мире прямо над ником",
        category = Category.PLAYER
)
public class WorldTracker extends Module {

    private static final float FONT_SIZE = 6;
    private static final float PADDING = 3.5F;
    private static final float GAP = 4;
    private static final float ROW_H = 12;

    public static WorldTracker get() {
        return Instance.get(WorldTracker.class);
    }

    public final MultiBooleanSetting items = new MultiBooleanSetting(this, "Предметы",
            new BooleanSetting(UseCooldowns.Item.NOTCH.label, true),
            new BooleanSetting(UseCooldowns.Item.GAPPLE.label, true),
            new BooleanSetting(UseCooldowns.Item.HEAL.label, true),
            new BooleanSetting(UseCooldowns.Item.NAUSEA.label, true),
            new BooleanSetting(UseCooldowns.Item.CHORUS.label, true),
            new BooleanSetting(UseCooldowns.Item.KELP.label, false),
            new BooleanSetting(UseCooldowns.Item.SCRAP.label, false));

    public final BooleanSetting names = new BooleanSetting(this, "Названия", true);

    public final BooleanSetting background = new BooleanSetting(this, "Фон", true);

    public final BooleanSetting blur = new BooleanSetting(this, "Блюр фон", true)
            .setVisible(() -> background.getValue());

    public final BooleanSetting debug = new BooleanSetting(this, "Логи детекта", false)
            .onAction(() -> UseCooldowns.debug = WorldTracker.get().debug.getValue());

    @EventHandler
    public void onUpdate(EventUpdate event) {
        UseCooldowns.tick(event);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        UseCooldowns.clear();
    }

    @Override
    protected void onDisable() {
        UseCooldowns.debug = false;
    }

    @Override
    protected void onEnable() {
        UseCooldowns.debug = debug.getValue();
    }

    /**
     * Рисует кулдауны игрока строкой над его ником.
     * @param topY верхняя граница ника
     */
    public void render(DrawContext context, PlayerEntity player, float centerX, float topY) {
        if (!isEnabled()) return;

        List<UseCooldowns.Item> active = new ArrayList<>();

        for (UseCooldowns.Item item : UseCooldowns.of(player.getUuid()).keySet()) {
            if (items.getValue(item.label)) active.add(item);
        }

        if (active.isEmpty()) return;

        Font font = Fonts.sf_regular;

        List<String> labels = new ArrayList<>();
        List<String> times = new ArrayList<>();

        float w = PADDING * 2 - GAP;

        for (UseCooldowns.Item item : active) {
            String label = names.getValue() ? item.label + " " : "";
            String time = UseCooldowns.format(UseCooldowns.remaining(player.getUuid(), item));

            labels.add(label);
            times.add(time);

            w += font.getWidth(label, FONT_SIZE) + font.getWidth(time, FONT_SIZE) + GAP;
        }

        float bgX = centerX - w / 2;
        float bgY = topY - ROW_H - 2;

        if (background.getValue() && blur.getValue()) {
            RenderUtil.Blur.blur(bgX, bgY, w, ROW_H, 1, 4, ColorUtil.multAlpha(ColorUtil.getColor(0), 0.2F));
        } else if (background.getValue()) {
            RenderUtil.Render2D.rect(bgX, bgY, w, ROW_H, ColorUtil.multAlpha(ColorUtil.getColor(0), 0.4F), 4);
        }

        // как в renderTag: фон отправляем на экран до текста, иначе он ляжет поверх букв
        Client.get().render2D().flushAll();

        float cx = bgX + PADDING;
        float textY = bgY + ROW_H / 2F - 3.65F;

        for (int i = 0; i < active.size(); i++) {
            String label = labels.get(i);
            String time = times.get(i);

            if (!label.isEmpty()) {
                font.draw(label, cx, textY, FONT_SIZE, ColorUtil.getColor(220));
                cx += font.getWidth(label, FONT_SIZE);
            }

            font.draw(time, cx, textY, FONT_SIZE, ColorUtil.client());

            cx += font.getWidth(time, FONT_SIZE) + GAP;
        }
    }
}
