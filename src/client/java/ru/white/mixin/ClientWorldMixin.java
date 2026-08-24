package ru.white.mixin;

import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.utils.annotation.IMinecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin implements IMinecraft {

    @Shadow
    @Final
    private ClientWorld.Properties clientWorldProperties;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initHook(CallbackInfo info) {
        new WorldLoadEvent().hook();
    }



}