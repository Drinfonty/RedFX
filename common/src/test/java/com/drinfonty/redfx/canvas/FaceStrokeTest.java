package com.drinfonty.redfx.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FaceStrokeTest {
	@Test
	void roundTripsBlockCoordinatesOnAllFaces() {
		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			for (int bx = -2; bx <= 2; bx++) {
				for (int by = 0; by <= 2; by++) {
					for (int bz = -2; bz <= 2; bz++) {
						int bu = FaceStroke.blockU(face, bx, by, bz);
						int bv = FaceStroke.blockV(face, bx, by, bz);
						int norm = FaceStroke.normal(face, bx, by, bz);

						int rx = FaceStroke.worldX(face, bu, bv, norm);
						int ry = FaceStroke.worldY(face, bu, bv, norm);
						int rz = FaceStroke.worldZ(face, bu, bv, norm);

						assertEquals(bx, rx, "x mismatch for face " + face);
						assertEquals(by, ry, "y mismatch for face " + face);
						assertEquals(bz, rz, "z mismatch for face " + face);

						for (int u = 0; u < 16; u++) {
							int encU = FaceStroke.encodeU(face, bu, u);
							assertEquals(bu, FaceStroke.blockOfU(face, encU), "blockOfU for face " + face);
						}
						for (int v = 0; v < 16; v++) {
							int encV = FaceStroke.encodeV(face, bv, v);
							assertEquals(bv, FaceStroke.blockOfV(face, encV), "blockOfV for face " + face);
						}
					}
				}
			}
		}
	}
}
