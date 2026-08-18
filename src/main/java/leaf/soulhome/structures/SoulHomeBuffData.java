/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.SoulRegion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

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

    private List<AwardedRoom> awardedRooms = List.of();
    private long contentHash;
    private boolean scanned;

    public SoulHomeBuffData()
    {
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

        return tag;
    }

    public List<AwardedRoom> awardedRooms()
    {
        return this.awardedRooms;
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
     * @return whether anything changed, and so whether buffs need pushing again
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
