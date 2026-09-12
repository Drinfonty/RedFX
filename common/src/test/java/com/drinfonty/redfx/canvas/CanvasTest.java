package com.drinfonty.redfx.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanvasTest {

	@Test
	void rejectsInvalidTexelArraySize() {
		assertThrows(IllegalArgumentException.class, () -> new Canvas(new int[100], 1000L));
		assertThrows(IllegalArgumentException.class, () -> new Canvas(new int[300], 1000L, 2000L));
	}

	@Test
	void emptyCanvasProperties() {
		Canvas empty = Canvas.empty();
		assertTrue(empty.isEmpty());
		assertEquals(0L, empty.fadeStartTimeMs());
		assertEquals(0L, empty.nextErodeTimeMs());
		assertEquals(Canvas.TEXELS, empty.texels().length);
	}

	@Test
	void isEmptyDetectsPaintedTexels() {
		int[] texels = new int[Canvas.TEXELS];
		Canvas canvas = new Canvas(texels, 1000L, 2000L);
		assertTrue(canvas.isEmpty());

		texels[42] = 0x88CC0000;
		Canvas painted = new Canvas(texels, 1000L, 2000L);
		assertFalse(painted.isEmpty());
	}

	@Test
	void copyProducesDistinctEqualInstance() {
		int[] texels = new int[Canvas.TEXELS];
		texels[10] = 0xFFFF0000;
		Canvas original = new Canvas(texels, 1500L, 2500L);

		Canvas copy = original.copy();
		assertEquals(original, copy);
		assertEquals(original.hashCode(), copy.hashCode());
		assertNotEquals(System.identityHashCode(original), System.identityHashCode(copy));
		assertNotEquals(System.identityHashCode(original.texels()), System.identityHashCode(copy.texels()));

		// Mutating copy array does not mutate original array
		copy.texels()[10] = 0;
		assertNotEquals(original, copy);
	}

	@Test
	void equalsAndHashCodeContract() {
		int[] t1 = new int[Canvas.TEXELS];
		t1[5] = 0xFF112233;
		int[] t2 = t1.clone();

		Canvas c1 = new Canvas(t1, 1000L, 2000L);
		Canvas c2 = new Canvas(t2, 1000L, 2000L);
		Canvas c3 = new Canvas(t2, 1001L, 2000L); // different fade start
		Canvas c4 = new Canvas(t2, 1000L, 2001L); // different next erode

		int[] tDiff = t1.clone();
		tDiff[5] = 0xFF998877;
		Canvas cDiff = new Canvas(tDiff, 1000L, 2000L);

		assertEquals(c1, c1);
		assertEquals(c1, c2);
		assertEquals(c1.hashCode(), c2.hashCode());

		assertNotEquals(c1, c3);
		assertNotEquals(c1, c4);
		assertNotEquals(c1, cDiff);
		assertNotEquals(c1, null);
		assertNotEquals(c1, "not a canvas");
	}
}
