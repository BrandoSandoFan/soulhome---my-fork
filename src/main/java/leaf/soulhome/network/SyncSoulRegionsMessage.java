/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.feedback.RegionHighlight;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sends one player the regions their soulhome scan found, so their client can outline them.
 *
 * <p>Sent only in response to the Soul Lens being used - never on a timer and never to everyone.
 * The boxes are a debugging aid a player asked for, and a soulhome can hold dozens of regions.
 *
 * <p>Display only, like {@link SyncSoulBuffsMessage}: nothing a client does with these changes what
 * anyone is awarded.
 */
public class SyncSoulRegionsMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulRegionsMessage INVALID = new SyncSoulRegionsMessage("", List.of());

    public static final Codec<SyncSoulRegionsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("dimension")
                                    .forGetter(SyncSoulRegionsMessage::getDimension),
                            RegionHighlight.CODEC.listOf().fieldOf("regions")
                                    .forGetter(SyncSoulRegionsMessage::getRegions))
                    .apply(instance, SyncSoulRegionsMessage::new));

    private final String dimension;
    private final List<RegionHighlight> regions;

    public SyncSoulRegionsMessage(String dimension, List<RegionHighlight> regions)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.regions = List.copyOf(regions);
    }

    public String getDimension()
    {
        return this.dimension;
    }

    public List<RegionHighlight> getRegions()
    {
        return this.regions;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientSoulRegions.accept(this.dimension, this.regions));
    }

    /**
     * The client's copy of the last regions it was shown.
     *
     * <p>A plain static holder rather than anything client-only, so a dedicated server loading this
     * class never touches a missing type. The renderer that reads it is the client-only part.
     */
    public static final class ClientSoulRegions
    {
        /** How long the outlines stay up after the lens is used. */
        public static final long LIFETIME_MILLIS = 30_000L;

        private static volatile String dimension = "";
        private static volatile List<RegionHighlight> regions = List.of();
        private static volatile long shownAtMillis;

        private ClientSoulRegions()
        {
        }

        static void accept(String dimension, List<RegionHighlight> regions)
        {
            ClientSoulRegions.dimension = dimension;
            ClientSoulRegions.regions = regions;
            ClientSoulRegions.shownAtMillis = System.currentTimeMillis();
        }

        /**
         * The regions to draw in this dimension, or nothing once they have expired or the player
         * has moved to a different soul than the one they were shown.
         */
        public static List<RegionHighlight> get(String currentDimension)
        {
            if (regions.isEmpty() || !dimension.equals(currentDimension))
            {
                return List.of();
            }

            return System.currentTimeMillis() - shownAtMillis > LIFETIME_MILLIS ? List.of() : regions;
        }
    }
}
