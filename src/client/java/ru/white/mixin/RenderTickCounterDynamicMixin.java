package ru.white.mixin; // Укажите ваш пакет миксинов


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.utils.player.ITimerSpeed;

@Mixin(targets = "net.minecraft.client.render.RenderTickCounter$Dynamic")
public class RenderTickCounterDynamicMixin implements ITimerSpeed {

    @Unique
    private float speed = 1.0F;

    @Shadow private float dynamicDeltaTicks;

    // Реализуем геттер и сеттер из нашего интерфейса
    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    /**
     * Внедряемся в конец метода beginRenderTick(long timeMillis),
     * где рассчитывается dynamicDeltaTicks, и умножаем её на наш speed.
     */
    @Inject(
            method = "beginRenderTick(J)I",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;lastTimeMillis:J",
                    shift = At.Shift.AFTER
            )
    )
    private void applySpeedToDelta(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        // Умножаем дельту на наш коэффициент скорости
        this.dynamicDeltaTicks *= this.speed;
    }
}