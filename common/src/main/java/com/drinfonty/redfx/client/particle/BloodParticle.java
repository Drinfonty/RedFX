package com.drinfonty.redfx.client.particle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

        this.lifetime = 40; // max fall time before despawning
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

        long nowMs = System.currentTimeMillis();
        long baseDelayMs = RedfxConfig.get().splatterGrowthDelayTicks * 50L;
        float configFactor = Math.max(0.5f, RedfxConfig.get().splatterGrowthDelayTicks / 2.0f);

        List<ClientCanvasStore.PendingTexel> allTexels = new ArrayList<>();
        Map<Long, List<ClientCanvasStore.PendingTexel>> scheduledByTime = new TreeMap<>();
        java.util.function.BiConsumer<ClientCanvasStore.PendingTexel, Long> addTexel = (pt, execTime) -> {
            allTexels.add(pt);
            scheduledByTime.computeIfAbsent(execTime, k -> new ArrayList<>()).add(pt);
        };

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
                    if (surfaceInfo.blockPos().equals(targetBlock) || Math.abs(surfaceInfo.elevation() - targetElevation) < 0.45) {
                        paintTexel(modifiedCanvases, store, surfaceInfo.blockPos(), Direction.UP.get3DDataValue(), uTexel, vTexel, col);
                        solidTopBlocks.add(surfaceInfo.blockPos());
                        blockElevations.put(surfaceInfo.blockPos(), surfaceInfo.elevation());

                        ClientCanvasStore.PendingTexel pt = new ClientCanvasStore.PendingTexel(
                            surfaceInfo.blockPos(), Direction.UP.get3DDataValue(), uTexel, vTexel, col, expirationMs
                        );
                        long execTime = stage == 0 ? nowMs : nowMs + (long) stage * baseDelayMs;
                        addTexel.accept(pt, execTime);
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

                    BlockState bState = this.level.getBlockState(bPos);
                    double localTop = PaintSurface.topOf(this.level, bPos, bState);
                    if (localTop == PaintSurface.NONE) {
                        localTop = 1.0;
                    }
                    int localTexels = Math.max(1, (int) Math.round(localTop * 16.0));

                    BlockPos belowPos = bPos.below();
                    BlockState belowState = this.level.getBlockState(belowPos);

                    for (Direction hDir : horizontalDirs) {
                        boolean sidePaintable = bState.isFaceSturdy(this.level, bPos, hDir)
                            || bState.isCollisionShapeFullBlock(this.level, bPos)
                            || (localTop != PaintSurface.NONE);
                        if (!sidePaintable) continue;

                        boolean belowPaintable = belowState.isFaceSturdy(this.level, belowPos, hDir)
                            || belowState.isCollisionShapeFullBlock(this.level, belowPos);

                        BlockPos neighborPos = bPos.relative(hDir);
                        SurfaceInfo neighborInfo = resolveTopSurfaceInfo(neighborPos);

                        if (neighborInfo != null && myElevation - neighborInfo.elevation() < 0.05 && bState.isCollisionShapeFullBlock(this.level, bPos)) {
                            // Neighbor is at same height or higher; no exposed vertical drop
                            continue;
                        }

                        int sideFace = hDir.get3DDataValue();

                        // Identify columns along this edge that have blood, and connect them to the edge
                        boolean[] hasBlood = new boolean[Canvas.SIZE];
                        int[] rimColors = new int[Canvas.SIZE];
                        PaintSurface.BlockEdge[] columnEdges = new PaintSurface.BlockEdge[Canvas.SIZE];

                        for (int coord = 0; coord < Canvas.SIZE; coord++) {
                            PaintSurface.BlockEdge edge = PaintSurface.findEdge(this.level, bPos, bState, hDir, coord);
                            if (edge == null) continue;

                            int edgeCol = topTexels[edge.edgeV() * Canvas.SIZE + edge.edgeU()];

                            // If outer edge pixel is empty, check up to 2 pixels inward (splash near edge)
                            if (edgeCol == 0) {
                                int inU1 = edge.edgeU();
                                int inV1 = edge.edgeV();
                                switch (hDir) {
                                    case NORTH -> inV1 += 1;
                                    case SOUTH -> inV1 -= 1;
                                    case WEST -> inU1 += 1;
                                    case EAST -> inU1 -= 1;
                                }
                                if (inU1 >= 0 && inU1 < Canvas.SIZE && inV1 >= 0 && inV1 < Canvas.SIZE) {
                                    int inward1 = topTexels[inV1 * Canvas.SIZE + inU1];
                                    if (inward1 != 0) {
                                        edgeCol = inward1;
                                        topTexels[edge.edgeV() * Canvas.SIZE + edge.edgeU()] = inward1;
                                        addTexel.accept(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), edge.edgeU(), edge.edgeV(), inward1, expirationMs), nowMs + 2 * baseDelayMs);
                                    } else {
                                        int inU2 = inU1;
                                        int inV2 = inV1;
                                        switch (hDir) {
                                            case NORTH -> inV2 += 1;
                                            case SOUTH -> inV2 -= 1;
                                            case WEST -> inU2 += 1;
                                            case EAST -> inU2 -= 1;
                                        }
                                        if (inU2 >= 0 && inU2 < Canvas.SIZE && inV2 >= 0 && inV2 < Canvas.SIZE) {
                                            int inward2 = topTexels[inV2 * Canvas.SIZE + inU2];
                                            if (inward2 != 0) {
                                                edgeCol = inward2;
                                                long bridgeTime = nowMs + 2 * baseDelayMs;
                                                topTexels[inV1 * Canvas.SIZE + inU1] = inward2;
                                                topTexels[edge.edgeV() * Canvas.SIZE + edge.edgeU()] = inward2;
                                                addTexel.accept(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), inU1, inV1, inward2, expirationMs), bridgeTime);
                                                addTexel.accept(new ClientCanvasStore.PendingTexel(bPos, Direction.UP.get3DDataValue(), edge.edgeU(), edge.edgeV(), inward2, expirationMs), bridgeTime);
                                            }
                                        }
                                    }
                                }
                            }

                            if (edgeCol != 0) {
                                hasBlood[coord] = true;
                                rimColors[coord] = edgeCol;
                                columnEdges[coord] = edge;
                            }
                        }

                        // Find continuous clusters of columns and trickle teardrop beads at random within the boundary
                        int cStart = -1;
                        for (int coord = 0; coord <= Canvas.SIZE; coord++) {
                            if (coord < Canvas.SIZE && hasBlood[coord]) {
                                if (cStart == -1) cStart = coord;
                            } else if (cStart != -1) {
                                int cEnd = coord - 1;

                                int[] beadColumns = EdgeDrip.selectBeadColumns(
                                    cStart, cEnd, bPos.getX(), bPos.getZ(), sideFace, this.splatIndex
                                );

                                for (int c : beadColumns) {
                                    PaintSurface.BlockEdge edge = columnEdges[c];
                                    if (edge == null) continue;

                                    int col = rimColors[c];
                                    int baseAlpha = (col >>> 24);
                                    int rgb = col & 0xFFFFFF;

                                    int uSide = EdgeDrip.sideU(sideFace, edge.edgeU(), edge.edgeV());
                                    double colTop = edge.colTop();
                                    double colBottom = edge.colBottom();
                                    double colPlane = edge.colPlane();

                                    double forwardOffset = switch (hDir) {
                                        case NORTH, WEST -> -0.0625;
                                        case SOUTH, EAST -> 0.0625;
                                        default -> 0.0;
                                    };
                                    double inFrontX = (hDir == Direction.WEST || hDir == Direction.EAST) ? colPlane + forwardOffset : (c + 0.5) / 16.0;
                                    double inFrontZ = (hDir == Direction.NORTH || hDir == Direction.SOUTH) ? colPlane + forwardOffset : (c + 0.5) / 16.0;

                                    boolean isInternalStep = false;
                                    if (inFrontX >= 0.0 && inFrontX <= 1.0 && inFrontZ >= 0.0 && inFrontZ <= 1.0) {
                                        double internalFloor = PaintSurface.surfaceElevationAt(this.level, bPos, bState, inFrontX, inFrontZ);
                                        if (internalFloor != PaintSurface.NONE && internalFloor < colTop - 0.05) {
                                            isInternalStep = true;
                                            colBottom = internalFloor;
                                        }
                                    }

                                    double colHeight = Math.max(0.0, colTop - colBottom);
                                    double colElevation = (double) bPos.getY() + colTop;
                                    int colLocalTexels = Math.max(1, (int) Math.round(colHeight * 16.0));

                                    double colExposedHeight;
                                    if (isInternalStep) {
                                        colExposedHeight = colHeight;
                                    } else {
                                        double neighborX = switch (hDir) {
                                            case NORTH, SOUTH -> (c + 0.5) / 16.0;
                                            case WEST -> 0.96875;
                                            case EAST -> 0.03125;
                                            default -> (c + 0.5) / 16.0;
                                        };
                                        double neighborZ = switch (hDir) {
                                            case NORTH -> 0.96875;
                                            case SOUTH -> 0.03125;
                                            case WEST, EAST -> (c + 0.5) / 16.0;
                                            default -> (c + 0.5) / 16.0;
                                        };

                                        double neighborElevation = PaintSurface.elevationAt(this.level, neighborPos, neighborX, neighborZ);

                                        if (neighborElevation != PaintSurface.NONE) {
                                            double drop = colElevation - neighborElevation;
                                            if (drop < 0.05) {
                                                // Neighbor is at same height or higher; no exposed vertical drop
                                                continue;
                                            }
                                            if (drop <= colHeight) {
                                                colExposedHeight = drop;
                                            } else {
                                                colExposedHeight = belowPaintable
                                                    ? Math.min(drop, colElevation - (double) belowPos.getY())
                                                    : colHeight;
                                            }
                                        } else {
                                            colExposedHeight = belowPaintable
                                                ? (colElevation - (double) belowPos.getY())
                                                : (colElevation - (double) bPos.getY());
                                        }
                                    }

                                    int colMaxDropTexels = Math.min(Canvas.SIZE, (int) Math.round(colExposedHeight * 16.0));
                                    if (colMaxDropTexels <= 0) continue;

                                    int maxAllowedStep = Math.max(1, colMaxDropTexels - 1);
                                    int dripLen = EdgeDrip.calculateDripLength(
                                        bPos.getX(), bPos.getZ(), sideFace, uSide,
                                        cStart, cEnd, maxAllowedStep, this.splatIndex
                                    );

                                    int beadHash = Math.abs((bPos.getX() * 3127 + bPos.getZ() * 739 + sideFace * 101 + uSide * 37) ^ (this.splatIndex * 19));
                                    long stepDelayMs = EdgeDrip.beadStepDelayMs(dripLen, configFactor, beadHash);
                                    long beadStartMs = nowMs + 2 * baseDelayMs + (beadHash % 35);

                                    for (int step = 0; step <= dripLen; step++) {
                                        BlockPos beadBlock;
                                        int vTexel;
                                        if (step < colLocalTexels) {
                                            beadBlock = bPos;
                                            vTexel = step;
                                        } else if (belowPaintable && !isInternalStep) {
                                            beadBlock = belowPos;
                                            vTexel = step - colLocalTexels;
                                        } else {
                                            break;
                                        }

                                        int dripAlpha = RedfxConfig.get().translucentEdges
                                            ? EdgeDrip.dripAlpha(baseAlpha, step, dripLen)
                                            : 255;
                                        int dripCol = (dripAlpha << 24) | rgb;

                                        ClientCanvasStore.PendingTexel pt = new ClientCanvasStore.PendingTexel(
                                            beadBlock, sideFace, uSide, vTexel, dripCol, expirationMs
                                        );
                                        long execTime = beadStartMs + (long) step * stepDelayMs;
                                        addTexel.accept(pt, execTime);
                                    }

                                    if (dripLen == maxAllowedStep) {
                                        double dripBaseY = colElevation - colExposedHeight;
                                        double worldX = bPos.getX();
                                        double worldZ = bPos.getZ();
                                        switch (hDir) {
                                            case NORTH -> {
                                                worldZ += colPlane;
                                                worldX += (15 - uSide + 0.5) / 16.0;
                                            }
                                            case SOUTH -> {
                                                worldZ += colPlane;
                                                worldX += (uSide + 0.5) / 16.0;
                                            }
                                            case WEST -> {
                                                worldX += colPlane;
                                                worldZ += (uSide + 0.5) / 16.0;
                                            }
                                            case EAST -> {
                                                worldX += colPlane;
                                                worldZ += (15 - uSide + 0.5) / 16.0;
                                            }
                                        }

                                        double frontX = worldX + hDir.getStepX() * 0.04;
                                        double frontZ = worldZ + hDir.getStepZ() * 0.04;

                                        BlockPos checkPos;
                                        BlockState checkState;
                                        if (isInternalStep) {
                                            checkPos = bPos;
                                            checkState = bState;
                                        } else {
                                            checkPos = BlockPos.containing(frontX, dripBaseY - 0.05, frontZ);
                                            checkState = this.level.getBlockState(checkPos);
                                        }

                                        double floorElevation = PaintSurface.elevationAt(
                                            this.level, checkPos, frontX - checkPos.getX(), frontZ - checkPos.getZ()
                                        );

                                        boolean touchesBlock = floorElevation != PaintSurface.NONE
                                            && Math.abs(floorElevation - dripBaseY) < 0.15
                                            && PaintSurface.topOf(this.level, checkPos, checkState) != PaintSurface.NONE;

                                        long arrivalTime = beadStartMs + (long) dripLen * stepDelayMs;

                                        if (touchesBlock) {
                                            double relX = Math.max(0.0, Math.min(1.0, frontX - checkPos.getX()));
                                            double relZ = Math.max(0.0, Math.min(1.0, frontZ - checkPos.getZ()));
                                            int landU = FaceAxes.texel(FaceAxes.u(Direction.UP.get3DDataValue(), relX, 0, relZ));
                                            int landV = FaceAxes.texel(FaceAxes.v(Direction.UP.get3DDataValue(), relX, 0, relZ));

                                            List<ClientCanvasStore.PendingTexel> smallSplat = new ArrayList<>();
                                            int splatAlpha = Math.min(255, Math.max(220, baseAlpha));
                                            int splatColor = (splatAlpha << 24) | rgb;
                                            int fadeColor = ((int) (splatAlpha * 0.75f) << 24) | rgb;

                                            smallSplat.add(new ClientCanvasStore.PendingTexel(
                                                checkPos, Direction.UP.get3DDataValue(), landU, landV, splatColor, expirationMs
                                            ));

                                            int[][] offsets = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
                                            int splatHash = Math.abs(beadHash ^ 0x5DEECE66);
                                            for (int i = 0; i < 4; i++) {
                                                if (((splatHash >> i) & 1) == 0 || (splatHash % 3 == 0)) {
                                                    int nu = landU + offsets[i][0];
                                                    int nv = landV + offsets[i][1];
                                                    if (nu >= 0 && nu < Canvas.SIZE && nv >= 0 && nv < Canvas.SIZE) {
                                                        smallSplat.add(new ClientCanvasStore.PendingTexel(
                                                            checkPos, Direction.UP.get3DDataValue(), nu, nv, fadeColor, expirationMs
                                                        ));
                                                    }
                                                }
                                            }
                                            int diagIdx = splatHash % 4;
                                            int[][] diags = { {1, 1}, {-1, 1}, {1, -1}, {-1, -1} };
                                            int du = landU + diags[diagIdx][0];
                                            int dv = landV + diags[diagIdx][1];
                                            if (du >= 0 && du < Canvas.SIZE && dv >= 0 && dv < Canvas.SIZE) {
                                                smallSplat.add(new ClientCanvasStore.PendingTexel(
                                                    checkPos, Direction.UP.get3DDataValue(), du, dv, fadeColor, expirationMs
                                                ));
                                            }

                                            ClientCanvasStore.get().scheduleStage(arrivalTime, smallSplat);

                                            final float dripR = ((rgb >> 16) & 0xFF) / 255.0f;
                                            final float dripG = ((rgb >> 8) & 0xFF) / 255.0f;
                                            final float dripB = (rgb & 0xFF) / 255.0f;

                                            if (RedfxConfig.get().enableSplatDust) {
                                                final double splatFloorY = floorElevation;
                                                ClientCanvasStore.get().scheduleAction(arrivalTime, () -> {
                                                    try {
                                                        BlockState dustState = Blocks.SNOW_BLOCK.defaultBlockState();
                                                        Particle dust = Minecraft.getInstance().particleEngine.createParticle(
                                                            new BlockParticleOption(ParticleTypes.FALLING_DUST, dustState),
                                                            frontX, splatFloorY + 0.02, frontZ, 0.0, 0.01, 0.0
                                                        );
                                                        if (dust instanceof SingleQuadParticle sqp) {
                                                            sqp.setColor(dripR, dripG, dripB);
                                                        }
                                                        if (dust != null) {
                                                            Minecraft.getInstance().particleEngine.add(dust);
                                                        }
                                                    } catch (Throwable ignored) {
                                                    }
                                                });
                                            }
                                        } else {
                                            final double dropSpawnX = frontX;
                                            final double dropSpawnY = dripBaseY - 0.02;
                                            final double dropSpawnZ = frontZ;
                                            final float dropR = ((rgb >> 16) & 0xFF) / 255.0f;
                                            final float dropG = ((rgb >> 8) & 0xFF) / 255.0f;
                                            final float dropB = (rgb & 0xFF) / 255.0f;

                                            ClientCanvasStore.get().scheduleAction(arrivalTime, () -> {
                                                try {
                                                    Minecraft mc = Minecraft.getInstance();
                                                    if (mc.level == null) return;
                                                    Particle drop = mc.particleEngine.createParticle(
                                                        ParticleTypes.FALLING_WATER, dropSpawnX, dropSpawnY, dropSpawnZ, 0.0, 0.0, 0.0
                                                    );
                                                    if (drop instanceof SingleQuadParticle sqp) {
                                                        sqp.setColor(dropR, dropG, dropB);
                                                    }
                                                } catch (Throwable ignored) {
                                                }
                                            });
                                        }
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

            boolean isVerticalWall = face == Direction.NORTH.get3DDataValue()
                || face == Direction.SOUTH.get3DDataValue()
                || face == Direction.WEST.get3DDataValue()
                || face == Direction.EAST.get3DDataValue();

            Map<Integer, List<ClientCanvasStore.PendingTexel>> wallStageTexels = new TreeMap<>();

            BloodSplatter.StagedCanvasWriter wallWriter = (gu, gv, col, stage) -> {
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
                wallStageTexels.computeIfAbsent(stage, k -> new ArrayList<>()).add(pt);
            };

            if (isVerticalWall && RedfxConfig.get().wallDripping) {
                BloodSplatter.stampWallDripGlobal(globalCenterU, globalCenterV, argb, this.splatIndex, scale, wallWriter);
                int maxStage = wallStageTexels.keySet().stream().max(Integer::compareTo).orElse(2);
                int beadLen = Math.max(1, maxStage - 2);
                long stepDelayMs = EdgeDrip.beadStepDelayMs(beadLen, configFactor);
                long beadStartMs = nowMs + 2 * baseDelayMs;

                for (Map.Entry<Integer, List<ClientCanvasStore.PendingTexel>> entry : wallStageTexels.entrySet()) {
                    int stage = entry.getKey();
                    long execTime;
                    if (stage == 0) {
                        execTime = nowMs;
                    } else if (stage <= 2) {
                        execTime = nowMs + (long) stage * baseDelayMs;
                    } else {
                        int step = stage - 2;
                        execTime = beadStartMs + (long) step * stepDelayMs;
                    }
                    for (ClientCanvasStore.PendingTexel pt : entry.getValue()) {
                        addTexel.accept(pt, execTime);
                    }
                }
            } else {
                BloodSplatter.stampGlobal(globalCenterU, globalCenterV, argb, this.splatIndex, scale, wallWriter);
                for (Map.Entry<Integer, List<ClientCanvasStore.PendingTexel>> entry : wallStageTexels.entrySet()) {
                    int stage = entry.getKey();
                    long execTime = stage == 0 ? nowMs : nowMs + (long) stage * baseDelayMs;
                    for (ClientCanvasStore.PendingTexel pt : entry.getValue()) {
                        addTexel.accept(pt, execTime);
                    }
                }
            }
        }

        // Publish texels: either immediately or scheduled per-pixel across time
        boolean gradual = RedfxConfig.get().gradualSplatter && RedfxConfig.get().splatterGrowthDelayTicks > 0;
        if (!gradual) {
            store.applyTexels(allTexels);
        } else {
            for (Map.Entry<Long, List<ClientCanvasStore.PendingTexel>> entry : scheduledByTime.entrySet()) {
                long execTime = entry.getKey();
                List<ClientCanvasStore.PendingTexel> texels = entry.getValue();
                if (texels.isEmpty()) continue;

                if (execTime <= nowMs) {
                    store.applyTexels(texels);
                } else {
                    store.scheduleStage(execTime, texels);
                }
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
