/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    public static final SyncSoulLensBuffsMessage INVALID = new SyncSoulLensBuffsMessage(List.of());

    public static final Codec<SyncSoulLensBuffsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(LensBuffReport.CODEC.listOf().fieldOf("buffs")
                            .forGetter(SyncSoulLensBuffsMessage::getBuffs))
                    .apply(instance, SyncSoulLensBuffsMessage::new));

    private final List<LensBuffReport> buffs;

    public SyncSoulLensBuffsMessage(List<LensBuffReport> buffs)
    {
        this.buffs = List.copyOf(buffs);
    }

    public List<LensBuffReport> getBuffs()
    {
        return this.buffs;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientLensBuffs.accept(this.buffs));
    }

    /** See {@link SyncSoulLensReportMessage.ClientLensReport} - the same shape, one field wide. */
    public static final class ClientLensBuffs
    {
        private static volatile List<LensBuffReport> buffs = List.of();
        private static volatile long generation;
        private static volatile long consumedGeneration;

        private ClientLensBuffs()
        {
        }

        static void accept(List<LensBuffReport> buffs)
        {
            ClientLensBuffs.buffs = buffs;
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
    }
}
