package ru.white.module.api;

import ru.white.Client;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.impl.combat.*;
import ru.white.module.impl.combat.*;
import ru.white.module.impl.display.Arrows;
import ru.white.module.impl.display.ClickGui;
import ru.white.module.impl.display.Hud;

import ru.white.module.impl.display.InterFace;
import ru.white.module.impl.movement.*;
import ru.white.module.impl.player.*;
import ru.white.module.impl.render.*;
import ru.white.module.impl.utils.*;

import ru.white.module.impl.movement.*;
import ru.white.module.impl.player.*;
import ru.white.module.impl.render.*;
import ru.white.module.impl.utils.*;


import java.util.*;
import java.util.stream.Collectors;


public final class ModuleManager extends LinkedHashMap<Class<? extends Module>, Module> {


    
    public void init() {

        addSorted(

                new AttackAura(),
                new AutoSwap(),
                new AutoTotem(),
                new AimBot(),
                new ProjectileAimBot(),
                new NoFriendDamage(),
                new NoSlotChange(),
                new HitBoxes(),
                new Criticals(),
                new CrystalAura(),
                new AntiBot(),
                new TriggerBot(),
                new UseTracker(),


                new Sprint(),
                new NoSlow(),
                new Spider(),
                new NoWeb(),
                new Speed(),
                new AirStuck(),

                new ClickGui(),
                new Arrows(),
                new EntityEsp(),
                new NameTag(),
                new NoRender(),
                new SwingAnimation(),
                new GlassHands(),
                new GlassBlock(),
                new ShaderEsp(),
                new ShaderSky(),
                new WorldTweaks(),
                new Gamma(),
                new Particles(),
                new HealthAlert(),
                new BlockEsp(),
                new ChinaHat(),
                new JumpCircle(),
                new JumpCube(),
                new CrossHair(),
                new Trails(),
                new WorldCubes(),
                new Trajectories(),
                new ColorGrade(),
                new FireFlies(),
                new Svetoch(),
                new ScanWorld(),
                new FogBlur(),
                new TotemGhost(),
                new KillEffect(),
                new Hands(),
                new InterFace(),
                new ReportHelper()

                ,
                new AutoAccept(),
                new NoPush(),
                new NoDelay(),
                new ClickHelper(),
                new WorldTracker(),
                new InventoryMove(),
                new ElytraHelper(),
                new AutoLeave(),
                new AutoTool(),
                new AuctionHelper(),
                new LockSlot(),
                new FreeCamera(),
                new FreeLook(),
                new PvpSafe(),
                new AppleFarmer()

                ,
                new UnHook(),
                new NameProtect(),
                new GlassFarmer(),
                new PotionFarmer(),
                new SPJoiner(),
                new FakePlayer(),
                new ChestStealer(),
                new AutoClanUpgrade(),
                new AutoStorage(),
                new WardenHelper(),
                new AuraCrafter(),
                new AutoResell(),
                new ItemScroller(),
                new AutoDuel(),
                new AutoPotion(),
                new DanjHelper(),
                new AutoInvest()

        );

        this.values().stream()
                .filter(Module::isAutoEnabled)
                .forEach(module -> module.setEnabled(true, false));

        Client.eventHandler().subscribe(this);
    }

    public void addSorted(Module... modules) {
        Arrays.stream(modules)
                .forEach(module -> this.put(module.getClass(), module));
    }

    public void unregister(Module... modules) {
        Arrays.stream(modules).forEach(module -> this.remove(module.getClass()));
    }

    @EventHandler
    public void onKeyboardPress(EventKey event) {
            this.values().stream()
                    .filter(module -> module.getKey() == event.getKey())
                    //.filter(module -> !(module instanceof ClickGui))
                    .forEach(Module::toggle);
    }



    public <T extends Module> T get(final String name) {
        return this.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .map(module -> (T) module)
                .findFirst()
                .orElse(null);
    }


    public <T extends Module> T get(final Class<T> clazz) {
        return this.values().stream()
                .filter(module -> clazz.isAssignableFrom(module.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }


    public List<Module> get(final Category category) {
        return this.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }


    public Module getModule(String name) {
        return this.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Collection<Module> values() {
        return super.values().stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }
}
