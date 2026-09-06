package com.drinfonty.redfx.client.render;

import com.drinfonty.redfx.RedfxMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class PaintSprites {
	private static TextureAtlasSprite cached;

	private PaintSprites() {
	}

	public static TextureAtlasSprite paint() {
		TextureAtlasSprite sprite = cached;
		if (sprite == null) {
			sprite = Minecraft.getInstance().getModelManager()
				.getAtlas(TextureAtlas.LOCATION_BLOCKS)
				.getSprite(RedfxMod.id("block/splat/white"));
			cached = sprite;
		}
		return sprite;
	}

	public static void invalidate() {
		cached = null;
	}
}
