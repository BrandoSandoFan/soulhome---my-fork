/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.feedback.AscensionReport;
import leaf.soulhome.feedback.AttunementReport;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;

/**
 * Everything the Soul Anchor's screen shows, in one packet: what the soul is carrying (#154) and
 * what its climb still needs (#83).
 *
 * <p>The same relationship {@link SyncSoulLensBuffsMessage} has to {@link SyncSoulBuffsMessage}:
 * the always-on sync carries what a player is currently carrying, and this carries the fuller
 * picture, sent only when they ask for it by clicking the anchor.
 *
 * <p><b>One packet rather than two.</b> The two halves are read at the same moment, by the same
 * screen, and a screen opened on one of them would draw half of itself from whatever the other half
 * happened to be last time. Converting residue changes the essence the climb reports, and binding a
 * room changes nothing about it - but both arrive as the whole anchor, so neither can leave the
 * other stale.
 *
 * <p>Sent again after every accepted binding and every conversion, so the screen redraws from what
 * the server actually did rather than from what the click was expected to do. That is why there is
 * no client-side prediction here at all: the server is the only thing that knows whether a slot was
 * free, or how much residue was banked.
 */
public class SyncSoulAnchorMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulAnchorMessage INVALID =
            new SyncSoulAnchorMessage(AscensionReport.EMPTY, AttunementReport.EMPTY);

    public static final Codec<SyncSoulAnchorMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            AscensionReport.CODEC.optionalFieldOf("ascension", AscensionReport.EMPTY)
                                    .forGetter(SyncSoulAnchorMessage::getAscension),
                            AttunementReport.CODEC.optionalFieldOf("attunement", AttunementReport.EMPTY)
                                    .forGetter(SyncSoulAnchorMessage::getAttunement))
                    .apply(instance, SyncSoulAnchorMessage::new));

    private final AscensionReport ascension;
    private final AttunementReport attunement;

    public SyncSoulAnchorMessage(AscensionReport ascension, AttunementReport attunement)
    {
        this.ascension = ascension;
        this.attunement = attunement;
    }

    public AscensionReport getAscension()
    {
        return this.ascension;
    }

    public AttunementReport getAttunement()
    {
        return this.attunement;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientAnchor.accept(new Anchor(this.ascension, this.attunement)));
    }

    /** The anchor as the client last heard it. */
    public record Anchor(AscensionReport ascension, AttunementReport attunement)
    {
        public static final Anchor EMPTY = new Anchor(AscensionReport.EMPTY, AttunementReport.EMPTY);
    }

    /**
     * See {@link SyncSoulLensBuffsMessage.ClientLensBuffs} - the same shape, with one addition.
     *
     * <p>A report that arrives while the anchor screen is already open <i>updates</i> it rather than
     * opening a second one, so a binding does not close and reopen the thing the player is clicking
     * in. {@link #latest()} is that path; {@link #consumeIfNew()} is the one that opens it.
     */
    public static final class ClientAnchor
    {
        private static volatile Anchor anchor = Anchor.EMPTY;
        private static volatile long generation;
        private static volatile long consumedGeneration;

        private ClientAnchor()
        {
        }

        static void accept(Anchor state)
        {
            ClientAnchor.anchor = state;
            ClientAnchor.generation++;
        }

        public static Anchor consumeIfNew()
        {
            if (consumedGeneration == generation)
            {
                return null;
            }

            consumedGeneration = generation;
            return anchor;
        }

        /** The most recent report, whether or not it has been consumed - for a screen already open. */
        public static Anchor latest()
        {
            return anchor;
        }

        public static long generation()
        {
            return generation;
        }
    }
}
