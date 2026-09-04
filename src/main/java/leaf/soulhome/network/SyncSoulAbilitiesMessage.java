/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.buffs.ClientSoulAbilities;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Tells a client what its player's active abilities (#87) look like right now: which one is
 * selected, how many charges each has banked, and how far along each recharge is.
 *
 * <p>Sent only when something the player would see changes - a charge landing or being spent, the
 * selection moving - rather than every tick. Between those the client runs its own copy of the
 * clock down, which is why {@link State} carries the cooldown length as well as the remaining
 * ticks: with both, a HUD arc animates smoothly off one packet a cooldown instead of twenty a
 * second.
 *
 * <p>Never authoritative, exactly like {@link SyncSoulBuffsMessage}. The server has already decided
 * whether a press was allowed by the time this arrives.
 */
public class SyncSoulAbilitiesMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulAbilitiesMessage INVALID = new SyncSoulAbilitiesMessage("", Map.of());

    public static final Codec<SyncSoulAbilitiesMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.optionalFieldOf("selected", "")
                                    .forGetter(SyncSoulAbilitiesMessage::getSelected),
                            Codec.unboundedMap(Codec.STRING, State.CODEC).fieldOf("abilities")
                                    .forGetter(SyncSoulAbilitiesMessage::getStates))
                    .apply(instance, SyncSoulAbilitiesMessage::new));

    private final String selected;
    private final Map<String, State> states;

    public SyncSoulAbilitiesMessage(String selected, Map<String, State> states)
    {
        this.selected = selected == null ? "" : selected;
        this.states = states == null ? Map.of() : Map.copyOf(states);
    }

    public String getSelected()
    {
        return this.selected;
    }

    public Map<String, State> getStates()
    {
        return this.states;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientSoulAbilities.accept(this.selected, this.states));
    }

    /**
     * One ability's state as the client needs to draw it.
     *
     * @param charges         banked uses
     * @param ticksToNext     ticks until the next charge lands, 0 when nothing is pending
     * @param maxCharges      the ceiling, so the HUD can draw empty pips as well as full ones
     * @param cooldownTicks   how long a whole recharge takes, so the client can animate between packets
     */
    public record State(int charges, int ticksToNext, int maxCharges, int cooldownTicks)
    {
        public static final Codec<State> CODEC =
                RecordCodecBuilder.create(instance -> instance
                        .group(
                                Codec.INT.fieldOf("charges").forGetter(State::charges),
                                Codec.INT.fieldOf("ticks_to_next").forGetter(State::ticksToNext),
                                Codec.INT.fieldOf("max_charges").forGetter(State::maxCharges),
                                Codec.INT.fieldOf("cooldown_ticks").forGetter(State::cooldownTicks))
                        .apply(instance, State::new));
    }
}
