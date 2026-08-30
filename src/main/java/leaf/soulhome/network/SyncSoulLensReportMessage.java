/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.feedback.LensRegionReport;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sends one player the full breakdown behind the regions the Soul Lens found, so their client can
 * open the lens screen (#50) rather than print a wall of chat.
 *
 * <p>Sent alongside {@link SyncSoulRegionsMessage} - that message stays exactly as it is, carrying
 * the box and headline the world outlines and corner labels need; this one carries the reasoning
 * a screen needs and a floating label does not.
 *
 * <p>Display only: nothing a client does with this changes what anyone is awarded.
 */
public class SyncSoulLensReportMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulLensReportMessage INVALID = new SyncSoulLensReportMessage("", List.of(), -1);

    public static final Codec<SyncSoulLensReportMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("dimension")
                                    .forGetter(SyncSoulLensReportMessage::getDimension),
                            LensRegionReport.CODEC.listOf().fieldOf("regions")
                                    .forGetter(SyncSoulLensReportMessage::getRegions),
                            Codec.INT.optionalFieldOf("standing_in", -1)
                                    .forGetter(SyncSoulLensReportMessage::getStandingIn))
                    .apply(instance, SyncSoulLensReportMessage::new));

    private final String dimension;
    private final List<LensRegionReport> regions;
    private final int standingIn;

    public SyncSoulLensReportMessage(String dimension, List<LensRegionReport> regions, int standingIn)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.regions = List.copyOf(regions);
        this.standingIn = standingIn;
    }

    public String getDimension()
    {
        return this.dimension;
    }

    public List<LensRegionReport> getRegions()
    {
        return this.regions;
    }

    /** Index into {@link #getRegions()} of the region the player was standing in, or {@code -1}. */
    public int getStandingIn()
    {
        return this.standingIn;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientLensReport.accept(this.regions, this.standingIn));
    }

    /**
     * The client's copy of the last report it was shown, plus a generation counter so the
     * client-only screen opener can tell a fresh arrival from data it has already reacted to.
     *
     * <p>A plain static holder rather than anything client-only, so a dedicated server loading this
     * class never touches a missing type - the same reasoning as
     * {@link SyncSoulRegionsMessage.ClientSoulRegions}. The screen that reacts to this is the
     * client-only part.
     */
    public static final class ClientLensReport
    {
        private static volatile List<LensRegionReport> regions = List.of();
        private static volatile int standingIn = -1;
        private static volatile long generation;
        private static volatile long consumedGeneration;

        private ClientLensReport()
        {
        }

        static void accept(List<LensRegionReport> regions, int standingIn)
        {
            ClientLensReport.regions = regions;
            ClientLensReport.standingIn = standingIn;
            ClientLensReport.generation++;
        }

        /**
         * The regions to explain, exactly once per arrival - {@code null} on every tick that is not
         * the one right after a fresh report landed, so a client that is already looking at the
         * screen is not re-opened over and over while it holds the lens.
         */
        public static List<LensRegionReport> consumeIfNew()
        {
            if (consumedGeneration == generation)
            {
                return null;
            }

            consumedGeneration = generation;
            return regions;
        }

        public static int standingIn()
        {
            return standingIn;
        }
    }
}
