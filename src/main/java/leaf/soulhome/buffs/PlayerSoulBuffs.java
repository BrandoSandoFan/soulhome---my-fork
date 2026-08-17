/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.SoulBuffSet;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The buffs one player is currently carrying.
 *
 * <p>Attached to the player rather than to a level, because that is where they have to live:
 * earned inside a soulhome, spent in the overworld, and expected to survive a death, a dimension
 * change and a relog.
 */
public class PlayerSoulBuffs
{
    private SoulBuffSet buffs = SoulBuffSet.empty();

    public SoulBuffSet get()
    {
        return this.buffs;
    }

    /**
     * @return whether this actually changed anything, so callers can skip a pointless client sync
     */
    public boolean set(SoulBuffSet newBuffs)
    {
        SoulBuffSet replacement = newBuffs == null ? SoulBuffSet.empty() : newBuffs;

        if (this.buffs.equals(replacement))
        {
            return false;
        }

        this.buffs = replacement;
        return true;
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();

        for (Map.Entry<String, Double> entry : this.buffs.asMap().entrySet())
        {
            tag.putDouble(entry.getKey(), entry.getValue());
        }

        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        if (tag == null)
        {
            this.buffs = SoulBuffSet.empty();
            return;
        }

        Map<String, Double> magnitudes = new LinkedHashMap<>();

        for (String key : tag.getAllKeys())
        {
            magnitudes.put(key, tag.getDouble(key));
        }

        // SoulBuffSet.of drops anything at or below zero, so a save from an older build that
        // stored a since-removed buff at zero does not linger
        this.buffs = SoulBuffSet.of(magnitudes);
    }
}
