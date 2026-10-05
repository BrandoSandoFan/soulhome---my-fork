/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The sky a soul is drawn under (#163/#164) - reduced to numbers a renderer can draw and a test can
 * read, the same way {@link SoulAmbience} reduces its fog.
 *
 * <h2>Why a sky at all</h2>
 *
 * <p>The soul dimension draws no sky ({@code SkyType.NONE}), so for its whole first release "the
 * sky" was the fog colour: one flat colour, pulled part of the way from pale blue toward a blend of
 * every room. #163's first playtest called it bland, and it was - a flat colour has nowhere to put a
 * second trait, no way to show height, and no way to change with rank except by getting paler. This
 * is the sky that answers those three: a gradient from the horizon up to a zenith of the soul's own
 * colour, a veil of light in the colour of its second trait, and stars that come out as it climbs.
 *
 * <h2>What each part answers</h2>
 *
 * <ul>
 *   <li><b>Zenith</b> - what the soul is, deepened. The horizon is the fog colour, exactly (the
 *       renderer reads it back off the fog rather than recomputing it), so builds at the edge of
 *       sight fade into the sky rather than into a seam.</li>
 *   <li><b>Height</b> - rank. At rank 0 the zenith barely differs from the horizon: a close, low,
 *       hazy lid. Each rank deepens it and lifts it, which is the Ascent's "the firmament rises"
 *       made visible standing still.</li>
 *   <li><b>Veil</b> - the soul's second-strongest axis, drawn as slow ribbons of light. This is where
 *       a soul of hearths <i>and</i> libraries shows both, rather than one colour averaged from two.
 *       The owner's note on #163 asked for a firmament that feels physical rather than abstract;
 *       moving curtains of light are the most physical thing a sky can hold that is not weather.</li>
 *   <li><b>Stars</b> - rank again, and only rank: none at rank 0, a full field at the ceiling.</li>
 * </ul>
 *
 * <h2>What it never does</h2>
 *
 * <p>Nothing here touches the lightmap, so the light a player builds by is the same under every sky.
 * The zenith has a luminance floor of its own, lower than the fog's - the sky overhead may be deep,
 * never black. Everything moves slowly and is eased by the client; there is no flash, strobe or
 * sudden change at any setting (#167).
 *
 * @param zenith   colour straight up, 0 to 1 per channel
 * @param height   0 to 1: how much of the dome the zenith colour reaches - rank, seen
 * @param veil     the veil's colour
 * @param veilStrength 0 to 1: how visible the veil is, 0 for none
 * @param glow     the horizon glow's colour - the soul's own colour, brightened
 * @param glowStrength 0 to 1
 * @param stars    0 to 1: how much of the star field shows
 * @param active   false when there is nothing to draw and the renderer should leave the sky alone
 */
public record SoulSky(
        float[] zenith, float height, float[] veil, float veilStrength, float[] glow, float glowStrength,
        float stars, boolean active)
{
    /** No sky: the renderer draws nothing and the dimension looks as it did before this existed. */
    public static final SoulSky NONE = new SoulSky(
            SoulAmbience.NEUTRAL, 0f, SoulAmbience.NEUTRAL, 0f, SoulAmbience.NEUTRAL, 0f, 0f, false);

    /** The zenith of a soul with no character: a deeper blue than the soul biome's pale haze. */
    public static final float[] NEUTRAL_ZENITH = {0.30f, 0.40f, 0.72f};

    /** No zenith is darker than this, whatever it is made of - deep overhead, never black. */
    public static final float ZENITH_FLOOR = 0.2f;

    /** How dark a zenith is taken, before the floor - the luminance a fully-built soul's sky reaches. */
    public static final float ZENITH_LUMINANCE = 0.26f;

    /** How much of the dome the zenith reaches at rank 0 and at the ceiling. */
    public static final float RANK_0_HEIGHT = 0.3f;

    public static final float RANK_MAX_HEIGHT = 1f;

    /** How visible the veil is at full depth and intensity. Faint enough to read as light, not paint. */
    public static final float MAX_VEIL = 0.55f;

    /** And the horizon glow. */
    public static final float MAX_GLOW = 0.45f;

    /**
     * The sky over one soul, right now.
     *
     * @param character the blend over every classified room
     * @param rank      its ascension rank
     * @param maxRank   the configured ceiling
     * @param settings  the viewer's own switches
     */
    public static SoulSky of(SoulCharacter character, int rank, int maxRank, AmbienceSettings settings)
    {
        if (settings == null || !settings.active() || !(settings.rankVisualsActive() || settings.characterActive()))
        {
            return NONE;
        }

        final float intensity = (float) settings.intensity();
        final boolean coloured = settings.characterActive() && character != null && !character.isEmpty();
        final float depth = coloured ? (float) Math.sqrt(character.depth()) : 0f;

        float[] own = coloured ? SoulAmbience.characterColour(character) : NEUTRAL_ZENITH;
        own = saturate(own, 1.35f);

        // the zenith is the soul's own colour taken deep, mixed in from a neutral deep blue by how
        // much has been built - so an empty soul still has a sky, and a full one has its own
        final float[] zenith = floor(atLuminance(mix(NEUTRAL_ZENITH, own, depth), ZENITH_LUMINANCE), ZENITH_FLOOR);

        final float rankFraction = settings.rankVisualsActive() ? fraction(rank, maxRank) : 0.5f;
        final float height = (RANK_0_HEIGHT + (RANK_MAX_HEIGHT - RANK_0_HEIGHT) * rankFraction) * intensity;

        float[] veil = NEUTRAL_ZENITH;
        float veilStrength = 0f;
        float[] glow = SoulAmbience.NEUTRAL;
        float glowStrength = 0f;

        if (coloured)
        {
            final SoulAxis second = secondAxis(character);

            // a soul committed to one axis still gets a veil - its own colour, brightened - so the
            // sky has something moving in it; a soul of two gets its second in the veil
            veil = saturate(second == null
                    ? lighten(own, 0.35f)
                    : SoulAmbience.axisColour(character, second), 1.4f);
            veilStrength = MAX_VEIL * intensity * depth * (second == null ? 0.7f : 1f);

            glow = lighten(own, 0.25f);
            glowStrength = MAX_GLOW * intensity * depth;
        }

        final float stars = settings.rankVisualsActive() ? intensity * rankFraction : 0f;

        return new SoulSky(zenith, height, veil, veilStrength, glow, glowStrength, stars, true);
    }

    /**
     * The axis with the second-largest share, or null when fewer than two axes have anything on
     * them. Ties go to the earlier axis, so the answer never flickers between two equal ones.
     */
    static SoulAxis secondAxis(SoulCharacter character)
    {
        SoulAxis first = null;
        SoulAxis second = null;

        for (SoulAxis axis : SoulAxis.values())
        {
            final double share = character.share(axis);

            if (share <= 0d)
            {
                continue;
            }

            if (first == null || share > character.share(first))
            {
                second = first;
                first = axis;
            }
            else if (second == null || share > character.share(second))
            {
                second = axis;
            }
        }

        return second;
    }

    /** Relative luminance, the same weights {@link SoulAmbience} floors its fog by. */
    public static float luminance(float[] colour)
    {
        return 0.2126f * colour[0] + 0.7152f * colour[1] + 0.0722f * colour[2];
    }

    private static float fraction(int rank, int maxRank)
    {
        return maxRank <= 0 ? 1f : Math.max(0f, Math.min(1f, rank / (float) maxRank));
    }

    private static float[] mix(float[] from, float[] to, float amount)
    {
        final float t = Math.max(0f, Math.min(1f, amount));

        return new float[] {
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t};
    }

    /** Pushes a colour away from its own grey, keeping its luminance roughly where it was. */
    private static float[] saturate(float[] colour, float amount)
    {
        final float grey = luminance(colour);

        return new float[] {
                clamp(grey + (colour[0] - grey) * amount),
                clamp(grey + (colour[1] - grey) * amount),
                clamp(grey + (colour[2] - grey) * amount)};
    }

    private static float[] lighten(float[] colour, float amount)
    {
        return mix(colour, new float[] {1f, 1f, 1f}, amount);
    }

    /** The same hue, scaled to a target luminance - down only, since a zenith is never lighter than its colour. */
    private static float[] atLuminance(float[] colour, float target)
    {
        final float current = luminance(colour);

        if (current <= target || current <= 0f)
        {
            return colour;
        }

        final float scale = target / current;

        return new float[] {colour[0] * scale, colour[1] * scale, colour[2] * scale};
    }

    /** Lifted, hue kept, to at least {@code floor} - see {@link #ZENITH_FLOOR}. */
    private static float[] floor(float[] colour, float floor)
    {
        final float current = luminance(colour);

        if (current >= floor)
        {
            return colour;
        }

        if (current <= 0f)
        {
            return new float[] {floor, floor, floor};
        }

        final float scale = floor / current;

        return new float[] {clamp(colour[0] * scale), clamp(colour[1] * scale), clamp(colour[2] * scale)};
    }

    private static float clamp(float value)
    {
        return Math.max(0f, Math.min(1f, value));
    }
}
