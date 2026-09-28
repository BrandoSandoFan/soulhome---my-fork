/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.AbilityCharges;
import leaf.soulhome.structures.core.SoulBuffSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
public class PlayerSoulBuffs implements INBTSerializable<CompoundTag>
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
    private static final String MAX_KEY = "max";
    private static final String COOLDOWN_KEY = "cooldown";

    private SoulBuffSet buffs = SoulBuffSet.empty();
    private final Map<String, AbilityCharges> abilities = new LinkedHashMap<>();

    /**
     * The ceiling and cooldown each bank last recharged at while its ability was owned, so a bank
     * whose room has gone dormant can go on recharging at the same pace - its own magnitude is zero
     * by then, and says nothing about how fast it used to fill (#275).
     */
    private final Map<String, Rate> rates = new LinkedHashMap<>();
    private String selectedAbility = "";

    /**
     * The trophy room's targeted knockback resistance (#196): who this player takes less
     * knockback from, and by how much. Recomputed alongside {@link #buffs} on every scan and every
     * login, never persisted - the mounted heads it is derived from already are, in
     * {@code SoulHomeBuffData}, and re-deriving it there is what keeps "the head comes down, the
     * grudge goes with it on the next scan" true without a save format of its own.
     */
    private Map<UUID, Double> knockbackGrudges = Map.of();

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

        // an ability whose room was demolished or unbound keeps its bank rather than losing it, and
        // tickDormant goes on filling it. Deleting it here was what let unbinding and at once
        // rebinding an ability room refill it for free: the room going to zero deleted the bank,
        // and being granted again handed it back full (#275). SoulAbilities only shows and fires
        // abilities a player currently owns, so a dormant bank is invisible until then.

        if (!this.selectedAbility.isEmpty() && replacement.magnitude(this.selectedAbility) <= 0d)
        {
            this.selectedAbility = "";
        }

        return true;
    }

    /** How much less knockback this player takes from {@code attacker}, zero for no grudge. */
    public double grudgeAgainst(UUID attacker)
    {
        return this.knockbackGrudges.getOrDefault(attacker, 0d);
    }

    public void setGrudges(Map<UUID, Double> grudges)
    {
        this.knockbackGrudges = grudges == null || grudges.isEmpty() ? Map.of() : Map.copyOf(grudges);
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

    /** Remembers the pace an owned ability recharges at, for {@link #tickDormant}. */
    public void noteRate(String abilityType, int maxCharges, int cooldownTicks)
    {
        if (abilityType == null || abilityType.isEmpty())
        {
            return;
        }

        final Rate rate = new Rate(Math.max(0, maxCharges), Math.max(0, cooldownTicks));

        // called every tick for every owned ability, and the pace almost never changes
        if (!rate.equals(this.rates.get(abilityType)))
        {
            this.rates.put(abilityType, rate);
        }
    }

    /**
     * One tick of recharging for every bank whose ability is not in {@code owned}, at the pace it
     * last had. A bank that reaches full is dropped: a fresh grant arrives full anyway, so nothing
     * is lost, and a player who never rebuilds a room does not carry its bank forever. A bank with
     * no recorded pace (never owned since this was saved) is dropped too, as the old code did -
     * there is nothing to recharge it by, and frozen empty forever would be worse than full.
     */
    public void tickDormant(Collection<String> owned)
    {
        final Iterator<Map.Entry<String, AbilityCharges>> it = this.abilities.entrySet().iterator();

        while (it.hasNext())
        {
            final Map.Entry<String, AbilityCharges> entry = it.next();

            if (owned.contains(entry.getKey()))
            {
                continue;
            }

            final Rate rate = this.rates.get(entry.getKey());

            if (rate == null || rate.maxCharges() <= 0)
            {
                it.remove();
                this.rates.remove(entry.getKey());
                continue;
            }

            final AbilityCharges after = entry.getValue().tick(rate.maxCharges(), rate.cooldownTicks());

            if (after.charges() >= rate.maxCharges())
            {
                it.remove();
                this.rates.remove(entry.getKey());
            }
            else
            {
                entry.setValue(after);
            }
        }
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

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider)
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

            final Rate rate = this.rates.get(entry.getKey());

            if (rate != null)
            {
                state.putInt(MAX_KEY, rate.maxCharges());
                state.putInt(COOLDOWN_KEY, rate.cooldownTicks());
            }
            abilityTag.put(entry.getKey(), state);
        }

        tag.put(ABILITIES_KEY, abilityTag);
        tag.putString(SELECTED_KEY, this.selectedAbility);
        tag.putInt(RANK_KEY, this.rank);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag)
    {
        this.abilities.clear();
        this.rates.clear();
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

            // absent on a save from before dormant banks were kept - noteRate fills it on the
            // first tick the ability is owned, and tickDormant drops a bank that never gets one
            if (state.contains(MAX_KEY))
            {
                this.rates.put(key, new Rate(Math.max(0, state.getInt(MAX_KEY)), Math.max(0, state.getInt(COOLDOWN_KEY))));
            }
        }

        this.selectedAbility = tag.getString(SELECTED_KEY);

        // absent on any save written before rank existed - reads back as 0, matching a soulhome
        // that has never ascended, same as SoulHomeBuffData's own missing-key default
        this.rank = Math.max(0, tag.getInt(RANK_KEY));
    }

    private record Rate(int maxCharges, int cooldownTicks)
    {
    }
}
