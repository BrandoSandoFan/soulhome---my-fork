/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.buffs.SoulAbilities;
import net.minecraft.server.level.ServerPlayer;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * "I pressed use on ability X" (#87). The first client-to-server message in the mod, and written
 * accordingly.
 *
 * <p>Every other message in {@code network/} runs server to client and carries a result the client
 * only has to draw. This one runs the other way and carries a <i>request</i>, so none of it can be
 * taken at face value: the ability id below is a string a modified client picked, and the only
 * thing that makes it safe is that {@link SoulAbilities#use} treats it as a claim to be checked
 * against what the player actually owns rather than as an instruction.
 *
 * <p>{@link IPayloadContext#player()} is the player, and is never null on the server for a
 * message that arrived over the wire - but it is null when the logical server handles a packet from
 * a nonexistent connection, so it is checked anyway.
 */
public class UseSoulAbilityMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<UseSoulAbilityMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("use_soul_ability"));

    public static final UseSoulAbilityMessage INVALID = new UseSoulAbilityMessage("");

    public static final Codec<UseSoulAbilityMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(Codec.STRING.optionalFieldOf("ability", "")
                            .forGetter(UseSoulAbilityMessage::getAbility))
                    .apply(instance, UseSoulAbilityMessage::new));

    private final String ability;

    public UseSoulAbilityMessage(String ability)
    {
        this.ability = ability == null ? "" : ability;
    }

    public String getAbility()
    {
        return this.ability;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            final ServerPlayer sender = context.player() instanceof ServerPlayer server ? server : null;

            if (sender != null)
            {
                SoulAbilities.use(sender, this.ability);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
