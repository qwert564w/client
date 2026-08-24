package ru.white.module.impl.utils;

import com.mojang.authlib.GameProfile;
import ru.white.Client;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.impl.render.Particles; // Импортируем наш модуль частиц
import ru.white.module.impl.render.TotemGhost;
import ru.white.utils.math.MathUtil;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

@ModuleInfo(
        name = "Fake Player",
        desc = "Создает фейк игрока [Бан на SP / HW]",
        category = Category.OTHER
)
public class FakePlayer extends Module {
    public static OtherClientPlayerEntity fakePlayer;

    private int fakeRegenTicks = 0;
    private long lastSoundTime = 0;


    @EventHandler
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null || fakePlayer == null) return;

        fakePlayer.setYaw(0);
        fakePlayer.setPitch(0);
        fakePlayer.setHeadYaw(0);
        fakePlayer.setBodyYaw(0);

        fakePlayer.setMainArm(mc.player.getMainArm());

        if (fakeRegenTicks > 0) {
            fakeRegenTicks--;
            if (fakeRegenTicks % 20 == 0) {
                fakePlayer.setHealth(
                        Math.min(fakePlayer.getHealth() + 1.0F, 20.0F)
                );
            }
        }
    }

    /* =======================
       PACKETS
       ======================= */
    @EventHandler
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null || fakePlayer == null) return;

        // Only process on SEND (client->server), not on receive
        if (!event.isSend()) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            // Check if the crosshair target is our FakePlayer
            if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entityHit) {
                Entity targetEntity = entityHit.getEntity();
                if (targetEntity != null && targetEntity.getId() == fakePlayer.getId()) {
                    packet.handle(new PlayerInteractEntityC2SPacket.Handler() {

                        @Override
                        public void attack() {
                            handleAttack();
                        }

                        @Override
                        public void interact(Hand hand) {}

                        @Override
                        public void interactAt(Hand hand, Vec3d pos) {}
                    });
                }
            }
        }

        if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            handleExplosion(explosion);
        }
    }

    /* =======================
       ATTACK LOGIC
       ======================= */
    private void handleAttack() {
        if (mc.player.squaredDistanceTo(fakePlayer) > 36) return;

        float damage = calculateDamage(mc.player, fakePlayer);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSoundTime >= 100) {
            boolean crit =
                    mc.player.getVelocity().y < -0.08
                            && !mc.player.isOnGround()
                            && !mc.player.isSprinting();
            mc.player.playSound(
                    crit
                            ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT
                            : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    1.0F,
                    1.0F
            );
            lastSoundTime = currentTime;
        }

        applyFakeDamage(fakePlayer, damage  );
    }

    private float calculateDamage(PlayerEntity attacker, LivingEntity target) {

        ItemStack weapon = attacker.getMainHandStack();

        // ===== BASE DAMAGE =====
        double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);

        float cooled = attacker.getAttackCooldownProgress(0.5f);
        float damage = (float) (base * (0.2F + cooled * cooled * 0.8F));

        // ===== CRIT (vanilla logic) =====
        boolean crit =
                attacker.getVelocity().y < -0.08D &&
                        !attacker.isOnGround() &&
                        !attacker.isSprinting();

        if (crit) {
            damage *= 1.5F;
        }

        // ===== ENCHANTMENTS =====
        DynamicRegistryManager registryManager = attacker.getEntityWorld().getRegistryManager();
        var enchantmentRegistry = registryManager.getOrThrow(RegistryKeys.ENCHANTMENT);

        // Sharpness: 0.5 + 0.5 * level (vanilla formula)
        int sharp = enchantmentRegistry.getEntry(Enchantments.SHARPNESS.getValue())
                .map(entry -> EnchantmentHelper.getLevel(entry, weapon))
                .orElse(0);
        if (sharp > 0) {
            float sharpBonus = 0.5F + 0.5F * sharp;
            damage += sharpBonus;
        }

        // Smite: 2.5 * level
        int smite = enchantmentRegistry.getEntry(Enchantments.SMITE.getValue())
                .map(entry -> EnchantmentHelper.getLevel(entry, weapon))
                .orElse(0);
        if (smite > 0 && isUndead(target)) {
            float smiteBonus = 2.5F * smite;
            damage += smiteBonus;
        }

        // Bane of Arthropods: 2.5 * level
        int bane = enchantmentRegistry.getEntry(Enchantments.BANE_OF_ARTHROPODS.getValue())
                .map(entry -> EnchantmentHelper.getLevel(entry, weapon))
                .orElse(0);
        if (bane > 0 && isArthropod(target)) {
            float baneBonus = 2.5F * bane;
            damage += baneBonus;
        }

        return damage;
    }

    private boolean isUndead(Entity e) {
        return e instanceof ZombieEntity
                || e instanceof SkeletonEntity
                || e instanceof WitherSkeletonEntity
                || e instanceof WitherEntity
                || e instanceof ZoglinEntity
                || e instanceof ZombieHorseEntity
                || e instanceof ZombieVillagerEntity
                || e instanceof HuskEntity
                || e instanceof StrayEntity
                || e instanceof DrownedEntity
                || e instanceof PhantomEntity;
    }

    private boolean isArthropod(Entity e) {
        return e instanceof SpiderEntity
                || e instanceof CaveSpiderEntity
                || e instanceof SilverfishEntity
                || e instanceof EndermiteEntity
                || e instanceof BeeEntity;
    }

    /* =======================
       DAMAGE / TOTEM
       ======================= */
    private void applyFakeDamage(OtherClientPlayerEntity target, float damage) {
        float absorption = target.getAbsorptionAmount();

        if (absorption > 0) {
            float used = Math.min(absorption, damage);
            target.setAbsorptionAmount(absorption - used);
            damage -= used;
        }

        if (damage > 0) {
            float oldHealth = target.getHealth();
            float newHealth = Math.max(target.getHealth() - damage, 0.5F);
            target.setHealth(newHealth);
        }

        target.hurtTime = 10;
        target.timeUntilRegen = 10;

        // Add effects only if they don't exist or are about to expire
        if (!target.hasStatusEffect(StatusEffects.REGENERATION) ||
                target.getStatusEffect(StatusEffects.REGENERATION).getDuration() < 100) {
            target.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1)
            );
        }

        if (!target.hasStatusEffect(StatusEffects.ABSORPTION) ||
                target.getStatusEffect(StatusEffects.ABSORPTION).getDuration() < 20) {
            target.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1)
            );
        }

        if (target.getHealth() <= 1.0F
                && target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {

            triggerTotem(target);
        }
    }

    private void triggerTotem(OtherClientPlayerEntity target) {
        mc.player.playSound(
                SoundEvents.ITEM_TOTEM_USE,
                1.0F,
                1.0F
        );

        TotemGhost totemGhost = Client.get().moduleManager().get(TotemGhost.class);
        if (totemGhost != null) {
            totemGhost.spawnGhost(target);
        }




        Particles particles = Client.get().moduleManager().get(Particles.class);

        if(particles.totem.getValue()) {
            for (int i = 0; i < particles.onTtSizs.getValue() ; i++) {
                double angle = MathUtil.randomValue(0, Math.PI * 2);
                double speedXZ = MathUtil.randomValue(2.5, 4.0);
                double motionX = Math.cos(angle) * speedXZ;
                double motionZ = Math.sin(angle) * speedXZ;
                double motionY = MathUtil.randomValue(0.0, target.getHeight() / 2);

                particles.spawnParticleTotem(particles.totemParticles, new Vec3d(target.getX() + MathUtil.randomValue(-0.2, 0.2),
                        target.getY() + MathUtil.randomValue(0.1F, target.getHeight()),
                        target.getZ() + MathUtil.randomValue(-0.2, 0.2)), new Vec3d(motionX, motionY, motionZ));
            }
        } else {
            mc.particleManager.addEmitter(
                    target,
                    ParticleTypes.TOTEM_OF_UNDYING,
                    30
            );

        }


        target.clearStatusEffects();
        target.setHealth(4.0F);
        target.setAbsorptionAmount(12.0F);

        fakeRegenTicks = 1400;
    }

    /* =======================
       EXPLOSION
       ======================= */
    private void handleExplosion(ExplosionS2CPacket explosion) {

        Vec3d expPos = explosion.center();
        double dist = fakePlayer.getEntityPos().distanceTo(expPos);

        if (dist > 11.0) return;

        float damage = (float) ((1.0 - dist / 11.0) * 12.0F) ;

        applyFakeDamage(fakePlayer, damage);

        mc.player.playSound(
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                0.7F,
                1.0F
        );
    }

    /* =======================
       ENABLE / DISABLE
       ======================= */
    public void onToggled(boolean active) {
        if (mc.player == null || mc.world == null) return;

        if (!active) {
            if (fakePlayer != null) {
                fakePlayer.discard();
                fakePlayer = null;
            }
            return;
        }

        GameProfile profile = new GameProfile(
                UUID.fromString("32d0a964-137a-2c03-7cc9-df6700000000"),
                "NIGHT_NPC"
        );

        fakePlayer = new OtherClientPlayerEntity(mc.world, profile);

        fakePlayer.refreshPositionAndAngles(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch()
        );

        fakePlayer.setStackInHand(
                Hand.MAIN_HAND,
                new ItemStack(Items.NETHERITE_SWORD)
        );

        fakePlayer.setStackInHand(
                Hand.OFF_HAND,
                new ItemStack(Items.TOTEM_OF_UNDYING)
        );

        fakePlayer.setHealth(20.0F);
        fakePlayer.setAbsorptionAmount(0);

        mc.world.addEntity(fakePlayer);
    }

    @Override
    public void onEnable() {
        onToggled(true);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        onToggled(false);
        super.onDisable();
    }
}