/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.RegionType;

import java.util.List;

/**
 * Deserialisation for {@link ArchetypeDefinition} and friends.
 *
 * <p>Kept apart from the definitions themselves so that the detection and scoring code stays free
 * of DataFixerUpper - which only exists inside a Minecraft classpath - and can therefore be unit
 * tested directly. Follows the {@code RecordCodecBuilder} style already used by
 * {@code SoulChunkGenerator} and {@code SyncDimensionListMessage}.
 */
public final class ArchetypeCodecs
{
    /**
     * Accepts either a bare string or an array of strings, so the common single-entry case stays
     * terse in a datapack:
     *
     * <pre>{@code
     * { "block": "minecraft:lectern" }
     * { "block": ["minecraft:chest", "minecraft:barrel"] }
     * }</pre>
     */
    public static final Codec<List<String>> STRING_OR_LIST =
            Codec.either(Codec.STRING, Codec.STRING.listOf())
                    .xmap(
                            // written out rather than List::of - the varargs overload makes the
                            // method reference's inference fragile in this position
                            either -> either.map(single -> List.of(single), many -> many),
                            list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));

    public static final Codec<RegionType> REGION_TYPE =
            Codec.STRING.comapFlatMap(
                    name -> RegionType.byName(name)
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(
                                    () -> "Unknown region type '" + name + "', expected 'enclosed' or 'open'")),
                    RegionType::getSerializedName);

    public static final Codec<BlockMatcher> BLOCK_MATCHER =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            STRING_OR_LIST.optionalFieldOf("block", List.of())
                                    .forGetter(BlockMatcher::blocks),
                            STRING_OR_LIST.optionalFieldOf("tag", List.of())
                                    .forGetter(BlockMatcher::tags))
                    .apply(instance, BlockMatcher::new));

    public static final Codec<ArchetypeDefinition.Requirement> REQUIREMENT =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            BLOCK_MATCHER.fieldOf("match")
                                    .forGetter(ArchetypeDefinition.Requirement::match),
                            Codec.INT.optionalFieldOf("min_count", 1)
                                    .forGetter(ArchetypeDefinition.Requirement::minCount))
                    .apply(instance, ArchetypeDefinition.Requirement::new));

    public static final Codec<ArchetypeDefinition.Signal> SIGNAL =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            BLOCK_MATCHER.fieldOf("match")
                                    .forGetter(ArchetypeDefinition.Signal::match),
                            Codec.DOUBLE.fieldOf("weight")
                                    .forGetter(ArchetypeDefinition.Signal::weight),
                            Codec.STRING.optionalFieldOf("role", ArchetypeDefinition.Signal.DEFAULT_ROLE)
                                    .forGetter(ArchetypeDefinition.Signal::role),
                            Codec.INT.optionalFieldOf("cap", ArchetypeDefinition.DEFAULT_CAP)
                                    .forGetter(ArchetypeDefinition.Signal::cap))
                    .apply(instance, ArchetypeDefinition.Signal::new));

    public static final Codec<ArchetypeDefinition.Tier> TIER =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.DOUBLE.fieldOf("min_score")
                                    .forGetter(ArchetypeDefinition.Tier::minScore),
                            Codec.INT.fieldOf("tier")
                                    .forGetter(ArchetypeDefinition.Tier::tier))
                    .apply(instance, ArchetypeDefinition.Tier::new));

    public static final Codec<ArchetypeDefinition.BuffSpec> BUFF_SPEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("type")
                                    .forGetter(ArchetypeDefinition.BuffSpec::type),
                            Codec.DOUBLE.fieldOf("per_tier")
                                    .forGetter(ArchetypeDefinition.BuffSpec::perTier),
                            Codec.DOUBLE.optionalFieldOf("max", Double.MAX_VALUE)
                                    .forGetter(ArchetypeDefinition.BuffSpec::max))
                    .apply(instance, ArchetypeDefinition.BuffSpec::new));

    /**
     * The datapack file format. There is no {@code id} field: an archetype is named by its file
     * path, so a file cannot claim to be something it is not. {@link ArchetypeManager} supplies the
     * real id via {@link ArchetypeDefinition#withId}.
     */
    public static final Codec<ArchetypeDefinition> ARCHETYPE =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("display_name")
                                    .forGetter(ArchetypeDefinition::displayName),
                            REGION_TYPE.listOf().optionalFieldOf("region_types", List.of(RegionType.ENCLOSED))
                                    .forGetter(ArchetypeDefinition::regionTypes),
                            Codec.INT.optionalFieldOf("min_volume", 1)
                                    .forGetter(ArchetypeDefinition::minVolume),
                            REQUIREMENT.listOf().optionalFieldOf("requirements", List.of())
                                    .forGetter(ArchetypeDefinition::requirements),
                            SIGNAL.listOf().optionalFieldOf("signals", List.of())
                                    .forGetter(ArchetypeDefinition::signals),
                            SIGNAL.listOf().optionalFieldOf("detractors", List.of())
                                    .forGetter(ArchetypeDefinition::detractors),
                            TIER.listOf().optionalFieldOf("tiers", List.of())
                                    .forGetter(ArchetypeDefinition::tiers),
                            BUFF_SPEC.listOf().optionalFieldOf("buffs", List.of())
                                    .forGetter(ArchetypeDefinition::buffs))
                    .apply(instance, (displayName, regionTypes, minVolume, requirements, signals, detractors, tiers, buffs) ->
                            new ArchetypeDefinition(
                                    ArchetypeDefinition.PLACEHOLDER_ID, displayName, regionTypes, minVolume,
                                    requirements, signals, detractors, tiers, buffs)));

    /**
     * The over-the-wire format, which does carry the id - the client has no file paths to derive
     * one from.
     */
    public static final Codec<ArchetypeDefinition> ARCHETYPE_WITH_ID =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("id")
                                    .forGetter(ArchetypeDefinition::id),
                            Codec.STRING.fieldOf("display_name")
                                    .forGetter(ArchetypeDefinition::displayName),
                            REGION_TYPE.listOf().optionalFieldOf("region_types", List.of(RegionType.ENCLOSED))
                                    .forGetter(ArchetypeDefinition::regionTypes),
                            Codec.INT.optionalFieldOf("min_volume", 1)
                                    .forGetter(ArchetypeDefinition::minVolume),
                            REQUIREMENT.listOf().optionalFieldOf("requirements", List.of())
                                    .forGetter(ArchetypeDefinition::requirements),
                            SIGNAL.listOf().optionalFieldOf("signals", List.of())
                                    .forGetter(ArchetypeDefinition::signals),
                            SIGNAL.listOf().optionalFieldOf("detractors", List.of())
                                    .forGetter(ArchetypeDefinition::detractors),
                            TIER.listOf().optionalFieldOf("tiers", List.of())
                                    .forGetter(ArchetypeDefinition::tiers),
                            BUFF_SPEC.listOf().optionalFieldOf("buffs", List.of())
                                    .forGetter(ArchetypeDefinition::buffs))
                    .apply(instance, ArchetypeDefinition::new));

    private ArchetypeCodecs()
    {
    }
}
