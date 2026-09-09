/* File created ~ 9 - 9 - 2026 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.items.SoulBinding;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's item data components.
 *
 * <p>1.20.5 replaced item NBT with a typed component map, so the two keys a bound soulkey used to
 * write onto its own stack - {@code soul_uuid} and {@code soul_name} - are one registered component
 * now. Registered rather than stuffed into {@code minecraft:custom_data} because a registered
 * component is the thing recipes, {@code /give} and the stack-equality check all understand.
 */
public final class DataComponentsRegistry
{
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SoulHome.MODID);

    /** Whose soulhome a bound key opens. Absent on an unbound key, which is how creative keys arrive. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SoulBinding>> SOUL_BINDING =
            DATA_COMPONENTS.register(
                    "soul_binding",
                    () -> DataComponentType.<SoulBinding>builder()
                            .persistent(SoulBinding.CODEC)
                            .networkSynchronized(SoulBinding.STREAM_CODEC)
                            .build());

    private DataComponentsRegistry()
    {
    }
}
