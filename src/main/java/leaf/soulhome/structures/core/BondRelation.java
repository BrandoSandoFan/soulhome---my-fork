/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;

/**
 * One way two rooms can relate - the closed vocabulary a {@link Bond}'s {@code relation} names,
 * registered in {@link BondRelationRegistry} the way clause types are in
 * {@link FormClauseRegistry}. The mod ships seven (see {@code BuiltinBondRelations}); a mod can
 * add its own onto its own registry, a datapack cannot.
 *
 * <p>Every relation reads its inputs entirely from {@link RegionAdjacency}, computed once per
 * scan. A relation that needed a traversal of its own would be a sign the adjacency layer is
 * missing something, and the fix is there, not here.
 */
public interface BondRelation
{
    /** e.g. {@code "adjoins"}. Unique within the registry. */
    String id();

    /** Declared once; both the production codec and the Gson test reader read this generically. */
    List<ClauseParamSpec> params();

    /**
     * The same relation seen from the other room. {@code above} from the mine's side is
     * {@code beneath} from the workshop's; a symmetric relation is its own mirror. Two declarations
     * that are mirrors of each other are one bond, not two - see {@link BondBook}.
     */
    default String mirror()
    {
        return id();
    }

    /**
     * How far, in cells, {@link RegionAdjacency} has to look for this relation to be gradeable
     * with these parameters - zero for a relation that reads nothing distance-based. The largest
     * over every loaded bond is the reach the scanner runs its floods to.
     */
    default int reach(ClauseParams params)
    {
        return 0;
    }

    /**
     * How well {@code self} relates to {@code other} - a confidence in {@code [0, 1]}, graded and
     * never boolean, with a diagnostic a player can act on: the near miss is the useful half, so
     * a zero should say what the gap was and what would have counted.
     */
    BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params);

    /**
     * The relation in plain words with its numbers stated, for the guide book: "sharing a wall",
     * "within 12 blocks". The other room's name is supplied by whoever writes the sentence.
     */
    String describe(ClauseParams params);
}
