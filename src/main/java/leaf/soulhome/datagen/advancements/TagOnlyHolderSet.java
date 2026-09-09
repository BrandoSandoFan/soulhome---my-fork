/* File created ~ 9 - 9 - 2026 */

package leaf.soulhome.datagen.advancements;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link HolderSet} that exists only to be written out as {@code "#namespace:path"}.
 *
 * <p>Data generation needs one of these because of an asymmetry in 1.21's predicate codecs. A
 * {@code LocationPredicate}'s biome field is a {@code HolderSet}, and {@code HolderSetCodec}
 * refuses to encode any set that reports it cannot be serialized in the registry set doing the
 * encoding. That check exists for a good reason at runtime - it is what stops a predicate from
 * naming an element of a registry that is not loaded - but at data generation time it has nothing
 * useful to check against: the soulhome biome and the {@code soulhome:is_soulhome} tag that names
 * it both ship as datapack JSON, and the generator's registry set is vanilla's built-ins plus
 * whatever a builtin-entries provider contributed. Neither is in it, and
 * {@code HolderSet.emptyNamed} bound to the generator's own biome lookup is still rejected,
 * because the owner the codec compares against is the one the serialization context wrapped rather
 * than the lookup itself.
 *
 * <p>So this says yes. It is a tag reference and nothing else: it never dereferences, never claims
 * to contain anything, and is used in exactly one place - writing the
 * "you entered your soulhome" advancement. The tag is resolved by the game at load time, which is
 * when the biome genuinely does exist, and a missing tag there is a loud error rather than a silent
 * pass. Nothing about this reaches the running game.
 */
record TagOnlyHolderSet<T>(TagKey<T> tag) implements HolderSet<T>
{
    @Override
    public Either<TagKey<T>, List<Holder<T>>> unwrap()
    {
        return Either.left(this.tag);
    }

    @Override
    public Optional<TagKey<T>> unwrapKey()
    {
        return Optional.of(this.tag);
    }

    /**
     * Always true - see the class javadoc. This is the whole reason the type exists; everything else
     * here is the minimum an unreadable set can answer.
     */
    @Override
    public boolean canSerializeIn(HolderOwner<T> owner)
    {
        return true;
    }

    @Override
    public Stream<Holder<T>> stream()
    {
        return Stream.empty();
    }

    @Override
    public int size()
    {
        return 0;
    }

    @Override
    public Optional<Holder<T>> getRandomElement(RandomSource random)
    {
        return Optional.empty();
    }

    @Override
    public Holder<T> get(int index)
    {
        throw new UnsupportedOperationException(this.tag + " is a reference for writing, not a readable set");
    }

    @Override
    public boolean contains(Holder<T> holder)
    {
        return false;
    }

    @Override
    public Iterator<Holder<T>> iterator()
    {
        return stream().iterator();
    }
}
