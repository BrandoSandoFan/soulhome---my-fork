/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.structures.core.ArchetypeClassifier;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.ArchetypeSignals;
import leaf.soulhome.structures.core.BlockSignature;
import leaf.soulhome.structures.core.ScoringSettings;
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

    /** Which blocks {@code RegionScanner} should cluster open-air regions around. */
    public static Predicate<BlockSignature> signalFilter()
    {
        return loaded.signalFilter();
    }

    /**
     * Replace the loaded set. Used by the reload listener on the server, and by the sync packet on
     * the client.
     */
    public static void replaceAll(Collection<ArchetypeDefinition> archetypes)
    {
        loaded = Loaded.of(archetypes);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler)
    {
        Map<String, ArchetypeDefinition> accepted = new LinkedHashMap<>();
        int rejected = 0;

        for (Map.Entry<String, JsonElement> entry : sortedByKey(files).entrySet())
        {
            final String id = entry.getKey();

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
            accepted.put(id, definition);
        }

        replaceAll(accepted.values());

        LogHelper.info("Loaded " + accepted.size() + " soulhome archetype(s)"
                + (rejected > 0 ? ", skipped " + rejected + " that failed to load" : "")
                + ": " + accepted.keySet());
    }

    /**
     * A warning rather than a rejection: an archetype may legitimately grant a buff type added by
     * another mod that is not installed. But a typo in a buff id would otherwise produce a room
     * that classifies perfectly and then does nothing at all, which is a miserable thing to debug.
     */
    private static void warnAboutUnknownBuffs(ArchetypeDefinition definition)
    {
        for (ArchetypeDefinition.BuffSpec buff : definition.buffs())
        {
            if (!SoulBuffEffects.isKnown(buff.type()))
            {
                LogHelper.warn("Soulhome archetype " + definition.id() + " grants '" + buff.type()
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
     */
    private record Loaded(
            List<ArchetypeDefinition> archetypes,
            ArchetypeClassifier classifier,
            Predicate<BlockSignature> signalFilter)
    {
        private static final Loaded EMPTY = of(List.of());

        private static Loaded of(Collection<ArchetypeDefinition> archetypes)
        {
            List<ArchetypeDefinition> frozen = List.copyOf(archetypes);
            return new Loaded(
                    frozen,
                    new ArchetypeClassifier(frozen, ScoringSettings.DEFAULTS),
                    ArchetypeSignals.filterFor(frozen));
        }
    }
}
