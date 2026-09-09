/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.registry;

import com.mojang.serialization.MapCodec;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.effects.FortuneLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class LootModifierRegistry
{
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoulHome.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<FortuneLootModifier>> FORTUNE =
            LOOT_MODIFIERS.register("fortune", () -> FortuneLootModifier.CODEC);
}
