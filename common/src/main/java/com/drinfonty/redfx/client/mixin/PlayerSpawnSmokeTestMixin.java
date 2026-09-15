package com.drinfonty.redfx.client.mixin;

import com.drinfonty.redfx.client.test.InGameSmokeTest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the actual player spawn event when the client player entity is added to the ClientLevel.
 */
@Mixin(ClientLevel.class)
public class PlayerSpawnSmokeTestMixin {
	@Inject(method = "addEntity", at = @At("RETURN"))
	private void redfx$onPlayerSpawned(Entity entity, CallbackInfo ci) {
		if (InGameSmokeTest.ENABLED && entity instanceof LocalPlayer) {
			InGameSmokeTest.onPlayerSpawned();
		}
	}
}
