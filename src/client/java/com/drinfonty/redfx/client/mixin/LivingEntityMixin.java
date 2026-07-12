package com.drinfonty.redfx.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.drinfonty.redfx.config.RedfxConfig;
import com.drinfonty.redfx.client.particle.BloodParticle;

// Import mobs for entity-specific blood colors
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

// Import item types & tags for weapon scaling
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;

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
        float configMultiplier = RedfxConfig.get().getMultiplier();
        float weaponMultiplier = getWeaponMultiplier(entity);
        float totalMultiplier = configMultiplier * weaponMultiplier;

        int baseCount = isDeath ? (25 + entity.getRandom().nextInt(15)) : (12 + entity.getRandom().nextInt(8));
        int count = Math.round(baseCount * totalMultiplier);
        
        if (count <= 0) return;
        
        com.drinfonty.redfx.RedfxMod.LOGGER.info("Spawning {} blood particles (totalMultiplier={}, weaponScale={}) for entity {} (yaw={})", 
            count, totalMultiplier, weaponMultiplier, entity.getType().getDescriptionId(), yaw);
        
        // Calculate force direction away from the source of the blow
        float absoluteAngle = entity.getYRot() + yaw;
        float rad = absoluteAngle * ((float)Math.PI / 180F);
        
        // Attacker is in direction (sin(rad), -cos(rad)). Blood sprays in opposite direction:
        double forceX = -Math.sin(rad);
        double forceZ = Math.cos(rad);

        String particleType = RedfxConfig.get().particleType;
        boolean isPoof = particleType.equals("RedPoof");
        
        // Base canvas mapping
        net.minecraft.world.level.block.state.BlockState blockState = null;
        if (!isPoof) {
            if (particleType.equals("TNT")) {
                blockState = Blocks.TNT.defaultBlockState();
            } else {
                // Use white wool as a clean canvas for color tinting
                blockState = Blocks.WHITE_WOOL.defaultBlockState();
            }
        }

        // Determine entity-specific blood colors
        float r = 1.0F;
        float g = 0.05F;
        float b = 0.05F;

        if (entity instanceof Blaze || entity instanceof MagmaCube) {
            // Yellow/Orange Flame
            r = 0.9F;
            g = 0.7F;
            b = 0.1F;
        } else if (entity instanceof Slime || entity instanceof Creeper) {
            // Lime Green
            r = 0.2F;
            g = 0.9F;
            b = 0.2F;
        } else if (entity instanceof EnderMan || entity instanceof EnderDragon) {
            // Purple
            r = 0.6F;
            g = 0.1F;
            b = 0.8F;
        } else if (entity instanceof AbstractSkeleton) {
            // Bone White
            r = 0.9F;
            g = 0.9F;
            b = 0.9F;
        }

        ClientLevel clientLevel = (ClientLevel) entity.level();

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
                net.minecraft.client.particle.Particle p = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.POOF, px, py, pz, vx, vy, vz
                );
                if (p instanceof net.minecraft.client.particle.SingleQuadParticle sqp) {
                    sqp.setColor(r, g, b);
                    sqp.setLifetime(RedfxConfig.get().particleLifetimeSeconds * 4);
                }
            } else {
                // Spawn our custom sliding/sticking BloodParticle and apply custom tint
                BloodParticle blood = new BloodParticle(clientLevel, px, py, pz, vx, vy, vz, blockState);
                blood.setColor(r, g, b);
                Minecraft.getInstance().particleEngine.add(blood);
            }
        }
    }

    private float getWeaponMultiplier(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1.0f;
        
        // Check if the local player is reasonably close to the entity ( melee/close range )
        double distSq = mc.player.distanceToSqr(entity);
        if (distSq > 36.0) { // Distance > 6 blocks
            // Player is far away. Check if holding a ranged weapon
            if (mc.player.getMainHandItem().getItem() instanceof ProjectileWeaponItem) {
                return 0.6f; // Ranged puncture (less blood spray)
            }
            return 1.0f; // Environmental damage or indirect
        }
        
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            return 0.4f; // Fist/blunt hit (very little blood)
        }
        
        if (stack.is(ItemTags.SWORDS)) {
            return 1.3f; // Sword slashing
        } else if (stack.is(ItemTags.AXES)) {
            return 1.8f; // Heavy Axe chopping
        } else if (stack.getItem() instanceof ProjectileWeaponItem) {
            return 0.6f; // Bow/Crossbow close range
        }
        
        return 1.0f; // Default item
    }
}
