/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Set;

/**
 * The synthetic block palette the region and classifier tests build worlds out of.
 *
 * <p>Each character in a {@link GridVolume} layout maps to one of these. Tag membership mirrors
 * what the shipped tag files under {@code data/soulhome/tags/blocks} resolve to, so the real
 * archetype definitions can be scored against these worlds.
 *
 * <p>Passability mirrors vanilla too, including the split between a block that fills its cell and
 * one that merely stops the fill: a fence, a wall, a door, a stair, a bed and most furniture are
 * {@link Passability#PARTIAL}, and only a genuine full cube is {@link Passability#BLOCKING}. The
 * region scanner treats the two differently - see {@link Passability} - so getting this wrong here
 * would make its tests agree with each other and disagree with the game.
 *
 * <p>Everything here is a <i>block</i>. Armour stands and item frames, which an armoury would
 * naturally display weapons on, are entities and so are invisible to block-based region detection
 * entirely - the armoury archetype is built out of smithing and armament blocks instead.
 */
public final class TestBlocks
{
    public static final TestBlock AIR = block("minecraft:air", Passability.EMPTY);

    // structure
    public static final TestBlock STONE = block("minecraft:stone", Passability.BLOCKING);
    public static final TestBlock GLASS = block("minecraft:glass", Passability.BLOCKING);

    // doors count as boundary whether open or shut
    public static final TestBlock DOOR = block("minecraft:oak_door", Passability.PARTIAL, "minecraft:doors");

    // library
    public static final TestBlock BOOKSHELF = block("minecraft:bookshelf", Passability.BLOCKING, "soulhome:bookshelves");
    public static final TestBlock CHISELED_BOOKSHELF = block("minecraft:chiseled_bookshelf", Passability.BLOCKING, "soulhome:bookshelves");
    public static final TestBlock LECTERN = block("minecraft:lectern", Passability.PARTIAL);
    public static final TestBlock CHAIR = block("minecraft:oak_stairs", Passability.PARTIAL, "minecraft:stairs", "soulhome:seating");
    public static final TestBlock CANDLE = block("minecraft:candle", Passability.PASSABLE, "minecraft:candles", "soulhome:lighting");

    // armoury
    public static final TestBlock ANVIL = block("minecraft:anvil", Passability.PARTIAL, "minecraft:anvil", "soulhome:smithing");
    public static final TestBlock GRINDSTONE = block("minecraft:grindstone", Passability.PARTIAL, "soulhome:smithing");
    public static final TestBlock BANNER = block("minecraft:white_banner", Passability.PASSABLE, "minecraft:banners", "soulhome:armament");
    public static final TestBlock IRON_BLOCK = block("minecraft:iron_block", Passability.BLOCKING, "soulhome:armament", "forge:storage_blocks");

    // enchanting
    public static final TestBlock ENCHANTING_TABLE = block("minecraft:enchanting_table", Passability.PARTIAL);
    public static final TestBlock OBSIDIAN = block("minecraft:obsidian", Passability.BLOCKING, "soulhome:arcane");

    // farm
    public static final TestBlock WHEAT = block("minecraft:wheat", Passability.PASSABLE, "minecraft:crops");
    public static final TestBlock FARMLAND = block("minecraft:farmland", Passability.PARTIAL);
    public static final TestBlock WATER = block("minecraft:water", Passability.PASSABLE);
    public static final TestBlock COMPOSTER = block("minecraft:composter", Passability.PARTIAL);
    public static final TestBlock HAY = block("minecraft:hay_block", Passability.BLOCKING);
    public static final TestBlock BARREL = block("minecraft:barrel", Passability.BLOCKING, "soulhome:storage");

    // alchemy lab
    public static final TestBlock BREWING_STAND = block("minecraft:brewing_stand", Passability.PARTIAL);
    public static final TestBlock CAULDRON = block("minecraft:cauldron", Passability.PARTIAL, "soulhome:alchemy_vessels");
    public static final TestBlock NETHER_WART = block("minecraft:nether_wart", Passability.PASSABLE, "soulhome:reagents");
    public static final TestBlock SOUL_SAND = block("minecraft:soul_sand", Passability.PARTIAL, "soulhome:reagents");

    // bedchamber
    public static final TestBlock BED = block("minecraft:red_bed", Passability.PARTIAL, "minecraft:beds", "soulhome:seating");
    public static final TestBlock JUKEBOX = block("minecraft:jukebox", Passability.BLOCKING);
    public static final TestBlock CARPET = block("minecraft:white_carpet", Passability.PASSABLE, "minecraft:wool_carpets", "soulhome:seating", "soulhome:furnishing");
    public static final TestBlock WOOL = block("minecraft:white_wool", Passability.BLOCKING, "minecraft:wool", "soulhome:furnishing");

    // mine
    public static final TestBlock ORE = block("minecraft:iron_ore", Passability.BLOCKING, "forge:ores");
    public static final TestBlock RAIL = block("minecraft:rail", Passability.PASSABLE, "minecraft:rails");
    public static final TestBlock LADDER = block("minecraft:ladder", Passability.PASSABLE);
    public static final TestBlock GOLD_BLOCK = block("minecraft:gold_block", Passability.BLOCKING, "forge:storage_blocks");
    public static final TestBlock TORCH = block("minecraft:torch", Passability.PASSABLE, "soulhome:lighting");

    // track
    public static final TestBlock FENCE = block("minecraft:oak_fence", Passability.PARTIAL, "minecraft:fences");
    public static final TestBlock COBBLE_WALL = block("minecraft:cobblestone_wall", Passability.PARTIAL, "minecraft:walls");
    public static final TestBlock ICE = block("minecraft:ice", Passability.BLOCKING);

    // training yard
    public static final TestBlock SLIME_BLOCK = block("minecraft:slime_block", Passability.BLOCKING);
    public static final TestBlock SCAFFOLDING = block("minecraft:scaffolding", Passability.PASSABLE);

    // hearth
    public static final TestBlock FURNACE = block("minecraft:furnace", Passability.BLOCKING);
    public static final TestBlock LAVA = block("minecraft:lava", Passability.PASSABLE);
    public static final TestBlock MAGMA = block("minecraft:magma_block", Passability.BLOCKING);
    public static final TestBlock NETHERRACK = block("minecraft:netherrack", Passability.BLOCKING);

    // hearth (#48): a smoker is one of the two blocks the hearth's new cooking signal counts,
    // matched by block id rather than a tag since neither vanilla block carries one of its own
    public static final TestBlock SMOKER = block("minecraft:smoker", Passability.BLOCKING);

    // lighting (#45): redstone lamps were missing from soulhome:lighting entirely
    public static final TestBlock REDSTONE_LAMP = block("minecraft:redstone_lamp", Passability.BLOCKING, "soulhome:lighting");

    // workshop
    public static final TestBlock CRAFTING_TABLE = block("minecraft:crafting_table", Passability.BLOCKING);

    // Blocks from mods this one does not depend on, for the three rooms written against them. Tag
    // membership mirrors the shipped tag files, where they are optional entries - a tag entry that
    // is allowed not to exist. The classifier neither knows nor cares which mod a block came from,
    // so these are ordinary palette entries; what they buy is a test that the rooms actually
    // classify, rather than three archetypes nothing has ever scored.
    //
    // Passability mirrors the real blocks: a workstation, a pedestal and a cogwheel all stop the
    // fill without filling their cell, the same as a vanilla anvil or fence.
    public static final TestBlock INSCRIPTION_TABLE = block("irons_spellbooks:inscription_table", Passability.PARTIAL);
    public static final TestBlock SCROLL_FORGE = block("irons_spellbooks:scroll_forge", Passability.PARTIAL);
    public static final TestBlock PEDESTAL = block("irons_spellbooks:pedestal", Passability.PARTIAL, "soulhome:arcane");
    public static final TestBlock ARCANE_ANVIL = block("irons_spellbooks:arcane_anvil", Passability.PARTIAL, "soulhome:smithing");
    public static final TestBlock ARMOR_PILE = block("irons_spellbooks:armor_pile", Passability.PARTIAL, "soulhome:armament");
    public static final TestBlock ALCHEMIST_CAULDRON = block("irons_spellbooks:alchemist_cauldron", Passability.PARTIAL, "soulhome:alchemy_vessels");
    public static final TestBlock FIREFLY_JAR = block("irons_spellbooks:firefly_jar", Passability.PARTIAL, "soulhome:lighting");

    public static final TestBlock COGWHEEL = block("create:cogwheel", Passability.PARTIAL, "soulhome:machinery");
    public static final TestBlock MECHANICAL_PRESS = block("create:mechanical_press", Passability.PARTIAL, "soulhome:machinery");
    public static final TestBlock BELT = block("create:belt", Passability.PARTIAL, "soulhome:machinery");
    public static final TestBlock ITEM_VAULT = block("create:item_vault", Passability.BLOCKING, "soulhome:storage");

    // cold storage
    public static final TestBlock PACKED_ICE = block("minecraft:packed_ice", Passability.BLOCKING);

    // shrine
    public static final TestBlock LODESTONE = block("minecraft:lodestone", Passability.BLOCKING);
    public static final TestBlock CHAIN = block("minecraft:chain", Passability.PARTIAL);

    // greenhouse
    public static final TestBlock POPPY = block("minecraft:poppy", Passability.PASSABLE, "minecraft:flowers");
    public static final TestBlock LEAVES = block("minecraft:oak_leaves", Passability.BLOCKING, "minecraft:leaves");

    // treasury
    public static final TestBlock DIAMOND_BLOCK = block("minecraft:diamond_block", Passability.BLOCKING, "soulhome:precious_blocks");

    // trophy room
    public static final TestBlock ZOMBIE_HEAD = block("minecraft:zombie_head", Passability.PARTIAL);
    public static final TestBlock SKELETON_SKULL = block("minecraft:skeleton_skull", Passability.PARTIAL);
    public static final TestBlock CREEPER_HEAD = block("minecraft:creeper_head", Passability.PARTIAL);
    public static final TestBlock PIGLIN_HEAD = block("minecraft:piglin_head", Passability.PARTIAL);
    public static final TestBlock WITHER_SKELETON_SKULL = block("minecraft:wither_skeleton_skull", Passability.PARTIAL);
    public static final TestBlock DRAGON_HEAD = block("minecraft:dragon_head", Passability.PARTIAL);

    // aquarium
    public static final TestBlock CORAL_BLOCK = block("minecraft:tube_coral_block", Passability.BLOCKING, "minecraft:coral_blocks");

    private TestBlocks()
    {
    }

    private static TestBlock block(String id, Passability passability, String... tags)
    {
        return new TestBlock(id, Set.of(tags), passability);
    }

    /**
     * A block in a synthetic world. Value equality comes free with the record, which is exactly
     * what {@link BlockCounts} needs from a signature.
     */
    public record TestBlock(String id, Set<String> tags, Passability passability) implements BlockSignature
    {
        @Override
        public boolean hasTag(String tagId)
        {
            return this.tags.contains(tagId);
        }
    }
}
