package com.drinfonty.redfx.client.mixin;

import com.drinfonty.redfx.client.ClientCanvasStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clears painted blood decals when a block is changed or broken on the client.
 */
@Mixin(LevelChunk.class)
public abstract class ClientLevelChunkMixin {
	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void redfx$clearBloodOnChange(BlockPos pos, BlockState after, boolean isMoving,
		CallbackInfoReturnable<BlockState> callback) {
		BlockState oldState = callback.getReturnValue();
		if (oldState == null) {
			return;
		}
		if (after.isAir() || !after.is(oldState.getBlock())) {
			ClientCanvasStore store = ClientCanvasStore.get();
			if (store.isPainted(pos)) {
				store.clearBlock(pos);
			}
		}
	}
}
