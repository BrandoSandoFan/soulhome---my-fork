/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.SoulBuffSet;

import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Tells a client what buffs its player currently has.
 *
 * <p>Display only. Magnitudes are computed and applied server-side; this copy exists so the
 * feedback work can render "you have +20% XP gain from your library" without asking the server
 * every frame.
 */
public class SyncSoulBuffsMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulBuffsMessage INVALID = new SyncSoulBuffsMessage(SoulBuffSet.empty());

    public static final Codec<SyncSoulBuffsMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                            .fieldOf("buffs")
                            .forGetter(message -> message.getBuffs().asMap()))
                    .apply(instance, magnitudes -> new SyncSoulBuffsMessage(SoulBuffSet.of(magnitudes))));

    private final SoulBuffSet buffs;

    public SyncSoulBuffsMessage(SoulBuffSet buffs)
    {
        this.buffs = buffs == null ? SoulBuffSet.empty() : buffs;
    }

    public SoulBuffSet getBuffs()
    {
        return this.buffs;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientSoulBuffs.accept(this.buffs));
    }

    /**
     * The client's view of its own buffs.
     *
     * <p>Deliberately a plain static holder rather than a client capability: nothing here needs a
     * client-only class, so there is no dist hazard, and there is exactly one local player.
     */
    public static final class ClientSoulBuffs
    {
        private static volatile SoulBuffSet current = SoulBuffSet.empty();

        private ClientSoulBuffs()
        {
        }

        static void accept(SoulBuffSet buffs)
        {
            current = buffs;
        }

        public static SoulBuffSet get()
        {
            return current;
        }
    }
}
