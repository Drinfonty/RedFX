package com.drinfonty.redfx.neoforge.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.CanvasMesher;
import com.drinfonty.redfx.client.render.PaintGeometry;
import com.drinfonty.redfx.client.render.PaintQuad;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.client.render.PaintSurface;
import com.drinfonty.redfx.config.RedfxConfig;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * Emits blood decals as part of the block's own model on NeoForge.
 */
public class RedfxDynamicModel extends DelegateBakedModel implements IDynamicBakedModel {
	private static final ModelProperty<BloodData> BLOOD_PROPERTY = new ModelProperty<>();
	private final BakedModel wrapped;

	private record BloodData(BlockPos pos, float surfaceY, boolean seeThrough) {
	}

	private static final Map<CacheKey, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();
	private static final int MAX_CACHED_CANVASES = 8192;

	private record CacheKey(Canvas canvas, int face, float surfaceY, boolean seeThrough) {
	}

	public RedfxDynamicModel(BakedModel wrapped) {
		super(wrapped);
		this.wrapped = wrapped;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		ModelData base = wrapped.getModelData(level, pos, state, modelData);
		if (!RedfxConfig.get().bloodEnabled) {
			return base;
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		if (!store.isPainted(pos)) {
			return base;
		}

		float surfaceY = PaintSurface.planeFor(level, pos, state, Direction.UP);
		boolean seeThrough = PaintSurface.isSeeThrough(state);

		return base.derive().with(BLOOD_PROPERTY, new BloodData(pos.immutable(), surfaceY, seeThrough)).build();
	}

	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		ChunkRenderTypeSet base = wrapped.getRenderTypes(state, rand, data);
		if (data.has(BLOOD_PROPERTY)) {
			return ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutout()));
		}
		return base;
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
			ModelData extraData, @Nullable RenderType renderType) {
		List<BakedQuad> base = wrapped.getQuads(state, side, rand, extraData, renderType);

		if (side != null || !RedfxConfig.get().bloodEnabled) {
			return base;
		}

		if (renderType != null && renderType != RenderType.cutout()) {
			return base;
		}

		BloodData data = extraData.get(BLOOD_PROPERTY);
		if (data == null) {
			return base;
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		if (!store.isPainted(data.pos)) {
			return base;
		}

		List<BakedQuad> quads = null;

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			Canvas canvas = store.get(data.pos, face);
			if (canvas == null) {
				continue;
			}

			int currentFace = face;
			List<BakedQuad> faceQuads = CACHE.computeIfAbsent(
				new CacheKey(canvas, currentFace, data.surfaceY, data.seeThrough),
				key -> build(key.canvas(), key.face(), key.surfaceY(), key.seeThrough()));

			if (quads == null) {
				quads = new ArrayList<>(base.size() + faceQuads.size() * 2);
				quads.addAll(base);
			}

			quads.addAll(faceQuads);
		}

		if (CACHE.size() > MAX_CACHED_CANVASES) {
			CACHE.clear();
		}

		return quads != null ? quads : base;
	}

	private static List<BakedQuad> build(Canvas canvas, int face, float surfaceY, boolean seeThrough) {
		Direction direction = Direction.from3DDataValue(face);
		List<PaintQuad> rectangles = CanvasMesher.mesh(canvas.texels(), face);
		List<BakedQuad> quads = new ArrayList<>(rectangles.size() * (seeThrough ? 2 : 1));
		float[] corners = new float[12];

		for (PaintQuad rectangle : rectangles) {
			PaintGeometry.corners(rectangle, corners, surfaceY);
			quads.add(bake(corners, direction, rectangle.argb(), false));

			if (seeThrough) {
				quads.add(bake(corners, direction, rectangle.argb(), true));
			}
		}

		return List.copyOf(quads);
	}

	private static BakedQuad bake(float[] corners, Direction direction, int argb, boolean back) {
		QuadBakingVertexConsumer consumer = new QuadBakingVertexConsumer();
		consumer.setSprite(PaintSprites.paint());
		consumer.setDirection(back ? direction.getOpposite() : direction);
		consumer.setShade(true);
		consumer.setTintIndex(-1);
		consumer.setHasAmbientOcclusion(true);

		for (int vertex = 0; vertex < 4; vertex++) {
			int source = back ? 3 - vertex : vertex;

			consumer.addVertex(
				corners[source * 3], corners[source * 3 + 1], corners[source * 3 + 2]);
			consumer.setColor(argb);
			consumer.setUv(PaintSprites.paint().getU(uOf(source)), PaintSprites.paint().getV(vOf(source)));
		}

		return consumer.bakeQuad();
	}

	private static float uOf(int vertex) {
		return vertex == 0 || vertex == 3 ? 0.0F : 1.0F;
	}

	private static float vOf(int vertex) {
		return vertex < 2 ? 1.0F : 0.0F;
	}
}
