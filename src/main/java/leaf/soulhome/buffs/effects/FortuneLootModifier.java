/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Treasury's bonus-drop roll - see {@link FortuneEffect} for what the buff means.
 *
 * <p>Forge 1.20.1 has no bus event carrying a block's generated drops (the old
 * {@code BlockEvent.HarvestDropsEvent} doesn't exist on this version - block loot only ever runs
 * through the loot table system now), so this rides the one hook that does: a Global Loot
 * Modifier, registered through {@code LootModifierRegistry} and enabled for every block loot
 * table by {@code data/soulhome/loot_modifiers/fortune.json}.
 */
public class FortuneLootModifier extends LootModifier
{
    public static final Codec<FortuneLootModifier> CODEC =
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, FortuneLootModifier::new));

    public FortuneLootModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return CODEC;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        // Global loot modifiers run for every loot table, not just blocks - only act on drops that
        // came from a block being harvested.
        if (generatedLoot.isEmpty() || context.getParamOrNull(LootContextParams.BLOCK_STATE) == null)
        {
            return generatedLoot;
        }

        final Entity breaker = context.getParamOrNull(LootContextParams.THIS_ENTITY);

        if (!(breaker instanceof Player player))
        {
            return generatedLoot;
        }

        final double chance = Math.min(1d, SoulBuffs.magnitude(player, SoulBuffTypes.FORTUNE));

        if (chance <= 0d || player.getRandom().nextDouble() >= chance)
        {
            return generatedLoot;
        }

        final ItemStack sample = generatedLoot.get(player.getRandom().nextInt(generatedLoot.size()));

        if (sample.getCount() < sample.getMaxStackSize())
        {
            sample.grow(1);
        }
        else
        {
            final ItemStack bonus = sample.copy();
            bonus.setCount(1);
            generatedLoot.add(bonus);
        }

        return generatedLoot;
    }
}
