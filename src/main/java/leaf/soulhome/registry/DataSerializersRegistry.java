/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A {@link ResourceLocation} an entity can carry in its synced data.
 *
 * <p>Two things about this changed under the port and both are enforced rather than advisory.
 * 1.20.5 replaced the hand-written write/read/copy triple with one {@code StreamCodec} - and
 * {@link ResourceLocation} already ships its own, so the serializer is that codec plus a wrapper;
 * {@code copy()} went with it, since a {@code ResourceLocation} is immutable, which was the only
 * reason the old one could return its argument unchanged. Separately, NeoForge refuses a call to
 * {@code EntityDataSerializers.registerSerializer} from a mod outright - a raw registration hands
 * out a numeric id by call order, so a client and a server that loaded their mods in different
 * orders would disagree about what each id means. A registry entry is keyed by name instead.
 */
public class DataSerializersRegistry
{
    public static final DeferredRegister<EntityDataSerializer<?>> DATA_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, SoulHome.MODID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<ResourceLocation>> RESOURCE_LOCATION =
            DATA_SERIALIZERS.register(
                    "resource_location", () -> EntityDataSerializer.forValueType(ResourceLocation.STREAM_CODEC));

}
