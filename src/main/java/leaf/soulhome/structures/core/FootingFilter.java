/*
 * File created ~ 22 - 11 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.function.Predicate;

/**
 * What {@link RegionScanner} indexes under a room's own cells (#307): the blocks that stand on
 * something a form wants to read, and the blocks they might be standing on.
 *
 * <p>Two predicates rather than one because the second is the expensive half. A music room asks
 * what its note blocks stand on, and what they stand on is stone, planks, wool, glass - what every
 * soul is built of. Folded into the ordinary geometry filter, those would index every wall of
 * every room, and every stone cell an open-air build took in, which changes every room's identity
 * hash and pushes a large build toward {@link ScanSettings#maxGeometryCells}, where truncation
 * would start dropping the cells other rooms' forms ask about. Kept here, they are indexed only
 * directly under a block that {@link #standing} matches, which is the only place anything reads
 * them. See {@link ArchetypeSignals#footingFilterFor} for which elements land in which half.
 *
 * @param standing blocks whose footing is read - the upper element of a {@code "reach": "footing"}
 *                 clause
 * @param footing  blocks worth indexing when found under one of those, beyond whatever the
 *                 ordinary geometry filter already indexes
 */
public record FootingFilter(Predicate<BlockSignature> standing, Predicate<BlockSignature> footing)
{
}
