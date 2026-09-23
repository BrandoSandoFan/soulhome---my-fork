/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Writes one piece of a soul's music (#163) - a few minutes of it, then {@link SoulSynth} rests
 * before asking for the next.
 *
 * <h2>What keeps it listenable without anyone listening</h2>
 *
 * <p>This was written by something with no ears, for an owner who said up front they did not trust
 * its taste, so the rules that make it listenable are structural rather than a matter of tuning:
 *
 * <ul>
 *   <li><b>Every note is in the mode.</b> Harmony, melody and arpeggio all come off {@link
 *       MusicMode#note}, so there is no wrong note to play. {@code SoulComposerTest} checks every
 *       event of every voice's pieces.</li>
 *   <li><b>A melody is a motif, not a random walk.</b> Generative music's recognisable failure is
 *       notes that never become a tune. Each piece writes one short motif and then states it, varies
 *       it and transposes it onto each chord - which is what makes a phrase something a player can
 *       half-remember, and what a random walk never is.</li>
 *   <li><b>Harmony moves slowly and along a known path.</b> Two bars a chord, chosen from a table of
 *       moves that work in any mode (tonic to subdominant, relative minor, and back), always ending
 *       where it started so the piece closes rather than stops.</li>
 *   <li><b>It breathes.</b> Density rises and falls across the piece - thin at either end, fullest
 *       in the middle - and the lead rests more often than it plays. Ambient music that never stops
 *       for breath is a drone with ambitions.</li>
 * </ul>
 *
 * <h2>Rank</h2>
 *
 * <p>The same "larger, never louder" rule as the one-shots (#216). A higher rank is slower, longer,
 * set wider across the stereo field and given more room in {@link SoulSynth}'s reverb. Not one note
 * of it is louder.
 *
 * <p>Minecraft-free and deterministic: the same brief and seed write the same piece, which is what
 * lets it be tested, and what lets a soul's music be recognisably its own from visit to visit.
 */
public final class SoulComposer
{
    /** Slowest and fastest tempo across the ranks, before the style's own multiplier. */
    public static final double RANK_0_BPM = 72d;

    public static final double RANK_MAX_BPM = 58d;

    /** How many chords a piece holds, at rank 0 and at the ceiling - two bars each. */
    public static final int RANK_0_CHORDS = 12;

    public static final int RANK_MAX_CHORDS = 20;

    /** Seconds a piece is left to ring after its last note is released. */
    public static final double TAIL_SECONDS = 8d;

    /**
     * Where a chord on each degree may go next, as {@code {to, weight}} pairs. Written in degrees so
     * one table serves every mode: I to IV, I to vi, IV to V, vi to IV and so on - the moves that
     * sound like somewhere in Ionian and still sound like somewhere in Dorian or Aeolian.
     */
    private static final int[][][] MOVES = {
            /* 0 */ {{3, 4}, {5, 4}, {4, 2}, {1, 1}},
            /* 1 */ {{4, 4}, {3, 2}, {5, 1}},
            /* 2 */ {{5, 3}, {3, 2}},
            /* 3 */ {{0, 3}, {4, 3}, {1, 2}, {5, 1}},
            /* 4 */ {{0, 4}, {5, 2}, {3, 1}},
            /* 5 */ {{3, 4}, {1, 2}, {4, 2}, {2, 1}},
            /* 6 */ {{0, 3}, {4, 1}}};

    /** The rhythmic values a motif is built from, in beats. No sixteenths: nothing here hurries. */
    private static final double[] MOTIF_RHYTHMS = {1d, 1d, 2d, 1.5d, 0.5d, 3d};

    private SoulComposer()
    {
    }

    /**
     * One composed piece, ready for {@link SoulSynth}.
     *
     * @param voice     what the piece was written for
     * @param support   the runner-up voice answering under it, or null for a solo
     * @param mode      its mode
     * @param tonic     its tonic, as a MIDI note
     * @param bpm       its tempo
     * @param space     0 to 1: how much room the reverb gives it - rank, heard
     * @param length    seconds from its first note to the end of its tail
     * @param events    every note, sorted by start
     */
    public record Piece(
            SoulVoice voice, SoulVoice support, MusicMode mode, int tonic, double bpm, double space,
            double length, List<NoteEvent> events)
    {
        public Piece
        {
            events = List.copyOf(events);
        }
    }

    /** Writes the piece {@code brief} asks for, from {@code seed}. */
    public static Piece compose(SoulMusicBrief brief, long seed)
    {
        final SplittableRandom random = new SplittableRandom(seed);
        final SoulVoice voice = brief.pick(random.nextDouble());
        final SoulVoice support = brief.runnerUp(voice);
        final MusicStyle style = MusicStyle.of(voice);
        final MusicStyle supportStyle = support == null ? null : MusicStyle.of(support);

        final double rank = brief.rankFraction();
        final double bpm = lerp(RANK_0_BPM, RANK_MAX_BPM, rank) * style.tempo();
        final double beat = 60d / bpm;
        final double bar = 4d * beat;
        final double chordLength = 2d * bar;
        final int chords = (int) Math.round(lerp(RANK_0_CHORDS, RANK_MAX_CHORDS, rank)) + random.nextInt(3);
        final int tonic = brief.tonic();
        final MusicMode mode = style.mode();

        // wider with rank: a larger soul is set further across the field, not played harder
        final float spread = (float) lerp(0.35d, 0.8d, rank);

        final List<NoteEvent> events = new ArrayList<>();
        final int[] progression = progression(chords, random, mode);
        final Motif motif = Motif.write(random, mode);

        for (int index = 0; index < chords; index++)
        {
            final double start = index * chordLength;
            final int degree = progression[index];
            final double density = shape((index + 0.5d) / chords) * style.density();

            pad(events, mode, tonic, degree, start, chordLength, style, spread, random);

            if (random.nextDouble() < density)
            {
                motif.state(events, mode, tonic + 12 * style.leadOctave(), degree, start, beat, style.lead(),
                        (float) (0.55d + 0.2d * random.nextDouble()), spread * 0.5f * signed(random), random);
            }

            if (random.nextDouble() < density * 0.7d)
            {
                arpeggio(events, mode, tonic + 12 * (style.leadOctave() + 1), degree, start, beat, style.texture(),
                        0.28f, spread, random);
            }

            // the runner-up voice answers under the lead - still in this piece's mode, so it is
            // heard as the soul's other half rather than as a second piece playing over the first
            if (supportStyle != null && random.nextDouble() < density * 0.5d * supportShare(brief, voice, support))
            {
                final Instrument answer = supportStyle.lead() == style.lead() ? supportStyle.texture() : supportStyle.lead();

                arpeggio(events, mode, tonic + 12 * supportStyle.leadOctave(), degree, start + bar, beat, answer,
                        0.3f, spread, random);
            }
        }

        final double body = chords * chordLength;

        if (style.drone())
        {
            events.add(new NoteEvent(0d, body, tonic - 12, 0.5f, Instrument.DRONE, 0f));
        }

        // the last chord again, on the tonic, held into the tail so the piece closes on home
        pad(events, mode, tonic, 0, body, bar * 2d, style, spread, random);

        events.sort(Comparator.comparingDouble(NoteEvent::start));

        return new Piece(voice, support, mode, tonic, bpm, rank, body + bar * 2d + TAIL_SECONDS, events);
    }

    /**
     * Density across a piece, 0 to 1: thin at either end and fullest a little past the middle, so
     * a piece arrives, says what it has to say and leaves rather than starting and stopping.
     */
    static double shape(double position)
    {
        final double t = Math.max(0d, Math.min(1d, position));

        return 0.25d + 0.75d * Math.pow(Math.sin(Math.PI * Math.pow(t, 0.85d)), 1.5d);
    }

    /** A chord walk that starts on the tonic and ends on it, never sitting on one chord twice running. */
    static int[] progression(int chords, SplittableRandom random, MusicMode mode)
    {
        final int[] out = new int[chords];
        int current = 0;

        for (int index = 1; index < chords; index++)
        {
            if (index == chords - 1)
            {
                // arrive home from somewhere that leads there, rather than from wherever the walk ended
                out[index - 1] = out[index - 1] == 0 && mode.stable(4) ? 4 : out[index - 1];
                out[index] = 0;
                break;
            }

            current = step(current, random, mode);
            out[index] = current;
        }

        return out;
    }

    /**
     * The next chord, never one whose fifth is diminished. Every mode has exactly one such degree -
     * Lydian's is its fourth, which the table leans on hardest - and a diminished triad held for two
     * bars under a pad is the one chord here that would sound like a mistake rather than a mood.
     */
    private static int step(int from, SplittableRandom random, MusicMode mode)
    {
        for (int attempt = 0; attempt < 8; attempt++)
        {
            final int next = move(from, random);

            if (mode.stable(next))
            {
                return next;
            }
        }

        return 0;
    }

    private static int move(int from, SplittableRandom random)
    {
        final int[][] moves = MOVES[Math.floorMod(from, 7)];
        int total = 0;

        for (int[] move : moves)
        {
            total += move[1];
        }

        int target = random.nextInt(total);

        for (int[] move : moves)
        {
            if (target < move[1])
            {
                return move[0];
            }

            target -= move[1];
        }

        return 0;
    }

    /**
     * The pad's chord: a bass note, then the chord's triad spread open, crowned with the style's
     * colour - a seventh, or the fifth again an octave up where a seventh would be too heavy. Held a
     * little past the chord so the next one arrives under it rather than after a gap.
     */
    private static void pad(
            List<NoteEvent> events, MusicMode mode, int tonic, int degree, double start, double length,
            MusicStyle style, float spread, SplittableRandom random)
    {
        final double held = length * 1.12d;
        final float velocity = (float) (0.42d + 0.08d * random.nextDouble());

        // open voicing: the third goes up an octave (a tenth) rather than sitting a third above the
        // root, which is what keeps a low triad from turning to mud on a small speaker
        events.add(new NoteEvent(start, held, mode.note(tonic - 12, degree), velocity, Instrument.PAD, 0f));
        events.add(new NoteEvent(start, held, mode.note(tonic, degree), velocity * 0.8f, Instrument.PAD, -spread * 0.6f));
        events.add(new NoteEvent(start, held, mode.note(tonic, degree + 4), velocity * 0.75f, Instrument.PAD, spread * 0.6f));
        events.add(new NoteEvent(start, held, mode.note(tonic, degree + 9), velocity * 0.6f, Instrument.PAD, -spread * 0.3f));
        events.add(new NoteEvent(start, held, mode.note(tonic, degree + (style.seventh() ? 13 : 11)),
                velocity * 0.45f, Instrument.PAD, spread * 0.3f));
    }

    /**
     * An arpeggio over one bar: up the chord, sometimes back down, a note a beat or a half beat.
     * Quiet - it is texture - and the colour degree of the mode is folded in now and then so a
     * Lydian piece sounds Lydian.
     */
    private static void arpeggio(
            List<NoteEvent> events, MusicMode mode, int base, int degree, double start, double beat,
            Instrument instrument, float velocity, float spread, SplittableRandom random)
    {
        final int[] shape = random.nextBoolean() ? new int[] {0, 2, 4, 7} : new int[] {0, 4, 2, 4, 7, 4};
        final double step = shape.length > 4 ? beat * 0.5d : beat;
        final float pan = spread * signed(random);

        for (int index = 0; index < shape.length; index++)
        {
            int noteDegree = degree + shape[index];

            if (index == shape.length - 1 && random.nextDouble() < 0.35d)
            {
                noteDegree = mode.colourDegree() + 7 * Math.floorDiv(noteDegree, 7);
            }

            events.add(new NoteEvent(
                    start + index * step + humanise(random),
                    step * 1.5d,
                    mode.note(base, noteDegree),
                    velocity * (float) (0.85d + 0.3d * random.nextDouble()),
                    instrument,
                    pan * (1f - index / (float) shape.length)));
        }
    }

    /** A few milliseconds either side of the grid, so a struck note does not land like a metronome. */
    private static double humanise(SplittableRandom random)
    {
        return (random.nextDouble() - 0.5d) * 0.03d;
    }

    private static float signed(SplittableRandom random)
    {
        return random.nextBoolean() ? 1f : -1f;
    }

    /** How strongly the runner-up is heard: its weight against the lead's, never more than the lead. */
    private static double supportShare(SoulMusicBrief brief, SoulVoice voice, SoulVoice support)
    {
        final double lead = brief.weight(voice);
        final double second = brief.weight(support);

        return lead <= 0d ? 0d : Math.min(1d, 0.4d + second / lead);
    }

    /** The chord tone of {@code chordDegree}'s triad nearest to {@code degree}, in any octave. */
    static int snapToChord(int degree, int chordDegree)
    {
        int best = degree;
        int bestDistance = Integer.MAX_VALUE;

        for (int octave = -2; octave <= 3; octave++)
        {
            for (int tone : new int[] {0, 2, 4})
            {
                final int candidate = chordDegree + tone + 7 * octave;
                final int distance = Math.abs(candidate - degree);

                if (distance < bestDistance)
                {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static double lerp(double from, double to, double amount)
    {
        return from + (to - from) * Math.max(0d, Math.min(1d, amount));
    }

    /**
     * A short phrase - four to six notes, in scale degrees and beats - stated on a chord and varied
     * each time it comes back.
     */
    private record Motif(int[] degrees, double[] rhythm)
    {
        static Motif write(SplittableRandom random, MusicMode mode)
        {
            final int length = 4 + random.nextInt(3);
            final int[] degrees = new int[length];
            final double[] rhythm = new double[length];
            int degree = random.nextBoolean() ? 0 : 2 + 2 * random.nextInt(2);

            for (int index = 0; index < length; index++)
            {
                degrees[index] = degree;
                rhythm[index] = MOTIF_RHYTHMS[random.nextInt(MOTIF_RHYTHMS.length)];

                // mostly steps, sometimes a third, rarely a leap - and a leap turns back, which is
                // the oldest rule of melody there is and still the one that makes a line a line
                final double shape = random.nextDouble();
                final int move = shape < 0.6d ? 1 : shape < 0.88d ? 2 : 4;
                final int direction = degree > 4 ? -1 : degree < -1 ? 1 : random.nextBoolean() ? 1 : -1;

                degree += move * direction;
            }

            // the phrase lands on the long note, so it sounds finished
            rhythm[length - 1] = Math.max(rhythm[length - 1], 2d);

            // and the mode's own colour is in it somewhere (exactly so when stated on the tonic chord)
            if (random.nextDouble() < 0.6d)
            {
                degrees[1 + random.nextInt(length - 1)] = mode.colourDegree();
            }

            return new Motif(degrees, rhythm);
        }

        /**
         * The motif, stated once over a chord: transposed to the chord's root, sometimes inverted in
         * contour or with a note dropped, so its return is familiar and never identical.
         */
        void state(
                List<NoteEvent> events, MusicMode mode, int base, int chordDegree, double start, double beat,
                Instrument instrument, float velocity, float pan, SplittableRandom random)
        {
            final boolean invert = random.nextDouble() < 0.25d;
            final int skip = random.nextDouble() < 0.3d ? 1 + random.nextInt(this.degrees.length - 1) : -1;
            final int shift = chordDegree > 4 ? chordDegree - 7 : chordDegree;
            double at = start + beat * random.nextInt(3);

            for (int index = 0; index < this.degrees.length; index++)
            {
                final double length = this.rhythm[index] * beat;

                if (index != skip)
                {
                    final int relative = this.degrees[index] - this.degrees[0];
                    int degree = this.degrees[0] + (invert ? -relative : relative) + shift;

                    // where the phrase leans hardest - its first note and the long one it lands on -
                    // it sits on the chord, so the tune agrees with the pad at the moments it is heard
                    if (index == 0 || index == this.degrees.length - 1)
                    {
                        degree = snapToChord(degree, chordDegree);
                    }

                    events.add(new NoteEvent(
                            at + humanise(random),
                            length * 0.95d,
                            mode.note(base, degree),
                            velocity * (float) (0.9d + 0.2d * random.nextDouble()) * (index == 0 ? 1.08f : 1f),
                            instrument,
                            pan));
                }

                at += length;
            }
        }
    }
}
