/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulTrait;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What one soul looks and sounds like, sent to everyone standing in it - the Ambience epic (#163).
 *
 * <p>Sent to the <b>dimension</b> rather than to an owner, which is the one thing about this
 * message that differs from every other sync in the mod. Ambience is a property of the place, not
 * of the player: a visitor standing in somebody else's soul should see that soul's sky, not the one
 * their own soulhome would have. Their buffs, their rank and their attunement all stay their own -
 * see {@code StructureScanService#refresh} for why that distinction is drawn so carefully
 * everywhere else.
 *
 * <p>The blend itself is computed server-side, from the classified rooms the server already holds,
 * and arrives here already summed: the client is told what the soul is like, not handed the room
 * list to work it out from. That keeps the render loop's input to six numbers and means a client
 * never needs a datapack's archetypes to draw a sky.
 *
 * <p>Sent when a scan finishes, when a player arrives, and when a rank changes - which is every
 * moment any of this can be different. Not on a timer: nothing here moves on its own.
 *
 * <p>Display only, like {@link SyncSoulBuffsMessage}. Nothing a client does with this changes what
 * anyone is awarded, and a client that never receives one draws the sky the mod has always drawn.
 */
public class SyncSoulAmbienceMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSoulAmbienceMessage INVALID =
            new SyncSoulAmbienceMessage("", 0, 0, 1, 0, Map.of());

    public static final Codec<SyncSoulAmbienceMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.STRING.fieldOf("dimension")
                                    .forGetter(SyncSoulAmbienceMessage::getDimension),
                            Codec.INT.fieldOf("rank")
                                    .forGetter(SyncSoulAmbienceMessage::getRank),
                            Codec.INT.fieldOf("max_rank")
                                    .forGetter(SyncSoulAmbienceMessage::getMaxRank),
                            Codec.INT.fieldOf("verge_half_extent")
                                    .forGetter(SyncSoulAmbienceMessage::getVergeHalfExtent),
                            Codec.INT.fieldOf("ceiling_y")
                                    .forGetter(SyncSoulAmbienceMessage::getCeilingY),
                            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("pulls")
                                    .forGetter(SyncSoulAmbienceMessage::getPulls))
                    .apply(instance, SyncSoulAmbienceMessage::new));

    private final String dimension;
    private final int rank;
    private final int maxRank;
    private final int vergeHalfExtent;
    private final int ceilingY;
    private final Map<String, Double> pulls;

    public SyncSoulAmbienceMessage(
            String dimension, int rank, int maxRank, int vergeHalfExtent, int ceilingY, Map<String, Double> pulls)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.rank = rank;
        this.maxRank = maxRank;
        this.vergeHalfExtent = Math.max(1, vergeHalfExtent);
        this.ceilingY = ceilingY;
        this.pulls = pulls == null ? Map.of() : Map.copyOf(pulls);
    }

    /** The same message, built from a blend the server has already computed. */
    public static SyncSoulAmbienceMessage of(
            String dimension, int rank, int maxRank, int vergeHalfExtent, int ceilingY, SoulCharacter character)
    {
        Map<String, Double> pulls = new java.util.LinkedHashMap<>();

        for (SoulTrait trait : SoulTrait.values())
        {
            final double pull = character == null ? 0d : character.pull(trait);

            if (pull > 0d)
            {
                pulls.put(trait.id(), pull);
            }
        }

        return new SyncSoulAmbienceMessage(dimension, rank, maxRank, vergeHalfExtent, ceilingY, pulls);
    }

    public String getDimension()
    {
        return this.dimension;
    }

    public int getRank()
    {
        return this.rank;
    }

    public int getMaxRank()
    {
        return this.maxRank;
    }

    public int getVergeHalfExtent()
    {
        return this.vergeHalfExtent;
    }

    /**
     * How far out the soul reads as going, as a half-extent. The box for this rank, unless this
     * soulhome has a legacy grant (#80) that reaches further - in which case it is the further of
     * the two, because a grandfathered build standing outside today's verge is somewhere that
     * player may be, and a haze that started at the wall would tell them it is not.
     */
    public int getVergeHalfExtentOrLegacy()
    {
        return this.vergeHalfExtent;
    }

    /** The top of the box, so the firmament's drift has somewhere to be. */
    public int getCeilingY()
    {
        return this.ceilingY;
    }

    public Map<String, Double> getPulls()
    {
        return this.pulls;
    }

    /**
     * The blend this message carries. A trait id this version does not know is dropped rather than
     * refused - the same answer {@code ArchetypeDefinition} gives a datapack that names one, for
     * the same reason.
     */
    public SoulCharacter character()
    {
        Map<SoulTrait, Double> resolved = new EnumMap<>(SoulTrait.class);

        for (Map.Entry<String, Double> entry : this.pulls.entrySet())
        {
            final SoulTrait trait = SoulTrait.byId(entry.getKey());

            if (trait != null && entry.getValue() != null && entry.getValue() > 0d)
            {
                resolved.merge(trait, entry.getValue(), Double::sum);
            }
        }

        return new SoulCharacter(resolved);
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> ClientSoulAmbience.accept(this));
    }

    /**
     * The client's copy of the soul it is standing in.
     *
     * <p>A plain static holder, like {@link SyncSoulBoundsMessage.ClientSoulBounds}, so a dedicated
     * server loading this class never touches a client-only type. The renderer and the sound
     * handler that read it are the client-only parts.
     */
    public static final class ClientSoulAmbience
    {
        private static volatile SyncSoulAmbienceMessage current = INVALID;

        private ClientSoulAmbience()
        {
        }

        static void accept(SyncSoulAmbienceMessage message)
        {
            current = message;
        }

        /** What this dimension is like, or nothing if it is not a soul this client has been told about. */
        public static SyncSoulAmbienceMessage forDimension(String currentDimension)
        {
            final SyncSoulAmbienceMessage message = current;

            return message.dimension.equals(currentDimension) ? message : INVALID;
        }

        public static boolean isKnown(SyncSoulAmbienceMessage message)
        {
            return message != INVALID && !message.dimension.isEmpty();
        }
    }
}
