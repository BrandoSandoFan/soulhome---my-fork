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
    public static final TestBlock DOOR = block("minecraft:oak_door", Passability.BLOCKING, "minecraft:doors");

    // library
    public static final TestBlock BOOKSHELF = block("minecraft:bookshelf", Passability.BLOCKING, "minecraft:bookshelves");
    public static final TestBlock LECTERN = block("minecraft:lectern", Passability.BLOCKING);
    public static final TestBlock CHAIR = block("minecraft:oak_stairs", Passability.BLOCKING, "minecraft:stairs", "soulhome:seating");
    public static final TestBlock CANDLE = block("minecraft:candle", Passability.PASSABLE, "minecraft:candles", "soulhome:lighting");

    // armoury
    public static final TestBlock ANVIL = block("minecraft:anvil", Passability.BLOCKING, "minecraft:anvil", "soulhome:smithing");
    public static final TestBlock GRINDSTONE = block("minecraft:grindstone", Passability.BLOCKING, "soulhome:smithing");
    public static final TestBlock BANNER = block("minecraft:white_banner", Passability.PASSABLE, "minecraft:banners", "soulhome:armament");
    public static final TestBlock IRON_BLOCK = block("minecraft:iron_block", Passability.BLOCKING, "soulhome:armament", "forge:storage_blocks");

    // enchanting
    public static final TestBlock ENCHANTING_TABLE = block("minecraft:enchanting_table", Passability.BLOCKING);
    public static final TestBlock OBSIDIAN = block("minecraft:obsidian", Passability.BLOCKING, "soulhome:arcane");

    // farm
    public static final TestBlock WHEAT = block("minecraft:wheat", Passability.PASSABLE, "minecraft:crops");
    public static final TestBlock FARMLAND = block("minecraft:farmland", Passability.BLOCKING);
    public static final TestBlock WATER = block("minecraft:water", Passability.PASSABLE);
    public static final TestBlock COMPOSTER = block("minecraft:composter", Passability.BLOCKING);
    public static final TestBlock HAY = block("minecraft:hay_block", Passability.BLOCKING);
    public static final TestBlock BARREL = block("minecraft:barrel", Passability.BLOCKING, "soulhome:storage");

    // alchemy lab
    public static final TestBlock BREWING_STAND = block("minecraft:brewing_stand", Passability.BLOCKING);
    public static final TestBlock CAULDRON = block("minecraft:cauldron", Passability.BLOCKING, "soulhome:alchemy_vessels");
    public static final TestBlock NETHER_WART = block("minecraft:nether_wart", Passability.PASSABLE, "soulhome:reagents");
    public static final TestBlock SOUL_SAND = block("minecraft:soul_sand", Passability.BLOCKING, "soulhome:reagents");

    // bedchamber
    public static final TestBlock BED = block("minecraft:red_bed", Passability.BLOCKING, "minecraft:beds", "soulhome:seating");
    public static final TestBlock JUKEBOX = block("minecraft:jukebox", Passability.BLOCKING);
    public static final TestBlock CARPET = block("minecraft:white_carpet", Passability.PASSABLE, "minecraft:wool_carpets", "soulhome:seating", "soulhome:furnishing");
    public static final TestBlock WOOL = block("minecraft:white_wool", Passability.BLOCKING, "minecraft:wool", "soulhome:furnishing");

    // mine
    public static final TestBlock ORE = block("minecraft:iron_ore", Passability.BLOCKING, "forge:ores");
    public static final TestBlock RAIL = block("minecraft:rail", Passability.PASSABLE, "minecraft:rails");
    public static final TestBlock LADDER = block("minecraft:ladder", Passability.PASSABLE);
    public static final TestBlock GOLD_BLOCK = block("minecraft:gold_block", Passability.BLOCKING, "forge:storage_blocks");
    public static final TestBlock TORCH = block("minecraft:torch", Passability.PASSABLE, "soulhome:lighting");

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
