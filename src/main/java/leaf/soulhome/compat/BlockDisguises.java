/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * A copycat panel wearing bookshelves looks like bookshelves to the player who built the room out
 * of them, and should score like bookshelves. {@link leaf.soulhome.structures.core.BlockSignature}
 * cannot know that on its own: the material lives in the copycat's block entity, not in the
 * {@code BlockState} the snapshot copies, and {@code structures/core} is Minecraft-free on purpose
 * and must never gain a reason to know what a copycat even is.
 *
 * <p>So the substitution happens here, on the game side, before a signature is ever built - see
 * {@code SnapshotBlockVolume#capture}, the only caller. Resolved by block entity instance rather
 * than by block id, because Create's own copycats already share one block entity class across
 * every copycat block it ships (and whatever it ships next), so one disguise handles all of them
 * with no per-block-id list to keep in sync.
 *
 * <p>Every disguise here reaches its mod through reflection alone, the same rule
 * {@link ModAttributes} follows and for the same reason: a hard reference to a Create class in
 * this mod's constant pool would be a {@code NoClassDefFoundError} the first time anything here
 * ran on a server that never installed Create.
 */
public final class BlockDisguises
{
    private static final List<BlockDisguise> HANDLERS = List.of(new CreateCopycatDisguise());

    private BlockDisguises()
    {
    }

    /**
     * @return the material the given block entity is dressed as, or empty if none of the known
     *         disguises recognise it - which includes every block entity when no disguised-block
     *         mod is installed at all
     */
    public static Optional<BlockState> materialOf(BlockEntity blockEntity)
    {
        for (BlockDisguise handler : HANDLERS)
        {
            Optional<BlockState> material = handler.materialOf(blockEntity);

            if (material.isPresent())
            {
                return material;
            }
        }

        return Optional.empty();
    }
}
