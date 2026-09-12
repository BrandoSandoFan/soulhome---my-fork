/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.BondRelation;
import leaf.soulhome.structures.core.BondRelationRegistry;
import leaf.soulhome.structures.core.BondRelations;

/**
 * Registers the seven bond relations the mod ships onto {@link BondRelationRegistry#BUILTIN} -
 * the vocabulary the Soul Architecture epic (#140) writes with. The same shape as
 * {@link BuiltinFormClauses}, and called from the same two places: the mod's bootstrap, and the
 * Minecraft-free test fixture that needs the real vocabulary present.
 */
public final class BuiltinBondRelations
{
    private BuiltinBondRelations()
    {
    }

    public static void registerAll()
    {
        registerAll(BondRelationRegistry.BUILTIN);
    }

    /** Safe to call more than once against the same registry; whichever runs first wins. */
    public static void registerAll(BondRelationRegistry registry)
    {
        if (registry.get("adjoins").isPresent())
        {
            return;
        }

        for (BondRelation relation : BondRelations.all())
        {
            if (registry.get(relation.id()).isEmpty())
            {
                registry.register(relation);
            }
        }
    }
}
