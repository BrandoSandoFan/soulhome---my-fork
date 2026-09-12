/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link BondRelation} a bond's {@code relation} can name. Closed vocabulary, open
 * registry: {@link #BUILTIN} holds the seven the mod ships once {@code BuiltinBondRelations} has
 * run, and a mod adds its own by registering onto its own instance. Tests build their own rather
 * than touching {@link #BUILTIN}, so one test's fake relation never leaks into another's.
 */
public final class BondRelationRegistry
{
    public static final BondRelationRegistry BUILTIN = new BondRelationRegistry();

    private final Map<String, BondRelation> byId = new LinkedHashMap<>();

    public synchronized void register(BondRelation relation)
    {
        if (this.byId.containsKey(relation.id()))
        {
            throw new IllegalStateException("A bond relation is already registered as '" + relation.id() + "'");
        }

        this.byId.put(relation.id(), relation);
    }

    public Optional<BondRelation> get(String id)
    {
        return Optional.ofNullable(this.byId.get(id));
    }

    public Collection<BondRelation> all()
    {
        return List.copyOf(this.byId.values());
    }
}
