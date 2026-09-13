/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.structures.core.ArchetypeClassifier;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.ArchetypeSignals;
import leaf.soulhome.structures.core.Aspect;
import leaf.soulhome.structures.core.BlockSignature;
import leaf.soulhome.structures.core.BondBook;
import leaf.soulhome.structures.core.BondRelationRegistry;
import leaf.soulhome.structures.core.RegionGeometry;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Loads archetype definitions from datapacks.
 *
 * <p>Reads {@code data/<namespace>/soulhome_archetypes/<name>.json}. A malformed archetype is
 * logged loudly and skipped; it never fails the reload, because taking a whole datapack down over
 * one bad weight would be a much worse experience for the packmaker than a clear log line.
 *
 * <p>Holds the loaded set statically. The server writes it on reload; readers get an immutable
 * snapshot, so a scan running on a worker thread cannot see a half-applied reload.
 */
public class ArchetypeManager extends SimpleJsonResourceReloadListener
{
    /** Datapack folder these definitions live in. */
    public static final String DIRECTORY = "soulhome_archetypes";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static volatile Loaded loaded = Loaded.EMPTY;

    public ArchetypeManager()
    {
        super(GSON, DIRECTORY);
    }

    public static List<ArchetypeDefinition> archetypes()
    {
        return loaded.archetypes();
    }

    /** Classifier built over the currently loaded archetypes. */
    public static ArchetypeClassifier classifier()
    {
        return loaded.classifier();
    }

    /**
     * Which blocks {@code RegionScanner} should cluster open-air regions around - the palettes of
     * the archetypes that accept open regions, not of every archetype. See
     * {@link ArchetypeSignals#openClusterFilterFor} for why the two differ (#134).
     */
    public static Predicate<BlockSignature> signalFilter()
    {
        return loaded.signalFilter();
    }

    /** Which blocks {@code RegionScanner} should keep positions for - see {@link RegionGeometry}. */
    public static Predicate<BlockSignature> geometryFilter()
    {
        return loaded.geometryFilter();
    }

    /** Whether {@code RegionScanner} should also track clearance data - see {@link ArchetypeSignals#needsClearance}. */
    public static boolean needsClearance()
    {
        return loaded.needsClearance();
    }

    /**
     * The whole bundle in one read. A caller that needs more than one of {@link #classifier()},
     * {@link #signalFilter()}, {@link #geometryFilter()} or {@link #needsClearance()} for the same
     * pass - a scan worker, most of all - must take this once and read every value off it, rather
     * than calling the accessors above separately: each of those re-reads the volatile field, and a
     * reload landing between two such reads would pair one archetype set's filters with another
     * set's classifier.
     */
    public static Loaded loaded()
    {
        return loaded;
    }

    /**
     * Replace the loaded set. Used by the reload listener on the server, and by the sync packet on
     * the client.
     */
    public static void replaceAll(Collection<ArchetypeDefinition> archetypes)
    {
        loaded = Loaded.of(archetypes);
    }

    /**
     * Rebuild the classifier after the scoring config changed. The classifier holds its settings
     * rather than reading them per region, so a config reload has to be pushed rather than
     * waited for.
     */
    public static void onScoringSettingsChanged()
    {
        loaded = Loaded.of(loaded.archetypes());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler)
    {
        Map<String, ArchetypeDefinition> accepted = new LinkedHashMap<>();
        int rejected = 0;

        for (Map.Entry<String, JsonElement> entry : sortedByKey(files).entrySet())
        {
            final String id = entry.getKey();

            if (holdsABondAsARequirement(entry.getValue()))
            {
                // rule 1 of the Soul Architecture epic (#140): a bond is evidence, never a gate, and
                // the codec is the cheapest place to guarantee it. Said in as many words, because
                // the parse failure that would otherwise report this names a missing 'match' key
                LogHelper.error("Could not read soulhome archetype " + id
                        + ": 'requirements' holds a bond. A bond is evidence, never a gate; it belongs under 'bonds'.");
                rejected++;
                continue;
            }

            ArchetypeDefinition definition = ArchetypeCodecs.ARCHETYPE
                    .parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error -> LogHelper.error("Could not read soulhome archetype " + id + ": " + error))
                    .orElse(null);

            if (definition == null)
            {
                rejected++;
                continue;
            }

            definition = definition.withId(id);
            List<String> errors = definition.validationErrors();

            if (!errors.isEmpty())
            {
                for (String error : errors)
                {
                    LogHelper.error("Soulhome archetype " + id + " is invalid: " + error);
                }

                rejected++;
                continue;
            }

            warnAboutUnknownBuffs(definition);

            for (String warning : definition.validationWarnings())
            {
                LogHelper.warn("Soulhome archetype " + id + ": " + warning);
            }

            accepted.put(id, definition);
        }

        replaceAll(accepted.values());

        // the bonds between them, resolved once here so a mirror declared on both sides or a
        // relation nothing registered is said once at load rather than discovered per scan
        BondBook bonds = BondBook.of(accepted.values(), BondRelationRegistry.BUILTIN);

        for (String warning : bonds.duplicates())
        {
            LogHelper.warn("Soulhome bond: " + warning);
        }

        for (String warning : bonds.unknownRelations())
        {
            LogHelper.warn("Soulhome bond: " + warning);
        }

        LogHelper.info("Loaded " + accepted.size() + " soulhome archetype(s)"
                + (rejected > 0 ? ", skipped " + rejected + " that failed to load" : "")
                + ": " + accepted.keySet());
    }

    /** Whether any entry of {@code requirements} looks like a bond - carries {@code with} or {@code relation}. */
    private static boolean holdsABondAsARequirement(JsonElement json)
    {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("requirements"))
        {
            return false;
        }

        JsonElement requirements = json.getAsJsonObject().get("requirements");

        if (!requirements.isJsonArray())
        {
            return false;
        }

        for (JsonElement requirement : requirements.getAsJsonArray())
        {
            if (requirement.isJsonObject()
                    && (requirement.getAsJsonObject().has("with") || requirement.getAsJsonObject().has("relation")))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * A warning rather than a rejection: an archetype may legitimately grant a buff type added by
     * another mod that is not installed. But a typo in a buff id would otherwise produce a room
     * that classifies perfectly and then does nothing at all, which is a miserable thing to debug.
     */
    private static void warnAboutUnknownBuffs(ArchetypeDefinition definition)
    {
        warnAboutUnknownBuffs(definition, definition.id(), definition.buffs());

        // an aspect's payout is as easy to typo as the archetype's own, and the result is worse:
        // a room that classifies, takes the aspect the player built for, and grants nothing (#172)
        for (Aspect aspect : definition.aspects())
        {
            warnAboutUnknownBuffs(definition, definition.id() + " aspect '" + aspect.id() + "'", aspect.buffs());
        }
    }

    private static void warnAboutUnknownBuffs(ArchetypeDefinition definition, String where, List<ArchetypeDefinition.BuffSpec> buffs)
    {
        for (ArchetypeDefinition.BuffSpec buff : buffs)
        {
            if (!SoulBuffEffects.isKnown(buff.type()))
            {
                LogHelper.warn("Soulhome archetype " + where + " grants '" + buff.type()
                        + "', which nothing is registered to apply. Known buff types: "
                        + SoulBuffEffects.knownTypes());
            }
        }
    }

    /**
     * Deterministic order, so that two servers with the same datapacks tie-break equal scores the
     * same way.
     */
    private static Map<String, JsonElement> sortedByKey(Map<ResourceLocation, JsonElement> files)
    {
        List<Map.Entry<ResourceLocation, JsonElement>> entries = new ArrayList<>(files.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));

        Map<String, JsonElement> byId = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries)
        {
            byId.put(entry.getKey().toString(), entry.getValue());
        }

        return byId;
    }

    /**
     * One immutable bundle, so readers never observe a torn update: the classifier and the signal
     * filter are always the ones derived from exactly these archetypes.
     *
     * @param adjacencyReach how far {@code RegionScanner} should look for paths and routes between
     *                       regions - the furthest any loaded bond could grade, and zero when none
     *                       is distance-based, see {@link ArchetypeSignals#adjacencyReachFor}
     */
    public record Loaded(
            List<ArchetypeDefinition> archetypes,
            ArchetypeClassifier classifier,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            boolean needsClearance,
            int adjacencyReach)
    {
        private static final Loaded EMPTY = of(List.of());

        private static Loaded of(Collection<ArchetypeDefinition> archetypes)
        {
            List<ArchetypeDefinition> frozen = List.copyOf(archetypes);
            return new Loaded(
                    frozen,
                    new ArchetypeClassifier(frozen, SoulHomeConfig.scoringSettings()),
                    ArchetypeSignals.openClusterFilterFor(frozen),
                    ArchetypeSignals.geometryFilterFor(frozen),
                    ArchetypeSignals.needsClearance(frozen),
                    ArchetypeSignals.adjacencyReachFor(frozen, BondRelationRegistry.BUILTIN));
        }
    }
}
