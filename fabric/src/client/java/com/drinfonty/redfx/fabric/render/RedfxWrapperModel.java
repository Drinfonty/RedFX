package com.drinfonty.redfx.fabric.render;

import java.util.List;
import java.util.function.Predicate;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.CanvasMesher;
import com.drinfonty.redfx.client.render.PaintGeometry;
import com.drinfonty.redfx.client.render.PaintQuad;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.client.render.PaintSurface;
import com.drinfonty.redfx.config.RedfxConfig;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emits blood decals as part of the block's own model on Fabric using FRAPI.
 */
public class RedfxWrapperModel extends WrapperBlockStateModel {
	public RedfxWrapperModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
		RandomSource random, Predicate<Direction> cullTest) {
		super.emitQuads(emitter, level, pos, state, random, cullTest);

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
		emitter.chunkLayer(ChunkSectionLayer.CUTOUT);
		emitter.emit();
	}

	private static float uOf(int vertex) {
		return vertex == 0 || vertex == 3 ? 0.0F : 1.0F;
	}

	private static float vOf(int vertex) {
		return vertex < 2 ? 1.0F : 0.0F;
	}

	@Override
	public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		if (!RedfxConfig.get().bloodEnabled) {
			return super.createGeometryKey(level, pos, state, random);
		}

		ClientCanvasStore store = ClientCanvasStore.get();
		Object[] key = new Object[FaceAxes.FACE_COUNT + 2];
		key[0] = super.createGeometryKey(level, pos, state, random);
		key[FaceAxes.FACE_COUNT + 1] = PaintSurface.isSeeThrough(state);
		boolean painted = false;

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			Canvas canvas = store.get(pos, face);
			key[face + 1] = canvas;
			painted |= canvas != null;
		}

		return painted ? List.of(key) : key[0];
	}
}
