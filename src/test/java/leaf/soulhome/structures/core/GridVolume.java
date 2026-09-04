/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link BlockVolume} built from ASCII art, so region-detection cases read as the shapes they
 * describe rather than as coordinate arithmetic.
 *
 * <p>Layers run bottom to top; within a layer, rows are Z and columns are X:
 *
 * <pre>{@code
 * GridVolume.of(
 *     new String[]{ "###",
 *                   "###",
 *                   "###" },   // y = 0, the floor
 *     new String[]{ "###",
 *                   "#.#",
 *                   "###" },   // y = 1, a one-cell room
 *     new String[]{ "###",
 *                   "###",
 *                   "###" });  // y = 2, the ceiling
 * }</pre>
 *
 * <p>The grid is automatically padded with one cell of air on every side, so a build that fills
 * its own layout is still surrounded by "outside" - the same thing the game-side bounds derivation
 * does.
 */
public final class GridVolume implements BlockVolume
{
    private static final int PADDING = 1;

    private final RegionBounds bounds;
    private final TestBlocks.TestBlock[][][] blocks;
    private final Map<Character, TestBlocks.TestBlock> palette;

    private GridVolume(String[][] layers, Map<Character, TestBlocks.TestBlock> palette)
    {
        this.palette = palette;

        final int sizeY = layers.length;
        final int sizeZ = layers[0].length;
        final int sizeX = layers[0][0].length();

        for (int y = 0; y < sizeY; y++)
        {
            if (layers[y].length != sizeZ)
            {
                throw new IllegalArgumentException("Layer " + y + " has " + layers[y].length + " rows, expected " + sizeZ);
            }

            for (int z = 0; z < sizeZ; z++)
            {
                if (layers[y][z].length() != sizeX)
                {
                    throw new IllegalArgumentException(
                            "Layer " + y + " row " + z + " is " + layers[y][z].length() + " wide, expected " + sizeX);
                }
            }
        }

        this.bounds = new RegionBounds(
                -PADDING, -PADDING, -PADDING,
                sizeX - 1 + PADDING, sizeY - 1 + PADDING, sizeZ - 1 + PADDING);

        this.blocks = new TestBlocks.TestBlock[sizeX][sizeY][sizeZ];

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    final char symbol = layers[y][z].charAt(x);
                    TestBlocks.TestBlock block = palette.get(symbol);

                    if (block == null)
                    {
                        throw new IllegalArgumentException("No palette entry for '" + symbol + "'");
                    }

                    this.blocks[x][y][z] = block;
                }
            }
        }
    }

    public static GridVolume of(String[]... layers)
    {
        return new GridVolume(layers, defaultPalette());
    }

    public static GridVolume of(Map<Character, TestBlocks.TestBlock> extraPalette, String[]... layers)
    {
        Map<Character, TestBlocks.TestBlock> palette = defaultPalette();
        palette.putAll(extraPalette);
        return new GridVolume(layers, palette);
    }

    public static Map<Character, TestBlocks.TestBlock> defaultPalette()
    {
        Map<Character, TestBlocks.TestBlock> palette = new HashMap<>();
        palette.put('.', TestBlocks.AIR);
        palette.put('#', TestBlocks.STONE);
        palette.put('g', TestBlocks.GLASS);
        palette.put('D', TestBlocks.DOOR);
        palette.put('B', TestBlocks.BOOKSHELF);
        palette.put('L', TestBlocks.LECTERN);
        palette.put('S', TestBlocks.CHAIR);
        palette.put('c', TestBlocks.CANDLE);
        palette.put('A', TestBlocks.ANVIL);
        palette.put('n', TestBlocks.BANNER);
        palette.put('i', TestBlocks.IRON_BLOCK);
        palette.put('G', TestBlocks.GRINDSTONE);
        palette.put('E', TestBlocks.ENCHANTING_TABLE);
        palette.put('o', TestBlocks.OBSIDIAN);
        palette.put('w', TestBlocks.WHEAT);
        palette.put('f', TestBlocks.FARMLAND);
        palette.put('~', TestBlocks.WATER);
        palette.put('C', TestBlocks.COMPOSTER);
        palette.put('h', TestBlocks.HAY);
        palette.put('b', TestBlocks.BARREL);
        palette.put('p', TestBlocks.BREWING_STAND);
        palette.put('u', TestBlocks.CAULDRON);
        palette.put('r', TestBlocks.NETHER_WART);
        palette.put('s', TestBlocks.SOUL_SAND);
        palette.put('d', TestBlocks.BED);
        palette.put('j', TestBlocks.JUKEBOX);
        palette.put('x', TestBlocks.CARPET);
        palette.put('W', TestBlocks.WOOL);
        palette.put('O', TestBlocks.ORE);
        palette.put('=', TestBlocks.RAIL);
        palette.put('l', TestBlocks.LADDER);
        palette.put('Y', TestBlocks.GOLD_BLOCK);
        palette.put('t', TestBlocks.TORCH);
        palette.put('F', TestBlocks.FENCE);
        palette.put('|', TestBlocks.COBBLE_WALL);
        palette.put('I', TestBlocks.ICE);
        palette.put('M', TestBlocks.SLIME_BLOCK);
        palette.put('a', TestBlocks.SCAFFOLDING);
        palette.put('N', TestBlocks.FURNACE);
        palette.put('v', TestBlocks.LAVA);
        palette.put('m', TestBlocks.MAGMA);
        palette.put('k', TestBlocks.NETHERRACK);
        palette.put('P', TestBlocks.PACKED_ICE);
        palette.put('Q', TestBlocks.LODESTONE);
        palette.put('H', TestBlocks.CHAIN);
        palette.put('e', TestBlocks.POPPY);
        palette.put('z', TestBlocks.LEAVES);
        palette.put('J', TestBlocks.DIAMOND_BLOCK);
        palette.put('K', TestBlocks.ZOMBIE_HEAD);
        palette.put('U', TestBlocks.SKELETON_SKULL);
        palette.put('V', TestBlocks.CREEPER_HEAD);
        palette.put('X', TestBlocks.PIGLIN_HEAD);
        palette.put('Z', TestBlocks.WITHER_SKELETON_SKULL);
        palette.put('R', TestBlocks.DRAGON_HEAD);
        palette.put('T', TestBlocks.CORAL_BLOCK);

        // the eight ability rooms (#88-#92, #94-#96). The letters ran out, so these are digits and
        // punctuation - a layout that reads as a shape is what matters, not which glyph stands for
        // what, and every fixture that uses these declares them at its own head anyway.
        palette.put('q', TestBlocks.GLASS_PANE);
        palette.put('!', TestBlocks.BELL);
        palette.put('1', TestBlocks.IRON_BARS);
        palette.put('*', TestBlocks.TARGET);
        palette.put('2', TestBlocks.AMETHYST_BLOCK);
        palette.put('%', TestBlocks.BUDDING_AMETHYST);
        palette.put('3', TestBlocks.END_ROD);
        palette.put('y', TestBlocks.FENCE_GATE);
        palette.put('+', TestBlocks.WATER_CAULDRON);
        palette.put('4', TestBlocks.LIGHTNING_ROD);
        palette.put('5', TestBlocks.COPPER_BLOCK);
        palette.put('6', TestBlocks.TNT);
        palette.put('7', TestBlocks.DISPENSER);
        palette.put('8', TestBlocks.REDSTONE_BLOCK);
        palette.put('9', TestBlocks.SCULK_CATALYST);
        palette.put('0', TestBlocks.SCULK);
        palette.put('$', TestBlocks.SCULK_SENSOR);
        palette.put('&', TestBlocks.DEEPSLATE);
        palette.put('-', TestBlocks.WOODEN_SLAB);
        palette.put('_', TestBlocks.WOODEN_TRAPDOOR);
        return palette;
    }

    public Map<Character, TestBlocks.TestBlock> palette()
    {
        return this.palette;
    }

    @Override
    public RegionBounds bounds()
    {
        return this.bounds;
    }

    @Override
    public Passability passabilityAt(int x, int y, int z)
    {
        TestBlocks.TestBlock block = blockAt(x, y, z);
        return block == null ? Passability.EMPTY : block.passability();
    }

    @Override
    public BlockSignature signatureAt(int x, int y, int z)
    {
        TestBlocks.TestBlock block = blockAt(x, y, z);
        return block == null || block.passability() == Passability.EMPTY ? null : block;
    }

    private TestBlocks.TestBlock blockAt(int x, int y, int z)
    {
        // the padding ring is open air, which is what makes "escapes to the sky" detectable
        if (x < 0 || y < 0 || z < 0
                || x >= this.blocks.length
                || y >= this.blocks[0].length
                || z >= this.blocks[0][0].length)
        {
            return null;
        }

        return this.blocks[x][y][z];
    }
}
