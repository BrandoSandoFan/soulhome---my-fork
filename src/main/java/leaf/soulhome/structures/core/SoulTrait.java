/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * What one room says about the soul it stands in - the Ambience epic (#163/#165).
 *
 * <p>An archetype declares these in its own JSON, as {@code "character": {"warm": 1.0}}, so a
 * datapack's room colours the soul it is built in with no Java change. What a trait <i>looks and
 * sounds like</i> is the part that stays in Java ({@link SoulAmbience}), because a colour in a
 * datapack would let a pack write a soul that is unreadably dark or that strobes, and #167 rules
 * both out at the level of the mechanism rather than by asking nicely.
 *
 * <p>A closed vocabulary, like {@code BondRelations} and the form clauses: an archetype naming a
 * trait that is not here contributes nothing and says so in the log at load, rather than being
 * rejected. A pack written against a later version of the mod should lose its colouring, not its
 * rooms.
 *
 * <p>These names are never shown to a player. See {@link SoulAxis} for why.
 */
public enum SoulTrait
{
    /** Fire, forge and hearth. */
    WARM("warm", SoulAxis.THERMAL, true),

    /** Ice, water and the high thin air. */
    COLD("cold", SoulAxis.THERMAL, false),

    /** Enchantment, ritual, the rift. */
    ARCANE("arcane", SoulAxis.ESSENCE, true),

    /** Worked metal and stone: the workshop, the armoury, the mine. */
    WROUGHT("wrought", SoulAxis.ESSENCE, false),

    /** Growing things. */
    VERDANT("verdant", SoulAxis.VITALITY, true),

    /** Bone, dust and the emptied places. */
    HOLLOW("hollow", SoulAxis.VITALITY, false);

    private final String id;
    private final SoulAxis axis;
    private final boolean positive;

    SoulTrait(String id, SoulAxis axis, boolean positive)
    {
        this.id = id;
        this.axis = axis;
        this.positive = positive;
    }

    /** The id an archetype's {@code character} block names this by. */
    public String id()
    {
        return this.id;
    }

    public SoulAxis axis()
    {
        return this.axis;
    }

    /** Whether this is the pole a positive lean on its axis points at - see {@link SoulCharacter#lean}. */
    public boolean isPositive()
    {
        return this.positive;
    }

    /** The trait with this id, or null for one this version does not know. */
    public static SoulTrait byId(String id)
    {
        if (id == null)
        {
            return null;
        }

        for (SoulTrait trait : values())
        {
            if (trait.id.equals(id))
            {
                return trait;
            }
        }

        return null;
    }
}
