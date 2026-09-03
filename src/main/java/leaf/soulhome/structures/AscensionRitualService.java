/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.registry.ItemsRegistry;
import leaf.soulhome.structures.core.AscensionSettings;
import leaf.soulhome.structures.core.PillarInspector;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ascension ritual itself (#83): the four requirements, and standing on the pillar's cap long
 * enough to spend them. {@code AscensionEvents} is the only thing that calls into this - a tick
 * hook driving whichever soulhome currently has a ritual running, and a right-click on the Soul
 * Anchor asking what is still missing.
 *
 * <p>One ritual per soulhome at a time, tracked here rather than on the anchor or in
 * {@code SoulHomeBuffData}: it is transient state that a server restart is allowed to forget
 * (nothing was ever spent until success, so there is nothing to refund on a restart either), and
 * keying it by dimension is what makes "two players ascending the same soulhome cannot double
 * spend one payment of essence" true for free - a second player simply finds the lock held and is
 * never offered a ritual of their own to start.
 */
public final class AscensionRitualService
{
    private static final Map<ResourceKey<Level>, RitualState> ACTIVE = new HashMap<>();

    /** Re-applied every tick so an aborted or completed ritual's effects lapse within a second. */
    private static final int EFFECT_REFRESH_TICKS = 25;

    /** How often, in ticks, a running ritual re-validates the whole pillar rather than just the cap. */
    private static final int FULL_REVALIDATION_INTERVAL = 10;

    private AscensionRitualService()
    {
    }

    /**
     * What one soulhome currently has and needs for its next rank - shared by the tick loop
     * (to decide whether to start a ritual) and the Soul Anchor's right-click readout, so the two
     * can never disagree about the same soulhome at the same instant.
     */
    public record Readiness(
            boolean anchorPresent, int targetRank, PillarInspector.Result pillar,
            double willpowerHave, double willpowerRequired, int essenceHave, int essenceRequired, boolean maxed)
    {
        public boolean pillarValid()
        {
            return this.pillar.valid();
        }

        public boolean willpowerMet()
        {
            return this.willpowerHave >= this.willpowerRequired;
        }

        public boolean essenceMet()
        {
            return this.essenceHave >= this.essenceRequired;
        }

        public boolean allMet()
        {
            return !this.maxed && this.anchorPresent && pillarValid() && willpowerMet() && essenceMet();
        }
    }

    private record RitualState(
            UUID playerId, int targetRank, BlockPos capPos, Item essenceItem, int essenceCount, int ticksRemaining)
    {
        private RitualState withTicksRemaining(int remaining)
        {
            return new RitualState(this.playerId, this.targetRank, this.capPos, this.essenceItem, this.essenceCount, remaining);
        }
    }

    /** What this soulhome has and needs for the rank past its current one. */
    public static Readiness checkReadiness(ServerLevel level, ServerPlayer player)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final int currentRank = data.ascensionRank();
        final int maxRank = SoulHomeConfig.maxRank();
        final int targetRank = currentRank + 1;
        final AscensionSettings settings = SoulHomeConfig.ascensionSettings();

        if (currentRank >= maxRank)
        {
            return new Readiness(data.anchorPos().isPresent(), targetRank, PillarInspector.Result.NO_BASE, data.totalScore(), 0, 0, 0, true);
        }

        final Optional<BlockPos> anchor = data.anchorPos();
        final double willpowerRequired = settings.willpowerRequired(targetRank);
        final int essenceRequired = settings.essenceCountPerRank();

        if (anchor.isEmpty())
        {
            return new Readiness(false, targetRank, PillarInspector.Result.NO_BASE, data.totalScore(), willpowerRequired, 0, essenceRequired, false);
        }

        final SoulBounds bounds = SoulHomeConfig.soulBounds(currentRank);
        final LiveBlockVolume volume = new LiveBlockVolume(level, bounds.toRegionBounds());
        final PillarInspector.Result pillar = PillarInspector.inspect(
                volume, anchor.get().getX(), anchor.get().getZ(), bounds.floorY(), bounds.ceilingY(), settings.pillarSearchRadius());

        final int essenceHave = countEssence(player, essenceItem(targetRank));

        return new Readiness(true, targetRank, pillar, data.totalScore(), willpowerRequired, essenceHave, essenceRequired, false);
    }

    /**
     * Called once per online player per tick, from {@code AscensionEvents}. Drives whichever
     * soulhome's ritual is running, and offers a fresh one to a player standing ready on a pillar's
     * cap when none is.
     */
    public static void tick(ServerPlayer player)
    {
        if (!SoulHomeConfig.enforceBounds() || !DimensionHelper.isInSoulDimension(player))
        {
            return;
        }

        if (!(player.level() instanceof ServerLevel level))
        {
            return;
        }

        final ResourceKey<Level> key = level.dimension();
        final RitualState state = ACTIVE.get(key);

        if (state != null)
        {
            if (state.playerId().equals(player.getUUID()))
            {
                tickActive(level, player, key, state);
            }

            // someone else already holds this soulhome's one ritual slot
            return;
        }

        final Readiness readiness = checkReadiness(level, player);

        if (!readiness.allMet())
        {
            return;
        }

        final SoulBounds bounds = SoulHomeConfig.soulBounds(SoulHomeBuffData.get(level).ascensionRank());
        final BlockPos capPos = findPlayerCap(player, readiness.pillar(), bounds.ceilingY());

        if (capPos == null)
        {
            return;
        }

        startRitual(level, player, key, readiness, capPos);
    }

    /** The player just left the ritual belongs to them (logout, death, changed dimension). */
    public static void abortIfRitualBelongsTo(ServerPlayer player)
    {
        for (Map.Entry<ResourceKey<Level>, RitualState> entry : Map.copyOf(ACTIVE).entrySet())
        {
            if (entry.getValue().playerId().equals(player.getUUID()))
            {
                ACTIVE.remove(entry.getKey());
                refund(player, entry.getValue());
            }
        }
    }

    /** Convert as much banked residue as the config rate allows into Essence I, at the Soul Anchor. */
    public static int convertResidue(ServerLevel level, ServerPlayer player)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final double rate = SoulHomeConfig.essenceSettings().residueToEssenceRate();
        final int units = data.claimEssenceFromResidue(rate);

        if (units > 0)
        {
            giveOrDrop(player, new ItemStack(ItemsRegistry.SUBLIME_ESSENCE.get(0).get(), units));
        }

        return units;
    }

    /** The same status a right-click on the Soul Anchor reports, sent as chat to {@code player}. */
    public static void reportStatus(ServerLevel level, ServerPlayer player)
    {
        final int convertedEssence = convertResidue(level, player);

        if (convertedEssence > 0)
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RESIDUE_CONVERTED, convertedEssence)
                    .withStyle(ChatFormatting.AQUA));
        }

        final Readiness readiness = checkReadiness(level, player);
        final int rank = SoulHomeBuffData.get(level).ascensionRank();

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_HEADER).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RANK, SoulBounds.rankLabel(rank))
                .withStyle(ChatFormatting.WHITE));

        if (readiness.maxed())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_MAXED).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (ACTIVE.containsKey(level.dimension()) && !ACTIVE.get(level.dimension()).playerId().equals(player.getUUID()))
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_IN_PROGRESS).withStyle(ChatFormatting.YELLOW));
            return;
        }

        reportPillar(player, readiness.pillar(), level);

        report(player, Constants.StringKeys.ANCHOR_WILLPOWER_OK, Constants.StringKeys.ANCHOR_WILLPOWER_MISSING,
                readiness.willpowerMet(), (int) Math.ceil(readiness.willpowerHave()), (int) Math.ceil(readiness.willpowerRequired()));

        report(player, Constants.StringKeys.ANCHOR_ESSENCE_OK, Constants.StringKeys.ANCHOR_ESSENCE_MISSING,
                readiness.essenceMet(), readiness.essenceHave(), readiness.essenceRequired(),
                SoulBounds.rankLabel(readiness.targetRank()));

        if (readiness.allMet())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_READY).withStyle(ChatFormatting.GREEN));
        }
    }

    private static void report(ServerPlayer player, String okKey, String missingKey, boolean met, Object... args)
    {
        final ChatFormatting style = met ? ChatFormatting.GREEN : ChatFormatting.RED;
        player.sendSystemMessage(Component.translatable(met ? okKey : missingKey, args).withStyle(style));
    }

    private static void reportPillar(ServerPlayer player, PillarInspector.Result pillar, ServerLevel level)
    {
        if (pillar.valid())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_OK).withStyle(ChatFormatting.GREEN));
            return;
        }

        if (!pillar.hasBase())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_NO_BASE).withStyle(ChatFormatting.RED));
            return;
        }

        final SoulBounds bounds = SoulHomeConfig.soulBounds(SoulHomeBuffData.get(level).ascensionRank());
        final int gap = (bounds.ceilingY() - 1) - pillar.topY();

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_GAP, gap).withStyle(ChatFormatting.RED));
    }

    private static void startRitual(
            ServerLevel level, ServerPlayer player, ResourceKey<Level> key, Readiness readiness, BlockPos capPos)
    {
        final Item essenceItem = essenceItem(readiness.targetRank());
        removeEssence(player, essenceItem, readiness.essenceRequired());

        ACTIVE.put(key, new RitualState(
                player.getUUID(), readiness.targetRank(), capPos.immutable(), essenceItem, readiness.essenceRequired(),
                SoulHomeConfig.ascensionSettings().ritualDurationTicks()));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_STARTED).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void tickActive(ServerLevel level, ServerPlayer player, ResourceKey<Level> key, RitualState state)
    {
        if (!state.capPos().equals(player.blockPosition()))
        {
            ACTIVE.remove(key);
            refund(player, state);
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_ABORTED_MOVED).withStyle(ChatFormatting.RED));
            return;
        }

        final boolean dueForFullCheck = state.ticksRemaining() % FULL_REVALIDATION_INTERVAL == 0;

        if (dueForFullCheck && !pillarStillStandsUnder(level, state.capPos()))
        {
            ACTIVE.remove(key);
            refund(player, state);
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_ABORTED_PILLAR).withStyle(ChatFormatting.RED));
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_TICKS, 1, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_TICKS, 1, false, false, false));

        final int remaining = state.ticksRemaining() - 1;

        if (remaining <= 0)
        {
            ACTIVE.remove(key);
            complete(level, player, state);
            return;
        }

        ACTIVE.put(key, state.withTicksRemaining(remaining));
    }

    private static void complete(ServerLevel level, ServerPlayer player, RitualState state)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        data.setAscensionRank(state.targetRank());
        StructureScanService.refresh(player);

        level.sendParticles(ParticleTypes.END_ROD, state.capPos().getX() + 0.5, state.capPos().getY() + 0.2,
                state.capPos().getZ() + 0.5, 80, 0.6, 1.2, 0.6, 0.02);
        level.playSound(null, state.capPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);

        SoulAdvancements.onAscended(player, state.targetRank());

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_SUCCESS, SoulBounds.rankLabel(state.targetRank()))
                .withStyle(ChatFormatting.AQUA));
    }

    private static void refund(ServerPlayer player, RitualState state)
    {
        giveOrDrop(player, new ItemStack(state.essenceItem(), state.essenceCount()));
    }

    /**
     * The pillar under one specific cap position still stands - a cheap per-tick check, distinct
     * from the full {@link PillarInspector} sweep {@link #FULL_REVALIDATION_INTERVAL} runs less
     * often, so a ritual notices the block right under the player's feet vanishing immediately
     * rather than waiting up to that many ticks for the next full re-check.
     */
    private static boolean pillarStillStandsUnder(ServerLevel level, BlockPos capPos)
    {
        final BlockPos below = capPos.below();
        return SnapshotBlockVolume.passabilityOf(level, below, level.getBlockState(below)).isFullBlock();
    }

    private static BlockPos findPlayerCap(ServerPlayer player, PillarInspector.Result pillar, int ceilingY)
    {
        if (!pillar.valid())
        {
            return null;
        }

        final BlockPos feet = player.blockPosition();

        if (feet.getY() != ceilingY)
        {
            return null;
        }

        for (PillarInspector.CapCell cell : pillar.capCells())
        {
            if (cell.x() == feet.getX() && cell.z() == feet.getZ())
            {
                return feet;
            }
        }

        return null;
    }

    private static int essenceRankIndex(int rank)
    {
        return rank >= 1 && rank <= ItemsRegistry.SUBLIME_ESSENCE.size() ? rank - 1 : -1;
    }

    private static Item essenceItem(int rank)
    {
        final int index = essenceRankIndex(rank);
        return index < 0 ? null : ItemsRegistry.SUBLIME_ESSENCE.get(index).get();
    }

    private static int countEssence(ServerPlayer player, Item essenceItem)
    {
        if (essenceItem == null)
        {
            return 0;
        }

        int count = 0;

        for (ItemStack stack : player.getInventory().items)
        {
            if (stack.is(essenceItem))
            {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static void removeEssence(ServerPlayer player, Item essenceItem, int amount)
    {
        int remaining = amount;

        for (ItemStack stack : player.getInventory().items)
        {
            if (remaining <= 0)
            {
                break;
            }

            if (stack.is(essenceItem))
            {
                final int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        final ItemStack remainder = stack.copy();
        player.getInventory().add(remainder);

        if (!remainder.isEmpty())
        {
            player.drop(remainder, false);
        }
    }
}
