package com.drinfonty.redfx.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import com.drinfonty.redfx.config.RedfxConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() && RedfxConfig.get().bloodEnabled) {
            // Check if damage has just occurred in this tick (hurtTime equals hurtDuration)
            if (self.hurtTime == self.hurtDuration && self.hurtTime > 0 && self.deathTime == 0) {
                float yaw = self.getHurtDir();
                spawnBloodParticles(self, yaw, false);
            }
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onHandleEntityEvent(byte status, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (status == 3 && RedfxConfig.get().bloodEnabled) {
            // Entity death status code is 3
            com.drinfonty.redfx.RedfxMod.LOGGER.info("Death status (3) detected for entity {}!", self.getType().getDescriptionId());
            if (self.level().isClientSide()) {
                spawnBloodParticles(self, 0.0f, true); // Larger burst on death
            }
        }
    }

    private void spawnBloodParticles(LivingEntity entity, float yaw, boolean isDeath) {
        BlockParticleOption blockParticle = new BlockParticleOption(
            ParticleTypes.BLOCK, 
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        
        float multiplier = RedfxConfig.get().getMultiplier();
        int baseCount = isDeath ? (25 + entity.getRandom().nextInt(15)) : (12 + entity.getRandom().nextInt(8));
        int count = Math.round(baseCount * multiplier);
        
        if (count <= 0) return;
        
        com.drinfonty.redfx.RedfxMod.LOGGER.info("Spawning {} blood particles (multiplier={}) for entity {} (yaw={})", 
            count, multiplier, entity.getType().getDescriptionId(), yaw);
        
        // Calculate force direction away from the source of the blow
        float absoluteAngle = entity.getYRot() + yaw;
        float rad = absoluteAngle * ((float)Math.PI / 180F);
        
        // Attacker is in direction (sin(rad), -cos(rad)). Blood sprays in opposite direction:
        double forceX = -Math.sin(rad);
        double forceZ = Math.cos(rad);

        boolean isPoof = RedfxConfig.get().particleType.equals("RedPoof");

        for (int i = 0; i < count; i++) {
            double px = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.8;
            double py = entity.getY() + entity.getBbHeight() * 0.5 + (entity.getRandom().nextDouble() - 0.5) * entity.getBbHeight() * 0.5;
            double pz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.8;
            
            // Adjust velocity based on attack direction + random spread
            double spreadSpeed = 0.1 + entity.getRandom().nextDouble() * 0.2;
            double vx = forceX * spreadSpeed + (entity.getRandom().nextDouble() - 0.5) * 0.15;
            double vy = 0.15 + entity.getRandom().nextDouble() * 0.25;
            double vz = forceZ * spreadSpeed + (entity.getRandom().nextDouble() - 0.5) * 0.15;
            
            if (isPoof) {
                net.minecraft.client.particle.Particle p = net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.POOF, px, py, pz, vx, vy, vz
                );
                if (p instanceof net.minecraft.client.particle.SingleQuadParticle sqp) {
                    // Set color to bright blood red (1.0, 0.05, 0.05)
                    sqp.setColor(1.0f, 0.05f, 0.05f);
                }
            } else {
                entity.level().addParticle(blockParticle, px, py, pz, vx, vy, vz);
            }
        }
    }
}
