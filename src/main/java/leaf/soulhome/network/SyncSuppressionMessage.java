/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How one observer perceives one other player's ascension, as suppression (#188).
 *
 * <p>Rank lives per soul in {@code SoulHomeBuffData}, not on the player, and
 * {@code SyncSoulBuffsMessage} only ever reaches its own player - so this is the first path by which
 * one client learns anything about another's progression. It is gated on the server, in
 * {@code SuppressionService}: a client whose player has no awarded room is never sent it, because
 * a gate the client enforced would be a gate a modified client simply ignores.
 *
 * <p>The server works out the whole {@code SuppressionSettings.Signature} - their rank for the
 * amount, this observer's for the legibility - and sends that rather than two ranks, so the curves
 * a server has configured are the curves every client draws, and a client never holds the raw
 * rank of anyone but its own player. {@code rings} of zero means "nothing to draw", and is how a
 * previously perceived player is cleared.
 *
 * <p>Carries the entity id rather than the UUID because that is what the client's renderer looks
 * entities up by, and the observer is only ever told about a player it is already tracking.
 */
public class SyncSuppressionMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncSuppressionMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_suppression"));

    public static final SyncSuppressionMessage INVALID = new SyncSuppressionMessage(-1, 0, 0f, 0f, 0f, 0f, 0f, false, false);

    public static final Codec<SyncSuppressionMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.INT.fieldOf("entity").forGetter(SyncSuppressionMessage::getEntityId),
                            Codec.INT.fieldOf("rings").forGetter(SyncSuppressionMessage::getRings),
                            Codec.FLOAT.fieldOf("radius").forGetter(SyncSuppressionMessage::getRadius),
                            Codec.FLOAT.fieldOf("strength").forGetter(SyncSuppressionMessage::getStrength),
                            Codec.FLOAT.fieldOf("legibility").forGetter(SyncSuppressionMessage::getLegibility),
                            Codec.FLOAT.fieldOf("displacement").forGetter(SyncSuppressionMessage::getDisplacement),
                            Codec.FLOAT.fieldOf("range").forGetter(SyncSuppressionMessage::getRange),
                            Codec.BOOL.fieldOf("distortion").forGetter(SyncSuppressionMessage::isDistortion),
                            Codec.BOOL.fieldOf("audio").forGetter(SyncSuppressionMessage::isAudio))
                    .apply(instance, SyncSuppressionMessage::new));

    private final int entityId;
    private final int rings;
    private final float radius;
    private final float strength;
    private final float legibility;
    private final float displacement;
    private final float range;
    private final boolean distortion;
    private final boolean audio;

    public SyncSuppressionMessage(
            int entityId, int rings, float radius, float strength, float legibility, float displacement,
            float range, boolean distortion, boolean audio)
    {
        this.entityId = entityId;
        this.rings = Math.max(0, rings);
        this.radius = radius;
        this.strength = strength;
        this.legibility = legibility;
        this.displacement = displacement;
        this.range = range;
        this.distortion = distortion;
        this.audio = audio;
    }

    /** Nothing to perceive around this player - clears whatever the observer last drew for them. */
    public static SyncSuppressionMessage cleared(int entityId)
    {
        return new SyncSuppressionMessage(entityId, 0, 0f, 0f, 0f, 0f, 0f, false, false);
    }

    public int getEntityId()
    {
        return this.entityId;
    }

    public int getRings()
    {
        return this.rings;
    }

    public float getRadius()
    {
        return this.radius;
    }

    public float getStrength()
    {
        return this.strength;
    }

    public float getLegibility()
    {
        return this.legibility;
    }

    public float getDisplacement()
    {
        return this.displacement;
    }

    public float getRange()
    {
        return this.range;
    }

    public boolean isDistortion()
    {
        return this.distortion;
    }

    public boolean isAudio()
    {
        return this.audio;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        if (this.entityId < 0)
        {
            return;
        }

        context.enqueueWork(() -> ClientPacketHandler.syncSuppression(this));
    }
}
