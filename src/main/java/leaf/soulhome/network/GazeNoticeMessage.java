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
 * "Something is looking into your soul" (#189): the prickle, and - for a target who is home - how
 * long the soul stays watched. See {@code GazeNotices} for what was chosen and why. Display only;
 * the line of chat that goes with it is sent separately, so a client that drops this still hears
 * about it.
 */
public class GazeNoticeMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<GazeNoticeMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("gaze_notice"));

    public static final GazeNoticeMessage INVALID = new GazeNoticeMessage(0d, 0, false);

    /** The last watcher has gone - clear anything still lingering. */
    public static final GazeNoticeMessage CLEAR = new GazeNoticeMessage(0d, 0, false);

    public static final Codec<GazeNoticeMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.DOUBLE.fieldOf("obviousness").forGetter(GazeNoticeMessage::getObviousness),
                            Codec.INT.fieldOf("linger").forGetter(GazeNoticeMessage::getLingerTicks),
                            Codec.BOOL.fieldOf("prickle").forGetter(GazeNoticeMessage::isPrickle))
                    .apply(instance, GazeNoticeMessage::new));

    private final double obviousness;
    private final int lingerTicks;
    private final boolean prickle;

    public GazeNoticeMessage(double obviousness, int lingerTicks, boolean prickle)
    {
        this.obviousness = Math.max(0d, Math.min(1d, obviousness));
        this.lingerTicks = Math.max(0, lingerTicks);
        this.prickle = prickle;
    }

    public double getObviousness()
    {
        return this.obviousness;
    }

    public int getLingerTicks()
    {
        return this.lingerTicks;
    }

    public boolean isPrickle()
    {
        return this.prickle;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() -> ClientPacketHandler.gazeNotice(this.obviousness, this.lingerTicks, this.prickle));
    }
}
