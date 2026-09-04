/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.config;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.structures.core.ActiveAbilitySettings;
import leaf.soulhome.structures.core.AscensionSettings;
import leaf.soulhome.structures.core.BuffSettings;
import leaf.soulhome.structures.core.EssenceSettings;
import leaf.soulhome.structures.core.ScanDebouncer;
import leaf.soulhome.structures.core.ScanSettings;
import leaf.soulhome.structures.core.ScoringSettings;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.utils.LogHelper;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every number the structure-buff feature can be tuned by, in one server-side config file.
 *
 * <p><b>Server, not common.</b> Magnitudes, thresholds and scan limits are all computed on the
 * server; the client copy of a player's buffs is for display only. Putting these in a common
 * config would let a client's file disagree with the server's and produce a interface that
 * confidently reports the wrong numbers.
 *
 * <p>The settings records in {@code structures.core} stay the source of truth for <i>meaning</i> -
 * this class only decides which values go into them. That split is what keeps the scoring and
 * aggregation rules unit-testable without a Forge config in the way.
 *
 * <p>Values are read into an immutable snapshot rather than being queried per block: a scan asks
 * for its settings once and then walks a few hundred thousand positions, and
 * {@code ForgeConfigSpec} lookups are map reads, not field reads.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SoulHomeConfig
{
    public static final ForgeConfigSpec SPEC;
    public static final Server SERVER;

    /**
     * Rebuilt on load and on reload. Volatile because a scan running on a worker may read it while
     * the config thread replaces it - readers get either the old snapshot or the new one, and both
     * are internally consistent.
     */
    private static volatile Snapshot snapshot = Snapshot.DEFAULTS;

    static
    {
        final Pair<Server, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Server::new);

        SERVER = pair.getLeft();
        SPEC = pair.getRight();
    }

    private SoulHomeConfig()
    {
    }

    /** Called from the mod constructor, before anything can ask for a value. */
    public static void register()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /**
     * Whether the whole feature is switched on. Checked at the entrances - scanning and buff
     * lookup - rather than inside every effect.
     */
    public static boolean enabled()
    {
        return snapshot.enabled();
    }

    /**
     * Whether a soul dimension may only be entered and left with a soul key. See
     * {@code SoulTravel}; the knob exists because a pack that wants its own way in - a portal
     * block, a command, another mod's teleport - needs one, and because "my warp scroll does
     * nothing" is a support question best answered by a line in the config.
     */
    public static boolean restrictSoulTravel()
    {
        return snapshot.restrictSoulTravel();
    }

    public static ScanSettings scanSettings()
    {
        return snapshot.scan();
    }

    public static ScoringSettings scoringSettings()
    {
        return snapshot.scoring();
    }

    public static BuffSettings buffSettings()
    {
        return snapshot.buffs();
    }

    /**
     * Whether a soulhome is bounded at all. Off returns every ascent-related behaviour to exactly
     * what it was before #79: no placement is refused, and the scan box falls back to
     * {@code SnapshotBlockVolume.populatedBounds}.
     */
    public static boolean enforceBounds()
    {
        return snapshot.enforceBounds();
    }

    /**
     * The box for the given ascension rank, from the {@code ascent} config section. Rank itself
     * lives in {@code SoulHomeBuffData} (#84); callers read it from there and pass it in here.
     */
    public static SoulBounds soulBounds(int rank)
    {
        return SoulBounds.forRank(
                rank, snapshot.maxRank(), snapshot.floorY(), snapshot.baseCeilingHeight(),
                snapshot.ceilingHeightPerRank(), snapshot.baseVerge(), snapshot.vergePerRank());
    }

    /** Highest ascension rank a soulhome can reach. A pack shortening or lengthening the ladder. */
    public static int maxRank()
    {
        return snapshot.maxRank();
    }

    /** Rank a soulhome with no save file yet starts at - for packs that hand out a larger soul from the start. */
    public static int startingRank()
    {
        return snapshot.startingRank();
    }

    /**
     * Whether the soul-residue tap of Sublime Essence (#82) is switched on. Off stops residue from
     * accruing at all - the overworld crafting ladder and consolidation are untouched by this knob,
     * since they need no per-tick state to keep working.
     */
    public static boolean residueTapEnabled()
    {
        return snapshot.residueTapEnabled();
    }

    /** The soul-residue accrual curve and its conversion rate into Essence I. See {@link EssenceSettings}. */
    public static EssenceSettings essenceSettings()
    {
        return snapshot.essence();
    }

    /** The ascension ritual's four requirements, minus the pillar itself. See {@link AscensionSettings}. */
    public static AscensionSettings ascensionSettings()
    {
        return snapshot.ascension();
    }

    /** The bounds every active ability is subject to (#87). See {@link ActiveAbilitySettings}. */
    public static ActiveAbilitySettings activeAbilitySettings()
    {
        return snapshot.activeAbilities();
    }

    /**
     * Whether one particular ability may be used. A server that is happy with Aegis may not be
     * happy with Soul Step near its spawn protection, so the switch is per ability rather than only
     * the master one - and it is a deny list rather than eight booleans so that an ability a
     * datapack adds can be switched off the same way as a shipped one.
     */
    public static boolean isAbilityEnabled(String abilityType)
    {
        return snapshot.activeAbilities().enabled() && !snapshot.disabledAbilities().contains(abilityType);
    }

    public static long quietPeriodMillis()
    {
        return snapshot.quietPeriodMillis();
    }

    public static long maxScanDelayMillis()
    {
        return snapshot.maxScanDelayMillis();
    }

    public static int checkIntervalTicks()
    {
        return snapshot.checkIntervalTicks();
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event)
    {
        refresh(event.getConfig());
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event)
    {
        refresh(event.getConfig());
    }

    private static void refresh(ModConfig config)
    {
        //every mod's config events come through this bus, and reading ours while another mod's
        //file is being loaded would throw
        if (config.getType() != ModConfig.Type.SERVER || !SoulHome.MODID.equals(config.getModId()))
        {
            return;
        }

        snapshot = Snapshot.read();

        // the classifier is built over the scoring settings, so it has to be rebuilt when they
        // change rather than picking the new values up on the next scan
        ArchetypeManager.onScoringSettingsChanged();
    }

    /**
     * One consistent set of values, read once. Reading each field separately at the point of use
     * would let a reload land halfway through a scan and mix old and new settings.
     */
    private record Snapshot(
            boolean enabled,
            boolean restrictSoulTravel,
            ScanSettings scan,
            ScoringSettings scoring,
            BuffSettings buffs,
            long quietPeriodMillis,
            long maxScanDelayMillis,
            int checkIntervalTicks,
            boolean enforceBounds,
            int floorY,
            int baseCeilingHeight,
            int ceilingHeightPerRank,
            int baseVerge,
            int vergePerRank,
            int maxRank,
            int startingRank,
            boolean residueTapEnabled,
            EssenceSettings essence,
            AscensionSettings ascension,
            ActiveAbilitySettings activeAbilities,
            Set<String> disabledAbilities)
    {
        private static final Snapshot DEFAULTS = new Snapshot(
                true,
                true,
                ScanSettings.DEFAULTS,
                ScoringSettings.DEFAULTS,
                BuffSettings.DEFAULTS,
                ScanDebouncer.DEFAULT_QUIET_PERIOD_MILLIS,
                ScanDebouncer.DEFAULT_MAX_DELAY_MILLIS,
                20,
                true,
                SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT,
                SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE,
                SoulBounds.DEFAULT_VERGE_PER_RANK,
                SoulBounds.MAX_RANK,
                0,
                true,
                EssenceSettings.DEFAULTS,
                AscensionSettings.DEFAULTS,
                ActiveAbilitySettings.DEFAULTS,
                Set.of());

        private static Snapshot read()
        {
            try
            {
                return new Snapshot(
                        SERVER.enabled.get(),
                        SERVER.restrictSoulTravel.get(),
                        new ScanSettings(
                                SERVER.maxRoomVolume.get(),
                                SERVER.clusterRadius.get(),
                                SERVER.minClusterSize.get(),
                                SERVER.maxRegions.get(),
                                SERVER.maxScannedCells.get(),
                                SERVER.maxGeometryCells.get(),
                                SERVER.minRoomVolume.get(),
                                SERVER.shellDepth.get()),
                        new ScoringSettings(
                                SERVER.diversityBonusPerRole.get(),
                                SERVER.densityFloor.get(),
                                SERVER.minDensityFactor.get(),
                                SERVER.ambiguityMargin.get(),
                                SERVER.structuralShareCap.get(),
                                SERVER.structuralRoleThreshold.get()),
                        new BuffSettings(
                                SERVER.repeatedRoomFalloff.get(),
                                SERVER.maxRoomsPerArchetype.get(),
                                SERVER.globalMaxMagnitude.get(),
                                readPairs(SERVER.archetypeMultipliers.get(), "archetype multiplier"),
                                readPairs(SERVER.buffTypeCaps.get(), "buff type cap"),
                                SERVER.entryFraction.get(),
                                SERVER.rampExponent.get(),
                                SERVER.ascensionPerRank.get(),
                                SERVER.ascensionCapPerRank.get()),
                        SERVER.quietPeriodMillis.get(),
                        SERVER.maxScanDelayMillis.get(),
                        SERVER.checkIntervalTicks.get(),
                        SERVER.enforceBounds.get(),
                        SERVER.floorY.get(),
                        SERVER.baseCeilingHeight.get(),
                        SERVER.ceilingHeightPerRank.get(),
                        SERVER.baseVerge.get(),
                        SERVER.vergePerRank.get(),
                        SERVER.maxRank.get(),
                        SERVER.startingRank.get(),
                        SERVER.residueTapEnabled.get(),
                        new EssenceSettings(
                                SERVER.residueRateMultiplier.get(),
                                SERVER.residueToEssenceRate.get()),
                        new AscensionSettings(
                                SERVER.essenceCountPerRank.get(),
                                SERVER.ritualDurationTicks.get(),
                                SERVER.baseWillpowerThreshold.get(),
                                SERVER.willpowerPerRank.get(),
                                SERVER.pillarSearchRadius.get()),
                        new ActiveAbilitySettings(
                                SERVER.abilitiesEnabled.get(),
                                SERVER.abilityCooldownMultiplier.get(),
                                SERVER.abilityMinCooldownTicks.get(),
                                SERVER.abilityMaxCharges.get()),
                        readAbilityIds(SERVER.disabledAbilities.get()));
            }
            catch (RuntimeException e)
            {
                // a hand-edited file can hold a combination the settings records reject; the
                // defaults are a far better outcome than a server that will not start
                LogHelper.error("Could not read the soulhome config, falling back to defaults: " + e);
                return DEFAULTS;
            }
        }

        /**
         * Reads a list of plain ability ids - the deny list behind {@link #isAbilityEnabled}.
         * Blank and commented lines are skipped, and ids are lowercased so a config written with
         * {@code Soulhome:Soul_Step} still switches off {@code soulhome:soul_step} rather than
         * silently matching nothing.
         */
        private static Set<String> readAbilityIds(List<? extends String> entries)
        {
            Set<String> ids = new LinkedHashSet<>();

            for (String entry : entries)
            {
                if (entry == null || entry.isBlank() || entry.startsWith("#"))
                {
                    continue;
                }

                ids.add(entry.trim().toLowerCase(Locale.ROOT));
            }

            return Set.copyOf(ids);
        }

        /**
         * Parses {@code soulhome:library=0.5} lines. A malformed line is skipped with a log rather
         * than failing the whole config - the same "one bad entry does not take the pack down"
         * rule the archetype loader follows.
         *
         * @param what what these pairs are, for the log line a packmaker will have to act on
         */
        private static Map<String, Double> readPairs(List<? extends String> entries, String what)
        {
            Map<String, Double> values = new LinkedHashMap<>();

            for (String entry : entries)
            {
                if (entry == null || entry.isBlank() || entry.startsWith("#"))
                {
                    continue;
                }

                final int separator = entry.indexOf('=');

                if (separator <= 0 || separator == entry.length() - 1)
                {
                    LogHelper.warn("Ignoring soulhome " + what + " '" + entry
                            + "': expected the form 'namespace:name=1.0'");
                    continue;
                }

                final String id = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);

                try
                {
                    final double value = Double.parseDouble(entry.substring(separator + 1).trim());

                    if (value < 0)
                    {
                        LogHelper.warn("Ignoring soulhome " + what + " for " + id
                                + ": a negative value would grant a negative buff");
                        continue;
                    }

                    values.put(id, value);
                }
                catch (NumberFormatException e)
                {
                    LogHelper.warn("Ignoring soulhome " + what + " '" + entry
                            + "': the value after '=' is not a number");
                }
            }

            return values;
        }
    }

    /** The spec itself. Comments here become the comments in the generated toml. */
    public static final class Server
    {
        /** Held out as fields so each list's element type is unambiguous at the define site. */
        private static final List<String> NO_MULTIPLIERS = List.of();

        /**
         * The ceilings a fresh config file is written with, kept in step with
         * {@link BuffSettings#DEFAULT_TYPE_CAPS}. Since a config file is only written once, a type
         * missing from an existing file is caught by that map at read time rather than falling
         * through to {@code global_max_magnitude} - see {@code BuffSettings#capFor}. This list is
         * still worth keeping complete: it is what a server owner opens the file and sees.
         */
        private static final List<String> DEFAULT_TYPE_CAPS = List.of(
                "soulhome:enchantment_power=6.0",
                "soulhome:double_jump=1.0",
                "soulhome:fire_aspect=6.0",
                "soulhome:max_mana=60.0",
                "soulhome:reach=2.0",
                "soulhome:surveyors_eye=3.0",
                "soulhome:aegis=12.0",
                "soulhome:soul_step=6.0",
                "soulhome:rally=6.0",
                "soulhome:call_of_the_herd=3.0",
                "soulhome:thunderclap=3.0",
                "soulhome:barrage=6.0",
                "soulhome:rupture=6.0");

        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue restrictSoulTravel;

        public final ForgeConfigSpec.DoubleValue repeatedRoomFalloff;
        public final ForgeConfigSpec.IntValue maxRoomsPerArchetype;
        public final ForgeConfigSpec.DoubleValue globalMaxMagnitude;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> archetypeMultipliers;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> buffTypeCaps;
        public final ForgeConfigSpec.DoubleValue entryFraction;
        public final ForgeConfigSpec.DoubleValue rampExponent;
        public final ForgeConfigSpec.DoubleValue ascensionPerRank;
        public final ForgeConfigSpec.DoubleValue ascensionCapPerRank;

        public final ForgeConfigSpec.DoubleValue diversityBonusPerRole;
        public final ForgeConfigSpec.DoubleValue densityFloor;
        public final ForgeConfigSpec.DoubleValue minDensityFactor;
        public final ForgeConfigSpec.DoubleValue ambiguityMargin;
        public final ForgeConfigSpec.DoubleValue structuralShareCap;
        public final ForgeConfigSpec.DoubleValue structuralRoleThreshold;

        public final ForgeConfigSpec.IntValue minRoomVolume;
        public final ForgeConfigSpec.IntValue maxRoomVolume;
        public final ForgeConfigSpec.IntValue shellDepth;
        public final ForgeConfigSpec.IntValue clusterRadius;
        public final ForgeConfigSpec.IntValue minClusterSize;
        public final ForgeConfigSpec.IntValue maxRegions;
        public final ForgeConfigSpec.LongValue maxScannedCells;
        public final ForgeConfigSpec.IntValue maxGeometryCells;

        public final ForgeConfigSpec.LongValue quietPeriodMillis;
        public final ForgeConfigSpec.LongValue maxScanDelayMillis;
        public final ForgeConfigSpec.IntValue checkIntervalTicks;

        public final ForgeConfigSpec.BooleanValue enforceBounds;
        public final ForgeConfigSpec.IntValue floorY;
        public final ForgeConfigSpec.IntValue baseCeilingHeight;
        public final ForgeConfigSpec.IntValue ceilingHeightPerRank;
        public final ForgeConfigSpec.IntValue baseVerge;
        public final ForgeConfigSpec.IntValue vergePerRank;
        public final ForgeConfigSpec.IntValue maxRank;
        public final ForgeConfigSpec.IntValue startingRank;

        public final ForgeConfigSpec.BooleanValue residueTapEnabled;
        public final ForgeConfigSpec.DoubleValue residueRateMultiplier;
        public final ForgeConfigSpec.DoubleValue residueToEssenceRate;

        public final ForgeConfigSpec.IntValue essenceCountPerRank;
        public final ForgeConfigSpec.IntValue ritualDurationTicks;
        public final ForgeConfigSpec.DoubleValue baseWillpowerThreshold;
        public final ForgeConfigSpec.DoubleValue willpowerPerRank;
        public final ForgeConfigSpec.IntValue pillarSearchRadius;

        public final ForgeConfigSpec.BooleanValue abilitiesEnabled;
        public final ForgeConfigSpec.DoubleValue abilityCooldownMultiplier;
        public final ForgeConfigSpec.IntValue abilityMinCooldownTicks;
        public final ForgeConfigSpec.IntValue abilityMaxCharges;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> disabledAbilities;

        private Server(ForgeConfigSpec.Builder builder)
        {
            builder.comment("Getting into and out of a soul dimension.").push("dimension");

            this.restrictSoulTravel = builder
                    .comment(
                            "Whether a soul dimension can only be entered and left with a soul key.",
                            "With this on, every other way across the boundary is refused: a Waystones warp plate",
                            "built inside a soulhome, a return scroll used in one, a portal, another mod's teleport.",
                            "They all skip the exit position saved on the way in and the rescan on the way out that",
                            "decides what the owner's rooms are currently worth, and a warp plate in a shared soul",
                            "is a public door into somebody's private dimension.",
                            "Turn it off if your pack wants its own way in - and expect players to arrive in a",
                            "soulhome with no saved way back to where they came from.")
                    .define("restrict_travel", true);

            builder.pop();

            builder.comment("Structures built inside a soulhome, and the buffs they grant.").push("structure_buffs");

            this.enabled = builder
                    .comment(
                            "Whether soulhome structures grant buffs at all.",
                            "Turning this off stops all scanning and zeroes every player's buffs; nothing is deleted,",
                            "so turning it back on restores what everyone had built.")
                    .define("enabled", true);

            builder.comment("How classified rooms turn into magnitudes.").push("buffs");

            this.repeatedRoomFalloff = builder
                    .comment(
                            "What each additional room of the same archetype is worth relative to the one before it.",
                            "0.5 means a second library counts half and a third a quarter, so one good library beats",
                            "a corridor of identical cupboards. 1.0 disables the falloff entirely.")
                    .defineInRange("repeated_room_falloff", 0.5d, 0d, 1d);

            this.maxRoomsPerArchetype = builder
                    .comment("Hard cap on how many rooms of one archetype contribute at all.")
                    .defineInRange("max_rooms_per_archetype", 3, 1, 64);

            this.globalMaxMagnitude = builder
                    .comment(
                            "Default ceiling on a buff type, however it was accumulated.",
                            "Magnitudes are unitless: for soulhome:xp_gain 1.0 is +100% experience, for",
                            "soulhome:enchantment_power it is one extra effective level at the table.",
                            "Types that are not proportions - jumps, seconds, levels - need their own ceiling below.")
                    .defineInRange("global_max_magnitude", 1.0d, 0d, 100d);

            this.buffTypeCaps = builder
                    .comment(
                            "Per-buff-type ceilings, as 'namespace:buff=magnitude', overriding global_max_magnitude.",
                            "One number cannot cap every buff, because magnitudes mean different things:",
                            "1.0 doubles experience gain, and would be a rounding error at an enchanting table.",
                            "An active ability's magnitude is a count - bolts, blocks of blink, absorption",
                            "points - so each of them needs its own ceiling here as much as enchanting power does.",
                            "Anything listed neither here nor in the mod's own defaults uses global_max_magnitude.")
                    .defineListAllowEmpty(
                            List.of("buff_type_caps"),
                            () -> DEFAULT_TYPE_CAPS,
                            entry -> entry instanceof String);

            this.archetypeMultipliers = builder
                    .comment(
                            "Per-archetype magnitude multipliers, as 'namespace:archetype=multiplier'.",
                            "Applied to everything that archetype grants, after its own cap and before the global one.",
                            "Archetypes not listed here are left at 1.0, and an unknown id is simply never used,",
                            "so a pack can be tuned down without editing its archetype files.",
                            "Example: [\"soulhome:library=0.5\", \"soulhome:farm=1.25\"]")
                    .defineListAllowEmpty(
                            List.of("archetype_multipliers"),
                            () -> NO_MULTIPLIERS,
                            entry -> entry instanceof String);

            this.entryFraction = builder
                    .comment(
                            "Fraction of a buff's ceiling granted right at an archetype's own tier-1 threshold -",
                            "what 'just barely qualifying' is worth. A room scoring below that threshold grants",
                            "nothing at all.")
                    .defineInRange("entry_fraction", BuffSettings.DEFAULT_ENTRY_FRACTION, 0d, 1d);

            this.rampExponent = builder
                    .comment(
                            "Shapes how the rest of a buff's ceiling is spread between the entry threshold and the",
                            "top of an archetype's tier ladder. 1.0 is linear; above 1 the payout back-loads towards",
                            "the top of the range, which stops a bigger pile of one kind of block being worth much",
                            "more than a smaller pile of the same thing. Below 1 front-loads instead - legal, but",
                            "works against that intent.")
                    .defineInRange("ramp_exponent", BuffSettings.DEFAULT_RAMP_EXPONENT, 0.01d, 10d);

            this.ascensionPerRank = builder
                    .comment(
                            "How much stronger a rank (#84) makes every amplification-friendly buff: rankFactor =",
                            "1 + ascension_per_rank * rank. Applied after an archetype's own max and its multiplier,",
                            "before the global type cap. Zero reproduces an unascended soul's numbers exactly at",
                            "every rank. Speed, mining speed, reach and swim speed are never amplified by this -",
                            "they stop being a benefit long before they stop growing.")
                    .defineInRange("ascension_per_rank", BuffSettings.DEFAULT_ASCENSION_PER_RANK, 0d, 100d);

            this.ascensionCapPerRank = builder
                    .comment(
                            "How much rank raises the global type ceiling itself, separately from ascension_per_rank:",
                            "effectiveCap = declaredCap * (1 + ascension_cap_per_rank * rank). Without this, a soul",
                            "that already sits at today's cap - which is exactly what climbing far enough to reach a",
                            "high rank tends to produce - would see rank amplify nothing for it.")
                    .defineInRange("ascension_cap_per_rank", BuffSettings.DEFAULT_ASCENSION_CAP_PER_RANK, 0d, 100d);

            builder.pop();

            builder.comment("How a room is scored against an archetype.").push("scoring");

            this.diversityBonusPerRole = builder
                    .comment(
                            "Score multiplier added for each distinct signal role beyond the first.",
                            "This is the lever that rewards a room with books, seating, lighting and a lectern",
                            "over a room with nothing but books. Set to 0 to score on volume alone.")
                    .defineInRange("diversity_bonus_per_role", 0.15d, 0d, 10d);

            this.densityFloor = builder
                    .comment("Signal blocks per cell of volume below which a region is penalised for being mostly empty.")
                    .defineInRange("density_floor", 0.02d, 0d, 1d);

            this.minDensityFactor = builder
                    .comment("Floor on that penalty, so a sparse room is weakened rather than erased.")
                    .defineInRange("min_density_factor", 0.25d, 0d, 1d);

            this.ambiguityMargin = builder
                    .comment(
                            "How far ahead the winning archetype must be before the room is assigned to it.",
                            "1.15 means '15% clear of the runner-up'; anything closer is reported as ambiguous",
                            "rather than being assigned by a coin toss.")
                    .defineInRange("ambiguity_margin", 1.15d, 1d, 10d);

            this.structuralShareCap = builder
                    .comment(
                            "Structural credit (from how a room's blocks are arranged, not just what it holds) is",
                            "capped at this fraction of the room's signal total, so a perfect arrangement of nothing",
                            "is worth nothing. 1.0 means arrangement can at most double what the room's contents",
                            "alone earned. Raised from 0.5 (#54): the old cap was a large, unexplained part of why",
                            "a well-arranged room still struggled to reach tier 2. Lower this, rather than raising",
                            "thresholds, if arrangement makes the top of an archetype's range too easy to reach.")
                    .defineInRange("structural_share_cap", 1.0d, 0d, 10d);

            this.structuralRoleThreshold = builder
                    .comment(
                            "Confidence a structural form must reach before its role counts toward the diversity",
                            "bonus above. Below this, an accidental sliver of a match does not buy a full",
                            "diversity bonus for free.")
                    .defineInRange("structural_role_threshold", 0.25d, 0d, 1d);

            builder.pop();

            builder.comment("Limits on what a scan will look at.").push("scanning");

            this.minRoomVolume = builder
                    .comment(
                            "Interior cells below which a sealed pocket is a crevice rather than a room.",
                            "Voids inside a thick wall, gaps behind a stair and hollow pillars are sealed spaces too,",
                            "and offering each of them as a region fills the lens with boxes drawn around nothing.")
                    .defineInRange("min_room_volume", ScanSettings.DEFAULT_MIN_ROOM_VOLUME, 1, 4096);

            this.maxRoomVolume = builder
                    .comment("Interior cells above which an enclosed pocket is treated as outdoors rather than a room.")
                    .defineInRange("max_room_volume", 4096, 8, 1_000_000);

            this.shellDepth = builder
                    .comment(
                            "How many layers of solid blocks packed against a room's walls still belong to that",
                            "building rather than being loose blocks an open-air cluster could form around.",
                            "One covers a roof laid onto a ceiling and the outer half of a double-thick wall.",
                            "0 disables it, which lets a barn's own roof come back as a second region on top of it.")
                    .defineInRange("shell_depth", ScanSettings.DEFAULT_SHELL_DEPTH, 0, 8);

            this.clusterRadius = builder
                    .comment(
                            "How far an open-air cluster reaches through clear space to pick up the next signal block.",
                            "Counted in steps through cells the cluster can cross rather than measured straight",
                            "through solid matter, so a wall between two builds is a boundary. Only blocks filling",
                            "their whole cell count as one: fences, walls, panes, slabs and stairs are things a",
                            "player puts inside a build, not the edge of it.",
                            "Raise it to join fields split by wide paths; lower it to tell closer builds apart.")
                    .defineInRange("cluster_radius", 3, 1, 32);

            this.minClusterSize = builder
                    .comment("Signal blocks an open-air cluster needs before it counts, so one planted flower is not a farm.")
                    .defineInRange("min_cluster_size", 4, 1, 4096);

            this.maxRegions = builder
                    .comment("Hard cap on regions considered per soulhome, which bounds the cost of classification.")
                    .defineInRange("max_regions", 64, 1, 1024);

            this.maxScannedCells = builder
                    .comment("Refuse to scan a soulhome whose populated area is larger than this many block positions.")
                    .defineInRange("max_scanned_cells", 4_000_000L, 4096L, 512_000_000L);

            this.maxGeometryCells = builder
                    .comment(
                            "Per-region cap on how many structurally-interesting block positions are indexed for",
                            "positional scoring. Past this the index is truncated and reported as such, rather",
                            "than silently scoring an arrangement badly for no reason a player can see.")
                    .defineInRange("max_geometry_cells", 8192, 64, 1_000_000);

            builder.pop();

            builder.comment("When a scan happens. Classification is a chunk sweep, so it never runs on a tick loop.").push("scheduling");

            this.quietPeriodMillis = builder
                    .comment("How long a soulhome must go unedited before it is rescanned, in milliseconds.")
                    .defineInRange("quiet_period_millis", 5_000L, 0L, 600_000L);

            this.maxScanDelayMillis = builder
                    .comment("Longest a continuously-edited soulhome will go without a scan, in milliseconds.")
                    .defineInRange("max_scan_delay_millis", 30_000L, 0L, 3_600_000L);

            this.checkIntervalTicks = builder
                    .comment("How often the pending set is checked, in ticks. Far finer than the debounce; rarely worth changing.")
                    .defineInRange("check_interval_ticks", 20, 1, 1200);

            builder.pop();

            builder.comment(
                            "The box a soulhome may build inside of: a floor, a ceiling and four walls.",
                            "A ceiling alone is not a limit in a void dimension - a player denied a second storey",
                            "simply builds one downward instead - so the floor matters exactly as much as the",
                            "ceiling. See #78/#79.")
                    .push("ascent");

            this.enforceBounds = builder
                    .comment(
                            "Whether a soulhome is bounded at all.",
                            "Off returns everything to how it worked before this box existed: no placement is",
                            "ever refused, and the scan box goes back to being inferred from populated chunks",
                            "rather than declared.")
                    .define("enforce_bounds", true);

            this.floorY = builder
                    .comment(
                            "Absolute Y below which nothing may be placed. Constant across every rank - only the",
                            "ceiling and the verge grow. Matches DimensionHelper.FLOOR_LEVEL by design: that is",
                            "where every soulhome's entry point and starting island surface sit, and a floor set",
                            "any higher would place a player's own arrival point outside their soulhome's box.")
                    .defineInRange("floor_y", SoulBounds.DEFAULT_FLOOR_Y, 0, 2032);

            this.baseCeilingHeight = builder
                    .comment("Build layers at rank 0. Six is deliberately mean: a floor, four of air, a ceiling.")
                    .defineInRange("base_ceiling_height", SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, 1, 4064);

            this.ceilingHeightPerRank = builder
                    .comment("Further build layers granted per ascension rank.")
                    .defineInRange("ceiling_height_per_rank", SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK, 1, 4064);

            this.baseVerge = builder
                    .comment(
                            "How far the buildable box reaches from the soulhome's origin on each horizontal axis,",
                            "at rank 0. Keep rank V's verge (base + 5 * per-rank) inside the scanner's own search",
                            "square, or builds near its edge start being clipped from scans silently.")
                    .defineInRange("base_verge", SoulBounds.DEFAULT_BASE_VERGE, 1, 128);

            this.vergePerRank = builder
                    .comment("Further verge granted per ascension rank.")
                    .defineInRange("verge_per_rank", SoulBounds.DEFAULT_VERGE_PER_RANK, 1, 128);

            this.maxRank = builder
                    .comment(
                            "Highest ascension rank a soulhome can reach. The shipped ladder runs 0 (unascended) to",
                            "5 (V); shortening or lengthening it changes how far base_ceiling_height/base_verge and",
                            "their per-rank steps above are ever multiplied out to - there is no separate table to",
                            "edit alongside it.")
                    .defineInRange("max_rank", SoulBounds.MAX_RANK, 0, 20);

            this.startingRank = builder
                    .comment(
                            "Rank a soulhome starts at when it is first created - not when this config is loaded, so",
                            "an existing soulhome's own rank is never touched by changing this. For a pack that wants",
                            "to hand out a larger soul from the start rather than making every player climb from 0.")
                    .defineInRange("starting_rank", 0, 0, 20);

            builder.comment(
                            "Sublime Essence (#82): the currency the ascension ritual spends. The soul-residue tap",
                            "lives here; the overworld crafting ladder and the nine-into-one consolidation need no",
                            "config of their own, since a datapack can already remove or recolour a recipe the",
                            "ordinary way.")
                    .push("essence");

            this.residueTapEnabled = builder
                    .comment(
                            "Whether a soulhome earns soul residue from its own built quality at all. Off makes",
                            "Sublime Essence purely craftable and consolidatable, for a pack that wants ascension",
                            "paid for rather than grown.")
                    .define("residue_tap_enabled", true);

            this.residueRateMultiplier = builder
                    .comment(
                            "Scales how fast a soulhome earns soul residue. The underlying curve is the square",
                            "root of the soulhome's total awarded room score, so a soulhome scoring four times as",
                            "much earns twice the residue, not four times - this only scales the whole curve up",
                            "or down.")
                    .defineInRange("residue_rate_multiplier", EssenceSettings.DEFAULT_RESIDUE_RATE_MULTIPLIER, 0d, 1000d);

            this.residueToEssenceRate = builder
                    .comment("How much soul residue converts into one Essence I at the Soul Anchor (#83).")
                    .defineInRange(
                            "residue_to_essence_rate", EssenceSettings.DEFAULT_RESIDUE_TO_ESSENCE_RATE, 0.001d, 1_000_000d);

            builder.pop();

            builder.comment(
                            "The ascension ritual (#83): a pillar, willpower, essence, and holding the cap. The box",
                            "in the ascent section above only grows once this actually raises the rank.")
                    .push("ritual");

            this.essenceCountPerRank = builder
                    .comment("How many of the target rank's Sublime Essence the ritual consumes on success.")
                    .defineInRange(
                            "essence_count_per_rank", AscensionSettings.DEFAULT_ESSENCE_COUNT_PER_RANK, 1, 64);

            this.ritualDurationTicks = builder
                    .comment("How long a player must hold the pillar's cap for one ascension, in ticks.")
                    .defineInRange(
                            "ritual_duration_ticks", AscensionSettings.DEFAULT_RITUAL_DURATION_TICKS, 1, 24_000);

            this.baseWillpowerThreshold = builder
                    .comment(
                            "Total awarded room score a soulhome needs to ascend to rank I. A tall empty pillar is",
                            "not an ascension - this is what makes the soul's own substance the thing pushing back",
                            "against the sky.")
                    .defineInRange(
                            "base_willpower_threshold", AscensionSettings.DEFAULT_BASE_WILLPOWER_THRESHOLD, 0d, 1_000_000d);

            this.willpowerPerRank = builder
                    .comment("Further total awarded room score required for every rank past the first.")
                    .defineInRange(
                            "willpower_per_rank", AscensionSettings.DEFAULT_WILLPOWER_PER_RANK, 0d, 1_000_000d);

            this.pillarSearchRadius = builder
                    .comment(
                            "How many blocks from the Soul Anchor the pillar's 3x3 base may sit - \"a few blocks\",",
                            "per #83. Also bounds how far the pillar is allowed to widen above its base before the",
                            "ritual simply stops looking, so raising this scales the cost of every check.")
                    .defineInRange(
                            "pillar_search_radius", AscensionSettings.DEFAULT_PILLAR_SEARCH_RADIUS, 2, 16);

            builder.pop();

            builder.comment(
                            "Active abilities (#87): the buffs a player presses a key for rather than simply",
                            "carries. Everything here bounds all of them at once; how far Soul Step blinks or how",
                            "wide Rupture opens is the ability's own business, scaled by the room's tier and by rank.")
                    .push("abilities");

            this.abilitiesEnabled = builder
                    .comment(
                            "The master switch. Off leaves the rooms that grant abilities classifying and scoring",
                            "exactly as they do now - they simply grant nothing usable, the HUD never appears, and",
                            "the two keys do nothing. For a server that wants the building and not the combat.")
                    .define("enabled", ActiveAbilitySettings.DEFAULTS.enabled());

            this.abilityCooldownMultiplier = builder
                    .comment(
                            "Scales every ability's recharge. Above 1 makes abilities rarer, below 1 more frequent.",
                            "The floor below is applied afterwards, so this cannot drive a cooldown to nothing.")
                    .defineInRange(
                            "cooldown_multiplier", ActiveAbilitySettings.DEFAULT_COOLDOWN_MULTIPLIER, 0.01d, 100d);

            this.abilityMinCooldownTicks = builder
                    .comment(
                            "The shortest any recharge may become, in ticks, after magnitude, rank and the",
                            "multiplier above have all had their say. This is what stops a high enough rank turning",
                            "an ability into a key that can be held down.")
                    .defineInRange(
                            "min_cooldown_ticks", ActiveAbilitySettings.DEFAULT_MIN_COOLDOWN_TICKS, 1, 24_000);

            this.abilityMaxCharges = builder
                    .comment("The most charges any one ability may bank, whatever its tier and the player's rank.")
                    .defineInRange("max_charges", ActiveAbilitySettings.DEFAULT_MAX_CHARGES, 1, 64);

            this.disabledAbilities = builder
                    .comment(
                            "Abilities switched off by id, one per line, e.g. 'soulhome:soul_step'. A server that is",
                            "happy with Aegis may not be happy with a blink near its spawn protection, and there is",
                            "no claim-mod API on 1.20.1 to consult about it - so this is the honest answer rather",
                            "than a pretence of integration. The room still classifies and still scores; it simply",
                            "grants nothing usable. Ids a datapack added work here too.",
                            "The shipped ids are: soulhome:surveyors_eye, soulhome:aegis, soulhome:soul_step,",
                            "soulhome:rally, soulhome:call_of_the_herd, soulhome:thunderclap, soulhome:barrage,",
                            "soulhome:rupture")
                    .defineList("disabled", List.of(), entry -> entry instanceof String);

            builder.pop();
            builder.pop();
            builder.pop();
        }
    }
}
