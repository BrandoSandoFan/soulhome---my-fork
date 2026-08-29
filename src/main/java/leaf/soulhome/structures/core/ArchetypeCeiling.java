/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.HashSet;
import java.util.Set;

/**
 * The highest score an archetype can ever produce, from its definition alone - #47.
 *
 * <p>Every signal at its cap, every form at confidence 1.0, no detractors present, and dense
 * enough to avoid the density penalty. No build can score higher than this, so it is the
 * denominator every one of an archetype's tier thresholds has to be checked against: a tier
 * threshold above this number can never be reached by any build, however good, and a buff ramped
 * against such a threshold (see {@link ArchetypeDefinition.BuffSpec#magnitudeAt}) can never pay
 * out in full either.
 *
 * <p>Deliberately reuses {@link ArchetypeClassifier#curve} rather than reimplementing the
 * sublinear response curve, so the ceiling can never quietly disagree with what the classifier
 * actually does with a maxed-out signal.
 *
 * <p>The rule this exists to enforce, for whoever adds the next archetype: aim tier 2 at roughly
 * half this number and tier 3 at roughly three-quarters to four-fifths of it, leaving headroom
 * above tier 3 for a build that also nails its forms. {@link ArchetypeCeilingTest} fails the build
 * the moment a shipped archetype's top tier stops being one of them.
 */
public final class ArchetypeCeiling
{
    private ArchetypeCeiling()
    {
    }

    public static double of(ArchetypeDefinition archetype, ScoringSettings settings)
    {
        double signalRaw = 0d;
        Set<String> roles = new HashSet<>();

        for (ArchetypeDefinition.Signal signal : archetype.signals())
        {
            signalRaw += signal.weight() * ArchetypeClassifier.curve(signal.cap());
            roles.add(signal.role());
        }

        double structuralRaw = 0d;

        for (Form form : archetype.structures())
        {
            structuralRaw += form.weight();
            roles.add(form.role());
        }

        final double structuralCap = settings.structuralShareCap() * signalRaw;
        final double raw = signalRaw + Math.min(structuralRaw, structuralCap);
        final double diversity = 1d + settings.diversityBonusPerRole() * Math.max(0, roles.size() - 1);

        return raw * diversity;
    }
}
