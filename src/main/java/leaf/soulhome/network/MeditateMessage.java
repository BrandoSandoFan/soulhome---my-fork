/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.MeditationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;

/**
 * "The Meditate key is held down, or it was just released" (#183). A hold rather than a press, so
 * this carries the key's live state on every transition rather than a one-shot request the way
 * {@code UseSoulAbilityMessage} does - the server is what actually counts the channel's ticks, and
 * it needs to know when to start and stop counting, not merely that the key was touched once.
 */
public class MeditateMessage implements Consumer<NetworkEvent.Context>
{
    public static final MeditateMessage INVALID = new MeditateMessage(false);

    public static final Codec<MeditateMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Codec.BOOL.optionalFieldOf("held", false)
                            .forGetter(MeditateMessage::isHeld))
                    .apply(instance, MeditateMessage::new));

    private final boolean held;

    public MeditateMessage(boolean held)
    {
        this.held = held;
    }

    public boolean isHeld()
    {
        return this.held;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() ->
        {
            final ServerPlayer sender = context.getSender();

            if (sender != null)
            {
                MeditationService.setHeld(sender, this.held);
            }
        });
    }
}
