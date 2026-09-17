/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulTrait;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
public class SyncSoulAmbienceMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncSoulAmbienceMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_soul_ambience"));

    public static final SyncSoulAmbienceMessage INVALID =
            new SyncSoulAmbienceMessage("", 0, 0, 1, 0, Map.of(), List.of());

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
                                    .forGetter(SyncSoulAmbienceMessage::getPulls),
                            // optional, so a client a version behind reads everything else and simply
                            // loses the direction a one-shot comes from (#215) rather than the sky
                            AmbientRoom.CODEC.listOf().optionalFieldOf("rooms", List.of())
                                    .forGetter(SyncSoulAmbienceMessage::getRooms))
                    .apply(instance, SyncSoulAmbienceMessage::new));

    private final String dimension;
    private final int rank;
    private final int maxRank;
    private final int vergeHalfExtent;
    private final int ceilingY;
    private final Map<String, Double> pulls;
    private final List<AmbientRoom> rooms;

    public SyncSoulAmbienceMessage(
            String dimension, int rank, int maxRank, int vergeHalfExtent, int ceilingY,
            Map<String, Double> pulls, List<AmbientRoom> rooms)
    {
        this.dimension = dimension == null ? "" : dimension;
        this.rank = rank;
        this.maxRank = maxRank;
        this.vergeHalfExtent = Math.max(1, vergeHalfExtent);
        this.ceilingY = ceilingY;
        this.pulls = pulls == null ? Map.of() : Map.copyOf(pulls);
        this.rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    /**
     * The same message, built from a blend the server has already computed.
     *
     * <p>{@code awarded} is carried for #215 only, and only the part of it that can ever matter: a
     * room whose archetype declares no {@code character} can never be the room a voice comes from,
     * and a room with no footprint (a save written before #152) has nowhere to come from. Both are
     * dropped here rather than on the client, so what goes on the wire is a handful of boxes rather
     * than every region a soulhome holds.
     */
    public static SyncSoulAmbienceMessage of(
            String dimension, int rank, int maxRank, int vergeHalfExtent, int ceilingY,
            SoulCharacter character, List<AwardedRoom> awarded, Map<String, ArchetypeDefinition> archetypes)
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

        List<AmbientRoom> rooms = new ArrayList<>();

        if (awarded != null && archetypes != null)
        {
            for (AwardedRoom room : awarded)
            {
                final ArchetypeDefinition archetype = archetypes.get(room.archetypeId());
                final RegionBounds bounds = room.footprint();

                if (archetype == null || archetype.characterPulls().isEmpty() || bounds == null)
                {
                    continue;
                }

                rooms.add(new AmbientRoom(
                        room.archetypeId(),
                        bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ()));
            }
        }

        return new SyncSoulAmbienceMessage(dimension, rank, maxRank, vergeHalfExtent, ceilingY, pulls, rooms);
    }

    /**
     * One classified room's box, so an ambient one-shot can come from the direction of the room that
     * earned it (#215).
     *
     * <p>Display only, like everything else in this message. Nothing a client does with a box
     * changes what anybody is awarded, and a client that never receives one falls back to the random
     * compass angle the one-shots have always used.
     */
    public record AmbientRoom(
            String archetypeId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        public static final Codec<AmbientRoom> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("archetype").forGetter(AmbientRoom::archetypeId),
                        Codec.INT.fieldOf("min_x").forGetter(AmbientRoom::minX),
                        Codec.INT.fieldOf("min_y").forGetter(AmbientRoom::minY),
                        Codec.INT.fieldOf("min_z").forGetter(AmbientRoom::minZ),
                        Codec.INT.fieldOf("max_x").forGetter(AmbientRoom::maxX),
                        Codec.INT.fieldOf("max_y").forGetter(AmbientRoom::maxY),
                        Codec.INT.fieldOf("max_z").forGetter(AmbientRoom::maxZ))
                .apply(instance, AmbientRoom::new));
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

    public List<AmbientRoom> getRooms()
    {
        return this.rooms;
    }

    /** The rooms this soul holds, as the one-shot placement maths reads them (#215). */
    public List<SoulAmbience.VoiceRoom> voiceRooms()
    {
        List<SoulAmbience.VoiceRoom> voiceRooms = new ArrayList<>(this.rooms.size());

        for (AmbientRoom room : this.rooms)
        {
            voiceRooms.add(new SoulAmbience.VoiceRoom(room.archetypeId(), new SoulAmbience.RoomBox(
                    room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ())));
        }

        return voiceRooms;
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
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    @Override
    public void accept(IPayloadContext context)
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
