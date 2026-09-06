package com.drinfonty.redfx.client.particle;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;

import com.drinfonty.redfx.canvas.BloodSplatter;
import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.canvas.FaceStroke;
import com.drinfonty.redfx.canvas.PaintColor;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.PaintSurface;
import com.drinfonty.redfx.config.RedfxConfig;

public class BloodParticle extends TerrainParticle {
    private final int splatIndex; // Picks one of 5 splat patterns (1 to 5)

    public BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, BlockState state) {
        super(level, x, y, z, vx, vy, vz, state);
        
        this.lifetime = 40; // max 2s flying in air before despawning if it doesn't hit anything
        this.gravity = 1.0F;
        this.friction = 0.98F;
        this.hasPhysics = true;

        this.roll = (float) (this.random.nextFloat() * Math.PI * 2.0);
        this.oRoll = this.roll;

        this.splatIndex = 1 + this.random.nextInt(5);

        float sizeScale = 0.8F + this.random.nextFloat() * 1.0F;
        this.quadSize *= sizeScale * RedfxConfig.get().particleSizeScale * 0.8F;
    }

    private BlockPos getAttachedBlockPos(Direction dir) {
        return switch (dir) {
            case UP -> {
                BlockPos below = BlockPos.containing(this.x, this.y - 0.2, this.z);
                BlockPos above = below.above();
                BlockState aboveState = this.level.getBlockState(above);
                if (!aboveState.isAir() && aboveState.getFluidState().isEmpty()) {
                    double top = PaintSurface.topOf(this.level, above, aboveState);
                    if (top != PaintSurface.NONE && top < 1.0) {
                        yield above;
                    }
                }
                yield below;
            }
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

        BlockPos currentPos = BlockPos.containing(this.x, this.y, this.z);
        boolean inWater = this.level.getFluidState(currentPos).is(FluidTags.WATER);

        if (inWater) {
            this.gravity = 0.02F;
            this.friction = 0.90F;
            
            this.lifetime = Math.min(this.lifetime, this.age + 15);
            if (this.lifetime > this.age) {
                this.alpha = (float)(this.lifetime - this.age) / 15.0f;
            } else {
                this.alpha = 0.0f;
            }
            
            if (this.random.nextFloat() < 0.30F) {
                try {
                    boolean isCampfire = RedfxConfig.get().waterParticleType.equals("CampfireSmoke");
                    Particle smoke = Minecraft.getInstance().particleEngine.createParticle(
                        isCampfire ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE,
                        this.x, this.y, this.z,
                        (this.random.nextDouble() - 0.5) * 0.02,
                        0.01 + this.random.nextDouble() * 0.02,
                        (this.random.nextDouble() - 0.5) * 0.02
                    );
                    if (smoke instanceof SingleQuadParticle sqp) {
                        sqp.setColor(this.rCol, this.gCol, this.bCol);
                    }
                    if (smoke instanceof BloodSmokeAccessor bsa) {
                        bsa.redfx$setBloodSmoke(true);
                    }
                    if (smoke != null) {
                        Minecraft.getInstance().particleEngine.add(smoke);
                    }
                } catch (Exception e) {
                }
            }
        }

        double oldXd = this.xd;
        double oldYd = this.yd;
        double oldZd = this.zd;

        super.tick();

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

        if (hitDirection != null) {
            BlockPos targetBlock = getAttachedBlockPos(hitDirection);
            BlockState targetState = this.level.getBlockState(targetBlock);
            if (targetState.isAir() || !targetState.getFluidState().isEmpty()) {
                hitDirection = null;
            } else {
                stampToCanvas(targetBlock, hitDirection);

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
                    }
                }

                this.remove();
            }
        }
    }

    private void stampToCanvas(BlockPos targetBlock, Direction hitDirection) {
        int face = hitDirection.get3DDataValue();
        double lx = this.x - targetBlock.getX();
        double ly = this.y - targetBlock.getY();
        double lz = this.z - targetBlock.getZ();

        lx = Math.max(0.0, Math.min(1.0, lx));
        ly = Math.max(0.0, Math.min(1.0, ly));
        lz = Math.max(0.0, Math.min(1.0, lz));

        double uCoord = FaceAxes.u(face, lx, ly, lz);
        double vCoord = FaceAxes.v(face, lx, ly, lz);

        int centerU = FaceAxes.texel(uCoord);
        int centerV = FaceAxes.texel(vCoord);

        int argb = PaintColor.fromRgb(this.rCol, this.gCol, this.bCol);

        int baseLifetimeSec = RedfxConfig.get().particleLifetimeSeconds;
        int variance = (int) (baseLifetimeSec * 0.25F);
        int finalSec = baseLifetimeSec;
        if (variance > 0) {
            finalSec += this.random.nextInt(variance * 2) - variance;
        }
        finalSec = Math.max(1, finalSec);
        long expirationMs = System.currentTimeMillis() + (finalSec * 1000L);

        float scale = 0.8f * RedfxConfig.get().splatSizeScale;

        // Convert the hit point into face-plane global texel coordinates
        int blockU = FaceStroke.blockU(face, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        int blockV = FaceStroke.blockV(face, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        int normal = FaceStroke.normal(face, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());

        int globalCenterU = FaceStroke.encodeU(face, blockU, centerU);
        int globalCenterV = FaceStroke.encodeV(face, blockV, centerV);

        ClientCanvasStore store = ClientCanvasStore.get();
        Map<BlockPos, int[]> modifiedBlocks = new HashMap<>();

        BloodSplatter.stampGlobal(globalCenterU, globalCenterV, argb, this.splatIndex, scale, (gu, gv, col) -> {
            int bu = FaceStroke.blockOfU(face, gu);
            int bv = FaceStroke.blockOfV(face, gv);
            int uTexel = gu - FaceStroke.encodeU(face, bu, 0);
            int vTexel = gv - FaceStroke.encodeV(face, bv, 0);

            if (uTexel < 0 || uTexel >= Canvas.SIZE || vTexel < 0 || vTexel >= Canvas.SIZE) {
                return;
            }

            int wx = FaceStroke.worldX(face, bu, bv, normal);
            int wy = FaceStroke.worldY(face, bu, bv, normal);
            int wz = FaceStroke.worldZ(face, bu, bv, normal);
            BlockPos bPos = new BlockPos(wx, wy, wz);

            if (face == Direction.UP.get3DDataValue()) {
                BlockState bs = this.level.getBlockState(bPos);
                BlockPos abovePos = bPos.above();
                BlockState aboveState = this.level.getBlockState(abovePos);
                if (!aboveState.isAir() && aboveState.getFluidState().isEmpty()) {
                    double top = PaintSurface.topOf(this.level, abovePos, aboveState);
                    if (top != PaintSurface.NONE && top < 1.0) {
                        bPos = abovePos;
                    }
                } else if (bs.isAir()) {
                    BlockPos belowPos = bPos.below();
                    BlockState belowState = this.level.getBlockState(belowPos);
                    if (!belowState.isAir() && belowState.getFluidState().isEmpty()
                        && PaintSurface.topOf(this.level, belowPos, belowState) != PaintSurface.NONE) {
                        bPos = belowPos;
                    }
                }
            }

            int[] texels = modifiedBlocks.get(bPos);
            if (texels == null) {
                // Verify the block on this plane is a valid solid surface
                BlockState bs = this.level.getBlockState(bPos);
                if (bs.isAir() || !bs.getFluidState().isEmpty()) {
                    return;
                }
                if (face == Direction.UP.get3DDataValue()) {
                    if (PaintSurface.topOf(this.level, bPos, bs) == PaintSurface.NONE) {
                        return;
                    }
                } else if (!bs.isFaceSturdy(this.level, bPos, hitDirection) || !bs.isCollisionShapeFullBlock(this.level, bPos)) {
                    return;
                }

                Canvas existing = store.get(bPos, face);
                texels = existing != null ? existing.texels().clone() : new int[Canvas.TEXELS];
                modifiedBlocks.put(bPos, texels);
            }

            int idx = vTexel * Canvas.SIZE + uTexel;
            texels[idx] = col;
        });

        // Publish all modified block face canvases to the store
        for (Map.Entry<BlockPos, int[]> entry : modifiedBlocks.entrySet()) {
            store.put(entry.getKey(), face, new Canvas(entry.getValue(), expirationMs));
        }
    }
}
