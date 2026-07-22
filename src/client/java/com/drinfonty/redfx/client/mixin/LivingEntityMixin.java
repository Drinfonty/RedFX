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
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;

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

            // Drip effect: low on health (<= 35% health)
            if (self.getHealth() > 0.0F && self.getHealth() / self.getMaxHealth() <= 0.35F && self.deathTime == 0) {
                // Randomize drip frequency: ~5% chance per tick (avg 1s interval)
                if (self.getRandom().nextFloat() < 0.05F) {
                    spawnBloodDrip(self);
                }
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

                // Spawn bone fragments if entity has bones
                if (hasBones(self)) {
                    spawnBoneFragments(self);
                }
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

        // Determine base entity-specific blood colors
        float[] baseColor = getBloodColor(entity);
        float r = baseColor[0];
        float g = baseColor[1];
        float b = baseColor[2];

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
            
            // Introduce stronger color variation per particle (+/- 0.18 variance)
            float variance = (entity.getRandom().nextFloat() - 0.5F) * 0.36F; // -0.18 to +0.18
            float particleR = Math.max(0.0F, Math.min(1.0F, r + variance));
            float particleG = Math.max(0.0F, Math.min(1.0F, g + (entity.getRandom().nextFloat() - 0.5F) * 0.18F));
            float particleB = Math.max(0.0F, Math.min(1.0F, b + (entity.getRandom().nextFloat() - 0.5F) * 0.18F));
            
            if (isPoof) {
                net.minecraft.client.particle.Particle p = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.POOF, px, py, pz, vx, vy, vz
                );
                if (p instanceof net.minecraft.client.particle.SingleQuadParticle sqp) {
                    sqp.setColor(particleR, particleG, particleB);
                    sqp.setLifetime(RedfxConfig.get().particleLifetimeSeconds * 4);
                }
            } else {
                // Spawn our custom sliding/sticking BloodParticle and apply custom tint
                BloodParticle blood = new BloodParticle(clientLevel, px, py, pz, vx, vy, vz, blockState);
                blood.setColor(particleR, particleG, particleB);
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

    private float[] getBloodColor(LivingEntity entity) {
        float r = 1.0F;
        float g = 0.05F;
        float b = 0.05F;

        if (entity instanceof Blaze || entity instanceof MagmaCube) {
            r = 0.9F;
            g = 0.7F;
            b = 0.1F;
        } else if (entity instanceof Slime || entity instanceof Creeper) {
            r = 0.2F;
            g = 0.9F;
            b = 0.2F;
        } else if (entity instanceof EnderMan || entity instanceof EnderDragon) {
            r = 0.6F;
            g = 0.1F;
            b = 0.8F;
        } else if (entity instanceof AbstractSkeleton) {
            r = 0.9F;
            g = 0.9F;
            b = 0.9F;
        } else if (entity instanceof Warden) {
            r = 0.05F;
            g = 0.3F;
            b = 0.7F;
        }
        return new float[]{r, g, b};
    }

    private void spawnBloodDrip(LivingEntity entity) {
        ClientLevel clientLevel = (ClientLevel) entity.level();
        String particleType = RedfxConfig.get().particleType;
        boolean isPoof = particleType.equals("RedPoof");
        
        net.minecraft.world.level.block.state.BlockState blockState = null;
        if (!isPoof) {
            if (particleType.equals("TNT")) {
                blockState = Blocks.TNT.defaultBlockState();
            } else {
                blockState = Blocks.WHITE_WOOL.defaultBlockState();
            }
        }

        float[] baseColor = getBloodColor(entity);
        float r = baseColor[0];
        float g = baseColor[1];
        float b = baseColor[2];

        double px = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.5;
        double py = entity.getY() + entity.getBbHeight() * 0.4 + (entity.getRandom().nextDouble() - 0.5) * entity.getBbHeight() * 0.2;
        double pz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.5;

        // Dripping velocity (slowly falling straight down)
        double vx = (entity.getRandom().nextDouble() - 0.5) * 0.01;
        double vy = -0.04 - entity.getRandom().nextDouble() * 0.04;
        double vz = (entity.getRandom().nextDouble() - 0.5) * 0.01;

        float variance = (entity.getRandom().nextFloat() - 0.5F) * 0.36F;
        float particleR = Math.max(0.0F, Math.min(1.0F, r + variance));
        float particleG = Math.max(0.0F, Math.min(1.0F, g + (entity.getRandom().nextFloat() - 0.5F) * 0.18F));
        float particleB = Math.max(0.0F, Math.min(1.0F, b + (entity.getRandom().nextFloat() - 0.5F) * 0.18F));

        if (isPoof) {
            net.minecraft.client.particle.Particle p = Minecraft.getInstance().particleEngine.createParticle(
                ParticleTypes.POOF, px, py, pz, vx, vy, vz
            );
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle sqp) {
                sqp.setColor(particleR, particleG, particleB);
                sqp.setLifetime(RedfxConfig.get().particleLifetimeSeconds * 4);
            }
        } else {
            BloodParticle blood = new BloodParticle(clientLevel, px, py, pz, vx, vy, vz, blockState);
            blood.setColor(particleR, particleG, particleB);
            Minecraft.getInstance().particleEngine.add(blood);
        }
    }

    private boolean hasBones(LivingEntity entity) {
        if (entity instanceof Animal || entity instanceof Player || entity instanceof AbstractSkeleton) {
            return true;
        }
        String simpleName = entity.getClass().getSimpleName();
        return simpleName.equals("Zombie")
            || simpleName.equals("ZombieVillager")
            || simpleName.equals("Husk")
            || simpleName.equals("Drowned")
            || simpleName.equals("ZombifiedPiglin")
            || simpleName.equals("Piglin")
            || simpleName.equals("PiglinBrute")
            || simpleName.equals("SkeletonHorse")
            || simpleName.equals("Villager")
            || simpleName.equals("WanderingTrader")
            || simpleName.equals("Witch")
            || simpleName.equals("Evoker")
            || simpleName.equals("Vindicator")
            || simpleName.equals("Pillager")
            || simpleName.equals("Illusioner");
    }

    private void spawnBoneFragments(LivingEntity entity) {
        ClientLevel clientLevel = (ClientLevel) entity.level();
        net.minecraft.world.level.block.state.BlockState boneBlockState = Blocks.BONE_BLOCK.defaultBlockState();

        int count = 3 + entity.getRandom().nextInt(4); // Spawn 3 to 6 bone fragment shards
        for (int i = 0; i < count; i++) {
            double px = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.7;
            double py = entity.getY() + entity.getBbHeight() * 0.5 + (entity.getRandom().nextDouble() - 0.5) * entity.getBbHeight() * 0.4;
            double pz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 0.7;

            // Sprays in random direction with standard explosion velocity
            double vx = (entity.getRandom().nextDouble() - 0.5) * 0.25;
            double vy = 0.2 + entity.getRandom().nextDouble() * 0.3;
            double vz = (entity.getRandom().nextDouble() - 0.5) * 0.25;

            BloodParticle boneParticle = new BloodParticle(clientLevel, px, py, pz, vx, vy, vz, boneBlockState);
            boneParticle.isBoneFragment = true; // Mark as bone fragment to skip blood splat texture swap
            boneParticle.setColor(1.0F, 1.0F, 1.0F); // No color tinting (keeps white bone texture)
            
            // Randomize size slightly so some are small chips and some are bigger shards
            boneParticle.setScale(0.8F + entity.getRandom().nextFloat() * 0.6F); 

            Minecraft.getInstance().particleEngine.add(boneParticle);
        }
    }
}
