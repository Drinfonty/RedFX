package com.drinfonty.redfx.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x")
    double redfx$getX();

    @Accessor("y")
    double redfx$getY();

    @Accessor("z")
    double redfx$getZ();

    @Accessor("level")
    ClientLevel redfx$getLevel();
}
