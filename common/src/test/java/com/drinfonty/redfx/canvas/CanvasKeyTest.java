package com.drinfonty.redfx.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CanvasKeyTest {
	@Test
	void roundTripsEveryCoordinateWithinRange() {
		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int y : new int[] { -64, 0, 64, 320 }) {
						long packed = CanvasKey.pack(lx, y, lz, face);
						assertEquals(lx, CanvasKey.localX(packed), "localX mismatch");
						assertEquals(y, CanvasKey.y(packed), "y mismatch");
						assertEquals(lz, CanvasKey.localZ(packed), "localZ mismatch");
						assertEquals(face, CanvasKey.face(packed), "face mismatch");
					}
				}
			}
		}
	}

	@Test
	void rejectsOutOfRangeCoordinates() {
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(-1, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(16, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(0, 0, -1, 0));
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(0, 0, 16, 0));
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(0, 0, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> CanvasKey.pack(0, 0, 0, 6));
	}
}
