/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.feedback.AttunementReport;
import leaf.soulhome.feedback.LensBuffReport;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sends one player their buffs and where each came from, so the Soul Lens can open a screen for
 * them (#50) instead of printing chat when used outside a soul.
 *
 * <p>{@link SyncSoulBuffsMessage} keeps carrying just the magnitudes, for the tooltip that is
 * always up to date without asking. This is the "why" behind those numbers, sent only when the
 * lens is used - the same relationship {@link SyncSoulLensReportMessage} has to
 * {@link SyncSoulRegionsMessage}.
 */
public class SyncSoulLensBuffsMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulLensBuffsMessage INVALID =
            new SyncSoulLensBuffsMessage(List.of(), AttunementReport.EMPTY);

    public static final Codec<SyncSoulLensBuffsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            LensBuffReport.CODEC.listOf().fieldOf("buffs")
                                    .forGetter(SyncSoulLensBuffsMessage::getBuffs),
                            // optional so that a payload without it - one written before this field
                            // existed - still decodes, rather than failing the whole message and
                            // leaving the lens with a blank screen and no explanation
                            AttunementReport.CODEC.optionalFieldOf("attunement", AttunementReport.EMPTY)
                                    .forGetter(SyncSoulLensBuffsMessage::getAttunement))
                    .apply(instance, SyncSoulLensBuffsMessage::new));

    private final List<LensBuffReport> buffs;
    private final AttunementReport attunement;

    public SyncSoulLensBuffsMessage(List<LensBuffReport> buffs, AttunementReport attunement)
    {
        this.buffs = List.copyOf(buffs);
        this.attunement = attunement;
    }

    public List<LensBuffReport> getBuffs()
    {
        return this.buffs;
    }

    /**
     * What this soulhome is carrying its buffs <i>instead of</i> (#153). The lens is the only
     * surface a player can reach while out in the world, which is where realising you would rather
     * have the aquarium attuned actually happens - so it reports the trade, even though binding
     * itself stays at the anchor.
     */
    public AttunementReport getAttunement()
    {
        return this.attunement;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientLensBuffs.accept(this.buffs, this.attunement));
    }

    /** See {@link SyncSoulLensReportMessage.ClientLensReport} - the same shape, one field wide. */
    public static final class ClientLensBuffs
    {
        private static volatile List<LensBuffReport> buffs = List.of();
        private static volatile AttunementReport attunement = AttunementReport.EMPTY;
        private static volatile long generation;
        private static volatile long consumedGeneration;

        private ClientLensBuffs()
        {
        }

        static void accept(List<LensBuffReport> buffs, AttunementReport attunement)
        {
            ClientLensBuffs.buffs = buffs;
            ClientLensBuffs.attunement = attunement;
            ClientLensBuffs.generation++;
        }

        public static List<LensBuffReport> consumeIfNew()
        {
            if (consumedGeneration == generation)
            {
                return null;
            }

            consumedGeneration = generation;
            return buffs;
        }

        /** The attunement that arrived with the buffs last consumed. */
        public static AttunementReport attunement()
        {
            return attunement;
        }
    }
}
