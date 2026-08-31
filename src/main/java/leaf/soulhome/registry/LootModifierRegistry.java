/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.registry;

import com.mojang.serialization.Codec;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.effects.FortuneLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LootModifierRegistry
{
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoulHome.MODID);

    public static final RegistryObject<Codec<FortuneLootModifier>> FORTUNE =
            LOOT_MODIFIERS.register("fortune", () -> FortuneLootModifier.CODEC);
}
