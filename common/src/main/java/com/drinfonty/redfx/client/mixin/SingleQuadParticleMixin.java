package com.drinfonty.redfx.client.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.drinfonty.redfx.client.particle.ParticleAlphaAccessor;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin implements ParticleAlphaAccessor {
    @Shadow protected float alpha;

    @Override
    public void redfx$setAlpha(float alpha) {
        this.alpha = alpha;
    }
}
