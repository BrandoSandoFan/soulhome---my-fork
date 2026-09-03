/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.buffs.SoulAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;

/**
 * "Move my selection along one" (#87). A player with five ability rooms needs to choose between
 * them, and five binds is worse than one bind and a cycle.
 *
 * <p>Carries only a direction. The selection itself lives on the server - a client that could name
 * which ability to select would be a client that could select one it does not own, and the whole
 * arrangement here is that it cannot.
 */
public class CycleSoulAbilityMessage implements Consumer<NetworkEvent.Context>
{
    public static final CycleSoulAbilityMessage INVALID = new CycleSoulAbilityMessage(true);

    public static final Codec<CycleSoulAbilityMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Codec.BOOL.optionalFieldOf("forward", true)
                            .forGetter(CycleSoulAbilityMessage::isForward))
                    .apply(instance, CycleSoulAbilityMessage::new));

    private final boolean forward;

    public CycleSoulAbilityMessage(boolean forward)
    {
        this.forward = forward;
    }

    public boolean isForward()
    {
        return this.forward;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() ->
        {
            final ServerPlayer sender = context.getSender();

            if (sender != null)
            {
                SoulAbilities.cycle(sender, this.forward);
            }
        });
    }
}
