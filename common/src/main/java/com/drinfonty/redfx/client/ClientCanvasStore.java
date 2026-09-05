package com.drinfonty.redfx.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.drinfonty.redfx.canvas.BloodSplatter;
import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.CanvasKey;
import com.drinfonty.redfx.canvas.FaceAxes;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Client-side canvas store read concurrently by chunk-mesher worker threads.
 * Canvases and chunk maps are copy-on-write so readers never see torn data.
 */
public final class ClientCanvasStore {
	private static final ClientCanvasStore INSTANCE = new ClientCanvasStore();

	private static long chunkKey(BlockPos pos) {
		return (((long) (pos.getX() >> 4)) & 0xFFFFFFFFL) | ((((long) (pos.getZ() >> 4)) & 0xFFFFFFFFL) << 32);
	}

	private static int chunkX(long chunkKey) {
		return (int) (chunkKey & 0xFFFFFFFFL);
	}

	private static int chunkZ(long chunkKey) {
		return (int) (chunkKey >>> 32);
	}

	/** Interval between erosion steps (200ms = 5 times per second). */
	private static final long ERODE_INTERVAL_MS = 200L;
	/** Number of edge pixels erased per step. */
	private static final int ERODE_PIXELS_PER_STEP = 3;

	public static ClientCanvasStore get() {
		return INSTANCE;
	}

	private final ConcurrentHashMap<Long, Long2ObjectMap<Canvas>> chunks = new ConcurrentHashMap<>();

	private ClientCanvasStore() {
	}

	public Canvas get(BlockPos pos, int face) {
		Long2ObjectMap<Canvas> canvases = chunks.get(chunkKey(pos));
		if (canvases == null) {
			return null;
		}
		return canvases.get(CanvasKey.pack(pos.getX() & 0xF, pos.getY(), pos.getZ() & 0xF, face));
	}

	public boolean isPainted(BlockPos pos) {
		Long2ObjectMap<Canvas> canvases = chunks.get(chunkKey(pos));
		if (canvases == null || canvases.isEmpty()) {
			return false;
		}
		int lx = pos.getX() & 0xF;
		int y = pos.getY();
		int lz = pos.getZ() & 0xF;

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			if (canvases.containsKey(CanvasKey.pack(lx, y, lz, face))) {
				return true;
			}
		}
		return false;
	}

	public synchronized void put(BlockPos pos, int face, Canvas canvas) {
		long chunkKey = chunkKey(pos);
		long key = CanvasKey.pack(pos.getX() & 0xF, pos.getY(), pos.getZ() & 0xF, face);

		Long2ObjectMap<Canvas> current = chunks.get(chunkKey);
		Long2ObjectOpenHashMap<Canvas> next = current == null
			? new Long2ObjectOpenHashMap<>()
			: new Long2ObjectOpenHashMap<>(current);

		if (canvas == null || canvas.isEmpty()) {
			next.remove(key);
		} else {
			next.put(key, canvas);
		}

		if (next.isEmpty()) {
			chunks.remove(chunkKey);
		} else {
			chunks.put(chunkKey, Long2ObjectMaps.unmodifiable(next));
		}

		dirtySection(pos);
	}

	public synchronized void clearBlock(BlockPos pos) {
		long chunkKey = chunkKey(pos);
		Long2ObjectMap<Canvas> current = chunks.get(chunkKey);
		if (current == null || current.isEmpty()) {
			return;
		}

		int lx = pos.getX() & 0xF;
		int y = pos.getY();
		int lz = pos.getZ() & 0xF;
		boolean changed = false;
		Long2ObjectOpenHashMap<Canvas> next = new Long2ObjectOpenHashMap<>(current);

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			long key = CanvasKey.pack(lx, y, lz, face);
			if (next.remove(key) != null) {
				changed = true;
			}
		}

		if (changed) {
			if (next.isEmpty()) {
				chunks.remove(chunkKey);
			} else {
				chunks.put(chunkKey, Long2ObjectMaps.unmodifiable(next));
			}
			dirtySection(pos);
		}
	}

	public synchronized void clearChunk(long chunkPosPacked) {
		Long2ObjectMap<Canvas> removed = chunks.remove(chunkPosPacked);
		if (removed != null && !removed.isEmpty()) {
			int chunkX = chunkX(chunkPosPacked);
			int chunkZ = chunkZ(chunkPosPacked);
			dirtyChunk(chunkX, chunkZ);
		}
	}

	public synchronized void clearAll() {
		chunks.clear();
	}

	/**
	 * Ticked every client tick to slowly erode and dissolve decals that have passed their fade start time.
	 */
	public synchronized void tickExpiration(long nowMs) {
		if (chunks.isEmpty()) {
			return;
		}

		List<BlockPos> toDirty = new ArrayList<>();
		Iterator<Map.Entry<Long, Long2ObjectMap<Canvas>>> it = chunks.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Long, Long2ObjectMap<Canvas>> entry = it.next();
			Long2ObjectMap<Canvas> map = entry.getValue();
			Long2ObjectOpenHashMap<Canvas> next = null;

			for (Long2ObjectMap.Entry<Canvas> cEntry : map.long2ObjectEntrySet()) {
				Canvas c = cEntry.getValue();
				if (c == null || c.fadeStartTimeMs() <= 0) {
					continue;
				}

				if (nowMs >= c.fadeStartTimeMs() && nowMs >= c.nextErodeTimeMs()) {
					long key = cEntry.getLongKey();
					int[] texels = c.texels().clone();
					int erased = BloodSplatter.erode(texels, ERODE_PIXELS_PER_STEP);

					if (next == null) {
						next = new Long2ObjectOpenHashMap<>(map);
					}

					boolean empty = true;
					for (int t : texels) {
						if (t != 0) {
							empty = false;
							break;
						}
					}

					if (empty || erased == 0) {
						next.remove(key);
					} else {
						Canvas erodedCanvas = new Canvas(texels, c.fadeStartTimeMs(), nowMs + ERODE_INTERVAL_MS);
						next.put(key, erodedCanvas);
					}

					long chunkKey = entry.getKey();
					int chunkX = chunkX(chunkKey);
					int chunkZ = chunkZ(chunkKey);
					int lx = CanvasKey.localX(key);
					int lz = CanvasKey.localZ(key);
					int y = CanvasKey.y(key);
					toDirty.add(new BlockPos((chunkX << 4) + lx, y, (chunkZ << 4) + lz));
				}
			}

			if (next != null) {
				if (next.isEmpty()) {
					it.remove();
				} else {
					entry.setValue(Long2ObjectMaps.unmodifiable(next));
				}
			}
		}

		for (BlockPos pos : toDirty) {
			dirtySection(pos);
		}
	}

	private void dirtySection(BlockPos pos) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
		}
	}

	private void dirtyChunk(int chunkX, int chunkZ) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			int minSection = level.getMinSectionY();
			int maxSection = level.getMaxSectionY();
			for (int sy = minSection; sy < maxSection; sy++) {
				level.setSectionDirtyWithNeighbors(chunkX, sy, chunkZ);
			}
		}
	}
}
