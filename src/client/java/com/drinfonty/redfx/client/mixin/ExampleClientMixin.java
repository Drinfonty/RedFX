package com.drinfonty.redfx.client.mixin;

import com.drinfonty.redfx.RedfxMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ExampleClientMixin {
	@Inject(at = @At("HEAD"), method = "run")
	private void init(CallbackInfo info) {
		RedfxMod.LOGGER.info("Hello from Minecraft.run() client Mixin!");
	}
}
