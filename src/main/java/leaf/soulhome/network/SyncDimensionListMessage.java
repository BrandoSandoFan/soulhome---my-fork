/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class SyncDimensionListMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncDimensionListMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_dimension_list"));

    public static final SyncDimensionListMessage INVALID = new SyncDimensionListMessage(null, false);

    public static final Codec<SyncDimensionListMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Level.RESOURCE_KEY_CODEC
                                    .optionalFieldOf("id", null)
                                    .forGetter(SyncDimensionListMessage::getId),
                            Codec.BOOL.fieldOf("add")
                                    .forGetter(SyncDimensionListMessage::getAdd))
                    .apply(instance, SyncDimensionListMessage::new));


    private final ResourceKey<Level> id;
    private final boolean add;

    public SyncDimensionListMessage(ResourceKey<Level> id, boolean add)
    {
        this.id = id;
        this.add = add;
    }

    public ResourceKey<Level> getId()
    {
        return this.id;
    }

    public boolean getAdd()
    {
        return this.add;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() -> ClientPacketHandler.syncDimensionList(this));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
