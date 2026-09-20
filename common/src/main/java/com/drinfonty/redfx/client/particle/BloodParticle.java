package com.drinfonty.redfx.client.particle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.drinfonty.redfx.canvas.EdgeDrip;
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
                        BlockState dustState = Blocks.SNOW_BLOCK.defaultBlockState();
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
        Map<CanvasTarget, int[]> modifiedCanvases = new HashMap<>();
        List<ClientCanvasStore.PendingTexel> stage0Paints = new ArrayList<>();
        List<ClientCanvasStore.PendingTexel> stage1Paints = new ArrayList<>();
        List<ClientCanvasStore.PendingTexel> stage2Paints = new ArrayList<>();

        if (face == Direction.UP.get3DDataValue()) {
            Set<BlockPos> solidTopBlocks = new HashSet<>();
            Map<BlockPos, Double> blockElevations = new HashMap<>();
            SurfaceInfo targetSurface = resolveTopSurfaceInfo(targetBlock);
            double targetElevation = targetSurface != null ? targetSurface.elevation() : (double) (targetBlock.getY() + 1.0);

            BloodSplatter.stampGlobal(globalCenterU, globalCenterV, argb, this.splatIndex, scale, (gu, gv, col, stage) -> {
                int bu = FaceStroke.blockOfU(face, gu);
                int bv = FaceStroke.blockOfV(face, gv);
                int uTexel = gu - FaceStroke.encodeU(face, bu, 0);
                int vTexel = gv - FaceStroke.encodeV(face, bv, 0);

                if (uTexel < 0 || uTexel >= Canvas.SIZE || vTexel < 0 || vTexel >= Canvas.SIZE) {
                    return;
                }

                int wx = bu;
                int wy = normal;
                int wz = bv;
                BlockPos colPos = new BlockPos(wx, wy, wz);
                SurfaceInfo surfaceInfo = resolveTopSurfaceInfo(colPos);

                if (surfaceInfo != null) {
                    // Only stamp on top faces that are roughly level with the impact surface
                    if (Math.abs(surfaceInfo.elevation() - targetElevation) < 0.45) {
                        paintTexel(modifiedCanvases, store, surfaceInfo.blockPos(), Direction.UP.get3DDataValue(), uTexel, vTexel, col);
                        solidTopBlocks.add(surfaceInfo.blockPos());
                        blockElevations.put(surfaceInfo.blockPos(), surfaceInfo.elevation());

                        ClientCanvasStore.PendingTexel pt = new ClientCanvasStore.PendingTexel(
                            surfaceInfo.blockPos(), Direction.UP.get3DDataValue(), uTexel, vTexel, col, expirationMs
                        );
                        if (stage == 0) stage0Paints.add(pt);
                        else if (stage == 1) stage1Paints.add(pt);
                        else stage2Paints.add(pt);
                    }
                }
            });

            if (RedfxConfig.get().dripOverEdges) {
                Direction[] horizontalDirs = new Direction[] {
                    Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
                };

                for (BlockPos bPos : solidTopBlocks) {
                    CanvasTarget topTarget = new CanvasTarget(bPos, Direction.UP.get3DDataValue());
                    int[] topTexels = modifiedCanvases.get(topTarget);
                    if (topTexels == null) continue;

                    double myElevation = blockElevations.computeIfAbsent(bPos, p -> {
                        SurfaceInfo si = resolveTopSurfaceInfo(p);
                        return si != null ? si.elevation() : (double) (p.getY() + 1.0);
                    });

                    for (Direction hDir : horizontalDirs) {
                        BlockPos neighborPos = bPos.relative(hDir);
                        SurfaceInfo neighborInfo = resolveTopSurfaceInfo(neighborPos);

                        double exposedHeight;
                        if (neighborInfo != null) {
                            double drop = myElevation - neighborInfo.elevation();
                            if (drop < 0.05) {
                                // Neighbor is at same height or higher; no exposed vertical drop
                                continue;
                            }
                            exposedHeight = drop;
                        } else {
                            // Neighbor column is air/cliff! Side face is exposed down to block base
                            exposedHeight = myElevation - bPos.getY();
                        }

                        int maxDropTexels = Math.min(Canvas.SIZE, (int) Math.round(exposedHeight * 16.0));
                        if (maxDropTexels <= 0) continue;

                        BlockPos paintBlock = resolvePaintableSideBlock(bPos, hDir);
                        if (paintBlock == null) continue;

                        int sideFace = hDir.get3DDataValue();

                        // Identify columns along this edge that have blood, and connect them to the edge
                        boolean[] hasBlood = new boolean[Canvas.SIZE];
                        int[] rimColors = new int[Canvas.SIZE];

                        for (int coord = 0; coord < Canvas.SIZE; coord++) {
                            int edgeCol = switch (hDir) {
                                case NORTH -> topTexels[0 * Canvas.SIZE + coord];
                                case SOUTH -> topTexels[15 * Canvas.SIZE + coord];
                                case WEST -> topTexels[coord * Canvas.SIZE + 0];
                                case EAST -> topTexels[coord * Canvas.SIZE + 15];
                                default -> 0;
                            };

                            // If outer edge pixel is empty, check up to 2 pixels inward (splash near edge)
                            if (edgeCol == 0) {
                                int inward1 = switch (hDir) {
                                    case NORTH -> topTexels[1 * Canvas.SIZE + coord];
                                    case SOUTH -> topTexels[14 * Canvas.SIZE + coord];
                                    case WEST -> topTexels[coord * Canvas.SIZE + 1];
                                    case EAST -> topTexels[coord * Canvas.SIZE + 14];
                                    default -> 0;
                                };
                                if (inward1 != 0) {
                                    edgeCol = inward1;
                                    switch (hDir) {
                                        case NORTH -> {
                                            topTexels[0 * Canvas.SIZE + coord] = inward1;
                                            stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 0, inward1, expirationMs));
                                        }
                                        case SOUTH -> {
                                            topTexels[15 * Canvas.SIZE + coord] = inward1;
                                            stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 15, inward1, expirationMs));
                                        }
                                        case WEST -> {
                                            topTexels[coord * Canvas.SIZE + 0] = inward1;
                                            stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 0, coord, inward1, expirationMs));
                                        }
                                        case EAST -> {
                                            topTexels[coord * Canvas.SIZE + 15] = inward1;
                                            stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 15, coord, inward1, expirationMs));
                                        }
                                    }
                                } else {
                                    int inward2 = switch (hDir) {
                                        case NORTH -> topTexels[2 * Canvas.SIZE + coord];
                                        case SOUTH -> topTexels[13 * Canvas.SIZE + coord];
                                        case WEST -> topTexels[coord * Canvas.SIZE + 2];
                                        case EAST -> topTexels[coord * Canvas.SIZE + 13];
                                        default -> 0;
                                    };
                                    if (inward2 != 0) {
                                        edgeCol = inward2;
                                        switch (hDir) {
                                            case NORTH -> {
                                                topTexels[1 * Canvas.SIZE + coord] = inward2;
                                                topTexels[0 * Canvas.SIZE + coord] = inward2;
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 1, inward2, expirationMs));
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 0, inward2, expirationMs));
                                            }
                                            case SOUTH -> {
                                                topTexels[14 * Canvas.SIZE + coord] = inward2;
                                                topTexels[15 * Canvas.SIZE + coord] = inward2;
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 14, inward2, expirationMs));
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), coord, 15, inward2, expirationMs));
                                            }
                                            case WEST -> {
                                                topTexels[coord * Canvas.SIZE + 1] = inward2;
                                                topTexels[coord * Canvas.SIZE + 0] = inward2;
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 1, coord, inward2, expirationMs));
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 0, coord, inward2, expirationMs));
                                            }
                                            case EAST -> {
                                                topTexels[coord * Canvas.SIZE + 14] = inward2;
                                                topTexels[coord * Canvas.SIZE + 15] = inward2;
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 14, coord, inward2, expirationMs));
                                                stage2Paints.add(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), 15, coord, inward2, expirationMs));
                                            }
                                        }
                                    }
                                }
                            }

                            if (edgeCol != 0) {
                                hasBlood[coord] = true;
                                rimColors[coord] = edgeCol;
                            }
                        }

                        // Find continuous clusters of columns and paint realistic drips down the side face
                        int cStart = -1;
                        for (int coord = 0; coord <= Canvas.SIZE; coord++) {
                            if (coord < Canvas.SIZE && hasBlood[coord]) {
                                if (cStart == -1) cStart = coord;
                            } else if (cStart != -1) {
                                int cEnd = coord - 1;
                                for (int c = cStart; c <= cEnd; c++) {
                                    int col = rimColors[c];
                                    int baseAlpha = (col >>> 24);
                                    int rgb = col & 0xFFFFFF;

                                    int uSide = switch (hDir) {
                                        case NORTH -> EdgeDrip.sideU(sideFace, c, 0);
                                        case SOUTH -> EdgeDrip.sideU(sideFace, c, 15);
                                        case WEST -> EdgeDrip.sideU(sideFace, 0, c);
                                        case EAST -> EdgeDrip.sideU(sideFace, 15, c);
                                        default -> 0;
                                    };

                                    int uSideStart = switch (hDir) {
                                        case NORTH -> EdgeDrip.sideU(sideFace, cEnd, 0);
                                        case SOUTH -> EdgeDrip.sideU(sideFace, cStart, 15);
                                        case WEST -> EdgeDrip.sideU(sideFace, 0, cStart);
                                        case EAST -> EdgeDrip.sideU(sideFace, 15, cEnd);
                                        default -> 0;
                                    };
                                    int uSideEnd = switch (hDir) {
                                        case NORTH -> EdgeDrip.sideU(sideFace, cStart, 0);
                                        case SOUTH -> EdgeDrip.sideU(sideFace, cEnd, 15);
                                        case WEST -> EdgeDrip.sideU(sideFace, 0, cEnd);
                                        case EAST -> EdgeDrip.sideU(sideFace, 15, cStart);
                                        default -> 0;
                                    };
                                    int minUSide = Math.min(uSideStart, uSideEnd);
                                    int maxUSide = Math.max(uSideStart, uSideEnd);

                                    int dripLen = EdgeDrip.calculateDripLength(
                                        paintBlock.getX(), paintBlock.getZ(), sideFace, uSide,
                                        minUSide, maxUSide, maxDropTexels - 1, this.splatIndex
                                    );

                                    for (int step = 0; step <= dripLen; step++) {
                                        int dripAlpha = RedfxConfig.get().translucentEdges
                                            ? EdgeDrip.dripAlpha(baseAlpha, step, dripLen)
                                            : 255;
                                        int dripCol = (dripAlpha << 24) | rgb;
                                        stage2Paints.add(new ClientCanvasStore.PendingTexel(paintBlock, sideFace, uSide, step, dripCol, expirationMs));
                                    }
                                }
                                cStart = -1;
                            }
                        }
                    }
                }
            }
        } else {
            Map<Long, BlockPos> resolvedBlocks = new HashMap<>();
            Set<Long> invalidBlocks = new HashSet<>();

            BloodSplatter.stampGlobal(globalCenterU, globalCenterV, argb, this.splatIndex, scale, (gu, gv, col, stage) -> {
                int bu = FaceStroke.blockOfU(face, gu);
                int bv = FaceStroke.blockOfV(face, gv);
                long blockKey = (((long) bu) << 32) | (((long) bv) & 0xFFFFFFFFL);

                if (invalidBlocks.contains(blockKey)) return;

                int uTexel = gu - FaceStroke.encodeU(face, bu, 0);
                int vTexel = gv - FaceStroke.encodeV(face, bv, 0);
                if (uTexel < 0 || uTexel >= Canvas.SIZE || vTexel < 0 || vTexel >= Canvas.SIZE) return;

                BlockPos bPos = resolvedBlocks.get(blockKey);
                if (bPos == null) {
                    int wx = FaceStroke.worldX(face, bu, bv, normal);
                    int wy = FaceStroke.worldY(face, bu, bv, normal);
                    int wz = FaceStroke.worldZ(face, bu, bv, normal);
                    bPos = new BlockPos(wx, wy, wz);

                    BlockState bs = this.level.getBlockState(bPos);
                    if (bs.isAir() || !bs.getFluidState().isEmpty()
                        || !bs.isFaceSturdy(this.level, bPos, hitDirection) || !bs.isCollisionShapeFullBlock(this.level, bPos)) {
                        invalidBlocks.add(blockKey);
                        return;
                    }
                    resolvedBlocks.put(blockKey, bPos);
                }

                ClientCanvasStore.PendingTexel pt = new ClientCanvasStore.PendingTexel(bPos, face, uTexel, vTexel, col, expirationMs);
                if (stage == 0) stage0Paints.add(pt);
                else if (stage == 1) stage1Paints.add(pt);
                else stage2Paints.add(pt);
            });
        }

        // Publish texels: either immediately or staged across progressive growth steps
        boolean gradual = RedfxConfig.get().gradualSplatter && RedfxConfig.get().splatterGrowthDelayTicks > 0;
        if (!gradual) {
            List<ClientCanvasStore.PendingTexel> all = new ArrayList<>(stage0Paints.size() + stage1Paints.size() + stage2Paints.size());
            all.addAll(stage0Paints);
            all.addAll(stage1Paints);
            all.addAll(stage2Paints);
            store.applyTexels(all);
        } else {
            long nowMs = System.currentTimeMillis();
            long delayMs = RedfxConfig.get().splatterGrowthDelayTicks * 50L;
            if (!stage0Paints.isEmpty()) {
                store.applyTexels(stage0Paints);
            }
            if (!stage1Paints.isEmpty()) {
                store.scheduleStage(nowMs + delayMs, stage1Paints);
            }
            if (!stage2Paints.isEmpty()) {
                store.scheduleStage(nowMs + 2L * delayMs, stage2Paints);
            }
        }
    }

    private record CanvasTarget(BlockPos pos, int face) {
    }

    private record SurfaceInfo(BlockPos blockPos, double elevation) {
    }

    private SurfaceInfo resolveTopSurfaceInfo(BlockPos colPos) {
        BlockPos abovePos = colPos.above();
        BlockState aboveState = this.level.getBlockState(abovePos);
        if (!aboveState.isAir() && aboveState.getFluidState().isEmpty()) {
            double top = PaintSurface.topOf(this.level, abovePos, aboveState);
            if (top != PaintSurface.NONE && top < 1.0) {
                return new SurfaceInfo(abovePos, abovePos.getY() + top);
            }
        }
        BlockState bs = this.level.getBlockState(colPos);
        if (!bs.isAir() && bs.getFluidState().isEmpty()) {
            double top = PaintSurface.topOf(this.level, colPos, bs);
            if (top != PaintSurface.NONE) {
                return new SurfaceInfo(colPos, colPos.getY() + top);
            }
        }
        BlockPos belowPos = colPos.below();
        BlockState belowState = this.level.getBlockState(belowPos);
        if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
            double top = PaintSurface.topOf(this.level, belowPos, belowState);
            if (top != PaintSurface.NONE) {
                return new SurfaceInfo(belowPos, belowPos.getY() + top);
            }
        }
        return null;
    }

    private BlockPos resolveTopSurface(BlockPos pos) {
        SurfaceInfo info = resolveTopSurfaceInfo(pos);
        return info != null ? info.blockPos() : null;
    }

    private BlockPos resolvePaintableSideBlock(BlockPos pos, Direction sideDir) {
        BlockState state = this.level.getBlockState(pos);
        if (state.isFaceSturdy(this.level, pos, sideDir) || state.isCollisionShapeFullBlock(this.level, pos)) {
            return pos;
        }
        BlockPos below = pos.below();
        BlockState belowState = this.level.getBlockState(below);
        if (belowState.isFaceSturdy(this.level, below, sideDir) || belowState.isCollisionShapeFullBlock(this.level, below)) {
            return below;
        }
        return null;
    }

    private void paintTexel(Map<CanvasTarget, int[]> modifiedCanvases, ClientCanvasStore store,
        BlockPos pos, int face, int u, int v, int col) {
        if (u < 0 || u >= Canvas.SIZE || v < 0 || v >= Canvas.SIZE) {
            return;
        }
        CanvasTarget target = new CanvasTarget(pos, face);
        int[] texels = modifiedCanvases.get(target);
        if (texels == null) {
            Canvas existing = store.get(pos, face);
            texels = existing != null ? existing.texels().clone() : new int[Canvas.TEXELS];
            modifiedCanvases.put(target, texels);
        }
        int idx = v * Canvas.SIZE + u;
        int existing = texels[idx];
        int existingAlpha = (existing >>> 24);
        int newAlpha = (col >>> 24);
        if (newAlpha > existingAlpha || (newAlpha == existingAlpha && existing != col)) {
            texels[idx] = col;
        }
    }
}
