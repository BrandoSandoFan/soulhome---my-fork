/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sends a player the box their soulhome is currently bounded by (#78/#79), so the client can draw
 * the firmament and the verge and the Soul Lens can report on them without either one recomputing
 * server-only config values itself.
 *
 * <p>Sent once when a player arrives in their own soulhome (see {@code StructureEvents}), not on a
 * timer - the box only changes with rank, which does not move on its own.
 *
 * <p>{@code legacyBox} is six ints {@code [minX, minY, minZ, maxX, maxY, maxZ]} when this soulhome
 * has a legacy grant (#80), or empty when it does not - {@code structures.core}'s own bounds type
 * would need a codec it has no other reason to carry, so the six numbers travel loose instead and
 * are reassembled client-side.
 */
public class SyncSoulBoundsMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulBoundsMessage INVALID = new SyncSoulBoundsMessage("", 0, 1, 1, List.of());

    public static final Codec<SyncSoulBoundsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("dimension").forGetter(SyncSoulBoundsMessage::getDimension),
                            Codec.INT.fieldOf("floor_y").forGetter(SyncSoulBoundsMessage::getFloorY),
                            Codec.INT.fieldOf("ceiling_y").forGetter(SyncSoulBoundsMessage::getCeilingY),
                            Codec.INT.fieldOf("verge_half_extent").forGetter(SyncSoulBoundsMessage::getVergeHalfExtent),
                            Codec.INT.listOf().fieldOf("legacy_box").forGetter(SyncSoulBoundsMessage::getLegacyBox))
                    .apply(instance, SyncSoulBoundsMessage::new));

    private final String dimension;
    private final int floorY;
    private final int ceilingY;
    private final int vergeHalfExtent;
    private final List<Integer> legacyBox;

    public SyncSoulBoundsMessage(String dimension, int floorY, int ceilingY, int vergeHalfExtent, List<Integer> legacyBox)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        this.vergeHalfExtent = vergeHalfExtent;
        this.legacyBox = legacyBox == null || legacyBox.size() != 6 ? List.of() : List.copyOf(legacyBox);
    }

    public String getDimension()
    {
        return this.dimension;
    }

    public int getFloorY()
    {
        return this.floorY;
    }

    public int getCeilingY()
    {
        return this.ceilingY;
    }

    public int getVergeHalfExtent()
    {
        return this.vergeHalfExtent;
    }

    public List<Integer> getLegacyBox()
    {
        return this.legacyBox;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientSoulBounds.accept(this));
    }

    /**
     * The client's copy of its own soulhome's box. A plain static holder, like
     * {@link SyncSoulRegionsMessage.ClientSoulRegions}, so a dedicated server loading this class
     * never touches a client-only type.
     */
    public static final class ClientSoulBounds
    {
        private static volatile SyncSoulBoundsMessage current = INVALID;

        private ClientSoulBounds()
        {
        }

        static void accept(SyncSoulBoundsMessage message)
        {
            current = message;
        }

        /** The current box, or nothing if this is not (or is no longer known to be) a soulhome. */
        public static SyncSoulBoundsMessage forDimension(String currentDimension)
        {
            return current.dimension.equals(currentDimension) ? current : INVALID;
        }
    }
}
