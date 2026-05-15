package com.wiyuka.acceleratedrecoiling.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@EventBusSubscriber(modid = "acceleratedrecoiling")
public class FastLadderCache {
    private static boolean[] CLIMBABLE_BLOCKS = new boolean[0];
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            FastLadderCache.rebuildCache();
        }
    }
    public static void rebuildCache() {
        int maxId = BuiltInRegistries.BLOCK.size() + 256;
        boolean[] newCache = new boolean[maxId];

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.builtInRegistryHolder().is(BlockTags.CLIMBABLE)) {
                int id = BuiltInRegistries.BLOCK.getId(block);
                if (id >= 0 && id < newCache.length) {
                    newCache[id] = true;
                }
            }
        }

        CLIMBABLE_BLOCKS = newCache;
    }

    public static boolean isFastLadder(Block block) {
        int id = BuiltInRegistries.BLOCK.getId(block);
        if (id >= 0 && id < CLIMBABLE_BLOCKS.length) {
            return CLIMBABLE_BLOCKS[id];
        }
        return false;
    }
}
