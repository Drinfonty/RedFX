package com.drinfonty.redfx.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;
import com.drinfonty.redfx.config.RedfxConfig;
import org.joml.Quaternionf;

public class BloodParticle extends TerrainParticle {
    private static int globalLandedCounter = 0;
    private boolean landed = false;
    private Direction attachedDirection = null;
    private int landedTicks = 0;
    private final int targetLandedTicks;
    private final int splatIndex; // Picks one of 5 splat patterns (1 to 5)

    // Dynamic facing camera mode that locks the particle flat against the attached surface (floor, wall, or ceiling)
    private final SingleQuadParticle.FacingCameraMode SURFACE_ALIGNED = (quaternion, camera, partialTicks) -> {
        if (this.attachedDirection != null) {
            switch (this.attachedDirection) {
                case UP -> quaternion.rotationX((float) (-Math.PI / 2.0)).rotateZ(this.roll);
                case DOWN -> quaternion.rotationX((float) (Math.PI / 2.0)).rotateZ(this.roll);
                case NORTH -> quaternion.rotationY((float) Math.PI).rotateZ(this.roll);
                case SOUTH -> quaternion.rotationY(0.0f).rotateZ(this.roll);
                case EAST -> quaternion.rotationY((float) (Math.PI / 2.0)).rotateZ(this.roll);
                case WEST -> quaternion.rotationY((float) (-Math.PI / 2.0)).rotateZ(this.roll);
            }
        }
    };

    public BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, BlockState state) {
        super(level, x, y, z, vx, vy, vz, state);
        
        // Retrieve lifetime setting from config (seconds to ticks) with +/- 25% random variance
        int baseLifetimeTicks = RedfxConfig.get().particleLifetimeSeconds * 20;
        int variance = (int) (baseLifetimeTicks * 0.25F);
        int finalTicks = baseLifetimeTicks;
        if (variance > 0) {
            finalTicks += this.random.nextInt(variance * 2) - variance;
        }
        this.targetLandedTicks = Math.max(20, finalTicks); // Ensure at least 1s landed lifetime
        
        // Setup initial physical properties for flying phase (short lifetime if it doesn't land)
        this.lifetime = 40; // max 2s flying in air before despawning
        this.gravity = 1.0F; // affected by gravity
        this.friction = 0.98F; // standard drag
        this.hasPhysics = true;

        // Randomize initial rotation/roll (radians)
        this.roll = (float) (this.random.nextFloat() * Math.PI * 2.0);
        this.oRoll = this.roll;

        // Pick a random splat pattern from 1 to 5
        this.splatIndex = 1 + this.random.nextInt(5);

        // Randomize size on spawn (ranging from 0.8x to 1.8x of base size)
        float sizeScale = 0.8F + this.random.nextFloat() * 1.0F;
        this.quadSize *= sizeScale;

        // If using splat textures, start at half size while flying to prevent shrinking on landing
        if (RedfxConfig.get().useSplatTexture) {
            this.quadSize *= 0.5F;
        }
    }

    private BlockPos getAttachedBlockPos(Direction dir) {
        return switch (dir) {
            case UP -> BlockPos.containing(this.x, this.y - 0.2, this.z);
            case DOWN -> BlockPos.containing(this.x, this.y + 0.2, this.z);
            case WEST -> BlockPos.containing(this.x + 0.2, this.y, this.z);
            case EAST -> BlockPos.containing(this.x - 0.2, this.y, this.z);
            case NORTH -> BlockPos.containing(this.x, this.y, this.z + 0.2);
            case SOUTH -> BlockPos.containing(this.x, this.y, this.z - 0.2);
        };
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.landed) {
            // Check if the attached block behind the splat is broken (became air)
            if (this.attachedDirection != null) {
                BlockPos attachedBlockPos = getAttachedBlockPos(this.attachedDirection);
                if (this.level.getBlockState(attachedBlockPos).isAir()) {
                    // Surface block was broken, remove the splat particle immediately!
                    this.remove();
                    return;
                }
            }
            
            this.landedTicks++;
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;
            
            // Fade out near the end of life
            if (this.landedTicks >= this.targetLandedTicks) {
                this.remove();
            } else if (this.landedTicks > this.targetLandedTicks - 20) {
                // Fade out over last 1 second (20 ticks)
                float ratio = (float)(this.targetLandedTicks - this.landedTicks) / 20.0f;
                this.alpha = ratio;
            }
            return;
        }

        // Check if the particle is currently underwater
        BlockPos currentPos = BlockPos.containing(this.x, this.y, this.z);
        boolean inWater = this.level.getFluidState(currentPos).is(FluidTags.WATER);

        if (inWater) {
            this.gravity = 0.02F; // Hover / drift slowly
            this.friction = 0.90F; // More drag/viscosity underwater
            
            // Limit remaining lifetime to at most 15 ticks and fade out alpha
            this.lifetime = Math.min(this.lifetime, this.age + 15);
            if (this.lifetime > this.age) {
                this.alpha = (float)(this.lifetime - this.age) / 15.0f;
            } else {
                this.alpha = 0.0f;
            }
            
            // Spawn color-tinted smoke particle to simulate dispersion (30% chance per tick)
            if (this.random.nextFloat() < 0.30F) {
                try {
                    Particle smoke = Minecraft.getInstance().particleEngine.createParticle(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        this.x, this.y, this.z,
                        (this.random.nextDouble() - 0.5) * 0.02,
                        0.01 + this.random.nextDouble() * 0.02,
                        (this.random.nextDouble() - 0.5) * 0.02
                    );
                    if (smoke instanceof SingleQuadParticle sqp) {
                        sqp.setColor(this.rCol, this.gCol, this.bCol);
                    }
                    if (smoke != null) {
                        Minecraft.getInstance().particleEngine.add(smoke);
                    }
                } catch (Exception e) {
                    // Ignore particle creation errors
                }
            }
        }

        // Store pre-tick velocities
        double oldXd = this.xd;
        double oldYd = this.yd;
        double oldZd = this.zd;

        // Perform standard physics tick
        super.tick();

        // Determine surface collision direction using Minecraft's internal collision signals (only if not underwater)
        Direction hitDirection = null;
        if (!inWater) {
            if (this.onGround) {
                hitDirection = Direction.UP;
            } else if (oldYd > 0.01 && this.yd == 0.0) {
                hitDirection = Direction.DOWN;
            } else if (Math.abs(oldXd) > 0.01 && this.xd == 0.0) {
                hitDirection = oldXd > 0 ? Direction.WEST : Direction.EAST;
            } else if (Math.abs(oldZd) > 0.01 && this.zd == 0.0) {
                hitDirection = oldZd > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }

        // Verify that the candidate surface block actually exists and is NOT air
        if (hitDirection != null) {
            BlockPos targetBlock = getAttachedBlockPos(hitDirection);
            if (this.level.getBlockState(targetBlock).isAir()) {
                hitDirection = null; // Do not land in midair!
            }
        }

        if (hitDirection != null) {
            boolean swapSuccess = false;

            // Switch to custom flat splat texture on landing if enabled!
            if (RedfxConfig.get().useSplatTexture) {
                try {
                    TextureAtlas blocksAtlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
                    if (blocksAtlas != null) {
                        TextureAtlasSprite splatSprite = blocksAtlas.getSprite(
                            Identifier.fromNamespaceAndPath("redfx", "block/blood_splat_" + this.splatIndex)
                        );
                        if (splatSprite != null) {
                            this.setSprite(splatSprite);
                            swapSuccess = true;
                        }
                    }
                } catch (Exception e) {
                    // Retry on subsequent ticks if resource manager is reloading/busy
                }
            } else {
                swapSuccess = true; // Immediate landing if custom textures are disabled
            }

            if (swapSuccess) {
                this.landed = true;
                this.attachedDirection = hitDirection;
                this.lifetime = this.age + 300 + this.targetLandedTicks; // Extend lifetime for landed splat state
                this.gravity = 0;
                this.xd = 0;
                this.yd = 0;
                this.zd = 0;
                this.alpha = 1.0f; // Reset alpha to full
                
                // Position quad center flush against surface using 64 guaranteed distinct depth layers (0.005 to 0.043 blocks) to eliminate all Z-fighting lines
                int depthTier = (globalLandedCounter++) & 63;
                double depthJitter = 0.005 + (depthTier * 0.0006);
                net.minecraft.world.phys.AABB bb = this.getBoundingBox();
                switch (hitDirection) {
                    case UP -> this.y = bb.minY + depthJitter;
                    case DOWN -> this.y = bb.maxY - depthJitter;
                    case WEST -> this.x = bb.maxX - depthJitter;
                    case EAST -> this.x = bb.minX + depthJitter;
                    case NORTH -> this.z = bb.maxZ - depthJitter;
                    case SOUTH -> this.z = bb.minZ + depthJitter;
                }
                this.setPos(this.x, this.y, this.z);

                // Double the quad size on landing to compensate for transparent texture borders
                if (RedfxConfig.get().useSplatTexture) {
                    this.quadSize *= 2.0F;
                }

                // Spawn 1 falling dust particle upon surface impact, matching the blood droplet's color (if enabled)
                if (RedfxConfig.get().enableSplatDust) {
                    try {
                        BlockState dustState = Blocks.WHITE_WOOL.defaultBlockState();
                        double dustVx = (this.random.nextDouble() - 0.5) * 0.04;
                        double dustVy = 0.02 + this.random.nextDouble() * 0.03;
                        double dustVz = (this.random.nextDouble() - 0.5) * 0.04;
                        
                        Particle dustParticle = Minecraft.getInstance().particleEngine.createParticle(
                            new BlockParticleOption(ParticleTypes.FALLING_DUST, dustState),
                            this.x, this.y, this.z, dustVx, dustVy, dustVz
                        );
                        if (dustParticle instanceof SingleQuadParticle sqp) {
                            sqp.setColor(this.rCol, this.gCol, this.bCol);
                        }
                        if (dustParticle != null) {
                            Minecraft.getInstance().particleEngine.add(dustParticle);
                        }
                    } catch (Exception e) {
                        // Ignore particle creation errors
                    }
                }
            }
        }
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        if (this.landed) {
            return SURFACE_ALIGNED;
        }
        return super.getFacingCameraMode();
    }

    // Override the rendering layer to enable alpha blending/translucency when landed (otherwise opaque terrain is used)
    @Override
    public SingleQuadParticle.Layer getLayer() {
        if (this.landed && RedfxConfig.get().useSplatTexture) {
            return SingleQuadParticle.Layer.TERRAIN;
        }
        return super.getLayer();
    }

    // Override UV mappings when landed to output the full custom splat texture instead of block crack snippets
    @Override
    protected float getU0() {
        return (this.landed && RedfxConfig.get().useSplatTexture) ? this.sprite.getU0() : super.getU0();
    }

    @Override
    protected float getU1() {
        return (this.landed && RedfxConfig.get().useSplatTexture) ? this.sprite.getU1() : super.getU1();
    }

    @Override
    protected float getV0() {
        return (this.landed && RedfxConfig.get().useSplatTexture) ? this.sprite.getV0() : super.getV0();
    }

    @Override
    protected float getV1() {
        return (this.landed && RedfxConfig.get().useSplatTexture) ? this.sprite.getV1() : super.getV1();
    }

    // Query lighting 0.2 blocks in front of the landed splat to prevent it sampling inside solid ground/wall blocks
    @Override
    public int getLightColor(float partialTicks) {
        if (this.landed && this.attachedDirection != null) {
            BlockPos pos = switch (this.attachedDirection) {
                case UP -> BlockPos.containing(this.x, this.y + 0.2, this.z);
                case DOWN -> BlockPos.containing(this.x, this.y - 0.2, this.z);
                case WEST -> BlockPos.containing(this.x - 0.2, this.y, this.z);
                case EAST -> BlockPos.containing(this.x + 0.2, this.y, this.z);
                case NORTH -> BlockPos.containing(this.x, this.y, this.z - 0.2);
                case SOUTH -> BlockPos.containing(this.x, this.y, this.z + 0.2);
            };
            return this.level.hasChunkAt(pos) ? net.minecraft.client.renderer.LevelRenderer.getLightColor(this.level, pos) : 0;
        }
        return super.getLightColor(partialTicks);
    }
}
