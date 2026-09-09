/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.buffs.ClientSoulBuffs;
import leaf.soulhome.structures.core.SoulBuffSet;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Tells a client what buffs its player currently has.
 *
 * <p>Never authoritative. Magnitudes are computed and clamped server-side; this copy exists so a
 * tooltip can render "you have +20% XP gain from your library" without asking the server every
 * frame, and so that an effect the client has to predict - block breaking - has a magnitude to
 * predict with. See {@link ClientSoulBuffs}.
 */
public class SyncSoulBuffsMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncSoulBuffsMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_soul_buffs"));

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
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() -> ClientSoulBuffs.accept(this.buffs));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
