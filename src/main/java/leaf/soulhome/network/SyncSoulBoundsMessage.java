/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

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
 *
 * <p>{@code rank} rides along too (#84): the client needs it to label the firmament with the same
 * rank the server actually used to compute this box, rather than guessing from the box's size.
 *
 * <p>{@code groundReach} and {@code growing} are terrain growth (#158/#162). Ground and walls are
 * deliberately different numbers, and the lens is where a player already is when they want to know
 * how much room they have - so it reports both rather than only the box. Sent on the same occasions
 * as everything else here, plus once when a growth job starts and once when it finishes, which is
 * every moment either of these two can change.
 */
public class SyncSoulBoundsMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncSoulBoundsMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_soul_bounds"));

    public static final SyncSoulBoundsMessage INVALID =
            new SyncSoulBoundsMessage("", 0, 0, 1, 1, List.of(), 0, false);

    public static final Codec<SyncSoulBoundsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("dimension").forGetter(SyncSoulBoundsMessage::getDimension),
                            Codec.INT.fieldOf("rank").forGetter(SyncSoulBoundsMessage::getRank),
                            Codec.INT.fieldOf("floor_y").forGetter(SyncSoulBoundsMessage::getFloorY),
                            Codec.INT.fieldOf("ceiling_y").forGetter(SyncSoulBoundsMessage::getCeilingY),
                            Codec.INT.fieldOf("verge_half_extent").forGetter(SyncSoulBoundsMessage::getVergeHalfExtent),
                            Codec.INT.listOf().fieldOf("legacy_box").forGetter(SyncSoulBoundsMessage::getLegacyBox),
                            Codec.INT.fieldOf("ground_reach").forGetter(SyncSoulBoundsMessage::getGroundReach),
                            Codec.BOOL.fieldOf("growing").forGetter(SyncSoulBoundsMessage::isGrowing))
                    .apply(instance, SyncSoulBoundsMessage::new));

    private final String dimension;
    private final int rank;
    private final int floorY;
    private final int ceilingY;
    private final int vergeHalfExtent;
    private final List<Integer> legacyBox;
    private final int groundReach;
    private final boolean growing;

    public SyncSoulBoundsMessage(
            String dimension, int rank, int floorY, int ceilingY, int vergeHalfExtent, List<Integer> legacyBox,
            int groundReach, boolean growing)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.rank = rank;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        this.vergeHalfExtent = vergeHalfExtent;
        this.legacyBox = legacyBox == null || legacyBox.size() != 6 ? List.of() : List.copyOf(legacyBox);
        this.groundReach = groundReach;
        this.growing = growing;
    }

    public int getRank()
    {
        return this.rank;
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

    /** How far this soulhome's ground actually reaches, as a half-extent - not how far it may. */
    public int getGroundReach()
    {
        return this.groundReach;
    }

    /** Whether ground is still arriving, so the lens can say so rather than reporting a half-grown reach flatly. */
    public boolean isGrowing()
    {
        return this.growing;
    }

    @Override
    public void accept(IPayloadContext context)
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

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
