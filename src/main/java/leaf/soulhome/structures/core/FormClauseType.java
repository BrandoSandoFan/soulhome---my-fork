/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;

/**
 * A registered {@code shape} or {@code relation} clause implementation: an id, the parameters it
 * reads, and a factory turning those parameters into the real {@link FormClause}.
 *
 * <p>This is the open vocabulary the structural form grammar (#27) is deliberately bounded to: new
 * clauses are added by a mod calling {@link FormClauseRegistry#register}, never by a datapack. #29
 * through #32 are the ones that ship with the mod; a modder can add their own the same way.
 */
public interface FormClauseType
{
    /** e.g. {@code "loop"} for a shape, {@code "above"} for a relation. Unique within its kind. */
    String id();

    Kind kind();

    /** Declared once; both the production codec and the Gson test reader read this generically. */
    List<ClauseParamSpec> params();

    /**
     * Builds the clause. {@code params} is guaranteed to hold exactly the values named by
     * {@link #params()} - every required one present, every optional one defaulted - so this never
     * needs to guard against a missing key.
     */
    FormClause create(ClauseParams params);

    /**
     * The inverse of {@link #create}: the parameter values that would rebuild an equivalent clause,
     * keyed the same way {@link #params()} declares them. Only ever called on a {@link FormClause}
     * this exact type produced.
     *
     * <p>Exists so the production codec's encode side - reached whenever the server syncs loaded
     * archetypes to a client - can round-trip an arbitrary registered clause generically, reading
     * {@link #params()} the same way {@code create} does rather than needing a bespoke encoder per
     * clause type.
     */
    Map<String, Object> encode(FormClause clause);

    /** Which JSON key a clause of this type is written under: {@code "shape"} or {@code "relation"}. */
    enum Kind
    {
        SHAPE,
        RELATION
    }
}
