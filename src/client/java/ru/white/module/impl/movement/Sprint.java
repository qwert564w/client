package ru.white.module.impl.movement;

import ru.white.Client;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.impl.combat.AttackAura;

import ru.white.module.impl.combat.TriggerBot;
import ru.white.utils.aura.TriggerUAttack;
import ru.white.utils.aura.UAttack;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "Sprint",
        desc = "Автоматический спринт",
        category = Category.MOVEMENT
)
public class Sprint extends Module {

    public static Sprint get() {
        return Instance.get(Sprint.class);
    }

    public int tick;

    @EventHandler
    public void onEvent(WorldLoadEvent e) {
        tick += 4;
    }



    @EventHandler
    public void onEvent(EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if(!mc.player.isAlive()) {
            tick += 2;
        }

        if (tick != 0) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
            tick--;
            return;
        }

        if(UAttack.resetSprintTick(AttackAura.target, AttackAura.get().getRanges() ) && AttackAura.get().isEnabled() && AttackAura.get().typeSprint.is("Silent")) {
            mc.options.sprintKey.setPressed(false);
            return;
        }
        if(UAttack.resetSprintTick(AttackAura.target, AttackAura.get().getRanges() ) && AttackAura.get().isEnabled() && AttackAura.get().typeSprint.is("Packet")) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
            return;
        }

        if(TriggerUAttack.resetSprintTick(TriggerBot.targets, Client.get.moduleManager().get(TriggerBot.class).getRanges()) && Client.get.moduleManager().get(TriggerBot.class).isEnabled() && Client.get.moduleManager().get(TriggerBot.class).typeSprint.is("Silent")) {
            mc.options.sprintKey.setPressed(false);
            return;
        }
        if(TriggerUAttack.resetSprintTick(TriggerBot.targets,  Client.get.moduleManager().get(TriggerBot.class).getRanges() ) && Client.get.moduleManager().get(TriggerBot.class).isEnabled() && Client.get.moduleManager().get(TriggerBot.class).typeSprint.is("Packet")) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
            return;
        }
        boolean horizontal = mc.player.horizontalCollision && !mc.player.collidedSoftly;
        boolean sneaking = mc.player.isSneaking() && !mc.player.isSwimming();
        boolean canSprint = !horizontal && mc.player.forwardSpeed > 0;
        if (sneaking)
            return;


        mc.options.sprintKey.setPressed(true);


    }
    @Override
    public void onDisable() {
        super.onDisable();

        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

}
