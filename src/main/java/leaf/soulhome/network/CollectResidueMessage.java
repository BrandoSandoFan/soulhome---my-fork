/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.SoulAnchorService;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "Convert my soul's residue into essence" (#82/#83) - the fourth client-to-server message in the
 * mod, and written with the same suspicion as the three before it.
 *
 * <p>It carries nothing but the asking. Which soulhome is converted, whether it is the sender's own,
 * whether they are standing at its anchor and how much they get are all decided by
 * {@link SoulAnchorService#collectResidue}, because every one of those is a fact about the server's
 * world rather than a claim a client gets to make.
 *
 * <p>The boolean below exists only because a packet has to encode to <i>something</i>: the channel
 * writes each message as a compound tag, and a codec with no fields at all does not produce one.
 * Nothing reads it.
 */
public class CollectResidueMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<CollectResidueMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("collect_residue"));

    public static final CollectResidueMessage INVALID = new CollectResidueMessage(true);

    public static final Codec<CollectResidueMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Codec.BOOL.optionalFieldOf("collect", true)
                            .forGetter(CollectResidueMessage::isCollect))
                    .apply(instance, CollectResidueMessage::new));

    private final boolean collect;

    public CollectResidueMessage()
    {
        this(true);
    }

    public CollectResidueMessage(boolean collect)
    {
        this.collect = collect;
    }

    public boolean isCollect()
    {
        return this.collect;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            final ServerPlayer sender = context.player() instanceof ServerPlayer server ? server : null;

            if (sender != null)
            {
                SoulAnchorService.collectResidue(sender);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
