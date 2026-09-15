package com.drinfonty.redfx.client.mixin;

import com.drinfonty.redfx.client.test.InGameSmokeTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientSmokeTestMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void redfx$onClientTick(CallbackInfo ci) {
		InGameSmokeTest.tick((Minecraft) (Object) this);
	}
}
