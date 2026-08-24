package ru.white.module.impl.combat;

import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "Criticals",
        desc = "ХЗ ЧО ПИСАТь",
        category = Category.COMBAT
)
public class Criticals extends Module {

    public static Criticals getInstance() {
        return Instance.get(Criticals.class);
    }
    @EventHandler
    public void onEvent(EventUpdate e) {
        if (mc.player != null
                && mc.player.getAttackCooldownProgress(2.0F) >= 1.0F
                && mc.player.isOnGround()
                && AttackAura.target != null ) {

            mc.player.setVelocity(
                    mc.player.getVelocity().x,
                    0.04,
                    mc.player.getVelocity().z
            );

            mc.player.velocityDirty = true;
        }
    }

    public boolean canCritical() {
        return this.isEnabled() && mc.player.fallDistance <= 0.0F && !mc.player.isOnGround();
    }

}
