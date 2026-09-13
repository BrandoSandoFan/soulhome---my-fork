/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.feedback.AttunementReport;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;

/**
 * Sends one player their soulhome's rooms and slots, so the Soul Anchor can open a screen on them
 * (#154).
 *
 * <p>The same relationship {@link SyncSoulLensBuffsMessage} has to {@link SyncSoulBuffsMessage}:
 * the always-on sync carries what a player is currently carrying, and this carries the fuller
 * picture, sent only when they ask for it by clicking the anchor.
 *
 * <p>Sent again after every accepted binding, so the screen redraws from what the server actually
 * did rather than from what the click was expected to do. That is why there is no client-side
 * prediction here at all: the server is the only thing that knows whether a slot was free.
 */
public class SyncAttunementMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncAttunementMessage INVALID = new SyncAttunementMessage(AttunementReport.EMPTY);

    public static final Codec<SyncAttunementMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(AttunementReport.CODEC.fieldOf("attunement")
                            .forGetter(SyncAttunementMessage::getAttunement))
                    .apply(instance, SyncAttunementMessage::new));

    private final AttunementReport attunement;

    public SyncAttunementMessage(AttunementReport attunement)
    {
        this.attunement = attunement;
    }

    public AttunementReport getAttunement()
    {
        return this.attunement;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientAttunement.accept(this.attunement));
    }

    /**
     * See {@link SyncSoulLensBuffsMessage.ClientLensBuffs} - the same shape, with one addition.
     *
     * <p>A report that arrives while the anchor screen is already open <i>updates</i> it rather than
     * opening a second one, so a binding does not close and reopen the thing the player is clicking
     * in. {@link #latest()} is that path; {@link #consumeIfNew()} is the one that opens it.
     */
    public static final class ClientAttunement
    {
        private static volatile AttunementReport attunement = AttunementReport.EMPTY;
        private static volatile long generation;
        private static volatile long consumedGeneration;

        private ClientAttunement()
        {
        }

        static void accept(AttunementReport report)
        {
            ClientAttunement.attunement = report;
            ClientAttunement.generation++;
        }

        public static AttunementReport consumeIfNew()
        {
            if (consumedGeneration == generation)
            {
                return null;
            }

            consumedGeneration = generation;
            return attunement;
        }

        /** The most recent report, whether or not it has been consumed - for a screen already open. */
        public static AttunementReport latest()
        {
            return attunement;
        }

        public static long generation()
        {
            return generation;
        }
    }
}
