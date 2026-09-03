/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulRegion;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the last scan of one soulhome found, saved with that soulhome's level.
 *
 * <p>Soul dimensions are one per player, so per-level saved data is already per-soulhome and needs
 * no further keying.
 *
 * <p>Two things are stored. The awarded rooms let a player's buffs be restored on login without
 * touching a single block. The content hash lets an untouched soulhome skip the classification and
 * buff-push half of a scan entirely - a player who places a torch and immediately breaks it has
 * changed nothing, and should cost nothing.
 */
public class SoulHomeBuffData extends SavedData
{
    /** File name under the level's data folder. */
    public static final String NAME = "soulhome_structures";

    private static final String KEY_ROOMS = "Rooms";
    private static final String KEY_ARCHETYPE = "Archetype";
    private static final String KEY_TIER = "Tier";
    private static final String KEY_SCORE = "Score";
    private static final String KEY_CONTENT_HASH = "ContentHash";
    private static final String KEY_SCANNED = "Scanned";

    // The legacy grant (#80): every soulhome that existed before the box in #79 landed is, by
    // definition, already over the line - most of them by a lot, since 256 free blocks of vertical
    // space made stacking floors the sensible thing to do. Rule 4 of the Ascent epic is that no
    // build loses what it already earned, so a pre-existing soulhome's own footprint - captured
    // once, on the first post-update scan that can actually see it - stays placeable and scannable
    // forever, on top of whatever the current rank's box grants.
    private static final String KEY_DATA_VERSION = "DataVersion";
    private static final String KEY_LEGACY_BOX = "LegacyBox";

    // The Ascent, phase two (#84): rank lives here, not on the player and not on the Soul Anchor
    // block (#83) - a soul can be visited by more than one person, rank is the *soul's*, and
    // breaking the anchor with a stray pickaxe swing must not cost four ranks of progress.
    private static final String KEY_ASCENSION_RANK = "AscensionRank";

    // Sublime Essence's soul-residue tap (#82): also the soulhome's, not the player's or the
    // anchor's, for exactly the same reason rank is. LastResidueAccrualMillis is real wall-clock
    // time, not a tick count, because residue has to keep earning while nobody is around to tick it
    // - see accrueResidue.
    private static final String KEY_RESIDUE = "Residue";
    private static final String KEY_LAST_RESIDUE_ACCRUAL_MILLIS = "LastResidueAccrualMillis";

    /**
     * Bumped once, for the legacy grant. A save written before this field existed reads back as
     * version 0 - {@code CompoundTag.getInt} on a missing key is 0 - and is exactly the set of
     * soulhomes the migration in {@link #migrateLegacyBoundsIfNeeded} needs to look at.
     */
    private static final int CURRENT_DATA_VERSION = 1;

    private List<AwardedRoom> awardedRooms = List.of();
    private long contentHash;
    private boolean scanned;
    private int dataVersion = CURRENT_DATA_VERSION;
    private RegionBounds legacyBox;
    private int ascensionRank;
    private double residue;
    private long lastResidueAccrualMillis;

    public SoulHomeBuffData()
    {
        // a soulhome with no save file yet is a soulhome created after the box existed - it never
        // gets a legacy grant, and needs no migration to skip.
        //
        // starting_rank is read here, not as a field default, because load() below always
        // overwrites this with the saved value afterwards - reading it here reaches only the one
        // case starting_rank is for: a soulhome that has never been saved before. A save written
        // before this field existed reads back as rank 0 via the same missing-key default every
        // other field in this class already relies on, never as whatever starting_rank happens to
        // be configured to today.
        this.ascensionRank = SoulHomeConfig.startingRank();
    }

    public static SoulHomeBuffData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(SoulHomeBuffData::load, SoulHomeBuffData::new, NAME);
    }

    public static SoulHomeBuffData load(CompoundTag tag)
    {
        SoulHomeBuffData data = new SoulHomeBuffData();
        List<AwardedRoom> rooms = new ArrayList<>();

        ListTag list = tag.getList(KEY_ROOMS, Tag.TAG_COMPOUND);

        for (int index = 0; index < list.size(); index++)
        {
            CompoundTag room = list.getCompound(index);
            final String archetype = room.getString(KEY_ARCHETYPE);
            final int tier = room.getInt(KEY_TIER);

            // a save written by a different version, or hand-edited: skip the row rather than
            // failing the whole level's data
            if (archetype.isBlank() || tier < 1)
            {
                continue;
            }

            rooms.add(new AwardedRoom(archetype, tier, room.getDouble(KEY_SCORE)));
        }

        data.awardedRooms = List.copyOf(rooms);
        data.contentHash = tag.getLong(KEY_CONTENT_HASH);
        data.scanned = tag.getBoolean(KEY_SCANNED);
        // absent on any save written before the legacy grant existed - reads back as 0, which is
        // exactly "not migrated yet"
        data.dataVersion = tag.getInt(KEY_DATA_VERSION);
        // absent on any save written before rank existed - reads back as 0, same as every soulhome
        // actually was before the ascension ritual (#83) could raise it
        data.ascensionRank = tag.getInt(KEY_ASCENSION_RANK);
        data.residue = tag.getDouble(KEY_RESIDUE);
        // absent on any save written before residue existed, and on a soulhome's very first save -
        // reads back as 0, which accrueResidue treats as "start the clock now" rather than as an
        // actual instant in 1970 to bill for
        data.lastResidueAccrualMillis = tag.getLong(KEY_LAST_RESIDUE_ACCRUAL_MILLIS);

        if (tag.contains(KEY_LEGACY_BOX))
        {
            int[] box = tag.getIntArray(KEY_LEGACY_BOX);

            if (box.length == 6)
            {
                try
                {
                    data.legacyBox = new RegionBounds(box[0], box[1], box[2], box[3], box[4], box[5]);
                }
                catch (IllegalArgumentException e)
                {
                    // a hand-edited or corrupt box: drop it rather than carry an inverted region
                    // forward forever. The next successful scan will just re-migrate.
                    LogHelper.warn("Discarding an invalid legacy soulhome box: " + e);
                    data.dataVersion = 0;
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();

        for (AwardedRoom room : this.awardedRooms)
        {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ARCHETYPE, room.archetypeId());
            entry.putInt(KEY_TIER, room.tier());
            entry.putDouble(KEY_SCORE, room.score());
            list.add(entry);
        }

        tag.put(KEY_ROOMS, list);
        tag.putLong(KEY_CONTENT_HASH, this.contentHash);
        tag.putBoolean(KEY_SCANNED, this.scanned);
        tag.putInt(KEY_DATA_VERSION, this.dataVersion);
        tag.putInt(KEY_ASCENSION_RANK, this.ascensionRank);
        tag.putDouble(KEY_RESIDUE, this.residue);
        tag.putLong(KEY_LAST_RESIDUE_ACCRUAL_MILLIS, this.lastResidueAccrualMillis);

        if (this.legacyBox != null)
        {
            tag.putIntArray(KEY_LEGACY_BOX, new int[] {
                    this.legacyBox.minX(), this.legacyBox.minY(), this.legacyBox.minZ(),
                    this.legacyBox.maxX(), this.legacyBox.maxY(), this.legacyBox.maxZ()
            });
        }

        return tag;
    }

    /**
     * Whether this soulhome predates the box in #79 and so carries a legacy grant.
     *
     * @return the box captured at migration, or empty for a soulhome created after the box existed
     */
    public Optional<RegionBounds> legacyBox()
    {
        return Optional.ofNullable(this.legacyBox);
    }

    /**
     * Whether this soulhome's legacy grant, if it has one, is still unknown. True only for the
     * brief window between a save written before #79 loading and its first successful scan -
     * placement enforcement treats this as "don't know yet, so don't block" rather than risk
     * refusing a legacy player's own pre-existing build before the migration that would have
     * protected it has had a chance to run.
     */
    public boolean needsLegacyMigration()
    {
        return this.dataVersion < CURRENT_DATA_VERSION;
    }

    /**
     * Capture this soulhome's pre-existing footprint the first time it can actually be read, so
     * rule 4 of the Ascent epic - no build loses what it already earned - holds for every soulhome
     * that existed before the box did. A no-op once this has succeeded, and safe to call on every
     * scan attempt until it does. <b>Server thread only</b>, same as the capture it reuses.
     */
    public void migrateLegacyBoundsIfNeeded(ServerLevel level)
    {
        if (this.dataVersion >= CURRENT_DATA_VERSION)
        {
            return;
        }

        if (!SnapshotBlockVolume.hasLoadedChunks(level))
        {
            // can't see this soulhome yet - try again on a later scan rather than concluding there
            // is nothing to preserve
            return;
        }

        final Optional<RegionBounds> found = SnapshotBlockVolume.populatedBounds(level);

        this.legacyBox = found.orElse(null);
        this.dataVersion = CURRENT_DATA_VERSION;
        setDirty();

        LogHelper.info("Migrated legacy soulhome bounds for " + level.dimension().location() + ": "
                + found.map(RegionBounds::toString).orElse("nothing built yet"));
    }

    public List<AwardedRoom> awardedRooms()
    {
        return this.awardedRooms;
    }

    /**
     * This soulhome's ascension rank, 0 (unascended) upward. Not clamped against the configured
     * {@code max_rank} here - {@link leaf.soulhome.structures.core.SoulBounds#forRank} is where
     * that clamp lives, so a pack that later lowers {@code max_rank} below a rank a soulhome
     * already reached loses nothing on disk, it is just clamped back down wherever the box is
     * computed from it.
     */
    public int ascensionRank()
    {
        return this.ascensionRank;
    }

    /**
     * Set this soulhome's ascension rank directly - used by the ascension ritual (#83) on success
     * and by {@code /soulhome ascent set} for operators. Clamped to a non-negative value only;
     * see {@link #ascensionRank()} for why the upper bound is not enforced here.
     *
     * @return whether the rank actually changed, and so whether this needs writing to disk
     */
    public boolean setAscensionRank(int rank)
    {
        final int clamped = Math.max(0, rank);

        if (clamped == this.ascensionRank)
        {
            return false;
        }

        this.ascensionRank = clamped;
        setDirty();
        return true;
    }

    /** Soul residue accrued so far - the primary tap of Sublime Essence (#82), spent at the Soul Anchor (#83). */
    public double residue()
    {
        return this.residue;
    }

    /**
     * Credit this soulhome for whatever real time has passed since it was last measured, at a rate
     * set by {@code totalScore} - the same total awarded room score {@code BuffCalculator} already
     * computes. Called after every completed scan, which is the schedule #82 asks for ("the same
     * schedule the scan service already runs on; no new timer") and also the one that happens to
     * fire on login even for a soulhome nobody has opened in days - so residue keeps accruing
     * across being offline, not just across being online and idle.
     *
     * <p>The first call ever made for a soulhome - a fresh save, or one written before residue
     * existed - only starts the clock. Crediting the gap between 1970 and now would hand every
     * soulhome that already exists years of backlogged residue the instant this update lands.
     *
     * @return whether anything changed, and so whether this needs writing to disk
     */
    public boolean accrueResidue(double totalScore)
    {
        final long now = System.currentTimeMillis();

        if (this.lastResidueAccrualMillis <= 0L)
        {
            this.lastResidueAccrualMillis = now;
            setDirty();
            return true;
        }

        final long elapsed = now - this.lastResidueAccrualMillis;

        if (elapsed <= 0)
        {
            // clock went backwards, or two accruals landed in the same millisecond: neither is
            // worth crediting, and leaving the stamp alone means the next genuine gap still counts
            return false;
        }

        if (!SoulHomeConfig.residueTapEnabled())
        {
            this.lastResidueAccrualMillis = now;
            setDirty();
            return true;
        }

        final double gained = SoulHomeConfig.essenceSettings().residueGained(totalScore, elapsed);

        this.lastResidueAccrualMillis = now;
        this.residue += gained;
        setDirty();
        return true;
    }

    /** Whether this soulhome has ever been scanned, as distinct from scanned and found empty. */
    public boolean hasBeenScanned()
    {
        return this.scanned;
    }

    public long contentHash()
    {
        return this.contentHash;
    }

    /**
     * Record a completed scan.
     *
     * @return whether anything changed, and so whether this needs writing to disk. Deliberately
     *         not the question "do buffs need pushing": a player's buffs can be wrong while these
     *         results are right, and skipping the push on an unchanged scan is how they stay wrong
     */
    public boolean update(List<AwardedRoom> rooms, long contentHash)
    {
        final boolean changed = !this.scanned
                || this.contentHash != contentHash
                || !this.awardedRooms.equals(rooms);

        this.awardedRooms = List.copyOf(rooms);
        this.contentHash = contentHash;
        this.scanned = true;

        if (changed)
        {
            setDirty();
        }

        return changed;
    }

    /**
     * Combined identity of every region found, order-independent so that the same build always
     * hashes the same however the scan happened to walk it.
     */
    public static long hashOf(List<SoulRegion> regions)
    {
        long hash = 0L;

        for (SoulRegion region : regions)
        {
            // xor rather than a running product: regions come back in score order, which can
            // change without a single block moving
            hash ^= region.identityHash();
        }

        return hash;
    }
}
