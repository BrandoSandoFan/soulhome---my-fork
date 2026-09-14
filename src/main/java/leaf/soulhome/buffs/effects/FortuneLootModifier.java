/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.FortuneLevels;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Treasury's bonus-drop roll - see {@link FortuneEffect} for what the buff means and #195 for why
 * this class no longer picks a random stack out of the already-generated loot and grows it: that
 * duplicated blocks that drop themselves, ignored Silk Touch, and affected loot tables vanilla's
 * own Fortune has no opinion about. The magnitude is now effective Fortune levels, and this class's
 * whole job is handing those levels to the block's own loot table and letting it decide what they
 * mean - the same design vanilla's {@code ApplyBonusCount} already has for every block that cares.
 *
 * <p>Forge 1.20.1 has no bus event carrying a block's generated drops (the old
 * {@code BlockEvent.HarvestDropsEvent} doesn't exist on this version - block loot only ever runs
 * through the loot table system now) and no event fires when an enchantment level is queried
 * either, so there is no hook to make {@code ApplyBonusCount} simply see a higher Fortune level on
 * the original roll. Instead this rides the one hook Forge does give a block's generated loot: a
 * Global Loot Modifier, registered through {@code LootModifierRegistry} and enabled for every block
 * loot table by {@code data/soulhome/loot_modifiers/fortune.json}. On a hit it rebuilds the tool
 * with one extra Fortune level and asks the block for its drops again with that tool - which is
 * exactly {@link Block#getDrops}, the same call vanilla itself uses, so every rule a block's own
 * loot table encodes (Silk Touch taking a different pool entry, a cap on the bonus, no
 * {@code apply_bonus} function at all on a block that drops itself) is inherited rather than
 * reimplemented.
 *
 * <p>Asking a block for its drops runs its loot table again, which runs every registered global
 * loot modifier again - this one included. Without {@link #REROLLING} that second roll's own hit
 * would ask for a third roll, forever; the guard makes the second roll's own attempt at this
 * modifier a no-op, so exactly one extra level is ever added.
 */
public class FortuneLootModifier extends LootModifier
{
    public static final Codec<FortuneLootModifier> CODEC =
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, FortuneLootModifier::new));

    /** Guards the reroll below against re-entering this same modifier - see the class javadoc. */
    private static final ThreadLocal<Boolean> REROLLING = ThreadLocal.withInitial(() -> Boolean.FALSE);

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
        final BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);

        if (generatedLoot.isEmpty() || state == null)
        {
            return generatedLoot;
        }

        if (Boolean.TRUE.equals(REROLLING.get()))
        {
            return generatedLoot;
        }

        final Entity breaker = context.getParamOrNull(LootContextParams.THIS_ENTITY);

        if (!(breaker instanceof Player player))
        {
            return generatedLoot;
        }

        final double magnitude = SoulBuffs.magnitude(player, SoulBuffTypes.FORTUNE);

        // No treasury, or the room's magnitude clamped to nothing: exactly vanilla's path, with no
        // roll, no reroll and no cost - see #195's acceptance criteria.
        if (magnitude <= 0d)
        {
            return generatedLoot;
        }

        final int extraLevels = FortuneLevels.extraLevels(magnitude, player.getRandom().nextDouble());

        if (extraLevels <= 0)
        {
            return generatedLoot;
        }

        final ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);

        // Bare hands cannot carry an enchantment, and mutating the shared ItemStack.EMPTY instance
        // to try would corrupt every empty stack in the game - so a bare-handed break, exactly like
        // a bare-handed break of a vanilla Fortune tool, gets nothing from this.
        if (tool == null || tool.isEmpty())
        {
            return generatedLoot;
        }

        final Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        final ServerLevel level = context.getLevel();

        if (origin == null || level == null)
        {
            return generatedLoot;
        }

        final ItemStack boostedTool = withExtraFortune(tool, extraLevels);
        final BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        final BlockPos pos = BlockPos.containing(origin);

        REROLLING.set(Boolean.TRUE);

        try
        {
            return new ObjectArrayList<>(Block.getDrops(state, level, pos, blockEntity, player, boostedTool));
        }
        finally
        {
            REROLLING.set(Boolean.FALSE);
        }
    }

    /** A copy of {@code tool} with {@code extraLevels} more Fortune than it already carries. */
    private static ItemStack withExtraFortune(ItemStack tool, int extraLevels)
    {
        final ItemStack boosted = tool.copy();
        final Map<Enchantment, Integer> levels = new HashMap<>(EnchantmentHelper.getEnchantments(boosted));
        levels.merge(Enchantments.BLOCK_FORTUNE, extraLevels, Integer::sum);
        EnchantmentHelper.setEnchantments(levels, boosted);
        return boosted;
    }
}
