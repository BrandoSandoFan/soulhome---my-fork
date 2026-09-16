/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * What a soul looks and sounds like right now - the Ambience epic (#163), reduced to numbers a
 * renderer can use and a test can read.
 *
 * <p>Two inputs, as the epic says: how far this soul has climbed ({@link #of}'s rank), and what is
 * built in it ({@link SoulCharacter}). Everything else here is the arithmetic between them, and it
 * lives in {@code structures.core} for the usual reason - a colour is far easier to argue about in
 * a unit test than through a screenshot.
 *
 * <h2>The rules this arithmetic exists to keep</h2>
 *
 * <ul>
 *   <li><b>Never obscures the build.</b> {@link #fogFar} is derived from the soulhome's own verge
 *       and is always past the far corner of it, so no fog ever stands between a player and
 *       anything they may legally have placed. Rank pushes it further out, never closer in: the lid
 *       comes off as you climb, and at rank 0 the void closes in <i>outside</i> the box rather than
 *       inside it.</li>
 *   <li><b>Never darkens the place.</b> The tint is a hue shift with a luminance floor, and the
 *       lightmap is not touched at all. A player at ground level sees exactly the light they saw
 *       before this epic, at every rank and every blend.</li>
 *   <li><b>Nothing flashes.</b> Everything here is a target, and every caller eases toward it. See
 *       {@code ClientAmbience}.</li>
 *   <li><b>Zero means off.</b> At {@code intensity} 0 the colour is the untouched one, the fog
 *       distance is {@link #NO_FOG_OVERRIDE}, and the mote rate is 0.</li>
 * </ul>
 *
 * @param red        fog and void colour, 0 to 1
 * @param green      as above
 * @param blue       as above
 * @param fogNear    where distance fog begins, in blocks, or {@link #NO_FOG_OVERRIDE}
 * @param fogFar     where it has closed, in blocks, or {@link #NO_FOG_OVERRIDE}
 * @param moteRate   firmament motes to try to spawn per client tick, 0 for none
 * @param tinted     whether the colour differs from the untouched one at all - a caller with
 *                   nothing to change should change nothing rather than setting the same colour
 */
public record SoulAmbience(
        float red, float green, float blue, float fogNear, float fogFar, float moteRate, boolean tinted)
{
    /** Handed back when rank visuals are off: leave Minecraft's own fog exactly as it is. */
    public static final float NO_FOG_OVERRIDE = -1f;

    /** The soul biome's own fog colour, {@code 0xC0D8FF} - what an unbuilt soul has always looked like. */
    public static final float[] NEUTRAL = {0.753f, 0.847f, 1.0f};

    /**
     * How far toward its character a soul can be pushed. Short of the whole way on purpose: a
     * soulhome that has gone all-in on one kind of room should still read as a soulhome rather than
     * as a themed level.
     */
    public static final float MAX_TINT = 0.85f;

    /** No blend may end up darker than this, whatever it is made of - see the class javadoc. */
    public static final float LUMINANCE_FLOOR = 0.45f;

    /** Multiplied by the verge to clear the box's own far corner, which sits at {@code sqrt(2)}. */
    public static final float VERGE_CLEARANCE = 1.5f;

    /** Blocks of slack past that corner at rank 0, so the wall itself is never in the haze. */
    public static final float RANK_0_MARGIN = 16f;

    /** How much further out the fog is pushed by the time a soul is at its last rank. */
    public static final float MAX_RANK_MARGIN = 240f;

    /**
     * Extra distance handed back as intensity falls away from full, so a player who wants a hint of
     * this gets a wider, airier soul rather than the same close sky at a paler colour. At zero
     * intensity nothing is drawn at all, so this never has to reach "no fog" on its own.
     */
    public static final float INTENSITY_SLACK = 192f;

    /** Firmament motes per tick at full intensity and the last rank. Sparse: this is a drift, not weather. */
    public static final float MAX_MOTE_RATE = 0.85f;

    /** Below this an axis is simply leaning; above it, it is contested and reads as its third thing. */
    public static final double TENSION_FLOOR = 0.35d;

    /** At and above this, the contested reading has taken over entirely. */
    public static final double TENSION_FULL = 0.95d;

    /** The untouched look: what a soul with no character, no rank colouring and nothing on looks like. */
    public static final SoulAmbience NONE = new SoulAmbience(
            NEUTRAL[0], NEUTRAL[1], NEUTRAL[2], NO_FOG_OVERRIDE, NO_FOG_OVERRIDE, 0f, false);

    /**
     * The look of one soul, right now.
     *
     * @param character        the blend over its classified rooms
     * @param rank             its ascension rank
     * @param maxRank          the configured ceiling, so a pack with a three-rung ladder still
     *                         reaches the fully-opened sky at the top of it
     * @param vergeHalfExtent  half the width of the box at this rank - what keeps fog off the build
     * @param settings         the viewer's own switches
     */
    public static SoulAmbience of(
            SoulCharacter character, int rank, int maxRank, int vergeHalfExtent, AmbienceSettings settings)
    {
        if (settings == null || !settings.active())
        {
            return NONE;
        }

        final float intensity = (float) settings.intensity();

        float[] colour = NEUTRAL;
        boolean tinted = false;

        if (settings.characterActive() && character != null && !character.isEmpty())
        {
            final float strength = MAX_TINT * intensity * (float) character.depth();

            if (strength > 0.001f)
            {
                colour = lift(mix(NEUTRAL, characterColour(character), strength));
                tinted = true;
            }
        }

        float fogNear = NO_FOG_OVERRIDE;
        float fogFar = NO_FOG_OVERRIDE;
        float moteRate = 0f;

        if (settings.rankVisualsActive())
        {
            final float rankFraction = rankFraction(rank, maxRank);
            final float clear = Math.max(16f, vergeHalfExtent) * VERGE_CLEARANCE;
            // intensity slides the whole thing outward rather than thinning it, so a player who
            // wants a hint of this gets a wider soul and not a murkier one
            fogFar = clear + RANK_0_MARGIN + rankFraction * MAX_RANK_MARGIN + (1f - intensity) * INTENSITY_SLACK;
            fogNear = fogFar * 0.55f;

            // more space to fill as the box grows, and the drift is what makes that space legible
            moteRate = MAX_MOTE_RATE * intensity * (0.4f + 0.6f * rankFraction);
        }

        return new SoulAmbience(colour[0], colour[1], colour[2], fogNear, fogFar, moteRate, tinted);
    }

    /** Whether {@link #fogFar} is something a caller should apply at all. */
    public boolean hasFog()
    {
        return this.fogFar > 0f;
    }

    /**
     * Where the next ambient one-shot (#166) lands relative to the listener - not the player's
     * feet, the position {@link OneShotPlacement#distanceFromListener()} is measured against.
     *
     * <p>Vanilla's linear-attenuation model skips a source past {@link OneShotPlacement#AUDIBLE_RADIUS}
     * outright and fades everything else to nothing at that same edge (#208). Placing a "distant"
     * sound exactly at 16 blocks horizontal, with a vertical spread measured from the player's feet
     * rather than their ear, put every draw either past the cliff or on it. This keeps every roll
     * {@link OneShotPlacement#SAFETY_MARGIN} blocks short of the radius, so "far" has to come from the
     * asset - a distant-sounding recording, or a lowpass - rather than from a curve that cannot
     * deliver it.
     *
     * @param horizontalRoll a fresh random number in {@code [0, 1)}
     * @param verticalRoll   a second, independent one - the caller owns the randomness so this stays
     *                       a function and can be tested
     */
    public static OneShotPlacement oneShotPlacement(double horizontalRoll, double verticalRoll)
    {
        final double horizontal = OneShotPlacement.MIN_HORIZONTAL
                + Math.max(0d, Math.min(1d, horizontalRoll))
                        * (OneShotPlacement.MAX_HORIZONTAL - OneShotPlacement.MIN_HORIZONTAL);
        final double vertical = (Math.max(0d, Math.min(1d, verticalRoll)) * 2d - 1d) * OneShotPlacement.MAX_VERTICAL;

        return new OneShotPlacement(horizontal, vertical);
    }

    /**
     * @param horizontalDistance blocks from the listener on the XZ plane
     * @param verticalOffset     blocks above (positive) or below the listener's own ear height
     */
    public record OneShotPlacement(double horizontalDistance, double verticalOffset)
    {
        /** Vanilla's own linear-attenuation radius for every event {@code SoulAmbienceSounds} draws from. */
        public static final double AUDIBLE_RADIUS = 16d;

        /** Kept this many blocks short of the radius, so a slow client tick or float drift can't tip a draw over it. */
        public static final double SAFETY_MARGIN = 4d;

        static final double MIN_HORIZONTAL = 6d;

        static final double MAX_HORIZONTAL = 10d;

        static final double MAX_VERTICAL = 3d;

        /** 3D distance from the listener - what vanilla's attenuation actually reads, not the horizontal leg alone. */
        public double distanceFromListener()
        {
            return Math.sqrt(this.horizontalDistance * this.horizontalDistance + this.verticalOffset * this.verticalOffset);
        }
    }

    /**
     * The colour a soul's own rooms pull it to, before depth and intensity are applied.
     *
     * <p>Each axis contributes its own reading, weighted by its share of the whole soul, so an axis
     * nothing has been built on says nothing rather than voting for its own midpoint.
     */
    public static float[] characterColour(SoulCharacter character)
    {
        float[] blended = {0f, 0f, 0f};
        float weight = 0f;

        for (SoulAxis axis : SoulAxis.values())
        {
            final float share = (float) character.share(axis);

            if (share <= 0f)
            {
                continue;
            }

            final float[] reading = axisColour(character, axis);

            blended[0] += reading[0] * share;
            blended[1] += reading[1] * share;
            blended[2] += reading[2] * share;
            weight += share;
        }

        return weight <= 0f ? NEUTRAL : new float[] {blended[0] / weight, blended[1] / weight, blended[2] / weight};
    }

    /**
     * One axis's reading: its poles blended by which way it leans, then carried toward the thing it
     * becomes when both poles are built at once.
     *
     * <p>That second step is the point of the whole epic's colour model. Averaging a soul of forges
     * and freezers into the middle of the warm/cold scale would tell a player who has built a great
     * deal that they have built nothing in particular. They get steam instead.
     */
    public static float[] axisColour(SoulCharacter character, SoulAxis axis)
    {
        final float lean = (float) character.lean(axis);
        final float[] poles = mix(
                Palette.of(axis.negative()), Palette.of(axis.positive()), (lean + 1f) / 2f);

        final float contested = (float) smoothstep(TENSION_FLOOR, TENSION_FULL, character.tension(axis));

        return contested <= 0f ? poles : mix(poles, Palette.contested(axis), contested);
    }

    /**
     * Which palette the next ambient one-shot is drawn from (#166).
     *
     * <p>{@code roll} is a fresh random number in {@code [0, 1)}; the caller owns the randomness so
     * that this stays a function and can be tested. A share of every draw goes to {@link
     * SoulVoice#BASE} whatever is built, because a soul is a place before it is a mix of rooms.
     */
    public static SoulVoice voiceFor(SoulCharacter character, double roll)
    {
        final double clamped = Math.max(0d, Math.min(0.999999d, roll));

        if (character == null || character.isEmpty())
        {
            return SoulVoice.BASE;
        }

        // the base voice keeps a share of the draw that falls as the soul takes on a character,
        // rather than being crowded out entirely by the first hearth
        final double baseShare = 1d - 0.75d * character.depth();

        if (clamped < baseShare)
        {
            return SoulVoice.BASE;
        }

        double remaining = (clamped - baseShare) / Math.max(1e-9d, 1d - baseShare);

        for (SoulAxis axis : SoulAxis.values())
        {
            final double share = character.share(axis);

            if (remaining >= share)
            {
                remaining -= share;
                continue;
            }

            return voiceOf(character, axis);
        }

        return SoulVoice.BASE;
    }

    private static SoulVoice voiceOf(SoulCharacter character, SoulAxis axis)
    {
        if (character.tension(axis) >= TENSION_FLOOR)
        {
            return Palette.contestedVoice(axis);
        }

        return Palette.voice(character.lean(axis) >= 0d ? axis.positive() : axis.negative());
    }

    private static float rankFraction(int rank, int maxRank)
    {
        if (maxRank <= 0)
        {
            return 1f;
        }

        return Math.max(0f, Math.min(1f, (float) rank / (float) maxRank));
    }

    private static float[] mix(float[] from, float[] to, float amount)
    {
        final float t = Math.max(0f, Math.min(1f, amount));

        return new float[] {
                lerp(from[0], to[0], t),
                lerp(from[1], to[1], t),
                lerp(from[2], to[2], t)};
    }

    /**
     * Raises a blend to the luminance floor without changing its hue, so no mix of rooms can make a
     * soul murky to build in. Scaling rather than clamping per channel, because clamping a channel
     * shifts the hue and would hand a player a different colour than the one their rooms earned.
     */
    private static float[] lift(float[] colour)
    {
        final float luminance = 0.2126f * colour[0] + 0.7152f * colour[1] + 0.0722f * colour[2];

        if (luminance >= LUMINANCE_FLOOR || luminance <= 0f)
        {
            return colour;
        }

        final float scale = LUMINANCE_FLOOR / luminance;

        return new float[] {
                Math.min(1f, colour[0] * scale),
                Math.min(1f, colour[1] * scale),
                Math.min(1f, colour[2] * scale)};
    }

    private static float lerp(float from, float to, float amount)
    {
        return from + (to - from) * amount;
    }

    private static double smoothstep(double edge0, double edge1, double value)
    {
        final double t = Math.max(0d, Math.min(1d, (value - edge0) / (edge1 - edge0)));

        return t * t * (3d - 2d * t);
    }

    /**
     * What each trait, and each contested axis, looks and sounds like.
     *
     * <p>In Java rather than in the archetype JSON on purpose (#167): a colour in a datapack is a
     * datapack that can make a soul unreadably dark, and an unreadable soul is not a balance
     * problem anyone can turn off. What a pack <i>does</i> get to say is which traits its room
     * pulls toward, which is the half that matters for "a datapack room colours the soul it is
     * built in with no Java change".
     */
    static final class Palette
    {
        private Palette()
        {
        }

        static float[] of(SoulTrait trait)
        {
            return switch (trait)
            {
                case WARM -> new float[] {1.00f, 0.62f, 0.34f};
                case COLD -> new float[] {0.55f, 0.78f, 1.00f};
                case ARCANE -> new float[] {0.72f, 0.52f, 1.00f};
                case WROUGHT -> new float[] {0.62f, 0.68f, 0.72f};
                case VERDANT -> new float[] {0.58f, 0.88f, 0.50f};
                case HOLLOW -> new float[] {0.66f, 0.62f, 0.68f};
            };
        }

        /** What an axis becomes when both of its poles are built at once. */
        static float[] contested(SoulAxis axis)
        {
            return switch (axis)
            {
                // steam: forge and freezer in the same soul, and the air between them
                case THERMAL -> new float[] {0.88f, 0.90f, 0.93f};
                // worked matter run through with the arcane, which is neither and looks like it
                case ESSENCE -> new float[] {0.55f, 0.85f, 0.80f};
                // growth that has taken somewhere emptied: overgrowth, not a compromise between them
                case VITALITY -> new float[] {0.70f, 0.80f, 0.42f};
            };
        }

        static SoulVoice voice(SoulTrait trait)
        {
            return switch (trait)
            {
                case WARM -> SoulVoice.WARM;
                case COLD -> SoulVoice.COLD;
                case ARCANE -> SoulVoice.ARCANE;
                case WROUGHT -> SoulVoice.WROUGHT;
                case VERDANT -> SoulVoice.VERDANT;
                case HOLLOW -> SoulVoice.HOLLOW;
            };
        }

        static SoulVoice contestedVoice(SoulAxis axis)
        {
            return switch (axis)
            {
                case THERMAL -> SoulVoice.STEAM;
                case ESSENCE -> SoulVoice.QUICKENED;
                case VITALITY -> SoulVoice.OVERGROWN;
            };
        }
    }
}
