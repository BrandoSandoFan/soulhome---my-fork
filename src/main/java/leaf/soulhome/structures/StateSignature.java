/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.BlockSignature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game-side {@link BlockSignature}: a real block, seen through the classifier's narrow window.
 *
 * <p>Identity is the {@code Block}, not the {@code BlockState}. A lit candle and an unlit one are
 * the same thing to an archetype, and collapsing states keeps the multiset small - a soulhome wall
 * of stairs would otherwise contribute a dozen distinct entries for what a player thinks of as one
 * kind of block.
 */
public final class StateSignature implements BlockSignature
{
    private static final Map<Block, StateSignature> CACHE = new ConcurrentHashMap<>();

    /**
     * Parsed tag ids. Reused across every block in every scan, so the hot path never re-parses a
     * {@code ResourceLocation}.
     */
    private static final Map<String, TagKey<Block>> TAG_KEYS = new ConcurrentHashMap<>();

    private final BlockState defaultState;
    private final String id;

    private StateSignature(Block block)
    {
        this.defaultState = block.defaultBlockState();

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        this.id = key == null ? "minecraft:air" : key.toString();
    }

    public static BlockSignature of(BlockState state)
    {
        return CACHE.computeIfAbsent(state.getBlock(), StateSignature::new);
    }

    @Override
    public String id()
    {
        return this.id;
    }

    @Override
    public boolean hasTag(String tagId)
    {
        TagKey<Block> key = TAG_KEYS.get(tagId);

        if (key == null)
        {
            ResourceLocation location = ResourceLocation.tryParse(tagId);

            if (location == null)
            {
                // a malformed tag in a datapack simply never matches; the loader has already
                // logged it, and failing a scan over it would be a worse outcome
                return false;
            }

            key = TagKey.create(Registries.BLOCK, location);
            TAG_KEYS.put(tagId, key);
        }

        // read live rather than cached: tag contents change on datapack reload
        return this.defaultState.is(key);
    }

    @Override
    public boolean equals(Object other)
    {
        return this == other
                || (other instanceof StateSignature signature && this.id.equals(signature.id));
    }

    @Override
    public int hashCode()
    {
        return this.id.hashCode();
    }

    @Override
    public String toString()
    {
        return this.id;
    }
}
