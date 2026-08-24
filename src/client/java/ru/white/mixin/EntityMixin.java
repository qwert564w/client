package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ru.white.Client;
import ru.white.module.impl.combat.HitBoxes;
import ru.white.module.impl.player.NoPush;
import ru.white.module.impl.render.ShaderEsp;
import ru.white.utils.annotation.IMinecraft;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements IMinecraft {
    @ModifyExpressionValue(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isLogicalSideForUpdatingMovement()Z",
                    ordinal = 1
            )
    )
    public boolean fixFalldistanceValue(boolean original) {
        if ((Object) this == mc.player) {
            return true;
        }

        return original;
    }

    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void client$getTargetingMargin(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity)) return;
        if (!HitBoxes.get().isEnabled()) return;
        if ( self instanceof PlayerEntity player && Client.get().friendManager().isFriend(player.getName().getString())) return;
        float base = cir.getReturnValue();
        float extra = HitBoxes.get().size.getValue();
        cir.setReturnValue(base + extra);
    }


    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void espMarkGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        ShaderEsp esp = ShaderEsp.getInstance();
        if (esp != null && esp.isEnabled() && ShaderEsp.isEspTarget((Entity)(Object)this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void espFriendTeamColor(CallbackInfoReturnable<Integer> cir) {
        ShaderEsp esp = ShaderEsp.getInstance();
        if (esp == null || !esp.isEnabled()) return;
        Entity self = (Entity)(Object)this;
        if (!(self instanceof PlayerEntity p)) return;
        if (Client.get().friendManager().isFriend(p.getName().getString())) {
            cir.setReturnValue(0x55FF55);
        }
    }

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void onPushAwayFrom(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;


        if (!(self instanceof ClientPlayerEntity)) {
            return;
        }


        NoPush noPush = NoPush.get();
        if (noPush == null || !noPush.isEnabled()) {
            return;
        }

        if (entity instanceof PlayerEntity && noPush.entity.getValue()) {
            ci.cancel();
        } else if (entity instanceof LivingEntity && !(entity instanceof PlayerEntity) && noPush.entity.getValue()) {
            ci.cancel();
        }
    }
}
