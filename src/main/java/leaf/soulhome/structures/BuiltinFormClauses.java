/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.AboveClauseType;
import leaf.soulhome.structures.core.AcrossClauseType;
import leaf.soulhome.structures.core.AlongClauseType;
import leaf.soulhome.structures.core.AtRangeClauseType;
import leaf.soulhome.structures.core.BeneathClauseType;
import leaf.soulhome.structures.core.BesideClauseType;
import leaf.soulhome.structures.core.ClusterClauseType;
import leaf.soulhome.structures.core.EnclosureClauseType;
import leaf.soulhome.structures.core.FormClauseRegistry;
import leaf.soulhome.structures.core.FormClauseType;
import leaf.soulhome.structures.core.InsideClauseType;
import leaf.soulhome.structures.core.LaneClauseType;
import leaf.soulhome.structures.core.LineClauseType;
import leaf.soulhome.structures.core.LoopClauseType;
import leaf.soulhome.structures.core.PlatformClauseType;
import leaf.soulhome.structures.core.SurroundsClauseType;
import leaf.soulhome.structures.core.WithinClauseType;

/**
 * Registers every {@code shape}/{@code relation} clause type the mod ships onto
 * {@link FormClauseRegistry#BUILTIN} - the vocabulary Phase 2 of the structural considerations epic
 * (#25) adds: distance and direction (#29), surrounding and containment (#30), closed circuits
 * (#31), and platforms/enclosures/lines/clusters (#32).
 *
 * <p>Every shipped archetype's JSON now references some of these ids (#34), and this is what makes
 * that vocabulary resolvable rather than every {@code shape}/{@code relation} clause in the game
 * falling back to {@link leaf.soulhome.structures.core.UnknownClause}. A datapack can register its
 * own clause types the same way, onto its own {@link FormClauseRegistry}.
 */
public final class BuiltinFormClauses
{
    private BuiltinFormClauses()
    {
    }

    public static void registerAll()
    {
        registerAll(FormClauseRegistry.BUILTIN);
    }

    /**
     * Registers onto an arbitrary registry, so a test can exercise this against its own throwaway
     * one. Safe to call more than once against the same registry - {@link FormClauseRegistry#register}
     * throws on a repeat id by design, and this is called from more than one independent place that
     * shares {@link FormClauseRegistry#BUILTIN}: {@code SoulHome}'s own bootstrap, and - since the
     * Minecraft-free unit test suite never runs that bootstrap - a test fixture that needs the real
     * vocabulary present. Whichever runs first wins; the rest are a no-op.
     */
    public static void registerAll(FormClauseRegistry registry)
    {
        if (registry.findById("soulhome:loop").isPresent())
        {
            return;
        }

        // #29 - distance, direction, and clear space between
        register(registry, new WithinClauseType());
        register(registry, new AtRangeClauseType());
        register(registry, new AboveClauseType());
        register(registry, new BeneathClauseType());
        register(registry, new BesideClauseType());
        register(registry, new AcrossClauseType());
        register(registry, new AlongClauseType());

        // #30 - surrounding and containment
        register(registry, new SurroundsClauseType());
        register(registry, new InsideClauseType());

        // #31 - the closed circuit
        register(registry, new LoopClauseType());

        // #32 - platforms, enclosures, lines, and clusters
        register(registry, new PlatformClauseType());
        register(registry, new EnclosureClauseType());
        register(registry, new LineClauseType());
        register(registry, new ClusterClauseType());

        // #73 - two groups of the same element with a gap between them, e.g. a fenced running lane
        register(registry, new LaneClauseType());
    }

    private static void register(FormClauseRegistry registry, FormClauseType type)
    {
        registry.register(type);
    }
}
