/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.SoulFeedback;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;

/**
 * "Something of ours is playing - stay out of the way for this long" (#212).
 *
 * <p>#166's rule is that the ambience must never make it harder to hear the ascension hum, an
 * ability firing or a soul key. The obvious way to keep it is a client-side {@code PlaySoundEvent}
 * hook, and it does not work here: every sound this mod plays is a vanilla event with a meaning of
 * its own, so matching {@code BEACON_AMBIENT} would duck for somebody else's beacon, and matching
 * {@code SoundSource.PLAYERS} inside a soul would duck for footsteps and for every block placed -
 * which is precisely what the owner of #212 asked not to happen.
 *
 * <p>So the mark travels with the sound, from the call site that knows what it is. It costs one
 * tiny packet at each of a handful of moments - an ability, a key, the two ends of a ritual - and
 * nothing at all the rest of the time. See {@code SoulSounds}.
 *
 * <p>Display only, like the rest of the ambience syncs. A client that drops this hears its ambience
 * over the hum, which is the fault this fixes and not a new one.
 */
public class AmbienceHoldMessage implements Consumer<NetworkEvent.Context>
{
    public static final AmbienceHoldMessage INVALID = new AmbienceHoldMessage("", 0);

    public static final Codec<AmbienceHoldMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("kind").forGetter(AmbienceHoldMessage::getKind),
                            Codec.INT.fieldOf("ticks").forGetter(AmbienceHoldMessage::getTicks))
                    .apply(instance, AmbienceHoldMessage::new));

    private final String kind;
    private final int ticks;

    public AmbienceHoldMessage(String kind, int ticks)
    {
        this.kind = kind == null ? "" : kind;
        this.ticks = Math.max(0, ticks);
    }

    public static AmbienceHoldMessage of(SoulFeedback feedback, int ticks)
    {
        return new AmbienceHoldMessage(feedback.id(), ticks);
    }

    public String getKind()
    {
        return this.kind;
    }

    public int getTicks()
    {
        return this.ticks;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        if (this.ticks <= 0 || this.kind.isEmpty())
        {
            return;
        }

        context.enqueueWork(() -> ClientPacketHandler.ambienceHold(SoulFeedback.byId(this.kind), this.ticks));
    }
}
