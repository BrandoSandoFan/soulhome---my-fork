/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A room that was actually awarded an archetype, reduced to the few things buffs depend on.
 *
 * <p>Deliberately small. This is what gets written to a soulhome's saved data, so that recomputing
 * a player's buffs on login is a map lookup rather than a chunk sweep - a player who logs in, dies
 * and changes dimension should not trigger three scans of a soulhome nobody has touched.
 *
 * @param aspectId which aspect the room took (the Aspects epic, #171), or null when its archetype
 *                 declares none and when the switch is off. Carried so that a login recomputes the
 *                 buff the last scan actually granted rather than quietly falling back to the
 *                 archetype's default - but never <i>trusted</i>: every scan derives it again, and
 *                 with {@code aspects.enabled} off nothing ever writes one, which is what makes the
 *                 saved form byte-identical to a save from before the epic (#176).
 */
public record AwardedRoom(String archetypeId, int tier, double score, String aspectId)
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

        aspectId = aspectId == null || aspectId.isBlank() ? null : aspectId;
    }

    /** A room that took no aspect - every awarded room before #171, and most fixtures since. */
    public AwardedRoom(String archetypeId, int tier, double score)
    {
        this(archetypeId, tier, score, null);
    }

    public boolean hasAspect()
    {
        return this.aspectId != null;
    }

    /** Reduce a full classification pass to just the rooms that earned something. */
    public static List<AwardedRoom> from(List<ClassificationResult> results)
    {
        List<AwardedRoom> awarded = new ArrayList<>();

        for (ClassificationResult result : results)
        {
            result.awarded().ifPresent(score -> awarded.add(
                    new AwardedRoom(score.archetypeId(), score.tier(), score.score(), score.aspectId())));
        }

        return awarded;
    }
}
