/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.AbilityCharges;
import leaf.soulhome.structures.core.SoulBuffSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The buffs one player is currently carrying, and the state of the ones they press (#87).
 *
 * <p>Attached to the player rather than to a level, because that is where they have to live:
 * earned inside a soulhome, spent in the overworld, and expected to survive a death, a dimension
 * change and a relog.
 *
 * <p>Charges and cooldowns live here for the same reason and one more: they are the player's, not
 * the soulhome's. A player who logs out halfway through a recharge should come back halfway through
 * it rather than fully loaded, which is only true if the clock is saved with them.
 */
public class PlayerSoulBuffs
{
    /**
     * Reserved NBT keys, prefixed so they cannot collide with a buff id. Buff magnitudes are stored
     * as bare doubles at the root of the tag - a format from before there was anything else to keep
     * - and a namespaced id can never start with {@code $}, so this stays unambiguous without a
     * migration.
     */
    private static final String ABILITIES_KEY = "$abilities";
    private static final String SELECTED_KEY = "$selected";
    private static final String RANK_KEY = "$rank";
    private static final String CHARGES_KEY = "charges";
    private static final String CLOCK_KEY = "clock";

    private SoulBuffSet buffs = SoulBuffSet.empty();
    private final Map<String, AbilityCharges> abilities = new LinkedHashMap<>();
    private String selectedAbility = "";

    // The ascension rank (#84) these buffs were computed at, cached here rather than looked up
    // fresh: SoulBuffs.magnitude() re-clamps every read against the buff type's cap (#85 raises
    // that cap per rank), and it is called on every hit and every block broken - a level and
    // SavedData lookup on that path is a cost every effect would pay for a number that is already
    // known the moment these buffs were set.
    private int rank;

    public SoulBuffSet get()
    {
        return this.buffs;
    }

    /** The rank {@link #get()} was computed at - see the field's own doc for why this is cached. */
    public int rank()
    {
        return this.rank;
    }

    /**
     * @return whether this actually changed anything, so callers can skip a pointless client sync
     */
    public boolean set(SoulBuffSet newBuffs, int newRank)
    {
        SoulBuffSet replacement = newBuffs == null ? SoulBuffSet.empty() : newBuffs;

        // cached regardless of whether the buffs themselves changed: a rank raised with
        // ascensionPerRank at 0 still has to be remembered, or the very next magnitude read
        // reclamps against the un-raised cap.
        this.rank = Math.max(0, newRank);

        if (this.buffs.equals(replacement))
        {
            return false;
        }

        this.buffs = replacement;

        // an ability whose room was demolished should not keep a bank of charges waiting for the
        // day it is rebuilt; a rebuilt room granting it again starts it fresh, from full
        this.abilities.keySet().removeIf(type -> replacement.magnitude(type) <= 0d);

        if (!this.selectedAbility.isEmpty() && replacement.magnitude(this.selectedAbility) <= 0d)
        {
            this.selectedAbility = "";
        }

        return true;
    }

    /** This ability's charges and clock, or an empty bank if it has never been granted. */
    public AbilityCharges chargesOf(String abilityType)
    {
        return this.abilities.getOrDefault(abilityType, AbilityCharges.EMPTY);
    }

    public void setCharges(String abilityType, AbilityCharges charges)
    {
        if (abilityType == null || abilityType.isEmpty() || charges == null)
        {
            return;
        }

        this.abilities.put(abilityType, charges);
    }

    public boolean hasChargesFor(String abilityType)
    {
        return this.abilities.containsKey(abilityType);
    }

    public Map<String, AbilityCharges> allCharges()
    {
        return Map.copyOf(this.abilities);
    }

    /** The ability the use key fires. Empty when the player has none, or has not chosen yet. */
    public String selectedAbility()
    {
        return this.selectedAbility;
    }

    public void selectAbility(String abilityType)
    {
        this.selectedAbility = abilityType == null ? "" : abilityType;
    }

    /**
     * Empties every bank and restarts every clock - what #87 asks for on death. Applied to whatever
     * the player currently owns rather than clearing the map, so an ability stays listed in the HUD
     * while it is recharging rather than vanishing until its first charge lands.
     */
    public void resetOnDeath()
    {
        for (Map.Entry<String, AbilityCharges> entry : this.abilities.entrySet())
        {
            entry.setValue(AbilityCharges.afterDeath(entry.getValue().ticksToNextCharge()));
        }
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();

        for (Map.Entry<String, Double> entry : this.buffs.asMap().entrySet())
        {
            tag.putDouble(entry.getKey(), entry.getValue());
        }

        CompoundTag abilityTag = new CompoundTag();

        for (Map.Entry<String, AbilityCharges> entry : this.abilities.entrySet())
        {
            CompoundTag state = new CompoundTag();
            state.putInt(CHARGES_KEY, entry.getValue().charges());
            state.putInt(CLOCK_KEY, entry.getValue().ticksToNextCharge());
            abilityTag.put(entry.getKey(), state);
        }

        tag.put(ABILITIES_KEY, abilityTag);
        tag.putString(SELECTED_KEY, this.selectedAbility);
        tag.putInt(RANK_KEY, this.rank);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        this.abilities.clear();
        this.selectedAbility = "";
        this.rank = 0;

        if (tag == null)
        {
            this.buffs = SoulBuffSet.empty();
            return;
        }

        Map<String, Double> magnitudes = new LinkedHashMap<>();

        for (String key : tag.getAllKeys())
        {
            // only the bare doubles at the root are magnitudes; the reserved compounds are read
            // below. Checking the tag type rather than the key name means a future reserved key
            // cannot be mistaken for a buff worth zero either.
            if (tag.getTagType(key) == Tag.TAG_DOUBLE)
            {
                magnitudes.put(key, tag.getDouble(key));
            }
        }

        // SoulBuffSet.of drops anything at or below zero, so a save from an older build that
        // stored a since-removed buff at zero does not linger
        this.buffs = SoulBuffSet.of(magnitudes);

        CompoundTag abilityTag = tag.getCompound(ABILITIES_KEY);

        for (String key : abilityTag.getAllKeys())
        {
            CompoundTag state = abilityTag.getCompound(key);
            this.abilities.put(
                    key,
                    new AbilityCharges(
                            Math.max(0, state.getInt(CHARGES_KEY)), Math.max(0, state.getInt(CLOCK_KEY))));
        }

        this.selectedAbility = tag.getString(SELECTED_KEY);

        // absent on any save written before rank existed - reads back as 0, matching a soulhome
        // that has never ascended, same as SoulHomeBuffData's own missing-key default
        this.rank = Math.max(0, tag.getInt(RANK_KEY));
    }
}
