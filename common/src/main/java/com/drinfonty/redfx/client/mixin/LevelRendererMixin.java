package com.drinfonty.redfx.client.mixin;

import com.drinfonty.redfx.client.ClientCanvasStore;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears painted blood decals when a block is changed or broken on the client.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "blockChanged", at = @At("HEAD"))
	private void redfx$clearBloodOnChange(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState,
		int flags, CallbackInfo callback) {
		ClientCanvasStore store = ClientCanvasStore.get();
		if (store.isPainted(pos)) {
			store.clearBlock(pos);
		}
	}
}
