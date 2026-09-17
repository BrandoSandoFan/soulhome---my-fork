/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <li><b>Larger, never louder.</b> Rank moves a one-shot further out and gives it a tail (#216),
 *       and {@link #oneShotProfile} scales the first sound down so the tail sums to exactly one
 *       unechoed one-shot. A rank V soul is a bigger place, not a noisier one.</li>
 *   <li><b>Never in the way of the game.</b> Every sound this mod plays for its own sake holds the
 *       ambience off (#212), and the hold is marked at this mod's own call sites rather than
 *       sniffed off the sound engine - so footsteps and block-placing can never trigger it. See
 *       {@link SoulFeedback}.</li>
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

    /**
     * The one-shot distance band at rank 0 and at the last rank, in blocks (#216).
     *
     * <p>A small soul's sounds are close in, a large one's come from out toward the verge - the
     * sense of the box moving outward, in the ear, at the rate the fog moves outward in the eye.
     * The far end stops short of {@link OneShotPlacement#AUDIBLE_RADIUS} less its safety margin,
     * because past that vanilla plays the sound at nothing (#208) and "distant" becomes "silent".
     */
    public static final double RANK_0_NEAR = 4d;

    public static final double RANK_0_FAR = 7d;

    public static final double RANK_MAX_NEAR = 8d;

    public static final double RANK_MAX_FAR = 11d;

    /** Vertical spread, which grows with rank for the same reason the horizontal band does. */
    public static final double RANK_0_VERTICAL = 2d;

    public static final double RANK_MAX_VERTICAL = 3d;

    /** Repeats at the last rank: a second and a half of tail, which is a hall rather than a canyon. */
    public static final int MAX_ECHOES = 3;

    public static final int MIN_ECHO_DELAY_TICKS = 6;

    public static final int MAX_ECHO_DELAY_TICKS = 10;

    /** What each repeat keeps of the one before it. Steep, because a tail that lingers is a loop. */
    public static final float ECHO_FALLOFF = 0.5f;

    /**
     * Pitch across the ranks. Shallower than it was: with distance and a tail carrying "larger",
     * pitch is the smallest of the three cues rather than the only one, and the old 0.9-to-0.65
     * sweep had never actually been heard (#208) when it was chosen.
     */
    public static final float RANK_0_PITCH = 0.95f;

    public static final float RANK_MAX_PITCH = 0.8f;

    /** No room-directed one-shot lands closer in than this, however close the room's own wall is. */
    public static final double MIN_ROOM_HORIZONTAL = 2d;

    /**
     * How far the ambient bed falls while this mod's own audio is playing (#212), and how long it
     * takes to come back. Two seconds of recovery: fast enough not to leave a hole after an ability,
     * slow enough that the return is not itself an event.
     */
    public static final float DUCK_LEVEL = 0.3f;

    public static final int DUCK_RECOVERY_TICKS = 40;

    /**
     * How long after a rank arrives the soul answers it with one extra one-shot (#216) - five
     * seconds, which is past the ritual's own completion note and its hold. The "and then the sky
     * changed" beat of #164, in sound, and deliberately not on the same tick as the sky.
     */
    public static final int ASCENSION_BEAT_DELAY_TICKS = 100;

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
     * How a soul of this rank throws a one-shot: how far out it lands, how many times it comes
     * back, and at what pitch (#216).
     *
     * <p>#166 asks that a rank V soul "sound larger than a rank 0 one - more space, longer reverb
     * tail, further-off sounds". What shipped was pitch alone, and pitch alone says <i>lower</i>: a
     * pitched-down chime is a bigger bell, not a bigger room. Three cues now carry it, and the
     * ordering between them is deliberate - distance and the tail do the work, pitch is the garnish.
     *
     * <p>The tail is built rather than rendered, because Minecraft has no reverb: a one-shot is
     * followed by {@link OneShotProfile#echoes()} quieter, lower, further-round-the-compass repeats
     * of the same event. That reads unmistakably as a larger space and costs no new asset.
     *
     * <p><b>Larger, never louder.</b> {@link OneShotProfile#leadVolume()} is set so that the first
     * sound plus every one of its echoes sums to exactly what a single unechoed one-shot was worth.
     * A rank V soul is a bigger place, not a noisier one, and {@code SoulAmbienceTest} pins that
     * across every rank and intensity.
     *
     * @param rank            this soul's ascension rank
     * @param maxRank         the configured ceiling, so a pack with a three-rung ladder still
     *                        reaches the fully-opened sound at the top of it
     * @param vergeHalfExtent half the width of the box - nothing is ever thrown past the verge
     * @param settings        the viewer's own switches; intensity thins the tail rather than the
     *                        distance, since distance is what "large" is made of and volume is not
     */
    public static OneShotProfile oneShotProfile(
            int rank, int maxRank, int vergeHalfExtent, AmbienceSettings settings)
    {
        final float fraction = rankFraction(rank, maxRank);
        final double intensity = settings == null ? 1d : settings.intensity();

        final double ceiling = Math.min(
                OneShotPlacement.AUDIBLE_RADIUS - OneShotPlacement.SAFETY_MARGIN,
                Math.max(1d, vergeHalfExtent));

        double near = Math.min(ceiling, lerp(RANK_0_NEAR, RANK_MAX_NEAR, fraction));
        double far = Math.min(ceiling, lerp(RANK_0_FAR, RANK_MAX_FAR, fraction));

        far = Math.max(near, far);

        final int echoes = (int) Math.round(MAX_ECHOES * fraction * intensity);
        final int delay = (int) Math.round(lerp(MIN_ECHO_DELAY_TICKS, MAX_ECHO_DELAY_TICKS, fraction));

        double tail = 0d;

        for (int i = 1; i <= echoes; i++)
        {
            tail += Math.pow(ECHO_FALLOFF, i);
        }

        return new OneShotProfile(
                near,
                far,
                lerp(RANK_0_VERTICAL, RANK_MAX_VERTICAL, fraction),
                echoes,
                delay,
                (float) (1d / (1d + tail)),
                ECHO_FALLOFF,
                RANK_0_PITCH - (RANK_0_PITCH - RANK_MAX_PITCH) * fraction);
    }

    /**
     * Where the next ambient one-shot (#166) lands relative to the listener - not the player's
     * feet, the position {@link OneShotPlacement#distanceFromListener()} is measured against.
     *
     * <p>Vanilla's linear-attenuation model skips a source past {@link OneShotPlacement#AUDIBLE_RADIUS}
     * outright and fades everything else to nothing at that same edge (#208). Placing a "distant"
     * sound exactly at 16 blocks horizontal, with a vertical spread measured from the player's feet
     * rather than their ear, put every draw either past the cliff or on it. Every roll here stays
     * {@link OneShotPlacement#SAFETY_MARGIN} blocks short of the radius, so "far" is expressed by
     * the band the profile hands back rather than by a curve that cannot deliver it.
     *
     * <p>Comes back with no direction, which the caller reads as "any compass angle will do". That
     * is right for the base voice, which is the place rather than a room in it, and it is the
     * fallback for a trait voice whose room could not be found - see {@link #oneShotOrigin}.
     *
     * @param profile        this soul's rank band - see {@link #oneShotProfile}
     * @param horizontalRoll a fresh random number in {@code [0, 1)}
     * @param verticalRoll   a second, independent one - the caller owns the randomness so this stays
     *                       a function and can be tested
     */
    public static OneShotPlacement oneShotPlacement(
            OneShotProfile profile, double horizontalRoll, double verticalRoll)
    {
        final double horizontal = profile.near()
                + Math.max(0d, Math.min(1d, horizontalRoll)) * (profile.far() - profile.near());
        final double vertical = (Math.max(0d, Math.min(1d, verticalRoll)) * 2d - 1d) * profile.maxVertical();

        return OneShotPlacement.fitted(horizontal, vertical, 0d, 0d);
    }

    /**
     * Where a one-shot with a room behind it comes from (#215).
     *
     * <p>A warm crackle in a soul with a hearth in it used to come from wherever the dice said,
     * which might be the aquarium. The sky can only shift as a whole; sound has a direction, and
     * Minecraft plays a positioned sound in stereo, so a crackle from the direction of the hearth is
     * the soul telling a player where their hearth is without a line of text.
     *
     * <p>The archetype definitions this reads are already on every client. The boxes ride along on
     * {@code SyncSoulAmbienceMessage} rather than being taken from the lens's own copy of them,
     * which looks like the cheaper answer and is not: the lens copy is sent only when the lens is
     * used and expires thirty seconds later, and one-shots are minutes apart, so a player who had
     * not just used their lens would have got the fallback every time. The ambience message is
     * already sent on exactly the events that can change a room - a scan, an arrival, a rank - and
     * only rooms whose archetype declares a character are put in it, which is a handful of boxes.
     *
     * <p>Two rules hold, and the tests pin both. A voice is <b>never</b> placed at a room that does
     * not pull toward it: the weight is the room's own {@code character} pull for the voice's traits,
     * and a room with no pull is not a candidate at all. And the direction is kept while the
     * distance is not - a hearth forty blocks off is heard <i>from that direction</i> at the edge of
     * hearing, not from forty blocks away at a gain of nothing. A room nearer than the band is heard
     * where it actually is, because pushing it outward would be as wrong as the fault this fixes.
     *
     * @return where to put it, or null when nothing pulls toward this voice, when the voice is
     *         {@link SoulVoice#BASE}, or when the listener is standing inside the only candidate and
     *         so there is no direction to give. The caller falls back to {@link #oneShotPlacement}:
     *         the blend is still right, only the direction is unknown.
     */
    public static OneShotPlacement oneShotOrigin(
            SoulVoice voice,
            List<VoiceRoom> rooms,
            Map<String, ArchetypeDefinition> archetypes,
            double listenerX,
            double listenerY,
            double listenerZ,
            double pickRoll,
            double horizontalRoll,
            OneShotProfile profile)
    {
        final Set<SoulTrait> traits = traitsOf(voice);

        if (traits.isEmpty() || rooms == null || rooms.isEmpty() || archetypes == null)
        {
            return null;
        }

        double total = 0d;

        for (VoiceRoom room : rooms)
        {
            total += pullOf(room, traits, archetypes);
        }

        if (total <= 0d)
        {
            return null;
        }

        double target = Math.max(0d, Math.min(0.999999d, pickRoll)) * total;
        VoiceRoom chosen = null;

        for (VoiceRoom room : rooms)
        {
            final double weight = pullOf(room, traits, archetypes);

            if (weight <= 0d)
            {
                continue;
            }

            if (target < weight)
            {
                chosen = room;
                break;
            }

            target -= weight;
            chosen = room;
        }

        if (chosen == null)
        {
            return null;
        }

        final RoomBox box = chosen.box();
        final double dx = box.nearestX(listenerX) - listenerX;
        final double dy = box.nearestY(listenerY) - listenerY;
        final double dz = box.nearestZ(listenerZ) - listenerZ;
        final double reach = Math.sqrt(dx * dx + dz * dz);

        if (reach < 1e-4d)
        {
            // standing in it, or directly under it: there is no compass direction to point at, and
            // inventing one would be worse than the honest random placement
            return null;
        }

        final double band = profile.near()
                + Math.max(0d, Math.min(1d, horizontalRoll)) * (profile.far() - profile.near());
        final double horizontal = Math.max(MIN_ROOM_HORIZONTAL, Math.min(band, reach));
        final double vertical = Math.max(-profile.maxVertical(), Math.min(profile.maxVertical(), dy));

        return OneShotPlacement.fitted(horizontal, vertical, dx / reach, dz / reach);
    }

    /**
     * Which traits a voice speaks for - one for a pole, both for a contested reading, none for
     * {@link SoulVoice#BASE}, which is the place rather than anything built in it.
     */
    public static Set<SoulTrait> traitsOf(SoulVoice voice)
    {
        for (SoulAxis axis : SoulAxis.values())
        {
            if (Palette.contestedVoice(axis) == voice)
            {
                return Set.of(axis.positive(), axis.negative());
            }
        }

        for (SoulTrait trait : SoulTrait.values())
        {
            if (Palette.voice(trait) == voice)
            {
                return Set.of(trait);
            }
        }

        return Set.of();
    }

    private static double pullOf(
            VoiceRoom room, Set<SoulTrait> traits, Map<String, ArchetypeDefinition> archetypes)
    {
        final ArchetypeDefinition archetype = room == null ? null : archetypes.get(room.archetypeId());

        if (archetype == null)
        {
            return 0d;
        }

        double pull = 0d;

        for (SoulTrait trait : traits)
        {
            pull += archetype.characterPulls().getOrDefault(trait, 0d);
        }

        return pull;
    }

    /**
     * How loud the ambient bed may be while this mod's own audio is playing, and how it comes back
     * afterwards (#212).
     *
     * <p>The step down is here and the smoothing is not: this hands back a target, and
     * {@code ClientAmbience} eases toward it a fixed share per tick like everything else in the
     * epic. A duck that snaps is a click, and a duck that recovers in one frame is a swell.
     *
     * @param holdTicksRemaining how much of a hold is still running, 0 for none
     * @param ticksSinceRelease  ticks since the last hold ended
     * @return 1 for undisturbed, {@link #DUCK_LEVEL} while held, rising between the two
     */
    public static float duckLevel(int holdTicksRemaining, int ticksSinceRelease)
    {
        if (holdTicksRemaining > 0)
        {
            return DUCK_LEVEL;
        }

        if (ticksSinceRelease >= DUCK_RECOVERY_TICKS)
        {
            return 1f;
        }

        final float recovered = Math.max(0, ticksSinceRelease) / (float) DUCK_RECOVERY_TICKS;

        return DUCK_LEVEL + (1f - DUCK_LEVEL) * recovered;
    }

    /**
     * One rank's worth of one-shot behaviour - see {@link #oneShotProfile}.
     *
     * @param near        nearest the band throws a one-shot, in blocks on the XZ plane
     * @param far         furthest it does
     * @param maxVertical how far above or below the listener's ear one may land
     * @param echoes      repeats after the first sound, none at rank 0
     * @param echoDelayTicks gap between one repeat and the next, which also grows with rank
     * @param leadVolume  the share of a one-shot's nominal volume the first sound gets, chosen so
     *                    the whole tail sums to exactly one of them
     * @param echoFalloff the share of the previous sound each repeat keeps
     * @param pitch       the base pitch, before the caller's own jitter
     */
    public record OneShotProfile(
            double near,
            double far,
            double maxVertical,
            int echoes,
            int echoDelayTicks,
            float leadVolume,
            float echoFalloff,
            float pitch)
    {
        /** What the first sound and every echo add up to, as a share of one unechoed one-shot. */
        public float totalEnergy()
        {
            float total = this.leadVolume;
            float echo = this.leadVolume;

            for (int i = 0; i < this.echoes; i++)
            {
                echo *= this.echoFalloff;
                total += echo;
            }

            return total;
        }

        /** The volume of the {@code index}-th repeat, counting the first sound as 0. */
        public float volumeOf(int index)
        {
            return this.leadVolume * (float) Math.pow(this.echoFalloff, Math.max(0, index));
        }
    }

    /**
     * @param horizontalDistance blocks from the listener on the XZ plane
     * @param verticalOffset     blocks above (positive) or below the listener's own ear height
     * @param directionX         unit vector toward the room this came from, or 0 with {@code
     *                           directionZ} for "the caller picks a compass angle"
     * @param directionZ         as above
     */
    public record OneShotPlacement(
            double horizontalDistance, double verticalOffset, double directionX, double directionZ)
    {
        /** Vanilla's own linear-attenuation radius for every event {@code SoulAmbienceSounds} draws from. */
        public static final double AUDIBLE_RADIUS = 16d;

        /** Kept this many blocks short of the radius, so a slow client tick or float drift can't tip a draw over it. */
        public static final double SAFETY_MARGIN = 4d;

        /**
         * The same placement, shrunk if it would otherwise reach the attenuation cliff.
         *
         * <p>Belt and braces: the rank bands are chosen so this never has to do anything, and it is
         * here so that a later tuning pass, or a verge small enough to squeeze the band, cannot
         * reintroduce #208 by arithmetic nobody re-checked.
         */
        public static OneShotPlacement fitted(
                double horizontal, double vertical, double directionX, double directionZ)
        {
            final double limit = AUDIBLE_RADIUS - SAFETY_MARGIN;
            final double distance = Math.sqrt(horizontal * horizontal + vertical * vertical);

            if (distance <= limit || distance <= 0d)
            {
                return new OneShotPlacement(horizontal, vertical, directionX, directionZ);
            }

            final double scale = limit / distance;

            return new OneShotPlacement(horizontal * scale, vertical * scale, directionX, directionZ);
        }

        /** Whether this carries a direction of its own, or the caller should roll a compass angle. */
        public boolean hasDirection()
        {
            return this.directionX != 0d || this.directionZ != 0d;
        }

        /** 3D distance from the listener - what vanilla's attenuation actually reads, not the horizontal leg alone. */
        public double distanceFromListener()
        {
            return Math.sqrt(this.horizontalDistance * this.horizontalDistance + this.verticalOffset * this.verticalOffset);
        }
    }

    /**
     * A classified room's box, in world coordinates, as the one-shot placement reads it.
     *
     * <p>Maxima are inclusive block coordinates, as {@code RegionBounds} gives them, so the far face
     * of the box is one block further out than {@code max} - which is what {@code nearest} accounts
     * for. A room is a solid the sound comes from, not a plane.
     */
    public record RoomBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        public double nearestX(double x)
        {
            return Math.max(this.minX, Math.min(this.maxX + 1d, x));
        }

        public double nearestY(double y)
        {
            return Math.max(this.minY, Math.min(this.maxY + 1d, y));
        }

        public double nearestZ(double z)
        {
            return Math.max(this.minZ, Math.min(this.maxZ + 1d, z));
        }
    }

    /** One classified room as a candidate for a voice: what it was awarded, and where it stands. */
    public record VoiceRoom(String archetypeId, RoomBox box)
    {
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

    private static double lerp(double from, double to, double amount)
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
