/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AscensionReport;
import leaf.soulhome.registry.ItemsRegistry;
import leaf.soulhome.sound.SoulSounds;
import leaf.soulhome.structures.core.AscensionSettings;
import leaf.soulhome.structures.core.AscensionSpectacle;
import leaf.soulhome.structures.core.PillarInspector;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulFeedback;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
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
            UUID playerId, int targetRank, BlockPos capPos, int baseY, int totalTicks, Item essenceItem,
            int essenceCount, int ticksRemaining, float[] colour)
    {
        private RitualState withTicksRemaining(int remaining)
        {
            return new RitualState(this.playerId, this.targetRank, this.capPos, this.baseY, this.totalTicks,
                    this.essenceItem, this.essenceCount, remaining, this.colour);
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

        final SoulBounds bounds = SoulHomeConfig.soulBounds(currentRank, data.islandFloorY());
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
        // a gazer is a spectator in someone else's soul (#187): hovering over a pillar's cap is not
        // standing on it, and a read-only visit must never start or hold a ritual
        if (!SoulHomeConfig.enforceBounds() || !DimensionHelper.isInSoulDimension(player) || GazeService.isGazing(player))
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

        final SoulHomeBuffData tickData = SoulHomeBuffData.get(level);
        final SoulBounds bounds = SoulHomeConfig.soulBounds(tickData.ascensionRank(), tickData.islandFloorY());
        final BlockPos capPos = findPlayerCap(player, readiness.pillar(), bounds.ceilingY());

        if (capPos == null)
        {
            return;
        }

        startRitual(level, player, key, readiness, capPos, bounds.floorY());
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

    /**
     * Everything the Soul Anchor's screen says about the climb (#83), for the soulhome the player
     * is standing in. The summary used to be printed to chat on every right-click; it is the same
     * summary, read off the same {@link Readiness} the ritual itself is judged against, and the
     * only thing that changed is where a player reads it.
     *
     * @param owner whether the player is this soul's own owner - a visitor may read the climb and
     *              may not convert a residue they did not earn
     */
    public static AscensionReport statusFor(ServerLevel level, ServerPlayer player, boolean owner)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final int rank = data.ascensionRank();
        final AscensionReport.Residue residue = residueOf(data);

        if (!SoulHomeConfig.enforceBounds())
        {
            // no climb to report on, and residue is not part of the climb - it keeps accruing and
            // keeps converting, so the screen still has that half to offer
            return new AscensionReport(
                    false, owner, rank, rank, false, false, false, false, 0, 0, 0, 0, 0, residue);
        }

        final Readiness readiness = checkReadiness(level, player);

        return new AscensionReport(
                true,
                owner,
                rank,
                readiness.targetRank(),
                readiness.maxed(),
                ritualHeldByAnother(level, player),
                readiness.pillarValid(),
                readiness.pillar().hasBase(),
                pillarGap(level, readiness.pillar()),
                (int) Math.ceil(readiness.willpowerHave()),
                (int) Math.ceil(readiness.willpowerRequired()),
                readiness.essenceHave(),
                readiness.essenceRequired(),
                residue);
    }

    /** Whether this soulhome's one ritual slot is currently held by somebody other than {@code player}. */
    public static boolean ritualHeldByAnother(ServerLevel level, ServerPlayer player)
    {
        final RitualState state = ACTIVE.get(level.dimension());

        return state != null && !state.playerId().equals(player.getUUID());
    }

    private static AscensionReport.Residue residueOf(SoulHomeBuffData data)
    {
        final double rate = SoulHomeConfig.essenceSettings().residueToEssenceRate();
        final double banked = data.residue();

        return new AscensionReport.Residue(
                SoulHomeConfig.residueTapEnabled(), banked, (int) (banked / rate), 0);
    }

    /**
     * How far short of the firmament a pillar that has a base stops. Meaningless without a base -
     * {@code topY} is {@code Integer.MIN_VALUE} there - so it is reported as zero and the screen
     * says "no base" instead.
     */
    private static int pillarGap(ServerLevel level, PillarInspector.Result pillar)
    {
        if (pillar.valid() || !pillar.hasBase())
        {
            return 0;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final SoulBounds bounds = SoulHomeConfig.soulBounds(data.ascensionRank(), data.islandFloorY());

        return (bounds.ceilingY() - 1) - pillar.topY();
    }

    private static void startRitual(
            ServerLevel level, ServerPlayer player, ResourceKey<Level> key, Readiness readiness, BlockPos capPos,
            int floorY)
    {
        final Item essenceItem = essenceItem(readiness.targetRank());
        removeEssence(player, essenceItem, readiness.essenceRequired());

        final int totalTicks = SoulHomeConfig.ascensionSettings().ritualDurationTicks();

        ACTIVE.put(key, new RitualState(
                player.getUUID(), readiness.targetRank(), capPos.immutable(), columnBaseY(level, capPos, floorY),
                totalTicks, essenceItem, readiness.essenceRequired(), totalTicks, soulColour(level)));

        // the whole ritual is one moment as far as the ambience is concerned (#212): the hum steps
        // up four times across thirty seconds and the gaps between them are as much a part of it as
        // the notes, so the hold is placed once, for the length the ritual is actually going to be,
        // rather than re-sent at each milestone and leaving the quiet stretches unprotected
        SoulSounds.hold(level, capPos, SoulFeedback.RITUAL, totalTicks);

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

        emitProgressEffects(level, state);

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

        // the box just grew; the ground under it has not yet. Queued rather than run here, so the
        // ritual completes on this tick and the island spreads outward over the next few seconds -
        // which is both what #161 asks for and the better reward moment (#158).
        TerrainGrowthService.rankChanged(level);

        // seen from anywhere in the soul, whatever its size - the one moment the whole place is
        // told about, so it is sent past the usual thirty-two block particle range
        emit(level, state, AscensionSpectacle.completion(state.targetRank(), seedOf(state)), true);
        SoulSounds.playFeedback(
                level, state.capPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f,
                SoulFeedback.RITUAL);

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

    /**
     * The spectacle's hold (see {@link AscensionSpectacle}) and a hum that steps up a note at each
     * quarter - so the ritual reads as something building rather than a debuff and a wait. Scaled
     * by {@code targetRank} the same way the ritual's own costs already are, so ascending to V looks
     * and sounds like the bigger event it is.
     */
    private static void emitProgressEffects(ServerLevel level, RitualState state)
    {
        final int elapsed = state.totalTicks() - state.ticksRemaining();
        final boolean step = isStep(elapsed, state.totalTicks());

        // the hold is for whoever is near the pillar, at the usual range and under each player's own
        // particle setting; a step is thrown to the whole soul, like the completion
        emit(level, state, AscensionSpectacle.hold(
                elapsed, state.totalTicks(), state.targetRank(), state.capPos().getY() - state.baseY(), seedOf(state)), step);

        // the hum steps up a note on the same quarters the spectacle throws its rings
        for (int i = 0; i < AscensionSpectacle.STEPS.length; i++)
        {
            if (elapsed == (int) (state.totalTicks() * AscensionSpectacle.STEPS[i]))
            {
                final float pitch = 0.8f + i * 0.2f;
                final float volume = (float) (0.6 + (state.targetRank() - 1) * 0.1);
                SoulSounds.playFeedback(
                        level, state.capPos(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, volume, pitch,
                        SoulFeedback.RITUAL, state.ticksRemaining());
                break;
            }
        }
    }

    private static boolean isStep(int elapsed, int total)
    {
        for (double fraction : AscensionSpectacle.STEPS)
        {
            if (elapsed == (int) (total * fraction))
            {
                return true;
            }
        }

        return false;
    }

    /** Any number fixed for the length of one ritual, so its particles vary tick to tick and not run to run. */
    private static long seedOf(RitualState state)
    {
        return state.playerId().getLeastSignificantBits() ^ state.capPos().asLong() ^ state.targetRank();
    }

    /**
     * Sends the spectacle's particles to every player in the soul, one by one, each with its own
     * velocity - a count of zero is how vanilla's particle packet says "this exact velocity" rather
     * than "this many, scattered".
     *
     * @param everywhere past the usual thirty-two blocks, for the moments the whole soul should see
     */
    private static void emit(ServerLevel level, RitualState state, List<AscensionSpectacle.Mote> motes, boolean everywhere)
    {
        final double x = state.capPos().getX() + 0.5d;
        final double y = state.capPos().getY();
        final double z = state.capPos().getZ() + 0.5d;
        final float[] colour = state.colour();
        final Vector3f soul = new Vector3f(colour[0], colour[1], colour[2]);
        final Vector3f light = new Vector3f(
                0.5f + 0.5f * colour[0], 0.5f + 0.5f * colour[1], 0.5f + 0.5f * colour[2]);
        final ParticleOptions soulDust = new DustColorTransitionOptions(soul, light, 1.4f);
        final ParticleOptions riseDust = new DustColorTransitionOptions(light, soul, 0.9f);

        for (ServerPlayer viewer : level.players())
        {
            for (AscensionSpectacle.Mote mote : motes)
            {
                final ParticleOptions type = switch (mote.kind())
                {
                    case SOUL -> soulDust;
                    case RISE -> riseDust;
                    case LIGHT -> ParticleTypes.END_ROD;
                    case GLYPH -> ParticleTypes.ENCHANT;
                    case SPARK -> ParticleTypes.FIREWORK;
                };

                level.sendParticles(viewer, type, everywhere,
                        x + mote.x(), y + mote.y(), z + mote.z(), 0, mote.vx(), mote.vy(), mote.vz(), 1d);
            }
        }
    }

    /**
     * The colour this soul's ritual is drawn in: what its rooms pull it toward, as the sky above it
     * is, or the soul biome's own pale blue for a soul with nothing built. Worked out once, when the
     * ritual starts - nothing is built during one, since the player is standing on the pillar.
     */
    private static float[] soulColour(ServerLevel level)
    {
        final SoulCharacter character = SoulHomeConfig.enabled()
                ? SoulCharacter.of(SoulHomeBuffData.get(level).awardedRooms(), ArchetypeManager.byId())
                : SoulCharacter.EMPTY;

        return character.isEmpty() ? SoulAmbience.NEUTRAL : SoulAmbience.characterColour(character);
    }

    /**
     * How far down from the cap the player's own pillar actually runs, found by walking down
     * through full blocks rather than trusting {@link PillarInspector}'s own base search - a
     * tapered or buttressed pillar's cap is not necessarily directly above its base, but the column
     * under a player's own feet always is, and that is the column the climbing effect follows.
     * Computed once, at the start of the ritual, rather than every tick.
     */
    private static int columnBaseY(ServerLevel level, BlockPos capPos, int floorY)
    {
        final BlockPos.MutableBlockPos cursor = capPos.mutable();

        while (cursor.getY() > floorY)
        {
            cursor.move(0, -1, 0);

            if (!SnapshotBlockVolume.passabilityOf(level, cursor, level.getBlockState(cursor)).isFullBlock())
            {
                return cursor.getY() + 1;
            }
        }

        return floorY;
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
