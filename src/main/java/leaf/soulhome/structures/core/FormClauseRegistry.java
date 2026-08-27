/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link FormClauseType} a {@code shape} or {@code relation} clause can dispatch to.
 *
 * <p>{@link #BUILTIN} is what the production codec and the loaded book read from; nothing is
 * registered to it by this issue (#27) - #29 through #32 are the ones that add {@code loop},
 * {@code above}, {@code surrounds} and the rest. Until then every {@code shape}/{@code relation}
 * clause in a datapack resolves to {@link UnknownClause}: logged once at load and otherwise inert,
 * exactly as the epic's issue chain says Phase 1 should behave - nothing changes for a player until
 * Phase 2 lands a clause type.
 *
 * <p>Tests build their own instance with {@link #FormClauseRegistry()} rather than touching
 * {@link #BUILTIN}, so one test's fake clause type can never leak into another's.
 */
public final class FormClauseRegistry
{
    public static final FormClauseRegistry BUILTIN = new FormClauseRegistry();

    private final Map<String, FormClauseType> byId = new LinkedHashMap<>();

    public synchronized void register(FormClauseType type)
    {
        if (this.byId.containsKey(type.id()))
        {
            throw new IllegalStateException(
                    "A structural clause type is already registered as '" + type.id() + "'");
        }

        this.byId.put(type.id(), type);
    }

    /** Empty if nothing is registered under this id, or if it is registered under the other kind. */
    public Optional<FormClauseType> get(FormClauseType.Kind kind, String id)
    {
        FormClauseType type = this.byId.get(id);
        return type != null && type.kind() == kind ? Optional.of(type) : Optional.empty();
    }

    /**
     * Looked up by id alone, kind-agnostic - {@link #register} already refuses to let a shape and
     * a relation share an id, so this is unambiguous. For the production codec's encode side, which
     * has a {@link FormClause#typeId()} to work from but no JSON key ({@code "shape"} vs
     * {@code "relation"}) to disambiguate by.
     */
    public Optional<FormClauseType> findById(String id)
    {
        return Optional.ofNullable(this.byId.get(id));
    }

    /**
     * Every registered clause type, for anything that wants to enumerate the vocabulary rather
     * than name it by hand - a coverage test asking "does every clause type describe itself",
     * for instance. Kept in step with {@link #register} for free, since both read the same map.
     */
    public Collection<FormClauseType> all()
    {
        return List.copyOf(this.byId.values());
    }
}
