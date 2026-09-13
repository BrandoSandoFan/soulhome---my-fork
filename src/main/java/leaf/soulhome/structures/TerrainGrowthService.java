/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.ApronPlan;
import leaf.soulhome.structures.core.ApronPlanner;
import leaf.soulhome.structures.core.GroundSurvey;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.structures.core.TerrainGrowthSettings;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grows a soulhome's island outward when it ascends (#158), without stalling the server doing it
 * (#161) and without touching a single block the player placed (#160).
 *
 * <h2>One rule decides when growth runs</h2>
 *
 * Growth is due whenever a soulhome's {@code grownRank} trails its {@code ascensionRank}. That one
 * condition covers an ascension a moment ago, a soulhome that was already rank III before any of
 * this shipped, and a server that stopped halfway through a band - so there is no ascension-shaped
 * trigger a duplicated event could fire twice, no "already grown" flag to keep in step, and
 * interrupted growth resumes for free the next time anyone opens the soul.
 *
 * <h2>Three phases, spread across ticks</h2>
 *
 * <ol>
 *   <li><b>Survey</b> - walk the box chunk by chunk, sorting every column into ground, built or
 *       void. A bounded number of chunks per tick.</li>
 *   <li><b>Plan</b> - {@link ApronPlanner}, once, off the survey. Pure arithmetic over an int array;
 *       a rank V box is about 44,000 columns and two linear sweeps of it.</li>
 *   <li><b>Place</b> - write the planned columns, again a bounded number of chunks per tick.</li>
 * </ol>
 *
 * The ascension itself finishes immediately and the ground arrives over the following seconds,
 * which #161 wants for tick time and which is also simply the better moment: ground spreading
 * outward after a rank-up is a reward, and ground that was silently already there is not.
 *
 * <h2>On #122, which this could easily have reintroduced</h2>
 *
 * Reading a block state from an unloaded chunk generates it synchronously on the server thread, and
 * that fault is the reason the scan pipeline is shaped the way it is. It is deliberately not being
 * reintroduced here, for a reason specific to this dimension: {@code SoulChunkGenerator} returns
 * every chunk untouched, so "generating" a soulhome chunk allocates an empty one and writes
 * nothing. The cost that remains is allocation and a disk read for a chunk that has been written
 * before, which is what the per-tick budget bounds. Anywhere else in the game this would be the
 * wrong shape entirely.
 */
public final class TerrainGrowthService
{
    private static final Map<ResourceKey<Level>, GrowthJob> ACTIVE = new HashMap<>();

    /**
     * Soulhomes whose growth has already been found not to be due, so the check is not repeated on
     * every arrival. Cleared whenever a rank changes, which is the only thing that can make growth
     * due again.
     */
    private static final Map<ResourceKey<Level>, Integer> SETTLED = new HashMap<>();

    private TerrainGrowthService()
    {
    }

    /**
     * Start growing this soulhome's ground if it is owed any. Cheap and safe to call often - the
     * usual answer is "nothing is due" and costs a saved-data lookup.
     *
     * <p><b>Server thread only.</b>
     */
    public static void considerGrowth(Level level)
    {
        if (!(level instanceof ServerLevel soulhome) || DimensionHelper.soulOwner(soulhome).isEmpty())
        {
            return;
        }

        final ResourceKey<Level> key = soulhome.dimension();

        if (ACTIVE.containsKey(key) || !SoulHomeConfig.enabled() || !SoulHomeConfig.enforceBounds())
        {
            return;
        }

        final TerrainGrowthSettings settings = SoulHomeConfig.terrainGrowthSettings();

        if (!settings.enabled())
        {
            return;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(soulhome);
        final int rank = Math.min(data.ascensionRank(), SoulHomeConfig.maxRank());

        if (settings.bandWidth(rank, data.grownRank()) <= 0)
        {
            return;
        }

        final Integer settled = SETTLED.get(key);

        if (settled != null && settled == rank)
        {
            // already tried at this rank and found nothing to do - usually a soulhome whose island
            // is already out at its limit. Retrying it on every arrival would survey the whole box
            // again for the same answer.
            return;
        }

        ACTIVE.put(key, new GrowthJob(soulhome, settings, rank, data.grownRank()));

        // so the lens says "ground is still arriving" rather than reporting a reach that is about
        // to change under it
        resyncOwner(soulhome);
    }

    /**
     * Resend this soulhome's box, and with it the ground reach and the growing flag, to its owner
     * if they are online. Called on both edges of a growth job, which between them are every moment
     * either of those two numbers can move.
     */
    private static void resyncOwner(ServerLevel soulhome)
    {
        final MinecraftServer server = soulhome.getServer();

        if (server == null)
        {
            return;
        }

        DimensionHelper.soulOwner(soulhome).ifPresent(owner ->
        {
            final ServerPlayer player = server.getPlayerList().getPlayer(owner);

            if (player != null)
            {
                StructureScanService.refresh(player);
            }
        });
    }

    /** A rank moved, so whatever was concluded about this soulhome's ground no longer holds. */
    public static void rankChanged(ServerLevel soulhome)
    {
        SETTLED.remove(soulhome.dimension());
        considerGrowth(soulhome);
    }

    /** Whether ground is arriving in this soulhome right now, for the scan service and for reporting. */
    public static boolean isGrowing(ResourceKey<Level> key)
    {
        return ACTIVE.containsKey(key);
    }

    /** How far along growth is, from 0 to 1, or empty when none is running. */
    public static Optional<Double> progress(ResourceKey<Level> key)
    {
        final GrowthJob job = ACTIVE.get(key);
        return job == null ? Optional.empty() : Optional.of(job.progress());
    }

    /** A soul dimension unloaded mid-job. Growth stays due and picks up where it left off. */
    public static void forget(Level level)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            ACTIVE.remove(serverLevel.dimension());
            SETTLED.remove(serverLevel.dimension());
        }
    }

    public static void onServerTick(MinecraftServer server)
    {
        if (ACTIVE.isEmpty())
        {
            return;
        }

        for (Map.Entry<ResourceKey<Level>, GrowthJob> entry : Map.copyOf(ACTIVE).entrySet())
        {
            final ServerLevel level = server.getLevel(entry.getKey());

            if (level == null)
            {
                // unloaded under us; growth is still due and resumes when it is next opened
                ACTIVE.remove(entry.getKey());
                continue;
            }

            try
            {
                if (entry.getValue().step(level))
                {
                    finish(server, level, entry.getValue());
                }
            }
            catch (RuntimeException e)
            {
                // a failed job leaves grownRank where it was, so the next attempt completes what
                // this one started rather than the soulhome being stuck half grown forever
                LogHelper.error("Terrain growth for soulhome " + entry.getKey().location() + " failed: " + e);
                ACTIVE.remove(entry.getKey());
            }
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level, GrowthJob job)
    {
        final ResourceKey<Level> key = level.dimension();

        ACTIVE.remove(key);
        SETTLED.put(key, job.rank());

        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        data.setGrownRank(job.rank());

        LogHelper.info("Grew " + job.placedColumns() + " columns of ground in soulhome " + key.location()
                + " for rank " + job.rank() + ": " + job.serverMillis() + "ms of server thread across "
                + job.ticks() + " ticks, " + job.elapsedMillis() + "ms of wall clock");

        if (job.placedColumns() > 0)
        {
            // the new ground is real terrain the scanner has not seen. Requested rather than run
            // now, so the debounce still absorbs whatever the player is doing at the same moment.
            StructureScanService.requestNow(level);
            announce(server, level, job);
        }

        resyncOwner(level);
    }

    private static void announce(MinecraftServer server, ServerLevel level, GrowthJob job)
    {
        DimensionHelper.soulOwner(level).ifPresent(owner ->
        {
            final ServerPlayer player = server.getPlayerList().getPlayer(owner);

            if (player != null)
            {
                player.sendSystemMessage(Component.translatable(
                                Constants.StringKeys.GROWTH_COMPLETE, job.placedColumns(), job.plannedLimit())
                        .withStyle(ChatFormatting.GREEN));
            }
        });
    }

    /**
     * How far this soulhome's ground currently reaches, as a half-extent, read off the chunks that
     * happen to be loaded. What {@code /soulhome ascent} reports beside how far the walls do (#162).
     *
     * <p>Loaded chunks only, deliberately: this runs from a command, and a command must not
     * generate a soulhome's whole box to answer a question about it. A player standing in their own
     * soul has the chunks around them loaded, which is where their ground is.
     *
     * <p><b>Server thread only.</b>
     */
    public static int groundReach(ServerLevel level, SoulBounds bounds)
    {
        final TerrainGrowthSettings settings = SoulHomeConfig.terrainGrowthSettings();
        final RegionBounds box = bounds.toRegionBounds();
        final GroundSurvey survey = new GroundSurvey(box.minX(), box.minZ(), box.maxX(), box.maxZ());

        for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++)
        {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++)
            {
                if (level.hasChunk(chunkX, chunkZ))
                {
                    surveyChunk(level.getChunk(chunkX, chunkZ), survey, box, bounds.floorY(), settings.groundBand());
                }
            }
        }

        return ApronPlanner.groundReach(survey);
    }

    /**
     * Sort one chunk's columns into ground, built and void.
     *
     * <p>The height comes off the chunk's own {@code WORLD_SURFACE} heightmap rather than from a
     * cell-by-cell walk down the column: it is one lookup per column instead of up to thirty-six,
     * and it is the same number the chunk maintains for everything else.
     *
     * <p><b>The three classes are not "empty, full, and how full".</b> Ground is what the apron
     * grows out of; built is what it keeps away from; and the line between them is the height of
     * the ground band. A patio a player laid at floor level is ground and the island continues out
     * of it. A bridge they threw across the verge stands above the band, is built, keeps its moat,
     * and the island grows around it. See #160.
     *
     * <p>The island's own trees and hills come out as built too, and that is the right answer
     * rather than a misclassification worth fixing. Nothing here can tell a tree from a tower, and
     * the two readings fail very differently: called built, a tree notches the coastline beside it
     * and costs a handful of columns of apron; called ground, it would seed an apron at the height
     * of its own canopy, which is a shelf of grass in mid-air.
     */
    private static void surveyChunk(LevelChunk chunk, GroundSurvey survey, RegionBounds box, int floorY, int groundBand)
    {
        final int baseX = chunk.getPos().getMinBlockX();
        final int baseZ = chunk.getPos().getMinBlockZ();

        final int minX = Math.max(box.minX(), baseX);
        final int maxX = Math.min(box.maxX(), baseX + 15);
        final int minZ = Math.max(box.minZ(), baseZ);
        final int maxZ = Math.min(box.maxZ(), baseZ + 15);

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                // getHeight returns the Y of the first air above the topmost non-air block
                final int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

                if (top > floorY + groundBand)
                {
                    survey.set(x, z, GroundSurvey.Kind.BUILT, top);
                }
                else if (top >= floorY)
                {
                    survey.set(x, z, GroundSurvey.Kind.GROUND, top);
                }
                else
                {
                    // nothing at or above the floor. Whatever is below it is the island's own
                    // underside, which the box never covered and which an apron may sit above.
                    survey.set(x, z, GroundSurvey.Kind.VOID, 0);
                }
            }
        }
    }

    /** One soulhome's run of growth, from the survey through to the last block written. */
    private static final class GrowthJob
    {
        private enum Phase
        {
            SURVEY, PLAN, PLACE, DONE
        }

        private final TerrainGrowthSettings settings;
        private final int rank;
        private final int grownRank;
        private final SoulBounds bounds;
        private final RegionBounds box;
        private final RegionBounds legacyBox;
        private final long soulSeed;
        private final GroundSurvey survey;
        private final List<Long> surveyChunks;
        private final long startedAtMillis = System.currentTimeMillis();

        private Phase phase = Phase.SURVEY;
        private int surveyCursor;
        private List<Long> placeChunks = List.of();
        private Map<Long, List<ApronPlan.Column>> byChunk = Map.of();
        private int placeCursor;
        private int placedColumns;
        private int plannedLimit;
        private int ticks;
        private long serverNanos;

        private GrowthJob(ServerLevel level, TerrainGrowthSettings settings, int rank, int grownRank)
        {
            this.settings = settings;
            this.rank = rank;
            this.grownRank = grownRank;
            this.bounds = SoulHomeConfig.soulBounds(rank);
            this.box = surveyBox(this.bounds, settings, rank);
            this.legacyBox = SoulHomeBuffData.get(level).legacyBox().orElse(null);
            this.soulSeed = seedOf(level);
            this.survey = new GroundSurvey(this.box.minX(), this.box.minZ(), this.box.maxX(), this.box.maxZ());
            this.surveyChunks = chunksOf(this.box);
        }

        private int rank()
        {
            return this.rank;
        }

        private int placedColumns()
        {
            return this.placedColumns;
        }

        private int plannedLimit()
        {
            return this.plannedLimit;
        }

        private int ticks()
        {
            return this.ticks;
        }

        /**
         * Server-thread time this job has actually cost, summed in nanoseconds and converted once.
         * Accumulating in milliseconds instead would truncate every tick under one to zero, which
         * is most of them - and would report a job that cost real time as having cost none, which
         * is the opposite of what #161 asks a growth job to be able to say about itself.
         */
        private long serverMillis()
        {
            return this.serverNanos / 1_000_000L;
        }

        /** Wall-clock time from the job starting to it finishing - how long the ground took to arrive. */
        private long elapsedMillis()
        {
            return System.currentTimeMillis() - this.startedAtMillis;
        }

        private double progress()
        {
            // survey and placement are counted as half the job each, which is roughly how they
            // divide in practice and is close enough for "roughly how far along" (#162)
            final double surveyed = this.surveyChunks.isEmpty()
                    ? 1.0 : (double) this.surveyCursor / this.surveyChunks.size();
            final double placed = this.placeChunks.isEmpty()
                    ? (this.phase == Phase.SURVEY || this.phase == Phase.PLAN ? 0.0 : 1.0)
                    : (double) this.placeCursor / this.placeChunks.size();

            return Math.min(1.0, 0.5 * surveyed + 0.5 * placed);
        }

        /** Advance one tick's worth. @return whether the job is finished */
        private boolean step(ServerLevel level)
        {
            final long startedAt = System.nanoTime();
            this.ticks++;

            try
            {
                return switch (this.phase)
                {
                    case SURVEY -> stepSurvey(level);
                    case PLAN -> stepPlan();
                    case PLACE -> stepPlace(level);
                    case DONE -> true;
                };
            }
            finally
            {
                this.serverNanos += System.nanoTime() - startedAt;
            }
        }

        private boolean stepSurvey(ServerLevel level)
        {
            final int budget = this.settings.chunksPerTick();

            for (int done = 0; done < budget && this.surveyCursor < this.surveyChunks.size(); done++)
            {
                final long packed = this.surveyChunks.get(this.surveyCursor++);
                final int chunkX = (int) (packed >> 32);
                final int chunkZ = (int) packed;

                // see the class javadoc on #122: this dimension's generator writes nothing, so a
                // chunk that has never existed costs an allocation rather than terrain generation
                surveyChunk(level.getChunk(chunkX, chunkZ), this.survey, this.box,
                        this.bounds.floorY(), this.settings.groundBand());
            }

            if (this.surveyCursor >= this.surveyChunks.size())
            {
                this.phase = Phase.PLAN;
            }

            return false;
        }

        private boolean stepPlan()
        {
            final ApronPlan plan = ApronPlanner.plan(
                    this.survey, this.settings, this.rank, this.grownRank, this.bounds.vergeHalfExtent(),
                    this.legacyBox, this.soulSeed);

            this.plannedLimit = plan.groundLimit();

            final Map<Long, List<ApronPlan.Column>> grouped = new HashMap<>();

            for (ApronPlan.Column column : plan.columns())
            {
                grouped.computeIfAbsent(chunkKey(column.x() >> 4, column.z() >> 4), ignored -> new ArrayList<>())
                        .add(column);
            }

            this.byChunk = grouped;
            this.placeChunks = grouped.keySet().stream().sorted().toList();
            this.phase = this.placeChunks.isEmpty() ? Phase.DONE : Phase.PLACE;

            return this.phase == Phase.DONE;
        }

        private boolean stepPlace(ServerLevel level)
        {
            final int budget = this.settings.chunksPerTick();

            for (int done = 0; done < budget && this.placeCursor < this.placeChunks.size(); done++)
            {
                final long packed = this.placeChunks.get(this.placeCursor++);
                level.getChunk((int) (packed >> 32), (int) packed);

                for (ApronPlan.Column column : this.byChunk.get(packed))
                {
                    placeColumn(level, column);
                }
            }

            if (this.placeCursor >= this.placeChunks.size())
            {
                this.phase = Phase.DONE;
                return true;
            }

            return false;
        }

        /**
         * Write one column of apron, in the blocks of the ground column it grew from.
         *
         * <p>Every cell is re-checked for air immediately before it is written, not merely at
         * survey time. A survey of a rank V box takes several seconds of ticks, and a player can
         * place a block inside that window; the survey is what decides <i>where</i> ground may go,
         * and this is what makes "no block a player placed is ever replaced" true regardless.
         */
        private void placeColumn(ServerLevel level, ApronPlan.Column column)
        {
            final int layers = this.settings.layersAt(column.surfaceY(), this.bounds.floorY());

            if (layers <= 0)
            {
                return;
            }

            final BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
            final BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos();
            boolean placedAny = false;

            for (int depth = 0; depth < layers; depth++)
            {
                final int y = column.surfaceY() - depth;
                target.set(column.x(), y, column.z());

                if (!level.getBlockState(target).isAir())
                {
                    continue;
                }

                source.set(column.sourceX(), y, column.sourceZ());
                BlockState material = level.getBlockState(source);

                if (material.isAir())
                {
                    // the source column is thinner than this one would be. Carry its lowest solid
                    // block down rather than leaving a hole, so an apron off a shallow shelf is
                    // still a shelf and not a grate.
                    material = deepestSolid(level, column.sourceX(), column.sourceZ(), column.surfaceY(), layers);
                }

                if (material.isAir() || isOccupiedByAnyone(level, target))
                {
                    continue;
                }

                // UPDATE_CLIENTS and nothing else: neighbour updates across tens of thousands of
                // new blocks would be a cascade of block ticks for grass that has no neighbour to
                // notify anyway, and the whole point of the job is that it is cheap per tick
                level.setBlock(target.immutable(), material, Block.UPDATE_CLIENTS);
                placedAny = true;
            }

            if (placedAny)
            {
                this.placedColumns++;
            }
        }

        private static BlockState deepestSolid(ServerLevel level, int x, int z, int surfaceY, int layers)
        {
            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            BlockState deepest = level.getBlockState(cursor.set(x, surfaceY, z));

            for (int depth = 1; depth < layers; depth++)
            {
                final BlockState below = level.getBlockState(cursor.set(x, surfaceY - depth, z));

                if (below.isAir())
                {
                    break;
                }

                deepest = below;
            }

            return deepest;
        }

        /**
         * Whether anyone is standing in this cell. A player who walked out into the verge while
         * their soul was growing must not be sealed into the ground that arrives around them
         * (#160); they simply get a hole where they were, which the next run of growth fills once
         * they have moved.
         */
        private static boolean isOccupiedByAnyone(ServerLevel level, BlockPos pos)
        {
            final AABB cell = new AABB(pos);

            for (Player player : level.players())
            {
                if (player.getBoundingBox().intersects(cell))
                {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * A soul's own identity as a number, so its coastline is its own and is the same every time it
     * grows. Soul dimensions are named after their owner's UUID, so there is nothing to store.
     */
    private static long seedOf(ServerLevel level)
    {
        return DimensionHelper.soulOwner(level)
                .map(owner -> owner.getMostSignificantBits() ^ owner.getLeastSignificantBits())
                .orElseGet(() -> (long) level.dimension().location().toString().hashCode());
    }

    /**
     * The part of the box worth surveying: the ground limit, plus enough margin that a build
     * standing just outside the limit still casts its clearance moat inward.
     *
     * <p>Not the whole box, because surveying a chunk means loading it, and loading a chunk that
     * has never existed writes an empty one to disk. A rank V box is about 170 chunks and its
     * ground limit is about 100 of them; the sixty-odd in between can never hold a block of apron
     * whatever they contain, so there is nothing to learn by creating them.
     */
    private static RegionBounds surveyBox(SoulBounds bounds, TerrainGrowthSettings settings, int rank)
    {
        final RegionBounds box = bounds.toRegionBounds();
        final int reach = settings.groundLimit(rank, bounds.vergeHalfExtent()) + settings.clearanceMargin();

        return new RegionBounds(
                Math.max(box.minX(), -reach), box.minY(), Math.max(box.minZ(), -reach),
                Math.min(box.maxX(), reach), box.maxY(), Math.min(box.maxZ(), reach));
    }

    private static List<Long> chunksOf(RegionBounds box)
    {
        final List<Long> chunks = new ArrayList<>();

        for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++)
        {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++)
            {
                chunks.add(chunkKey(chunkX, chunkZ));
            }
        }

        return List.copyOf(chunks);
    }

    private static long chunkKey(int chunkX, int chunkZ)
    {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
