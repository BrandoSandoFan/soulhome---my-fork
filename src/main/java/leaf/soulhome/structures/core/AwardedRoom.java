/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A room that was actually awarded an archetype, reduced to the three things buffs depend on.
 *
 * <p>Deliberately small. This is what gets written to a soulhome's saved data, so that recomputing
 * a player's buffs on login is a map lookup rather than a chunk sweep - a player who logs in, dies
 * and changes dimension should not trigger three scans of a soulhome nobody has touched.
 */
public record AwardedRoom(String archetypeId, int tier, double score)
{
    public AwardedRoom
    {
        if (archetypeId == null || archetypeId.isBlank())
        {
            throw new IllegalArgumentException("An awarded room must name its archetype");
        }

        if (tier < 1)
        {
            throw new IllegalArgumentException("An awarded room is at least tier 1, got " + tier);
        }
    }

    /** Reduce a full classification pass to just the rooms that earned something. */
    public static List<AwardedRoom> from(List<ClassificationResult> results)
    {
        List<AwardedRoom> awarded = new ArrayList<>();

        for (ClassificationResult result : results)
        {
            result.awarded().ifPresent(score ->
                    awarded.add(new AwardedRoom(score.archetypeId(), score.tier(), score.score())));
        }

        return awarded;
    }
}
