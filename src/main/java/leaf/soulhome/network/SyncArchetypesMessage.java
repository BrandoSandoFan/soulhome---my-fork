/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.ArchetypeCodecs;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sends the loaded archetype definitions to a client on login and on datapack reload.
 *
 * <p>The client needs these to tell a player what a room is missing without a round-trip to the
 * server for every tooltip. Buff magnitudes are still computed server-side and are not carried
 * here - this is the description of what the archetypes <i>are</i>, not what anyone has earned.
 */
public class SyncArchetypesMessage implements Consumer<NetworkEvent.Context>
{
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
    public void accept(NetworkEvent.Context context)
    {
        // deliberately not routed through ClientPacketHandler: nothing here needs a client-only
        // class, and keeping it that way means no @OnlyIn hazard on a dedicated server
        context.enqueueWork(() -> ArchetypeManager.replaceAll(this.archetypes));
    }
}
