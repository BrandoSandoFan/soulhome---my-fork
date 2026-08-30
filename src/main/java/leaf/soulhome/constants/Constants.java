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
