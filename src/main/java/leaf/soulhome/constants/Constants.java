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

        //Soul Vessel (#182) - the body a player leaves behind when they enter their soul
        public static final String VESSEL_DISTURBED = "message.soulhome.vessel.disturbed";

        //Meditation (#183) - the good way in, channelled at a Meditation Cushion
        public static final String KEY_MEDITATE = "key.soulhome.meditate";
        public static final String MEDITATION_NO_CUSHION = "message.soulhome.meditation.no_cushion";

        //Guest passage (#184) - entering a soul that is not yours is gated on your own rank
        public static final String GUEST_RANK_REQUIRED = "message.soulhome.guest.rank_required";
        public static final String GUEST_LEFT_BEHIND = "message.soulhome.guest.left_behind";

        //Soulgaze (#187) - the observatory's active, and the spectator session behind it
        public static final String GAZE_DISABLED = "message.soulhome.gaze.disabled";
        public static final String GAZE_FROM_OUTSIDE = "message.soulhome.gaze.from_outside";
        public static final String GAZE_NO_TARGET = "message.soulhome.gaze.no_target";
        public static final String GAZE_NO_SOUL = "message.soulhome.gaze.no_soul";
        public static final String GAZE_BEGIN = "message.soulhome.gaze.begin";
        public static final String GAZE_END_EXPIRED = "message.soulhome.gaze.end.expired";
        public static final String GAZE_END_SOUL_CLOSED = "message.soulhome.gaze.end.soul_closed";
        public static final String GAZE_END_RECALLED = "message.soulhome.gaze.end.recalled";

        //Being gazed at (#189) - what the watched player perceives
        public static final String GAZE_NOTICE_PRICKLE = "message.soulhome.gaze.notice.prickle";
        public static final String GAZE_NOTICE_OBSERVATORY = "message.soulhome.gaze.notice.observatory";
        public static final String GAZE_NOTICE_DIRECTION = "message.soulhome.gaze.notice.direction";
        public static final String GAZE_NOTICE_ELSEWHERE = "message.soulhome.gaze.notice.elsewhere";
        public static final String COMPASS_PREFIX = "direction.soulhome.";

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

        // Soul Architecture (#140): the bonds half of the report. A bond names the other room by
        // its display name and its relation by a translated phrase - never an id (#103) - and a
        // miss states the gap and the threshold, since the near miss is the useful half.
        public static final String REGION_BOND_HEADER = "message.soulhome.region.bond_header";
        public static final String REGION_BOND_HIT = "message.soulhome.region.bond_hit";
        public static final String REGION_BOND_MISS = "message.soulhome.region.bond_miss";
        public static final String REGION_BOND_DISCORD = "message.soulhome.region.bond_discord";
        public static final String REGION_BOND_CAPPED = "message.soulhome.region.bond_capped";
        public static final String BOND_RELATION_PREFIX = "bond.soulhome.relation.";

        // Aspects (#171): which of the things a room of this kind can be for it turned out to be.
        // The near miss is the actionable half here as it is everywhere else - and the line saying
        // the aspect cost the room nothing is not decoration. A player shown two aspects with
        // numbers beside them will assume the split cost them something, because that is what every
        // other pair of competing numbers in this mod means, and a suspected nerf nobody denies is
        // the fastest way for a feature to be read as a bug.
        public static final String REGION_ASPECT_HEADER = "message.soulhome.region.aspect_header";
        public static final String REGION_ASPECT_TAKEN = "message.soulhome.region.aspect_taken";
        public static final String REGION_ASPECT_RUNNER_UP = "message.soulhome.region.aspect_runner_up";
        public static final String REGION_ASPECT_HELD = "message.soulhome.region.aspect_held";
        public static final String REGION_ASPECT_TIP = "message.soulhome.region.aspect_tip";
        public static final String REGION_ASPECT_FREE = "message.soulhome.region.aspect_free";

        // Attunement (#153): an unattuned room reports in full, exactly as it always did, with one
        // line saying it is not being carried. Omitting the room, or showing it as worth zero,
        // would turn a choice the player made into a room that looks broken.
        public static final String REGION_ATTUNED = "message.soulhome.region.attuned";
        public static final String REGION_NOT_ATTUNED = "message.soulhome.region.not_attuned";

        public static final String BUFFS_HEADER = "message.soulhome.buffs.header";
        public static final String BUFFS_NONE = "message.soulhome.buffs.none";
        public static final String BUFFS_ENTRY = "message.soulhome.buffs.entry";
        public static final String BUFFS_SOURCE = "message.soulhome.buffs.source";
        public static final String BUFFS_SOURCE_ASPECT = "message.soulhome.buffs.source_aspect";
        public static final String BUFFS_CAPPED = "message.soulhome.buffs.capped";
        public static final String BUFFS_RANK_BONUS = "message.soulhome.buffs.rank_bonus";

        // Attunement (#151): what you are carrying, what you are carrying it instead of, and - the
        // line that matters most - that an unattuned room is not a room you have lost. The mod has
        // never before taken something away from a save that already exists, and a player who finds
        // fewer buffs than they went to bed with and is told nothing concludes it is broken.
        public static final String BUFFS_SLOTS = "message.soulhome.buffs.slots";
        public static final String BUFFS_DORMANT_HEADER = "message.soulhome.buffs.dormant_header";
        public static final String BUFFS_DORMANT_ROOM = "message.soulhome.buffs.dormant_room";
        public static final String BUFFS_DORMANT_ROOM_ASPECT = "message.soulhome.buffs.dormant_room_aspect";
        public static final String BUFFS_DORMANT_GRANT = "message.soulhome.buffs.dormant_grant";
        public static final String BUFFS_NOT_LOST = "message.soulhome.buffs.not_lost";

        /** The trophy room's targeted knockback resistance (#196) - whose head, and against them by how much. */
        public static final String BUFFS_TROPHY_GRUDGE_HEADER = "message.soulhome.buffs.trophy_grudge_header";
        public static final String BUFFS_TROPHY_GRUDGE = "message.soulhome.buffs.trophy_grudge";

        public static final String ATTUNE_EXCEEDED_HEADER = "message.soulhome.attune.exceeded_header";
        public static final String ATTUNE_EXCEEDED_SLOTS = "message.soulhome.attune.exceeded_slots";
        public static final String ATTUNE_EXCEEDED_WHERE = "message.soulhome.attune.exceeded_where";
        public static final String ATTUNE_EXCEEDED_KEPT = "message.soulhome.attune.exceeded_kept";
        public static final String ATTUNE_BOUND = "message.soulhome.attune.bound";
        public static final String ATTUNE_UNBOUND = "message.soulhome.attune.unbound";
        public static final String ATTUNE_NO_SLOTS = "message.soulhome.attune.no_slots";
        public static final String ATTUNE_NOT_YOURS = "message.soulhome.attune.not_yours";

        // The four buffs a soft ceiling stops before they overshoot themselves (#86): what a
        // player is told once the ceiling holds part of what they built back.
        public static final String BUFFS_SOFT_CEILING_CONVERTED = "message.soulhome.buffs.soft_ceiling_converted";
        public static final String BUFFS_SOFT_CEILING_DROPPED = "message.soulhome.buffs.soft_ceiling_dropped";

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

        // Terrain growth (#158/#162): what a player actually cares about when they ask how much
        // room they have is ground, not walls, and the two are deliberately different numbers.
        public static final String ASCENT_GROUND = "message.soulhome.ascent.ground";
        public static final String ASCENT_GROUND_NEXT = "message.soulhome.ascent.ground_next";
        public static final String ASCENT_GROUND_VERGE = "message.soulhome.ascent.ground_verge";
        public static final String ASCENT_GROUND_GROWING = "message.soulhome.ascent.ground_growing";
        public static final String ASCENT_GROUND_OFF = "message.soulhome.ascent.ground_off";
        public static final String GROWTH_COMPLETE = "message.soulhome.growth.complete";

        // /soulhome ascent set - the operator-only way to jump straight to a rank (#84)
        public static final String ASCENT_SET_SUCCESS = "message.soulhome.ascent.set_success";
        public static final String ASCENT_SET_OUT_OF_RANGE = "message.soulhome.ascent.set_out_of_range";

        // /soulhome ascent willpower - the operator-only way to test the ritual or the residue tap
        // against a chosen figure without building the rooms to earn it (#192)
        public static final String ASCENT_WILLPOWER_SET_SUCCESS = "message.soulhome.ascent.willpower_set_success";
        public static final String ASCENT_WILLPOWER_RESET_SUCCESS = "message.soulhome.ascent.willpower_reset_success";

        // The Soul Anchor and the ascension ritual (#83): the climb itself, rather than merely the
        // box it climbs against.
        public static final String ANCHOR_NOT_HERE = "message.soulhome.anchor.not_here";
        public static final String ANCHOR_ALREADY_EXISTS = "message.soulhome.anchor.already_exists";
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
        public static final String LENS_SCREEN_BONDS_HEADER = "gui.soulhome.lens.bonds_header";
        public static final String LENS_SCREEN_ASPECT_HEADER = "gui.soulhome.lens.aspect_header";
        public static final String LENS_SCREEN_MORE = "gui.soulhome.lens.more";
        public static final String LENS_SCREEN_EMPTY_DETAIL = "gui.soulhome.lens.empty_detail";
        public static final String LENS_SCREEN_BUFFS_TITLE = "gui.soulhome.lens.buffs_title";
        public static final String LENS_SCREEN_BUFFS_FROM = "gui.soulhome.lens.buffs_from";
        public static final String LENS_SCREEN_BUFFS_FROM_ASPECT = "gui.soulhome.lens.buffs_from_aspect";
        public static final String LENS_SCREEN_BUFFS_RANK_BONUS = "gui.soulhome.lens.buffs_rank_bonus";
        public static final String LENS_SCREEN_CLOSE = "gui.soulhome.lens.close";

        // The Soul Anchor's attunement screen (#154). Binding lives here rather than on the lens
        // because the anchor is already the one place in a soulhome you go to find out where you
        // stand, and because a loadout is a thing to plan before setting out rather than to change
        // halfway down a ravine.
        public static final String ANCHOR_SCREEN_TITLE = "gui.soulhome.anchor.title";
        public static final String ANCHOR_SCREEN_SLOTS = "gui.soulhome.anchor.slots";
        public static final String ANCHOR_SCREEN_PASSIVE = "gui.soulhome.anchor.passive";
        public static final String ANCHOR_SCREEN_ACTIVE = "gui.soulhome.anchor.active";
        public static final String ANCHOR_SCREEN_ATTUNED = "gui.soulhome.anchor.attuned";
        public static final String ANCHOR_SCREEN_DORMANT = "gui.soulhome.anchor.dormant";
        public static final String ANCHOR_SCREEN_ROOM = "gui.soulhome.anchor.room";
        public static final String ANCHOR_SCREEN_ROOM_ASPECT = "gui.soulhome.anchor.room_aspect";
        public static final String ANCHOR_SCREEN_GRANT = "gui.soulhome.anchor.grant";
        public static final String ANCHOR_SCREEN_WOULD_GRANT = "gui.soulhome.anchor.would_grant";
        public static final String ANCHOR_SCREEN_GONE = "gui.soulhome.anchor.gone";
        public static final String ANCHOR_SCREEN_NO_ROOMS = "gui.soulhome.anchor.no_rooms";
        public static final String ANCHOR_SCREEN_HINT = "gui.soulhome.anchor.hint";
        public static final String ANCHOR_SCREEN_VISITOR = "gui.soulhome.anchor.visitor";
        public static final String ANCHOR_SCREEN_NOT_LOST = "gui.soulhome.anchor.not_lost";

        // The ambience options screen (#163/#167). Client-side settings rather than server ones, and
        // so the only screen in the mod that changes nothing about anybody's game but the looker's.
        public static final String AMBIENCE_SCREEN_TITLE = "gui.soulhome.ambience.title";
        public static final String AMBIENCE_SCREEN_ENABLED = "gui.soulhome.ambience.enabled";
        public static final String AMBIENCE_SCREEN_RANK = "gui.soulhome.ambience.rank_visuals";
        public static final String AMBIENCE_SCREEN_CHARACTER = "gui.soulhome.ambience.character_colour";
        public static final String AMBIENCE_SCREEN_SOUND = "gui.soulhome.ambience.ambient_sound";
        public static final String AMBIENCE_SCREEN_INTENSITY = "gui.soulhome.ambience.intensity";
        public static final String AMBIENCE_SCREEN_VOLUME = "gui.soulhome.ambience.sound_volume";
        public static final String AMBIENCE_SCREEN_OFF = "gui.soulhome.ambience.off";
        public static final String AMBIENCE_SCREEN_COSMETIC = "gui.soulhome.ambience.cosmetic";

        // The same screen's second column: suppression's client switches (#188, #190), which lived
        // only in the toml until a player who gets motion sick had a screen to find them on.
        public static final String AMBIENCE_SCREEN_SECTION_SOUL = "gui.soulhome.ambience.section.soul";
        public static final String AMBIENCE_SCREEN_SECTION_SUPPRESSION = "gui.soulhome.ambience.section.suppression";
        public static final String AMBIENCE_SCREEN_SUPPRESSION_DISTORTION = "gui.soulhome.ambience.suppression_distortion";
        public static final String AMBIENCE_SCREEN_SUPPRESSION_DISTORTION_TIP = "gui.soulhome.ambience.suppression_distortion.tooltip";
        public static final String AMBIENCE_SCREEN_SUPPRESSION_AUDIO = "gui.soulhome.ambience.suppression_audio";
        public static final String AMBIENCE_SCREEN_SUPPRESSION_AUDIO_TIP = "gui.soulhome.ambience.suppression_audio.tooltip";

        // Suppression's sounds (#188), for anyone reading the game through its subtitles
        public static final String SUBTITLE_SUPPRESSION_DRONE = "subtitles.soulhome.suppression.drone";
        public static final String SUBTITLE_SUPPRESSION_THROB = "subtitles.soulhome.suppression.throb";

        // The climb, on the same screen as the loadout (#83): the requirement lines themselves are
        // the message.soulhome.anchor.* ones the summary was printed with, reused rather than
        // rewritten, so a screen and a chat line can never end up describing the same pillar
        // differently.
        public static final String ANCHOR_SCREEN_ASCENSION = "gui.soulhome.anchor.ascension";
        public static final String ANCHOR_SCREEN_RESIDUE = "gui.soulhome.anchor.residue";
        public static final String ANCHOR_SCREEN_RESIDUE_SHORT = "gui.soulhome.anchor.residue_short";
        public static final String ANCHOR_SCREEN_COLLECT = "gui.soulhome.anchor.collect";
        public static final String ANCHOR_SCREEN_COLLECT_NONE = "gui.soulhome.anchor.collect_none";

        // The box (#78/#79/#81): scarcity has to be legible, so the lens says what it is as
        // plainly as it says what a room scored.
        public static final String LENS_SCREEN_BOX_HEADER = "gui.soulhome.lens.box_header";
        public static final String LENS_SCREEN_BOX_LAYERS = "gui.soulhome.lens.box_layers";
        public static final String LENS_SCREEN_BOX_VERGE = "gui.soulhome.lens.box_verge";
        public static final String LENS_SCREEN_BOX_RANK = "gui.soulhome.lens.box_rank";
        public static final String LENS_SCREEN_BOX_LEGACY = "gui.soulhome.lens.box_legacy";
        public static final String LENS_SCREEN_BOX_GROUND = "gui.soulhome.lens.box_ground";
        public static final String LENS_SCREEN_BOX_GROWING = "gui.soulhome.lens.box_growing";

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
        public static final String ABILITY_CLEANSING_FONT_NOTHING = "message.soulhome.ability.cleansing_font.nothing";
        public static final String ABILITY_CLEANSING_FONT_CLEANSED = "message.soulhome.ability.cleansing_font.cleansed";
        public static final String ABILITY_LAST_STAND_UNHURT = "message.soulhome.ability.last_stand.unhurt";
        public static final String ABILITY_CALMING_SMOKE_NOTHING = "message.soulhome.ability.calming_smoke.nothing";

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
        public static final String ABILITY_NAME_CLEANSING_FONT = "ability.soulhome.cleansing_font";
        public static final String ABILITY_NAME_UPDRAFT = "ability.soulhome.updraft";
        public static final String ABILITY_NAME_LAST_STAND = "ability.soulhome.last_stand";
        public static final String ABILITY_NAME_CALMING_SMOKE = "ability.soulhome.calming_smoke";
        public static final String ABILITY_NAME_SOULGAZE = "ability.soulhome.soulgaze";
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
