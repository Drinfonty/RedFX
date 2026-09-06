package com.drinfonty.redfx.fabric.render;

import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.CanvasMesher;
import com.drinfonty.redfx.client.render.PaintGeometry;
import com.drinfonty.redfx.client.render.PaintQuad;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.client.render.PaintSurface;
import com.drinfonty.redfx.config.RedfxConfig;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emits blood decals as part of the block's own model on Fabric using FRAPI.
 */
public class RedfxWrapperModel extends DelegateBakedModel implements FabricBakedModel {
	private static RenderMaterial cutoutMaterial;
	protected final BakedModel wrapped;

	public RedfxWrapperModel(BakedModel wrapped) {
		super(wrapped);
		this.wrapped = wrapped;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockState state, BlockPos pos,
			Supplier<RandomSource> randomSupplier, Predicate<@Nullable Direction> cullTest) {
		((FabricBakedModel) wrapped).emitBlockQuads(emitter, level, state, pos, randomSupplier, cullTest);

		if (!RedfxConfig.get().bloodEnabled) {
			return;
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		if (!store.isPainted(pos)) {
			return;
		}

		TextureAtlasSprite sprite = PaintSprites.paint();
		float[] corners = new float[12];

		float surfaceY = PaintSurface.planeFor(level, pos, state, Direction.UP);
		boolean seeThrough = PaintSurface.isSeeThrough(state);

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			Canvas canvas = store.get(pos, face);
			if (canvas == null) {
				continue;
			}

			Direction direction = Direction.from3DDataValue(face);

			for (PaintQuad quad : CanvasMesher.mesh(canvas.texels(), face)) {
				PaintGeometry.corners(quad, corners, surfaceY);
				emit(emitter, sprite, direction, corners, quad.argb(), false);

				if (seeThrough) {
					emit(emitter, sprite, direction, corners, quad.argb(), true);
				}
			}
		}
	}

	@Override
	public void emitItemQuads(QuadEmitter emitter, Supplier<RandomSource> randomSupplier) {
		((FabricBakedModel) wrapped).emitItemQuads(emitter, randomSupplier);
	}

	private static void emit(QuadEmitter emitter, TextureAtlasSprite sprite, Direction direction,
			float[] corners, int argb, boolean back) {
		for (int vertex = 0; vertex < 4; vertex++) {
			int source = back ? 3 - vertex : vertex;

			emitter.pos(vertex, corners[source * 3], corners[source * 3 + 1], corners[source * 3 + 2]);
			emitter.color(vertex, argb);
			emitter.uv(vertex, sprite.getU(uOf(source)), sprite.getV(vOf(source)));
		}

		emitter.nominalFace(back ? direction.getOpposite() : direction);
		emitter.cullFace(null);
		RenderMaterial mat = getCutoutMaterial();
		if (mat != null) {
			emitter.material(mat);
		}
		emitter.emit();
	}

	private static RenderMaterial getCutoutMaterial() {
		if (cutoutMaterial == null) {
			var renderer = Renderer.get();
			if (renderer != null) {
				cutoutMaterial = renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
			}
		}
		return cutoutMaterial;
	}

	private static float uOf(int vertex) {
		return vertex == 0 || vertex == 3 ? 0.0F : 1.0F;
	}

	private static float vOf(int vertex) {
		return vertex < 2 ? 1.0F : 0.0F;
	}
}
