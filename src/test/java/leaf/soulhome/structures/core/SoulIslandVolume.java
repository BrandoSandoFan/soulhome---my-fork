/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One of the shipped starter islands as a {@link BlockVolume}, read straight from the structure
 * template the game places - the regression corpus of #139.
 *
 * <p>Every fault in the region detection epic (#133) was found by a player walking into a fresh
 * soul and noticing something odd, and every hand-written {@link GridVolume} layout was too small
 * and too deliberate to have shown it. The three templates under
 * {@code data/soulhome/structures} are what a player actually walks into, so they are what a test
 * asking "what does a brand-new soul report?" has to be asked of.
 *
 * <p>Read from the template rather than from a committed extraction of it, so there is nothing
 * to regenerate when a template changes; see {@link NbtReader}. What the template cannot say is
 * how each block behaves during a scan - that is Minecraft's knowledge, and it lives in
 * {@code soul_islands/palette.json} beside these tests: passability as
 * {@code SnapshotBlockVolume#passabilityOf} would derive it, and tags as the shipped tag files
 * and vanilla give them. A template block missing from the palette fails loudly with its id,
 * which is the one manual step a changed template can ask for.
 *
 * <p>Padded with two cells of air on every side, as {@code SnapshotBlockVolume} pads a soulhome,
 * so the outside-in fill has somewhere to start. Mutable, so a test can build on the island:
 * {@link #place(GridVolume, int, int, int)} drops one of the ASCII layouts onto it, air included,
 * the way a player would clear the ground before building.
 */
public final class SoulIslandVolume implements BlockVolume
{
    /** How many {@code soul_island<n>} templates ship - {@code DimensionRegistry.ISLAND_STYLE_COUNT}. */
    public static final int SHIPPED_ISLANDS = 3;

    /**
     * Where the templates live, relative to the repository root, as {@link ArchetypeJsonReader}
     * reads. {@code structure}, singular: 1.21 renamed the datapack folder, and the game reads
     * {@code soul_island<n>} from there.
     */
    public static final Path SHIPPED_DIRECTORY =
            Path.of("src", "main", "resources", "data", "soulhome", "structure");

    private static final String PALETTE_RESOURCE = "/soul_islands/palette.json";

    private static final int PADDING = 2;

    private static Map<String, PaletteEntry> palette;

    private final int style;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final RegionBounds bounds;
    private final TestBlocks.TestBlock[] blocks;

    private SoulIslandVolume(int style, int sizeX, int sizeY, int sizeZ)
    {
        this.style = style;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.bounds = new RegionBounds(
                -PADDING, -PADDING, -PADDING,
                sizeX - 1 + PADDING, sizeY - 1 + PADDING, sizeZ - 1 + PADDING);
        this.blocks = new TestBlocks.TestBlock[sizeX * sizeY * sizeZ];
    }

    /** The shipped template {@code soul_island<style>.nbt}, untouched. */
    public static SoulIslandVolume shipped(int style)
    {
        final Path file = SHIPPED_DIRECTORY.resolve("soul_island" + style + ".nbt");

        if (!Files.isRegularFile(file))
        {
            throw new IllegalStateException(
                    "Cannot find " + file.toAbsolutePath()
                            + " - these tests must run with the repository root as the working directory");
        }

        try (InputStream stream = Files.newInputStream(file))
        {
            return fromTemplate(style, NbtReader.readCompressed(stream));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /** Every shipped island, in style order. */
    public static List<SoulIslandVolume> allShipped()
    {
        List<SoulIslandVolume> islands = new ArrayList<>(SHIPPED_ISLANDS);

        for (int style = 0; style < SHIPPED_ISLANDS; style++)
        {
            islands.add(shipped(style));
        }

        return islands;
    }

    @SuppressWarnings("unchecked")
    private static SoulIslandVolume fromTemplate(int style, Map<String, Object> root)
    {
        List<Object> size = (List<Object>) root.get("size");
        SoulIslandVolume island = new SoulIslandVolume(
                style, (Integer) size.get(0), (Integer) size.get(1), (Integer) size.get(2));

        // a template with several palettes (a randomised one) is not what the islands are; take
        // the first, which is what the game does for a template placed with no palette chosen
        List<Object> states = root.containsKey("palette")
                ? (List<Object>) root.get("palette")
                : (List<Object>) ((List<Object>) root.get("palettes")).get(0);

        List<TestBlocks.TestBlock> resolved = new ArrayList<>(states.size());
        Set<String> unknown = new LinkedHashSet<>();

        for (Object state : states)
        {
            Map<String, Object> entry = (Map<String, Object>) state;
            final String id = (String) entry.get("Name");
            Map<String, Object> properties = entry.containsKey("Properties")
                    ? (Map<String, Object>) entry.get("Properties")
                    : Map.of();

            TestBlocks.TestBlock block = resolve(id, properties);

            if (block == null)
            {
                unknown.add(id);
            }

            resolved.add(block);
        }

        if (!unknown.isEmpty())
        {
            throw new IllegalStateException(
                    "soul_island" + style + " holds blocks the corpus palette does not describe: " + unknown
                            + ". Add each to src/test/resources" + PALETTE_RESOURCE
                            + " with the passability SnapshotBlockVolume would give it and its tags.");
        }

        for (Object block : (List<Object>) root.get("blocks"))
        {
            Map<String, Object> entry = (Map<String, Object>) block;
            List<Object> pos = (List<Object>) entry.get("pos");
            TestBlocks.TestBlock resolvedBlock = resolved.get((Integer) entry.get("state"));

            island.blocks[island.index((Integer) pos.get(0), (Integer) pos.get(1), (Integer) pos.get(2))] = resolvedBlock;
        }

        return island;
    }

    // region palette

    private static synchronized Map<String, PaletteEntry> palette()
    {
        if (palette != null)
        {
            return palette;
        }

        try (InputStream stream = SoulIslandVolume.class.getResourceAsStream(PALETTE_RESOURCE))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing test resource " + PALETTE_RESOURCE);
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
            {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, PaletteEntry> entries = new HashMap<>();

                for (Map.Entry<String, JsonElement> entry : json.entrySet())
                {
                    if (entry.getKey().startsWith("_"))
                    {
                        continue;
                    }

                    entries.put(entry.getKey(), PaletteEntry.read(entry.getValue().getAsJsonObject()));
                }

                palette = entries;
                return palette;
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not read " + PALETTE_RESOURCE, e);
        }
    }

    /** {@code null} for a block the palette does not know. */
    private static TestBlocks.TestBlock resolve(String id, Map<String, Object> properties)
    {
        PaletteEntry entry = palette().get(id);

        if (entry == null)
        {
            return null;
        }

        return new TestBlocks.TestBlock(id, entry.tags(), entry.passabilityFor(properties));
    }

    /**
     * @param passability      the fixed answer, or {@code null} when {@link #byProperty} decides
     * @param byProperty       which block state property to read, or {@code null}
     * @param byPropertyValues passability per value of that property
     * @param byPropertyDefault the passability for any other value
     */
    private record PaletteEntry(
            Passability passability,
            Set<String> tags,
            String byProperty,
            Map<String, Passability> byPropertyValues,
            Passability byPropertyDefault)
    {
        static PaletteEntry read(JsonObject json)
        {
            Set<String> tags = new LinkedHashSet<>();

            if (json.has("tags"))
            {
                for (JsonElement tag : json.getAsJsonArray("tags"))
                {
                    tags.add(tag.getAsString());
                }
            }

            if (json.has("passability_by"))
            {
                JsonObject by = json.getAsJsonObject("passability_by");
                Map<String, Passability> values = new HashMap<>();

                for (Map.Entry<String, JsonElement> value : by.getAsJsonObject("values").entrySet())
                {
                    values.put(value.getKey(), passability(value.getValue().getAsString()));
                }

                return new PaletteEntry(
                        null, Set.copyOf(tags), by.get("property").getAsString(), values,
                        passability(by.get("default").getAsString()));
            }

            return new PaletteEntry(
                    passability(json.get("passability").getAsString()), Set.copyOf(tags), null, Map.of(), null);
        }

        private static Passability passability(String name)
        {
            return Passability.valueOf(name.toUpperCase(Locale.ROOT));
        }

        Passability passabilityFor(Map<String, Object> properties)
        {
            if (this.byProperty == null)
            {
                return this.passability;
            }

            Object value = properties.get(this.byProperty);
            return value == null ? this.byPropertyDefault : this.byPropertyValues.getOrDefault(value.toString(), this.byPropertyDefault);
        }
    }

    // endregion

    // region the template's own size

    // The template's size, before the padding {@link #bounds} adds. What the game places the
    // island by - DimensionRegistry centres it on the origin horizontally and anchors it
    // vertically on the spawn column - so a test that has to reason in world coordinates rather
    // than template ones needs these rather than the padded bounds.

    public int templateSizeX()
    {
        return this.sizeX;
    }

    public int templateSizeY()
    {
        return this.sizeY;
    }

    public int templateSizeZ()
    {
        return this.sizeZ;
    }

    // endregion

    // region BlockVolume

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
        if (!inTemplate(x, y, z))
        {
            return null;
        }

        return this.blocks[index(x, y, z)];
    }

    private boolean inTemplate(int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < this.sizeX && y < this.sizeY && z < this.sizeZ;
    }

    private int index(int x, int y, int z)
    {
        return (x * this.sizeY + y) * this.sizeZ + z;
    }

    // endregion

    // region building on the island

    public int style()
    {
        return this.style;
    }

    public int sizeX()
    {
        return this.sizeX;
    }

    public int sizeY()
    {
        return this.sizeY;
    }

    public int sizeZ()
    {
        return this.sizeZ;
    }

    /** The template's own footprint in columns, whether or not every column holds anything. */
    public int footprintArea()
    {
        return this.sizeX * this.sizeZ;
    }

    /** The highest block in this column that stops the fill, or {@link Integer#MIN_VALUE} for none. */
    public int highestSolidY(int x, int z)
    {
        for (int y = this.sizeY - 1; y >= 0; y--)
        {
            if (passabilityAt(x, y, z).stopsFill())
            {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    /** Set one cell. {@link TestBlocks#AIR} clears it. Outside the template, silently ignored. */
    public void place(int x, int y, int z, TestBlocks.TestBlock block)
    {
        if (inTemplate(x, y, z))
        {
            this.blocks[index(x, y, z)] = block.passability() == Passability.EMPTY ? null : block;
        }
    }

    /**
     * Drop one of the ASCII layouts onto the island with its own {@code (0, 0, 0)} at the given
     * origin. Air in the layout clears whatever the island had there - a player builds on ground
     * they have cleared, and so does this.
     */
    public void place(GridVolume build, int originX, int originY, int originZ)
    {
        final RegionBounds box = build.bounds();

        // GridVolume pads its layout with one cell of air on every side; only the layout itself
        // is placed, so the island around it is left as it was
        for (int x = box.minX() + 1; x < box.maxX(); x++)
        {
            for (int y = box.minY() + 1; y < box.maxY(); y++)
            {
                for (int z = box.minZ() + 1; z < box.maxZ(); z++)
                {
                    BlockSignature signature = build.signatureAt(x, y, z);
                    TestBlocks.TestBlock block = signature == null
                            ? TestBlocks.AIR
                            : (TestBlocks.TestBlock) signature;

                    place(originX + x, originY + y, originZ + z, block);
                }
            }
        }
    }

    // endregion

    @Override
    public String toString()
    {
        return "soul_island" + this.style + " " + this.sizeX + "x" + this.sizeY + "x" + this.sizeZ;
    }
}
