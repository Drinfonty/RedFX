package com.drinfonty.redfx.client.mixin;

import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import com.drinfonty.redfx.client.particle.BloodSmokeAccessor;

@Mixin(CampfireSmokeParticle.class)
public abstract class CampfireSmokeParticleMixin implements BloodSmokeAccessor {
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
            ParticleAccessor acc = (ParticleAccessor) (Object) this;
            BlockPos pos = BlockPos.containing(acc.redfx$getX(), acc.redfx$getY(), acc.redfx$getZ());
            if (!acc.redfx$getLevel().getFluidState(pos).is(FluidTags.WATER)) {
                ((Particle) (Object) this).remove();
            }
        }
    }
}
