package com.drinfonty.redfx.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

@Mixin(Particle.class)
public abstract class ParticleMixin implements BloodSmokeAccessor {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected net.minecraft.client.multiplayer.ClientLevel level;
    @Shadow public abstract void remove();

    private boolean redfx$isBloodSmoke = false;

    @Override
    public void redfx$setBloodSmoke(boolean value) {
        this.redfx$isBloodSmoke = value;
    }

    @Override
    public boolean redfx$isBloodSmoke() {
        return this.redfx$isBloodSmoke;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (this.redfx$isBloodSmoke) {
            BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
            if (!this.level.getFluidState(pos).is(FluidTags.WATER)) {
                this.remove();
            }
        }
    }
}
