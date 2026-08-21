/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A minimal, hand-built {@link FormClause} for exercising {@link AllClause}, {@link AnyClause} and
 * {@link Form} without needing a real {@code shape}/{@code relation} clause type - none ship until
 * #29 through #32. Returns a fixed {@link FormResult} regardless of the geometry it is handed.
 */
record FakeClause(double confidence, String diagnostic, List<String> ownErrors) implements FormClause
{
    static FakeClause of(double confidence)
    {
        return new FakeClause(confidence, "", List.of());
    }

    static FakeClause of(double confidence, String diagnostic)
    {
        return new FakeClause(confidence, diagnostic, List.of());
    }

    static FakeClause invalid(String error)
    {
        return new FakeClause(0d, "", List.of(error));
    }

    @Override
    public String typeId()
    {
        return "fake";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        return new FormResult(this.confidence, this.diagnostic);
    }

    @Override
    public String describe()
    {
        return "fake(" + this.confidence + ")";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return this.ownErrors;
    }
}
