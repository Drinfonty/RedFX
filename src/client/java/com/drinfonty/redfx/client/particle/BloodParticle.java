package com.drinfonty.redfx.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import com.drinfonty.redfx.config.RedfxConfig;
import org.joml.Quaternionf;

public class BloodParticle extends TerrainParticle {
    private boolean landed = false;
    private int landedTicks = 0;
    private final int targetLandedTicks;
    private final int splatIndex; // Picks one of 5 splat patterns (1 to 5)

    // Define a static camera facing mode that locks the particle flat on the ground (XZ plane)
    private static final SingleQuadParticle.FacingCameraMode FLAT_ON_GROUND = (quaternion, camera, partialTicks) -> {
        quaternion.rotationX((float) (-Math.PI / 2.0)); // Rotate -90 degrees around X-axis (face upwards)
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
        this.targetLandedTicks = Math.max(20, finalTicks); // Ensure at least 1s lifetime
        
        // Setup initial physical properties
        // We set total lifetime to be high enough so it doesn't get removed while falling in air
        this.lifetime = 300 + this.targetLandedTicks; 
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
            // Check if the block directly below the particle is broken (became air)
            net.minecraft.core.BlockPos belowPos = net.minecraft.core.BlockPos.containing(this.x, this.y - 0.1, this.z);
            if (this.level.getBlockState(belowPos).isAir()) {
                // Ground was broken, remove the splat particle immediately!
                this.remove();
                return;
            } else {
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
        }

        // Store pre-tick velocities to detect wall collisions
        double oldXd = this.xd;
        double oldZd = this.zd;

        // Perform standard physics tick
        super.tick();

        if (this.onGround) {
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
                this.gravity = 0;
                this.xd = 0;
                this.yd = 0;
                this.zd = 0;
                this.alpha = 1.0f; // Reset alpha to full
                
                // Lift particle slightly above the ground block to prevent z-fighting
                this.y += 0.02;
                this.setPos(this.x, this.y, this.z);

                // Double the quad size on landing to compensate for transparent texture borders
                if (RedfxConfig.get().useSplatTexture) {
                    this.quadSize *= 2.0F;
                }
            }
        } else {
            // Check for wall collision (horizontal velocity got stopped)
            boolean hitWallX = Math.abs(oldXd) > 0.01 && Math.abs(this.xd) < 0.001;
            boolean hitWallZ = Math.abs(oldZd) > 0.01 && Math.abs(this.zd) < 0.001;
            
            if (hitWallX || hitWallZ) {
                // Slide down the wall slowly
                this.yd = -0.04;
                this.xd *= 0.1; // slow down horizontal movements
                this.zd *= 0.1;
            }
        }
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        if (this.landed) {
            return FLAT_ON_GROUND;
        }
        return super.getFacingCameraMode();
    }

    // Override the rendering layer to enable alpha blending/translucency when landed (otherwise opaque terrain is used)
    @Override
    public SingleQuadParticle.Layer getLayer() {
        if (this.landed && RedfxConfig.get().useSplatTexture) {
            return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
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

    // Query lighting 0.2 blocks above the landed splat to prevent it sampling inside/under the ground block (which is dark/black)
    @Override
    protected int getLightCoords(float partialTicks) {
        if (this.landed) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(this.x, this.y + 0.2, this.z);
            return this.level.isLoaded(pos) ? net.minecraft.client.renderer.LevelRenderer.getLightCoords(this.level, pos) : 0;
        }
        return super.getLightCoords(partialTicks);
    }
}
