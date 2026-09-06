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
import com.mojang.blaze3d.platform.Transparency;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * Emits blood decals as part of the block's own model on NeoForge.
 */
public class RedfxDynamicModel extends DelegateBlockStateModel implements DynamicBlockStateModel {
	private static final Map<CacheKey, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();
	private static final int MAX_CACHED_CANVASES = 8192;

	private record CacheKey(Canvas canvas, int face, float surfaceY, boolean seeThrough) {
	}

	public RedfxDynamicModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	public static void clearCache() {
		CACHE.clear();
	}

	@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
		RandomSource random, List<BlockStateModelPart> parts) {
		super.collectParts(level, pos, state, random, parts);

		if (!RedfxConfig.get().bloodEnabled) {
			return;
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		if (!store.isPainted(pos)) {
			return;
		}

		List<BakedQuad> quads = null;

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			Canvas canvas = store.get(pos, face);
			if (canvas == null) {
				continue;
			}

			int currentFace = face;
			boolean seeThrough = PaintSurface.isSeeThrough(state);

			for (PaintSurface.SurfaceCanvas sc : PaintSurface.splitCanvas(level, pos, state, face, canvas)) {
				List<BakedQuad> faceQuads = CACHE.computeIfAbsent(
					new CacheKey(sc.canvas(), currentFace, sc.surfaceY(), seeThrough),
					key -> build(key.canvas(), key.face(), key.surfaceY(), key.seeThrough()));

				if (quads == null) {
					quads = new ArrayList<>(faceQuads.size() * 2);
				}

				quads.addAll(faceQuads);
			}
		}

		if (quads != null && !quads.isEmpty()) {
			parts.add(new PaintPart(quads));
		}

		if (CACHE.size() > MAX_CACHED_CANVASES) {
			CACHE.clear();
		}
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
		MutableQuad quad = new MutableQuad();
		quad.setSprite(new Material.Baked(PaintSprites.paint(), false), Transparency.TRANSPARENT);
		quad.setDirection(back ? direction.getOpposite() : direction);

		for (int vertex = 0; vertex < 4; vertex++) {
			int source = back ? 3 - vertex : vertex;

			quad.setPosition(vertex,
				corners[source * 3], corners[source * 3 + 1], corners[source * 3 + 2]);
			quad.setUvFromSprite(vertex, uOf(source), vOf(source));
		}

		quad.setColor(argb);
		quad.setTintIndex(-1);
		quad.setShade(true);

		return quad.toBakedQuad();
	}

	private static float uOf(int vertex) {
		return vertex == 0 || vertex == 3 ? 0.0F : 1.0F;
	}

	private static float vOf(int vertex) {
		return vertex < 2 ? 1.0F : 0.0F;
	}

	private record PaintPart(List<BakedQuad> quads) implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(Direction direction) {
			return direction == null ? quads : List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return true;
		}

		@Override
		public Material.Baked particleMaterial() {
			return new Material.Baked(PaintSprites.paint(), false);
		}

		@Override
		public int materialFlags() {
			return 0;
		}
	}
}
