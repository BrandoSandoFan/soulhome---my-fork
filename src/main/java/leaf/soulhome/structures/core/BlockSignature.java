/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The classifier's view of a block.
 *
 * <p>Deliberately narrow, and deliberately free of any Minecraft type. Region detection and
 * archetype scoring are the parts of this feature most likely to go subtly wrong, so they are
 * kept unit-testable against synthetic worlds. The game-side implementation wraps a
 * {@code BlockState}; tests use a plain record.
 *
 * <p><b>Implementations must have value equality.</b> Signatures are used as multiset keys, so
 * two occurrences of the same block have to collapse onto one entry. The game-side
 * implementation keys on the {@code Block}, not the {@code BlockState}, so a lit and an unlit
 * candle are the same signature.
 */
public interface BlockSignature
{
    /**
     * The block's registry id, e.g. {@code "minecraft:bookshelf"}.
     */
    String id();

    /**
     * Whether this block carries the given block tag, e.g. {@code "soulhome:bookshelves"}.
     *
     * @param tagId a namespaced tag id, without a leading {@code #}
     */
    boolean hasTag(String tagId);
}
