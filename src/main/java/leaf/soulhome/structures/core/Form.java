/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One piece of structural evidence: named elements and a small clause tree relating them, e.g.
 * "the rails form a circuit, and it runs on ice or is ringed by it". See the structural form
 * grammar epic (#27) for the full design.
 *
 * <p>Two to four elements is the normal shape; a single-element form - a bare shape statement - is
 * permitted but should be the exception, since one element in isolation says much less about a room
 * than the same element related to another. More than four is rejected: a form naming that much of
 * the room is a schematic, and cannot produce a diagnostic more useful than "it is wrong".
 *
 * @param name                the form's own name, for logs and the structural report
 * @param weight              this form's share of {@code structuralRaw = Σ form.weight × confidence(form)}
 * @param role                a grouping label, mirroring {@link ArchetypeDefinition.Signal#role()}
 * @param elements            named {@link BlockMatcher}s the clause tree relates
 * @param root                the clause tree itself - a bare leaf, or an {@code all}/{@code any} node
 * @param referencedElements  every element name some clause in {@code root} actually reads, as
 *                            collected by the parser while it built the tree - not recomputed by
 *                            walking clause objects, since {@link FormClause} does not expose what a
 *                            leaf reads. Drives the "declared but unused" / "used but undeclared"
 *                            checks in {@link #validationErrors()}.
 */
public record Form(
        String name,
        double weight,
        String role,
        Map<String, BlockMatcher> elements,
        FormClause root,
        Set<String> referencedElements)
{
    public static final String DEFAULT_ROLE = "structure";

    /** "Forms relate two to four elements" - but a single-element form is permitted, see the class doc. */
    public static final int MAX_ELEMENTS = 4;

    /** "Nesting is capped at two levels" - counted in composite (`all`/`any`) nodes, not leaves. */
    public static final int MAX_NESTING = 2;

    public Form
    {
        elements = elements == null ? Map.of() : Map.copyOf(elements);
        role = role == null || role.isBlank() ? DEFAULT_ROLE : role;
        referencedElements = referencedElements == null ? Set.of() : Set.copyOf(referencedElements);
    }

    public FormResult evaluate(RegionGeometry geometry)
    {
        return this.root.evaluate(geometry, this.elements);
    }

    /** Whether this form's clause tree needs {@link RegionGeometry#isBlocked} to grade correctly. */
    public boolean needsClearance()
    {
        return this.root != null && this.root.needsClearance();
    }

    /**
     * Problems that should reject this archetype outright, logged loudly by the loader. Structure
     * is optional evidence, but a form that cannot mean what it says is a datapack bug, not
     * something to silently score as zero.
     */
    public List<String> validationErrors()
    {
        List<String> errors = new ArrayList<>();

        if (this.name == null || this.name.isBlank())
        {
            errors.add("'name' is missing or blank");
        }

        if (this.weight <= 0)
        {
            errors.add("'weight' must be positive, got " + this.weight);
        }

        if (this.elements.isEmpty())
        {
            errors.add("no 'elements' declared");
        }
        else if (this.elements.size() > MAX_ELEMENTS)
        {
            errors.add("names " + this.elements.size() + " elements, more than the " + MAX_ELEMENTS
                    + " a form may relate at once - that many blocks named together is a schematic,"
                    + " not evidence");
        }

        for (Map.Entry<String, BlockMatcher> entry : this.elements.entrySet())
        {
            for (String error : entry.getValue().validationErrors())
            {
                errors.add("elements." + entry.getKey() + ": " + error);
            }
        }

        for (String referenced : this.referencedElements)
        {
            if (!this.elements.containsKey(referenced))
            {
                errors.add("a clause refers to undeclared element '" + referenced + "'");
            }
        }

        for (String declared : this.elements.keySet())
        {
            if (!this.referencedElements.contains(declared))
            {
                errors.add("element '" + declared + "' is declared but never used by a clause");
            }
        }

        if (this.root == null)
        {
            errors.add("has no evaluable clause");
        }
        else
        {
            errors.addAll(this.root.validationErrors(this.elements.keySet()));

            int depth = nestingDepth(this.root);

            if (depth > MAX_NESTING)
            {
                errors.add("clause nesting is " + depth + " level(s) deep, past the "
                        + MAX_NESTING + "-level cap");
            }
        }

        return errors;
    }

    /** Worth a second look in review, but not rejected. */
    public List<String> validationWarnings()
    {
        List<String> warnings = new ArrayList<>();

        if (this.elements.size() == 1)
        {
            warnings.add("relates only one element - a bare shape statement says much less about a"
                    + " room than the same element related to another, and should be the exception");
        }

        return warnings;
    }

    /**
     * Depth counted in {@code all}/{@code any} nodes only - a leaf does not add a level on its own,
     * so {@code all} of plain leaves is depth 1 and {@code all} containing one {@code any} of leaves
     * is depth 2, exactly the shape the grammar's canonical example uses.
     */
    private static int nestingDepth(FormClause clause)
    {
        List<WeightedClause> children;

        if (clause instanceof AllClause all)
        {
            children = all.children();
        }
        else if (clause instanceof AnyClause any)
        {
            children = any.children();
        }
        else
        {
            return 0;
        }

        int deepestChild = 0;

        for (WeightedClause child : children)
        {
            deepestChild = Math.max(deepestChild, nestingDepth(child.clause()));
        }

        return 1 + deepestChild;
    }
}
