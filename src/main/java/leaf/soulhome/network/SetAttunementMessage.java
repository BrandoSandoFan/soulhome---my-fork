/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.AttunementService;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "Bind or unbind this room" (#154) - the third client-to-server message in the mod, and written
 * with the same suspicion as the two before it.
 *
 * <p>The room id below is an integer a modified client picked. Nothing here makes it safe;
 * {@link AttunementService#set} does, by checking it against the rooms the player's own soulhome
 * actually holds and against the slots that soulhome actually has. A forged id changes nothing, and
 * a forged id for <i>somebody else's</i> room changes nothing either, because the only soulhome
 * this can reach is the sender's own - the message carries no dimension.
 */
public class SetAttunementMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SetAttunementMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("set_attunement"));

    public static final SetAttunementMessage INVALID = new SetAttunementMessage(0, false);

    public static final Codec<SetAttunementMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.INT.optionalFieldOf("room", 0).forGetter(SetAttunementMessage::getRoomId),
                            Codec.BOOL.optionalFieldOf("bind", false).forGetter(SetAttunementMessage::isBind))
                    .apply(instance, SetAttunementMessage::new));

    private final int roomId;
    private final boolean bind;

    public SetAttunementMessage(int roomId, boolean bind)
    {
        this.roomId = roomId;
        this.bind = bind;
    }

    public int getRoomId()
    {
        return this.roomId;
    }

    public boolean isBind()
    {
        return this.bind;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            final ServerPlayer sender = context.player() instanceof ServerPlayer server ? server : null;

            if (sender != null)
            {
                AttunementService.set(sender, this.roomId, this.bind);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
