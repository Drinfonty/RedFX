package com.drinfonty.redfx.client.render;

import java.util.ArrayList;
import java.util.List;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.PaintColor;

/**
 * Greedy-merges runs of painted texels into maximal rectangles on a face.
 */
public final class CanvasMesher {
	private CanvasMesher() {
	}

	public static List<PaintQuad> mesh(int[] texels, int face) {
		if (texels.length != Canvas.TEXELS) {
			throw new IllegalArgumentException("canvas must be " + Canvas.TEXELS + " texels");
		}

		List<PaintQuad> quads = new ArrayList<>();

		int[] runStart = new int[Canvas.SIZE];
		int[] runEnd = new int[Canvas.SIZE];
		int[] runColor = new int[Canvas.SIZE];
		int[] runTop = new int[Canvas.SIZE];
		int openRuns = 0;

		int[] nextStart = new int[Canvas.SIZE];
		int[] nextEnd = new int[Canvas.SIZE];
		int[] nextColor = new int[Canvas.SIZE];
		int[] nextTop = new int[Canvas.SIZE];

		int[] rowStart = new int[Canvas.SIZE];
		int[] rowEnd = new int[Canvas.SIZE];
		int[] rowColor = new int[Canvas.SIZE];

		for (int pv = 0; pv < Canvas.SIZE; pv++) {
			int rowRuns = 0;
			int pu = 0;

			while (pu < Canvas.SIZE) {
				int value = PaintColor.normalise(texels[pv * Canvas.SIZE + pu]);

				if (!PaintColor.isPainted(value)) {
					pu++;
					continue;
				}

				int end = pu + 1;

				while (end < Canvas.SIZE
					&& PaintColor.normalise(texels[pv * Canvas.SIZE + end]) == value) {
					end++;
				}

				rowStart[rowRuns] = pu;
				rowEnd[rowRuns] = end;
				rowColor[rowRuns] = value;
				rowRuns++;

				pu = end;
			}

			// Try to extend runs from the previous row downwards
			int nextOpenRuns = 0;
			int matchedMask = 0;

			for (int i = 0; i < openRuns; i++) {
				int match = -1;

				for (int j = 0; j < rowRuns; j++) {
					if ((matchedMask & (1 << j)) == 0
						&& rowStart[j] == runStart[i]
						&& rowEnd[j] == runEnd[i]
						&& rowColor[j] == runColor[i]) {
						match = j;
						break;
					}
				}

				if (match >= 0) {
					matchedMask |= (1 << match);
					nextStart[nextOpenRuns] = runStart[i];
					nextEnd[nextOpenRuns] = runEnd[i];
					nextColor[nextOpenRuns] = runColor[i];
					nextTop[nextOpenRuns] = runTop[i];
					nextOpenRuns++;
				} else {
					// Finish the rectangle
					quads.add(new PaintQuad(face, runStart[i], runTop[i], runEnd[i], pv, runColor[i]));
				}
			}

			// Add new unmatched runs from this row
			for (int j = 0; j < rowRuns; j++) {
				if ((matchedMask & (1 << j)) == 0) {
					nextStart[nextOpenRuns] = rowStart[j];
					nextEnd[nextOpenRuns] = rowEnd[j];
					nextColor[nextOpenRuns] = rowColor[j];
					nextTop[nextOpenRuns] = pv;
					nextOpenRuns++;
				}
			}

			int[] swap = runStart; runStart = nextStart; nextStart = swap;
			swap = runEnd; runEnd = nextEnd; nextEnd = swap;
			swap = runColor; runColor = nextColor; nextColor = swap;
			swap = runTop; runTop = nextTop; nextTop = swap;
			openRuns = nextOpenRuns;
		}

		// Flush any remaining open runs
		for (int i = 0; i < openRuns; i++) {
			quads.add(new PaintQuad(face, runStart[i], runTop[i], runEnd[i], Canvas.SIZE, runColor[i]));
		}

		return quads;
	}
}
