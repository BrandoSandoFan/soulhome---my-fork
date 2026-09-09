/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.ArchetypeCodecs;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Sends the loaded archetype definitions to a client on login and on datapack reload.
 *
 * <p>The client needs these to tell a player what a room is missing without a round-trip to the
 * server for every tooltip. Buff magnitudes are still computed server-side and are not carried
 * here - this is the description of what the archetypes <i>are</i>, not what anyone has earned.
 */
public class SyncArchetypesMessage implements SoulPayload
{
    public static final CustomPacketPayload.Type<SyncArchetypesMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocationHelper.prefix("sync_archetypes"));

    public static final SyncArchetypesMessage INVALID = new SyncArchetypesMessage(List.of());

    public static final Codec<SyncArchetypesMessage> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(ArchetypeCodecs.ARCHETYPE_WITH_ID.listOf()
                            .fieldOf("archetypes")
                            .forGetter(SyncArchetypesMessage::getArchetypes))
                    .apply(instance, SyncArchetypesMessage::new));

    private final List<ArchetypeDefinition> archetypes;

    public SyncArchetypesMessage(List<ArchetypeDefinition> archetypes)
    {
        this.archetypes = List.copyOf(archetypes);
    }

    public List<ArchetypeDefinition> getArchetypes()
    {
        return this.archetypes;
    }

    @Override
    public void accept(IPayloadContext context)
    {
        // deliberately not routed through ClientPacketHandler: nothing here needs a client-only
        // class, and keeping it that way means no @OnlyIn hazard on a dedicated server
        context.enqueueWork(() -> ArchetypeManager.replaceAll(this.archetypes));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
