package com.drinfonty.redfx.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.level.block.state.BlockState;
import com.drinfonty.redfx.config.RedfxConfig;

public class BloodParticle extends TerrainParticle {
    private boolean landed = false;
    private int landedTicks = 0;
    private final int targetLandedTicks;

    public BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, BlockState state) {
        super(level, x, y, z, vx, vy, vz, state);
        
        // Retrieve lifetime setting from config (seconds to ticks)
        this.targetLandedTicks = RedfxConfig.get().particleLifetimeSeconds * 20;
        
        // Setup initial physical properties
        // We set total lifetime to be high enough so it doesn't get removed while falling in air
        this.lifetime = 300 + this.targetLandedTicks; 
        this.gravity = 1.0F; // affected by gravity
        this.friction = 0.98F; // standard drag
        this.hasPhysics = true;
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

        // Store pre-tick velocities to detect wall collisions
        double oldXd = this.xd;
        double oldZd = this.zd;

        // Perform standard physics tick
        super.tick();

        if (this.onGround) {
            this.landed = true;
            this.gravity = 0;
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;
            this.alpha = 1.0f; // Reset alpha to full
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
}
