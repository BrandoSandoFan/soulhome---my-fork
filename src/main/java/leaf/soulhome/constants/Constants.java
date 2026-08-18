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

        public static final String BUFFS_HEADER = "message.soulhome.buffs.header";
        public static final String BUFFS_NONE = "message.soulhome.buffs.none";
        public static final String BUFFS_ENTRY = "message.soulhome.buffs.entry";
        public static final String BUFFS_SOURCE = "message.soulhome.buffs.source";
        public static final String BUFFS_CAPPED = "message.soulhome.buffs.capped";

        public static final String LENS_HIGHLIGHTED = "message.soulhome.lens.highlighted";
        public static final String LENS_NOTHING_TO_SHOW = "message.soulhome.lens.nothing_to_show";
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
