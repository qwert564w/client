package ru.white.module.impl.movement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.other.Instance;

@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "Air Stuck", category = Category.MOVEMENT, desc = "Заморозка персонажа в воздухе")
public class AirStuck extends Module {
    public static AirStuck getInstance() {
        return Instance.get(AirStuck.class);
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (!mc.player.isOnGround()) {

            mc.player.setVelocity(0, 0, 0);
        }
    }

    @EventHandler
    public void onMotion(MotionEvent eventMotion) {


        if(!mc.player.isOnGround()) {
            eventMotion.ground(false);
        }

    }

    @EventHandler
    public void onPacket(EventPacket e) {

        if (!mc.player.isOnGround()) {
            if (e.getPacket() instanceof PlayerMoveC2SPacket) {
                e.cancel();
            }
        }
    }


}
