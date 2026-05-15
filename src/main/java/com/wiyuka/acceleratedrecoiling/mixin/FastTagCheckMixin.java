package com.wiyuka.acceleratedrecoiling.mixin;

import com.wiyuka.acceleratedrecoiling.utils.FastLadderCache;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FastTagCheckMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void fastIsClimbableCheck(TagKey<Block> tag, CallbackInfoReturnable<Boolean> cir) {
        if (tag == BlockTags.CLIMBABLE) {
            boolean isClimbable = FastLadderCache.isFastLadder(((BlockBehaviour.BlockStateBase)(Object)this).getBlock());
            cir.setReturnValue(isClimbable);
        }
    }
}