package com.drinfonty.redfx.client.render;

import com.drinfonty.redfx.RedfxMod;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class PaintSprites {
	private static TextureAtlasSprite cached;

	private PaintSprites() {
	}

	public static TextureAtlasSprite paint() {
		TextureAtlasSprite sprite = cached;
		if (sprite == null) {
			sprite = Sheets.BLOCKS_MAPPER.apply(RedfxMod.id("splat/white")).sprite();
			cached = sprite;
		}
		return sprite;
	}

	public static void invalidate() {
		cached = null;
	}
}
