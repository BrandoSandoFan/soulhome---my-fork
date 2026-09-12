/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.constants.Constants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * How a bond reads to a player: the relation as a phrase and the other room by its name, never
 * either one's id (#103). "opens into your Enchanting Room", not "connects soulhome:enchanting_room".
 */
public final class BondNames
{
    private BondNames()
    {
    }

    /** The relation as a verb phrase to precede "your <room>" - "shares a wall with", "opens into". */
    public static MutableComponent relation(String relationId)
    {
        return Component.translatable(Constants.StringKeys.BOND_RELATION_PREFIX + relationId);
    }

    /**
     * The other room's name. An archetype's display name is a translation key in its datapack,
     * so it is resolved rather than printed; one with no translation shows its key, which is the
     * right nudge to whoever wrote the pack.
     */
    public static MutableComponent room(String displayName, String archetypeId)
    {
        return displayName == null || displayName.isBlank()
                ? Component.literal(archetypeId)
                : Component.translatable(displayName);
    }
}
