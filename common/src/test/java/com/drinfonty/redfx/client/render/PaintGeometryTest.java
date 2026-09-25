package com.drinfonty.redfx.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.drinfonty.redfx.canvas.FaceAxes;

class PaintGeometryTest {
	private static final float D = PaintGeometry.DECAL_OFFSET;

	@Test
	void topFaceAndSideFacesMeetSeamlesslyAtEdges() {
		// Top face quad spanning full width at north edge (u: 0..16, v: 0..1)
		PaintQuad topQuadNorth = new PaintQuad(FaceAxes.UP, 0, 0, 16, 1, 0xFF000000);
		float[] topCorners = new float[12];
		PaintGeometry.corners(topQuadNorth, topCorners, 1.0F);

		// North face quad at top edge (u: 0..16, v: 0..1)
		PaintQuad northQuad = new PaintQuad(FaceAxes.NORTH, 0, 0, 16, 1, 0xFF000000);
		float[] northCorners = new float[12];
		PaintGeometry.corners(northQuad, northCorners, 1.0F);

		// On top face:
		// corners order: bottom-left (u0, v1), bottom-right (u1, v1), top-right (u1, v0), top-left (u0, v0)
		// For v0 = 0 (north edge):
		// top-right (index 6, 7, 8): u1=1.0, v0=0.0 -> x = 1.0+D, y = 1.0+D, z = -D
		// top-left (index 9, 10, 11): u0=0.0, v0=0.0 -> x = -D, y = 1.0+D, z = -D
		assertEquals(1.0F + D, topCorners[6], 1e-5F);
		assertEquals(1.0F + D, topCorners[7], 1e-5F);
		assertEquals(-D, topCorners[8], 1e-5F);

		assertEquals(-D, topCorners[9], 1e-5F);
		assertEquals(1.0F + D, topCorners[10], 1e-5F);
		assertEquals(-D, topCorners[11], 1e-5F);

		// On north face:
		// For v0 = 0 (top edge of north face):
		// top-right (index 6, 7, 8): u1=1.0, v0=0.0 -> x = -D, y = 1.0+D, z = -D
		// top-left (index 9, 10, 11): u0=0.0, v0=0.0 -> x = 1.0+D, y = 1.0+D, z = -D
		assertEquals(-D, northCorners[6], 1e-5F);
		assertEquals(1.0F + D, northCorners[7], 1e-5F);
		assertEquals(-D, northCorners[8], 1e-5F);

		assertEquals(1.0F + D, northCorners[9], 1e-5F);
		assertEquals(1.0F + D, northCorners[10], 1e-5F);
		assertEquals(-D, northCorners[11], 1e-5F);

		// Both top and north face meet along the exact segment: (-D, 1.0+D, -D) to (1.0+D, 1.0+D, -D)
	}

	@Test
	void partialBlockSideFacesPositionAtSurfaceY() {
		// Snow layer with surfaceY = 0.125F (2 texels high)
		float surfaceY = 0.125F;
		PaintQuad snowQuad = new PaintQuad(FaceAxes.EAST, 0, 0, 16, 2, 0xFF000000);
		float[] corners = new float[12];
		PaintGeometry.corners(snowQuad, corners, surfaceY);

		// v0 = 0.0 -> top edge should be at surfaceY + D = 0.125F + D
		assertEquals(surfaceY + D, corners[7], 1e-5F); // top-right Y
		assertEquals(surfaceY + D, corners[10], 1e-5F); // top-left Y

		// v1 = 2/16 = 0.125F -> bottom edge should be at surfaceY - v1 = 0.125F - 0.125F = 0.0F
		assertEquals(0.0F, corners[1], 1e-5F); // bottom-left Y
		assertEquals(0.0F, corners[4], 1e-5F); // bottom-right Y
	}
}
