package com.drinfonty.redfx.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import com.drinfonty.redfx.client.particle.BloodDripAccessor;
import com.drinfonty.redfx.client.particle.BloodParticle;
import com.drinfonty.redfx.client.particle.BloodSmokeAccessor;

@Mixin(Particle.class)
public abstract class ParticleMixin implements BloodSmokeAccessor, BloodDripAccessor {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected boolean onGround;
    @Shadow protected net.minecraft.util.RandomSource random;
    @Shadow protected net.minecraft.client.multiplayer.ClientLevel level;
    @Shadow public abstract void remove();

    private boolean redfx$isBloodSmoke = false;
    private boolean redfx$isBloodDrip = false;
    private float redfx$dripR = 1.0f;
    private float redfx$dripG = 0.0f;
    private float redfx$dripB = 0.0f;

    @Override
    public void redfx$setBloodSmoke(boolean value) {
        this.redfx$isBloodSmoke = value;
    }

    @Override
    public boolean redfx$isBloodSmoke() {
        return this.redfx$isBloodSmoke;
    }

    @Override
    public void redfx$setBloodDrip(float r, float g, float b) {
        this.redfx$isBloodDrip = true;
        this.redfx$dripR = r;
        this.redfx$dripG = g;
        this.redfx$dripB = b;
    }

    @Override
    public boolean redfx$isBloodDrip() {
        return this.redfx$isBloodDrip;
    }

    @Override
    public float redfx$getDripR() {
        return this.redfx$dripR;
    }

    @Override
    public float redfx$getDripG() {
        return this.redfx$dripG;
    }

    @Override
    public float redfx$getDripB() {
        return this.redfx$dripB;
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void onMove(double x, double y, double z, CallbackInfo ci) {
        if (this.redfx$isBloodSmoke) {
            BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
            if (!this.level.getFluidState(pos).is(FluidTags.WATER)) {
                this.remove();
            }
        }
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void onMoveReturn(double x, double y, double z, CallbackInfo ci) {
        if (this.redfx$isBloodDrip && this.onGround) {
            this.onGround = false;
            this.remove();
            BloodParticle.handleLanding(this.level, this.x, this.y, this.z,
                this.redfx$dripR, this.redfx$dripG, this.redfx$dripB, this.random);
        }
    }
}
