package com.drinfonty.redfx.fabric.render;

import java.util.function.Supplier;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.CanvasMesher;
import com.drinfonty.redfx.client.render.PaintGeometry;
import com.drinfonty.redfx.client.render.PaintQuad;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.client.render.PaintSurface;
import com.drinfonty.redfx.config.RedfxConfig;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emits blood decals as part of the block's own model on Fabric using FRAPI.
 */
public class RedfxWrapperModel extends ForwardingBakedModel {
	private static RenderMaterial cutoutMaterial;

	public RedfxWrapperModel(BakedModel wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos,
			Supplier<RandomSource> randomSupplier, RenderContext context) {
		super.emitBlockQuads(level, state, pos, randomSupplier, context);

		if (!RedfxConfig.get().bloodEnabled) {
			return;
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		if (!store.isPainted(pos)) {
			return;
		}

		TextureAtlasSprite sprite = PaintSprites.paint();
		float[] corners = new float[12];

		boolean seeThrough = PaintSurface.isSeeThrough(state);

		QuadEmitter emitter = context.getEmitter();

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			Canvas canvas = store.get(pos, face);
			if (canvas == null) {
				continue;
			}

			Direction direction = Direction.from3DDataValue(face);

			for (PaintSurface.SurfaceCanvas sc : PaintSurface.splitCanvas(level, pos, state, face, canvas)) {
				for (PaintQuad quad : CanvasMesher.mesh(sc.canvas().texels(), face)) {
					PaintGeometry.corners(quad, corners, sc.surfaceY());
					emit(emitter, sprite, direction, corners, quad.argb(), false);

					if (seeThrough) {
						emit(emitter, sprite, direction, corners, quad.argb(), true);
					}
				}
			}
		}
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
			var renderer = RendererAccess.INSTANCE.getRenderer();
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
