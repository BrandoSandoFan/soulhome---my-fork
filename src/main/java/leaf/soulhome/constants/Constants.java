/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.constants;

public class Constants
{
    public static class StringKeys
    {
        public static final String KEYS_CATEGORY = "keys.soulhome.main";
        public static final String KEY_SOUL_CHARGE = "key.soulhome.soul.charge";

        public static final String PATCHOULI_NOT_INSTALLED = "tooltip.soulhome.patchouli.not_installed";

        public static final String SOULHOME_ITEM_TOOLTIP = "tooltip.item.soulhome.%s";

        public static final String SHIFT_ITEM_TOOLTIP = "tooltip.item.info.shift";
        public static final String SHIFT_CONTROL_ITEM_TOOLTIP = "tooltip.item.info.shift_control";
        public static final String CONTROL_ITEM_TOOLTIP = "tooltip.item.info.control";

        //Structure analysis. A fuzzy classifier that cannot explain itself reads as a broken one,
        //so these are the strings that make the whole feature usable rather than decoration.
        public static final String ANALYSE_HEADER = "message.soulhome.analyse.header";
        public static final String ANALYSE_NOTHING_FOUND = "message.soulhome.analyse.nothing_found";
        public static final String ANALYSE_NO_ARCHETYPES = "message.soulhome.analyse.no_archetypes";
        public static final String ANALYSE_SCANNING = "message.soulhome.analyse.scanning";
        public static final String ANALYSE_DISABLED = "message.soulhome.analyse.disabled";
        public static final String ANALYSE_NO_SOULHOME = "message.soulhome.analyse.no_soulhome";
        public static final String ANALYSE_NOT_HERE = "message.soulhome.analyse.not_here";
        public static final String ANALYSE_NO_REGION_HERE = "message.soulhome.analyse.no_region_here";

        public static final String REGION_CLASSIFIED = "message.soulhome.region.classified";
        public static final String REGION_AMBIGUOUS = "message.soulhome.region.ambiguous";
        public static final String REGION_UNCLASSIFIED = "message.soulhome.region.unclassified";
        public static final String REGION_SHAPE = "message.soulhome.region.shape";
        public static final String REGION_NEXT_TIER = "message.soulhome.region.next_tier";
        public static final String REGION_AMBIGUOUS_DETAIL = "message.soulhome.region.ambiguous_detail";
        public static final String REGION_AMBIGUOUS_ADVICE = "message.soulhome.region.ambiguous_advice";
        public static final String REGION_CLOSEST = "message.soulhome.region.closest";
        public static final String REGION_REQUIREMENT_FAILED = "message.soulhome.region.requirement_failed";
        public static final String REGION_SIGNAL = "message.soulhome.region.signal";
        public static final String REGION_SIGNAL_CAPPED = "message.soulhome.region.signal_capped";
        public static final String REGION_MISSING = "message.soulhome.region.missing";

        // Structural considerations (#25): the arrangement half of the report. A clause's own
        // description/diagnostic carries the substance, so a new clause type never needs a new key
        // here - only the surrounding notices (capped, truncated, skipped) are fixed strings.
        public static final String REGION_STRUCTURE_HEADER = "message.soulhome.region.structure_header";
        public static final String REGION_STRUCTURE = "message.soulhome.region.structure";
        public static final String REGION_STRUCTURE_ZERO = "message.soulhome.region.structure_zero";
        public static final String REGION_STRUCTURE_CLAUSE_HIT = "message.soulhome.region.structure_clause_hit";
        public static final String REGION_STRUCTURE_CLAUSE_MISS = "message.soulhome.region.structure_clause_miss";
        public static final String REGION_STRUCTURE_CAPPED = "message.soulhome.region.structure_capped";
        public static final String REGION_STRUCTURE_TRUNCATED = "message.soulhome.region.structure_truncated";
        public static final String REGION_STRUCTURE_SKIPPED = "message.soulhome.region.structure_skipped";

        public static final String BUFFS_HEADER = "message.soulhome.buffs.header";
        public static final String BUFFS_NONE = "message.soulhome.buffs.none";
        public static final String BUFFS_ENTRY = "message.soulhome.buffs.entry";
        public static final String BUFFS_SOURCE = "message.soulhome.buffs.source";
        public static final String BUFFS_CAPPED = "message.soulhome.buffs.capped";

        // Refused travel across a soul dimension's boundary (see SoulTravel). A player who has
        // just watched a warp scroll do nothing needs to be told why, or it reads as a broken mod.
        public static final String TRAVEL_BLOCKED = "message.soulhome.travel.blocked";

        // The Ascent (#78/#79): a soulhome is a box, and building stops at its walls.
        public static final String ASCENT_DENIED = "message.soulhome.ascent.denied";
        public static final String ASCENT_HEADER = "message.soulhome.ascent.header";
        public static final String ASCENT_RANK = "message.soulhome.ascent.rank";
        public static final String ASCENT_BOX = "message.soulhome.ascent.box";
        public static final String ASCENT_BUILD_LAYERS = "message.soulhome.ascent.build_layers";
        public static final String ASCENT_LEGACY = "message.soulhome.ascent.legacy";
        public static final String ASCENT_NOT_YET = "message.soulhome.ascent.not_yet";
        public static final String ASCENT_MAXED = "message.soulhome.ascent.maxed";
        public static final String ASCENT_DISABLED = "message.soulhome.ascent.disabled";
        public static final String ASCENT_NO_SOULHOME = "message.soulhome.ascent.no_soulhome";

        // /soulhome ascent set - the operator-only way to jump straight to a rank (#84)
        public static final String ASCENT_SET_SUCCESS = "message.soulhome.ascent.set_success";
        public static final String ASCENT_SET_OUT_OF_RANGE = "message.soulhome.ascent.set_out_of_range";

        // The Soul Anchor and the ascension ritual (#83): the climb itself, rather than merely the
        // box it climbs against.
        public static final String ANCHOR_NOT_HERE = "message.soulhome.anchor.not_here";
        public static final String ANCHOR_ALREADY_EXISTS = "message.soulhome.anchor.already_exists";
        public static final String ANCHOR_HEADER = "message.soulhome.anchor.header";
        public static final String ANCHOR_RANK = "message.soulhome.anchor.rank";
        public static final String ANCHOR_MAXED = "message.soulhome.anchor.maxed";
        public static final String ANCHOR_READY = "message.soulhome.anchor.ready";
        public static final String ANCHOR_RESIDUE_CONVERTED = "message.soulhome.anchor.residue_converted";

        public static final String ANCHOR_PILLAR_OK = "message.soulhome.anchor.pillar_ok";
        public static final String ANCHOR_PILLAR_NO_BASE = "message.soulhome.anchor.pillar_no_base";
        public static final String ANCHOR_PILLAR_GAP = "message.soulhome.anchor.pillar_gap";
        public static final String ANCHOR_WILLPOWER_OK = "message.soulhome.anchor.willpower_ok";
        public static final String ANCHOR_WILLPOWER_MISSING = "message.soulhome.anchor.willpower_missing";
        public static final String ANCHOR_ESSENCE_OK = "message.soulhome.anchor.essence_ok";
        public static final String ANCHOR_ESSENCE_MISSING = "message.soulhome.anchor.essence_missing";

        public static final String ANCHOR_RITUAL_IN_PROGRESS = "message.soulhome.anchor.ritual_in_progress";
        public static final String ANCHOR_RITUAL_STARTED = "message.soulhome.anchor.ritual_started";
        public static final String ANCHOR_RITUAL_ABORTED_MOVED = "message.soulhome.anchor.ritual_aborted_moved";
        public static final String ANCHOR_RITUAL_ABORTED_PILLAR = "message.soulhome.anchor.ritual_aborted_pillar";
        public static final String ANCHOR_RITUAL_SUCCESS = "message.soulhome.anchor.ritual_success";

        public static final String LENS_HIGHLIGHTED = "message.soulhome.lens.highlighted";
        public static final String LENS_NOTHING_TO_SHOW = "message.soulhome.lens.nothing_to_show";

        // The Soul Lens screen (#50): the report moved out of chat and onto a screen, so its
        // copy lives here rather than as a message.soulhome.* line.
        public static final String LENS_SCREEN_TITLE = "gui.soulhome.lens.title";
        public static final String LENS_SCREEN_UNCLASSIFIED = "gui.soulhome.lens.unclassified";
        public static final String LENS_SCREEN_AMBIGUOUS = "gui.soulhome.lens.ambiguous";
        public static final String LENS_SCREEN_TIER = "gui.soulhome.lens.tier";
        public static final String LENS_SCREEN_SCORE = "gui.soulhome.lens.score";
        public static final String LENS_SCREEN_NEXT_TIER = "gui.soulhome.lens.next_tier";
        public static final String LENS_SCREEN_MAXED = "gui.soulhome.lens.maxed";
        public static final String LENS_SCREEN_AMBIGUOUS_DETAIL = "gui.soulhome.lens.ambiguous_detail";
        public static final String LENS_SCREEN_COUNTS = "gui.soulhome.lens.counts_for";
        public static final String LENS_SCREEN_SIGNALS_HEADER = "gui.soulhome.lens.signals_header";
        public static final String LENS_SCREEN_MISSING_HEADER = "gui.soulhome.lens.missing_header";
        public static final String LENS_SCREEN_ARRANGEMENT_HEADER = "gui.soulhome.lens.arrangement_header";
        public static final String LENS_SCREEN_GRANTS_HEADER = "gui.soulhome.lens.grants_header";
        public static final String LENS_SCREEN_MORE = "gui.soulhome.lens.more";
        public static final String LENS_SCREEN_EMPTY_DETAIL = "gui.soulhome.lens.empty_detail";
        public static final String LENS_SCREEN_BUFFS_TITLE = "gui.soulhome.lens.buffs_title";
        public static final String LENS_SCREEN_BUFFS_FROM = "gui.soulhome.lens.buffs_from";
        public static final String LENS_SCREEN_CLOSE = "gui.soulhome.lens.close";

        // The box (#78/#79/#81): scarcity has to be legible, so the lens says what it is as
        // plainly as it says what a room scored.
        public static final String LENS_SCREEN_BOX_HEADER = "gui.soulhome.lens.box_header";
        public static final String LENS_SCREEN_BOX_LAYERS = "gui.soulhome.lens.box_layers";
        public static final String LENS_SCREEN_BOX_VERGE = "gui.soulhome.lens.box_verge";
        public static final String LENS_SCREEN_BOX_RANK = "gui.soulhome.lens.box_rank";
        public static final String LENS_SCREEN_BOX_LEGACY = "gui.soulhome.lens.box_legacy";

        // Active abilities (#87): the two binds, the HUD, and what an ability says when it
        // refuses. A refusal has to name its reason - "nothing happened" is the single most
        // common shape of an ability bug report.
        public static final String KEY_ABILITY_USE = "key.soulhome.ability.use";
        public static final String KEY_ABILITY_CYCLE = "key.soulhome.ability.cycle";

        public static final String ABILITY_SELECTED = "message.soulhome.ability.selected";
        public static final String ABILITY_HUD_CHARGES = "gui.soulhome.ability.charges";

        public static final String ABILITY_SOUL_STEP_NO_ROOM = "message.soulhome.ability.soul_step.no_room";
        public static final String ABILITY_SOUL_STEP_DISABLED = "message.soulhome.ability.soul_step.disabled";
        public static final String ABILITY_HERD_NO_MOUNT = "message.soulhome.ability.herd.no_mount";
        public static final String ABILITY_HERD_WRONG_DIMENSION = "message.soulhome.ability.herd.wrong_dimension";
        public static final String ABILITY_HERD_SUMMONED = "message.soulhome.ability.herd.summoned";
        public static final String ABILITY_SURVEYORS_EYE_NOTHING = "message.soulhome.ability.surveyors_eye.nothing";
        public static final String ABILITY_RALLY_ALONE = "message.soulhome.ability.rally.alone";
        public static final String ABILITY_RALLY_SHARED = "message.soulhome.ability.rally.shared";
        public static final String ABILITY_THUNDERCLAP_NOTHING = "message.soulhome.ability.thunderclap.nothing";

        // Two refusals every active shares. A press that does nothing and says nothing is
        // indistinguishable from a mod that is broken, which is how a recharging Thunderclap and
        // one whose damage was refused elsewhere both read as "this ability does not work".
        public static final String ABILITY_RECHARGING = "message.soulhome.ability.recharging";
        public static final String ABILITY_NO_DAMAGE = "message.soulhome.ability.no_damage";

        // The ability names themselves, for the HUD and the report. Kept apart from the
        // archetype display names: a room and the thing it grants are not the same noun.
        public static final String ABILITY_NAME_SURVEYORS_EYE = "ability.soulhome.surveyors_eye";
        public static final String ABILITY_NAME_AEGIS = "ability.soulhome.aegis";
        public static final String ABILITY_NAME_SOUL_STEP = "ability.soulhome.soul_step";
        public static final String ABILITY_NAME_RALLY = "ability.soulhome.rally";
        public static final String ABILITY_NAME_CALL_OF_THE_HERD = "ability.soulhome.call_of_the_herd";
        public static final String ABILITY_NAME_THUNDERCLAP = "ability.soulhome.thunderclap";
        public static final String ABILITY_NAME_BARRAGE = "ability.soulhome.barrage";
        public static final String ABILITY_NAME_RUPTURE = "ability.soulhome.rupture";
    }

    public static class NBTKeys
    {
        public static final String LAST_DIMENSION_X = "LAST_DIMENSION_X";
        public static final String LAST_DIMENSION_Y = "LAST_DIMENSION_Y";
        public static final String LAST_DIMENSION_Z = "LAST_DIMENSION_Z";
        public static final String LAST_DIMENSION_MOD_ID = "LAST_DIMENSION_MOD_ID";
        public static final String LAST_DIMENSION_MOD_DIMENSION = "LAST_DIMENSION_MOD_DIMENSION";
    }
}
