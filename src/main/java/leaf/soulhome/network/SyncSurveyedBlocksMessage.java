/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.client.SurveyedBlocks;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * The ore positions Surveyor's Eye (#88) found, and how long they should stay drawn.
 *
 * <p>One packet per activation, carrying packed {@code BlockPos} longs. The server does the
 * searching because the client has no business being told where to look for ore by itself, and the
 * client does the drawing because outlines through terrain are a render concern - the same division
 * the Soul Lens already runs on.
 *
 * <p>Hostile mobs are deliberately <b>not</b> in here. Vanilla's Glowing effect already outlines an
 * entity through walls, is applied server-side, and expires on its own, so a second mechanism for
 * the half of the ability that vanilla already does would be code with nothing to say for itself.
 */
public class SyncSurveyedBlocksMessage implements Consumer<NetworkEvent.Context>
{
    public static final SyncSurveyedBlocksMessage INVALID = new SyncSurveyedBlocksMessage(List.of(), 0);

    public static final Codec<SyncSurveyedBlocksMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.LONG.listOf().fieldOf("positions")
                                    .forGetter(SyncSurveyedBlocksMessage::getPositions),
                            Codec.INT.fieldOf("duration_ticks")
                                    .forGetter(SyncSurveyedBlocksMessage::getDurationTicks))
                    .apply(instance, SyncSurveyedBlocksMessage::new));

    private final List<Long> positions;
    private final int durationTicks;

    public SyncSurveyedBlocksMessage(List<Long> positions, int durationTicks)
    {
        this.positions = positions == null ? List.of() : List.copyOf(positions);
        this.durationTicks = durationTicks;
    }

    public List<Long> getPositions()
    {
        return this.positions;
    }

    public int getDurationTicks()
    {
        return this.durationTicks;
    }

    @Override
    public void accept(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> SurveyedBlocks.accept(this.positions, this.durationTicks));
    }
}
