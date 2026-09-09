/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class Network
{
    private static final String PROTOCOL_VERSION = Integer.toString(1);

    public static void init(IEventBus modBus)
    {
        modBus.addListener(Network::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        toClient(registrar, SyncDimensionListMessage.TYPE, SyncDimensionListMessage.CODEC, SyncDimensionListMessage.INVALID);
        toClient(registrar, SyncArchetypesMessage.TYPE, SyncArchetypesMessage.CODEC, SyncArchetypesMessage.INVALID);
        toClient(registrar, SyncSoulBuffsMessage.TYPE, SyncSoulBuffsMessage.CODEC, SyncSoulBuffsMessage.INVALID);
        toClient(registrar, SyncSoulRegionsMessage.TYPE, SyncSoulRegionsMessage.CODEC, SyncSoulRegionsMessage.INVALID);
        toClient(registrar, SyncSoulLensReportMessage.TYPE, SyncSoulLensReportMessage.CODEC, SyncSoulLensReportMessage.INVALID);
        toClient(registrar, SyncSoulLensBuffsMessage.TYPE, SyncSoulLensBuffsMessage.CODEC, SyncSoulLensBuffsMessage.INVALID);
        toClient(registrar, SyncSoulBoundsMessage.TYPE, SyncSoulBoundsMessage.CODEC, SyncSoulBoundsMessage.INVALID);
        toClient(registrar, SyncSoulAbilitiesMessage.TYPE, SyncSoulAbilitiesMessage.CODEC, SyncSoulAbilitiesMessage.INVALID);
        toClient(registrar, SyncSurveyedBlocksMessage.TYPE, SyncSurveyedBlocksMessage.CODEC, SyncSurveyedBlocksMessage.INVALID);

        //the only two that travel client to server - see UseSoulAbilityMessage on why that matters
        toServer(registrar, UseSoulAbilityMessage.TYPE, UseSoulAbilityMessage.CODEC, UseSoulAbilityMessage.INVALID);
        toServer(registrar, CycleSoulAbilityMessage.TYPE, CycleSoulAbilityMessage.CODEC, CycleSoulAbilityMessage.INVALID);
    }

    private static <P extends SoulPayload> void toClient(
            PayloadRegistrar registrar, CustomPacketPayload.Type<P> type, Codec<P> codec, P invalid)
    {
        registrar.playToClient(type, streamCodec(codec, invalid), (payload, context) -> payload.accept(context));
    }

    private static <P extends SoulPayload> void toServer(
            PayloadRegistrar registrar, CustomPacketPayload.Type<P> type, Codec<P> codec, P invalid)
    {
        registrar.playToServer(type, streamCodec(codec, invalid), (payload, context) -> payload.accept(context));
    }

    /**
     * Carries a message as a single NBT compound written by its own {@link Codec}.
     *
     * <p>The alternative is a hand-written {@link StreamCodec} per message, reading and writing
     * fields in an order the two halves have to agree on by eye. These messages carry maps of
     * magnitudes, lists of boxes and nested records; a codec describes all of that once and cannot
     * disagree with itself.
     *
     * <p>An encode failure writes an empty compound rather than nothing at all. Writing nothing
     * leaves the buffer a field short of what the decoder will read, which corrupts every
     * subsequent packet on the connection rather than just losing this one - the decoder's own
     * fallback to {@code invalid} is the graceful half, and it only works if something was written.
     */
    private static <P extends SoulPayload> StreamCodec<RegistryFriendlyByteBuf, P> streamCodec(
            Codec<P> codec, P invalid)
    {
        return StreamCodec.of(
                (buffer, payload) -> buffer.writeNbt(
                        codec.encodeStart(NbtOps.INSTANCE, payload)
                                .resultOrPartial(error ->
                                        LogHelper.error("Could not encode " + payload.type().id() + ": " + error))
                                .map(CompoundTag.class::cast)
                                .orElseGet(CompoundTag::new)),
                buffer ->
                {
                    final CompoundTag tag = buffer.readNbt();

                    return tag == null
                            ? invalid
                            : codec.parse(NbtOps.INSTANCE, tag).result().orElse(invalid);
                });
    }

    //client side to server
    public static void sendToServer(SoulPayload msg)
    {
        PacketDistributor.sendToServer(msg);
    }

    //server side to client
    public static void sendTo(SoulPayload msg, ServerPlayer player)
    {
        if (!(player instanceof FakePlayer))
        {
            PacketDistributor.sendToPlayer(player, msg);
        }
    }

    public static void sendPacketToAll(SoulPayload packet)
    {
        PacketDistributor.sendToAllPlayers(packet);
    }


    public static void sendToAllAround(SoulPayload mes, ServerLevel level, BlockPos pos, int radius)
    {
        PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), radius, mes);
    }

    public static void sendToAllInWorld(SoulPayload mes, ServerLevel world)
    {
        PacketDistributor.sendToPlayersInDimension(world, mes);
    }

    public static void sendToTrackingTE(SoulPayload mes, BlockEntity te)
    {
        if (te != null && te.getLevel() instanceof ServerLevel level)
        {
            PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(te.getBlockPos()), mes);
        }
    }
}
