/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.Aspect;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.Bond;
import leaf.soulhome.structures.core.BondRelationRegistry;
import leaf.soulhome.structures.core.Form;
import leaf.soulhome.structures.core.FormClauseRegistry;
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
                                    .forGetter(ArchetypeDefinition.Requirement::minCount),
                            Codec.DOUBLE.optionalFieldOf("min_volume_fraction", 0d)
                                    .forGetter(ArchetypeDefinition.Requirement::minVolumeFraction))
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
                                    .forGetter(ArchetypeDefinition.Signal::cap),
                            Codec.BOOL.optionalFieldOf("seed", true)
                                    .forGetter(ArchetypeDefinition.Signal::seed))
                    .apply(instance, ArchetypeDefinition.Signal::new));

    public static final Codec<ArchetypeDefinition.Tier> TIER =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.DOUBLE.fieldOf("min_score")
                                    .forGetter(ArchetypeDefinition.Tier::minScore),
                            Codec.INT.fieldOf("tier")
                                    .forGetter(ArchetypeDefinition.Tier::tier))
                    .apply(instance, ArchetypeDefinition.Tier::new));

    /**
     * Weighted structural evidence - see {@link leaf.soulhome.structures.core.Form} and the
     * structural form grammar epic (#27). Hand-written rather than built here from
     * {@code RecordCodecBuilder}: the clause tree it reads is recursive and dispatches on an open,
     * datapack-facing vocabulary, neither of which {@code RecordCodecBuilder} expresses directly.
     */
    public static final Codec<Form> FORM = FormCodecs.forRegistry(FormClauseRegistry.BUILTIN);

    /**
     * Why this is not {@code FORM.listOf()}: see {@link FormCodecs#listOfForms}. A form this install
     * cannot evaluate has to drop out of the list without taking the archetype with it, and since
     * 1.20.5 DataFixerUpper's own list codec no longer does that for us.
     */
    public static final Codec<List<Form>> FORM_LIST = FormCodecs.listOfForms(FORM);

    /**
     * Where a room sits relative to other rooms - see {@link Bond} and the Soul Architecture epic
     * (#140). Hand-written for the same reasons {@link #FORM} is, and lenient element by element
     * the way {@link #FORM_LIST} is; see {@link BondCodecs}.
     */
    public static final Codec<List<Bond>> BONDS = BondCodecs.listForRegistry(BondRelationRegistry.BUILTIN);

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
     * One piece of evidence that a room is meant for an aspect - the Aspects epic (#171). Shaped
     * like a {@link #SIGNAL} minus the two fields that would mean nothing on it: an aspect touches
     * no diversity multiplier, so there is no {@code role}, and it never seeds an open-air cluster,
     * so there is no {@code seed}.
     */
    public static final Codec<Aspect.Lean> LEAN =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            BLOCK_MATCHER.fieldOf("match")
                                    .forGetter(Aspect.Lean::match),
                            Codec.DOUBLE.fieldOf("weight")
                                    .forGetter(Aspect.Lean::weight),
                            Codec.INT.optionalFieldOf("cap", ArchetypeDefinition.DEFAULT_CAP)
                                    .forGetter(Aspect.Lean::cap))
                    .apply(instance, Aspect.Lean::new));

    /**
     * What a room of this archetype can be <i>for</i>. {@code default} marks the one aspect that
     * pays the archetype's own top-level {@code buffs}; an aspect's own {@code structures} are
     * graded exactly as the archetype's are but credited only to the aspect, since a library is
     * expected to hold a lectern and only a scriptorium is expected to hold them in rows. They read
     * through {@link #FORM_LIST} for the same reason the archetype's own do: a form this install
     * cannot evaluate drops out rather than taking the archetype with it.
     *
     * <p>The rule that every block an aspect reads is a block the room already scores is not
     * expressed here. It cannot be: a codec sees one field at a time and the check needs the
     * archetype's own signals beside the aspect. It lives in
     * {@link ArchetypeDefinition#validationErrors} instead, which {@link ArchetypeManager} already
     * runs over every loaded archetype - so a malformed aspect is logged and skipped like any other
     * malformed element rather than failing the reload.
     */
    public static final Codec<Aspect> ASPECT =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("id")
                                    .forGetter(Aspect::id),
                            Codec.STRING.fieldOf("display_name")
                                    .forGetter(Aspect::displayName),
                            Codec.BOOL.optionalFieldOf("default", false)
                                    .forGetter(Aspect::isDefault),
                            LEAN.listOf().optionalFieldOf("leans", List.of())
                                    .forGetter(Aspect::leans),
                            FORM_LIST.optionalFieldOf("structures", List.of())
                                    .forGetter(Aspect::structures),
                            BUFF_SPEC.listOf().optionalFieldOf("buffs", List.of())
                                    .forGetter(Aspect::buffs))
                    .apply(instance, Aspect::new));

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
                                    .forGetter(ArchetypeDefinition::buffs),
                            FORM_LIST.optionalFieldOf("structures", List.of())
                                    .forGetter(ArchetypeDefinition::structures),
                            BONDS.optionalFieldOf("bonds", List.of())
                                    .forGetter(ArchetypeDefinition::bonds),
                            ASPECT.listOf().optionalFieldOf("aspects", List.of())
                                    .forGetter(ArchetypeDefinition::aspects))
                    .apply(instance, (displayName, regionTypes, minVolume, requirements, signals, detractors, tiers, buffs, structures, bonds, aspects) ->
                            new ArchetypeDefinition(
                                    ArchetypeDefinition.PLACEHOLDER_ID, displayName, regionTypes, minVolume,
                                    requirements, signals, detractors, tiers, buffs, structures, bonds, aspects)));

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
                                    .forGetter(ArchetypeDefinition::buffs),
                            FORM_LIST.optionalFieldOf("structures", List.of())
                                    .forGetter(ArchetypeDefinition::structures),
                            BONDS.optionalFieldOf("bonds", List.of())
                                    .forGetter(ArchetypeDefinition::bonds),
                            // aspects reach the client alongside everything else: the lens names
                            // them and the client has no datapack to read them from (#172)
                            ASPECT.listOf().optionalFieldOf("aspects", List.of())
                                    .forGetter(ArchetypeDefinition::aspects))
                    .apply(instance, ArchetypeDefinition::new));

    private ArchetypeCodecs()
    {
    }
}
