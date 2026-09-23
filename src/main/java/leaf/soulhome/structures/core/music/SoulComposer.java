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
 * Writes one piece of a soul's music (#163) - a couple of minutes of it, then {@link SoulSynth}
 * rests before asking for the next.
 *
 * <h2>What keeps it listenable without anyone listening</h2>
 *
 * <p>This was written by something with no ears, for an owner who said up front they did not trust
 * its taste, so the rules that make it listenable are structural rather than a matter of tuning:
 *
 * <ul>
 *   <li><b>Every note is in the mode.</b> Harmony, melody and arpeggio all come off {@link
 *       MusicMode#note}, so there is no wrong note to play. {@code SoulComposerTest} checks every
 *       pitched event of every voice's pieces.</li>
 *   <li><b>A melody is a motif, not a random walk.</b> Each piece writes one short motif and then
 *       states it, varies it and transposes it onto each chord - which is what makes a phrase
 *       something a player can half-remember, and what a random walk never is.</li>
 *   <li><b>Harmony moves slowly and along a known path.</b> Two bars a chord, chosen from a table of
 *       moves that work in any mode, never onto a diminished chord, always ending where it started.</li>
 *   <li><b>It breathes.</b> Density rises and falls across the piece - thin at either end, fullest
 *       in the middle.</li>
 * </ul>
 *
 * <h2>Each mood moves its own way (#261)</h2>
 *
 * <p>The first version wrote every voice the same way - a slow pad, a motif now and then, an
 * arpeggio now and then - and only the instruments changed, which is how a workshop came out as chill
 * as an ossuary. Each {@link MusicStyle.Groove} now has a writer of its own: a forge is a hammer on
 * the beat and an anvil on the backbeat over a driving bass; a hearth is quick figures flickering in
 * the middle of the range over a still base, with the fire crackling under it. The foley is written
 * into the score, on the grid, so a clank is part of the rhythm rather than an interruption of it.
 *
 * <h2>Nothing piercing, nothing held high</h2>
 *
 * <p>The same listen found everything too high and a wobbly high note held too long. Leads now sit
 * one octave over a tonic that is itself an octave lower, and every pitched note goes in through one
 * door ({@code Score#add}): nothing above {@link #CEILING}, and nothing at or above {@link
 * #HIGH_NOTE} held longer than {@link #HIGH_NOTE_BEATS} beats. That is a property the tests can hold
 * rather than a tendency the writers have.
 *
 * <h2>Rank</h2>
 *
 * <p>The same "larger, never louder" rule as the one-shots (#216). A higher rank is a little slower,
 * longer, set wider across the stereo field and given more room in {@link SoulSynth}'s reverb. Not
 * one note of it is louder.
 *
 * <p>Minecraft-free and deterministic: the same brief and seed write the same piece.
 */
public final class SoulComposer
{
    /** The highest pitched note any piece plays: G5, 784 Hz. */
    public static final int CEILING = 79;

    /** From here up (C5, 523 Hz) a note is a passing one: struck, or held no longer than {@link #HIGH_NOTE_BEATS}. */
    public static final int HIGH_NOTE = 72;

    public static final double HIGH_NOTE_BEATS = 1.5d;

    /** How much slower a style is played at the ceiling than at rank 0. */
    public static final double RANK_MAX_SLOWDOWN = 0.88d;

    /** How long a piece runs at rank 0 and at the ceiling, before its tail. */
    public static final double RANK_0_SECONDS = 100d;

    public static final double RANK_MAX_SECONDS = 170d;

    /** Seconds a piece is left to ring after its last note is released. */
    public static final double TAIL_SECONDS = 8d;

    /**
     * Where a chord on each degree may go next, as {@code {to, weight}} pairs. Written in degrees so
     * one table serves every mode: I to IV, I to vi, IV to V, vi to IV and so on.
     */
    private static final int[][][] MOVES = {
            /* 0 */ {{3, 4}, {5, 4}, {4, 2}, {1, 1}},
            /* 1 */ {{4, 4}, {3, 2}, {5, 1}},
            /* 2 */ {{5, 3}, {3, 2}},
            /* 3 */ {{0, 3}, {4, 3}, {1, 2}, {5, 1}},
            /* 4 */ {{0, 4}, {5, 2}, {3, 1}},
            /* 5 */ {{3, 4}, {1, 2}, {4, 2}, {2, 1}},
            /* 6 */ {{0, 3}, {4, 1}}};

    /** The rhythmic values a motif is built from, in beats. */
    private static final double[] MOTIF_RHYTHMS = {1d, 1d, 2d, 1.5d, 0.5d, 0.5d};

    /** The workshop's bass, in degrees above the chord's root, an eighth note each. */
    private static final int[] FORGE_BASS = {0, 0, 7, 0, 4, 0, 7, 4};

    private SoulComposer()
    {
    }

    /**
     * One composed piece, ready for {@link SoulSynth}.
     *
     * @param voice   what the piece was written for
     * @param support the runner-up voice answering under it, or null for a solo
     * @param mode    its mode
     * @param tonic   its tonic, as a MIDI note
     * @param bpm     its tempo
     * @param space   0 to 1: how much room the reverb gives it - rank, heard
     * @param length  seconds from its first note to the end of its tail
     * @param events  every note, sorted by start
     */
    public record Piece(
            SoulVoice voice, SoulVoice support, MusicMode mode, int tonic, double bpm, double space,
            double length, List<NoteEvent> events)
    {
        public Piece
        {
            events = List.copyOf(events);
        }

        /** Seconds per beat. */
        public double beat()
        {
            return 60d / this.bpm;
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
        final double bpm = style.bpm() * lerp(1d, RANK_MAX_SLOWDOWN, rank);
        final double beat = 60d / bpm;
        final double chordLength = 8d * beat;
        final double target = lerp(RANK_0_SECONDS, RANK_MAX_SECONDS, rank) + random.nextDouble() * 20d;
        final int chords = Math.max(6, (int) Math.round(target / chordLength));
        final MusicMode mode = style.mode();

        final Score score = new Score(mode, brief.tonic(), beat, (float) lerp(0.35d, 0.8d, rank), random);
        final int[] progression = progression(chords, random, mode);
        final Motif motif = Motif.write(random, mode);
        final double share = supportStyle == null ? 0d : supportShare(brief, voice, support);

        for (int index = 0; index < chords; index++)
        {
            final Chord chord = new Chord(
                    index * chordLength, progression[index], index, chords,
                    shape((index + 0.5d) / chords) * style.density());

            pad(score, chord, style, style.groove() == MusicStyle.Groove.FORGE);
            groove(score, chord, style, motif);

            if (supportStyle != null && index > 0 && index < chords - 1)
            {
                answer(score, chord, supportStyle, style, share);
            }
        }

        final double body = chords * chordLength;

        if (style.drone())
        {
            score.add(0d, body, score.tonic - 12, 0.5f, Instrument.DRONE, 0f);
        }

        if (style.groove() == MusicStyle.Groove.FLICKER)
        {
            // the fire itself, under the whole piece: the calm layer the flicker plays over
            score.add(0d, body + 4d * beat, 60, 0.55f, Instrument.CRACKLE, 0f);
        }

        // the tonic chord again, held into the tail so the piece closes on home - held even in a
        // forge, where the work has stopped
        pad(score, new Chord(body, 0, chords, chords, 0d), style, false);

        if (style.groove() == MusicStyle.Groove.FORGE)
        {
            // and the forge ends on one last strike, with the hammer
            score.add(body, 0.2d, score.tonic, 0.9f, Instrument.THUMP, 0f);
            score.add(body, 0.3d, mode.note(score.tonic + 12, 0), 0.7f, Instrument.CLANK, 0.2f);
        }

        score.events.sort(Comparator.comparingDouble(NoteEvent::start));

        return new Piece(voice, support, mode, score.tonic, bpm, rank, body + 8d * beat + TAIL_SECONDS, score.events);
    }

    // ---------------------------------------------------------------------------------------------
    // The grooves
    // ---------------------------------------------------------------------------------------------

    private static void groove(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        switch (style.groove())
        {
            case CALM -> calm(score, chord, style, motif);
            case FLICKER -> flicker(score, chord, style, motif, 1d);
            case FORGE -> forge(score, chord, style, motif);
            case DRIFT -> drift(score, chord, style, motif);
            case SHIMMER -> shimmer(score, chord, style, motif);
            case LILT -> lilt(score, chord, style, motif);
            case TOLL -> toll(score, chord, style);
            case VENT -> vent(score, chord, style, motif);
            case CLOCKWORK -> clockwork(score, chord, style, motif);
            case DAMP -> damp(score, chord, style, motif);
        }
    }

    /** The place itself: a mallet stating the motif now and then, glass answering under it. */
    private static void calm(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        if (score.random.nextDouble() < chord.density)
        {
            motif.state(score, chord.degree, chord.start, style.lead(), 0.55f, 1d);
        }

        if (score.random.nextDouble() < chord.density * 0.5d)
        {
            // from the fifth rather than the octave, so the texture sits under the tune rather than over it
            arpeggio(score, chord, 4, style.texture(), 0.26f, 1d);
        }
    }

    /**
     * Fire (#261): quick figures in the middle of the range - bursts of two or three eighths or
     * triplets that come and go like flames - over the still pad, with the crackle bed under all
     * of it and sharper pops thrown in off the grid.
     *
     * @param busy how much of the flicker to write; steam reuses this at a little over half
     */
    private static void flicker(Score score, Chord chord, MusicStyle style, Motif motif, double busy)
    {
        final double beat = score.beat;

        for (int bar = 0; bar < 2; bar++)
        {
            for (int count = 0; count < 4; count++)
            {
                if (score.random.nextDouble() >= (0.3d + 0.6d * chord.density) * busy)
                {
                    continue;
                }

                final double at = chord.start + (bar * 4 + count) * beat;
                final boolean triplet = score.random.nextInt(3) == 0;
                final double step = triplet ? beat / 3d : beat / 2d;
                final int notes = 1 + score.random.nextInt(triplet ? 3 : 2);
                // a fire's notes are quick, not high (#261)
                int degree = chord.root() + new int[] {0, 2, 4, -3}[score.random.nextInt(4)];

                for (int note = 0; note < notes; note++)
                {
                    score.add(at + note * step + score.humanise(), step * 0.9d, score.tune(score.mode.note(score.lead(), degree)),
                            (float) (0.38d + 0.18d * score.random.nextDouble()) * (note == 0 ? 1.1f : 1f),
                            style.lead(), score.pan() * 0.6f);

                    // flames lick up and fall back: a step either way, mostly down
                    degree += score.random.nextInt(4) == 0 ? 1 : -1;
                }
            }
        }

        if (score.random.nextDouble() < chord.density * 0.4d * busy)
        {
            // the tune, played at the fire's pace
            motif.state(score, chord.degree, chord.start + 4d * beat, style.lead(), 0.5f, 0.5d);
        }

        // pops: sharper than the bed, off the grid, a couple a bar
        final int pops = (int) Math.round((1d + 3d * chord.density) * busy);

        for (int pop = 0; pop < pops; pop++)
        {
            score.add(chord.start + score.random.nextDouble() * 8d * beat, 0.05d, 60,
                    (float) (0.45d + 0.4d * score.random.nextDouble()), Instrument.CRACKLE, score.pan());
        }
    }

    /**
     * A workshop (#261): the hammer on one and three, the anvil on two and four - two anvils a fifth
     * apart, taking turns - a ratchet ticking the off-beats, and a plucked bass driving eighths
     * under a short riff. It builds in over the first chords, hammer and bass first, so a piece
     * starts at work rather than arriving at it.
     */
    private static void forge(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;
        final double position = chord.index / (double) Math.max(1, chord.of - 1);
        final boolean ticks = position >= 0.1d;
        final boolean anvil = position >= 0.2d;

        for (int bar = 0; bar < 2; bar++)
        {
            final double downbeat = chord.start + bar * 4d * beat;

            for (int eighth = 0; eighth < 8; eighth++)
            {
                final double at = downbeat + eighth * beat / 2d;
                final boolean onBeat = eighth % 2 == 0;

                score.add(at, beat * 0.45d, score.mode.note(score.tonic, chord.degree + FORGE_BASS[eighth]),
                        onBeat ? 0.48f : 0.36f, Instrument.PLUCK, -0.15f);

                if (!onBeat && ticks)
                {
                    score.add(at, 0.02d, 88, eighth == 7 ? 0.34f : 0.26f, Instrument.TICK, 0.35f);
                }
            }

            score.add(downbeat, 0.2d, score.tonic, 0.95f, Instrument.THUMP, 0f);
            score.add(downbeat + 2d * beat, 0.2d, score.tonic, 0.8f, Instrument.THUMP, 0f);

            if (score.random.nextInt(5) == 0)
            {
                // the hammer's rebound, a pickup into the next bar
                score.add(downbeat + 3.5d * beat, 0.2d, score.tonic, 0.5f, Instrument.THUMP, 0f);
            }

            if (anvil)
            {
                score.add(downbeat + beat, 0.3d, score.mode.note(score.tonic + 12, chord.degree), 0.62f, Instrument.CLANK, 0.25f);
                score.add(downbeat + 3d * beat, 0.3d, score.mode.note(score.tonic + 12, chord.degree + 4),
                        0.66f, Instrument.CLANK, -0.25f);
            }
        }

        if (anvil && score.random.nextDouble() < chord.density)
        {
            // the riff: the motif at double time on the lead, answered by the mallet an octave down
            motif.state(score, chord.degree, chord.start, style.lead(), 0.5f, 0.5d);
        }
    }

    /**
     * Cold: a bell or two a bar in the middle of the range, a great deal of air between them, the
     * motif rung slowly now and then, and wind going past every few bars.
     */
    private static void drift(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;

        for (int bar = 0; bar < 2; bar++)
        {
            if (score.random.nextDouble() < 0.4d + 0.5d * chord.density)
            {
                final int degree = chord.root() + new int[] {0, 2, 4}[score.random.nextInt(3)];

                score.add(chord.start + bar * 4d * beat + score.humanise(), beat, score.mode.note(score.lead(), degree),
                        (float) (0.42d + 0.12d * score.random.nextDouble()), style.lead(), score.pan());
            }
        }

        if (score.random.nextDouble() < chord.density * 0.45d)
        {
            motif.state(score, chord.degree, chord.start + 4d * beat, style.lead(), 0.45f, 1d);
        }

        if (chord.index % 2 == 1 || score.random.nextDouble() < 0.25d)
        {
            score.add(chord.start + beat * score.random.nextInt(4), beat * (6d + 4d * score.random.nextDouble()), 67,
                    (float) (0.4d + 0.2d * score.random.nextDouble()), Instrument.WIND, score.pan());
        }

        if (score.random.nextDouble() < 0.3d)
        {
            score.add(chord.start + score.random.nextDouble() * 8d * beat, 0.1d, 78, 0.4f, Instrument.DRIP, score.pan());
        }
    }

    /**
     * The arcane, brought down to where it can be listened to (#261): slow rolling arpeggios in the
     * low middle of the range over the drone, a sparse lead, and a swell of rising air breathing into
     * each change of chord.
     */
    private static void shimmer(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;

        for (int bar = 0; bar < 2; bar++)
        {
            if (score.random.nextDouble() < 0.4d + 0.5d * chord.density)
            {
                final int[] roll = {0, 2, 4, 7, 4, 2};

                for (int note = 0; note < roll.length; note++)
                {
                    score.add(chord.start + bar * 4d * beat + note * beat / 1.5d + score.humanise(), beat * 0.6d,
                            score.mode.note(score.tonic, chord.root() + 4 + roll[note]),
                            (float) (0.3d + 0.08d * score.random.nextDouble()), Instrument.GLASS,
                            score.spread * (note / (float) roll.length - 0.5f));
                }
            }
        }

        if (score.random.nextDouble() < chord.density * 0.5d)
        {
            motif.state(score, chord.degree, chord.start + 4d * beat, style.texture(), 0.45f, 1d);
        }

        if (chord.index < chord.of - 1 && score.random.nextDouble() < 0.7d)
        {
            // breathing in: the swell ends exactly where the next chord begins
            score.add(chord.start + 4d * beat, 4d * beat, 60, 0.45f, Instrument.SWELL, 0f);
        }
    }

    /**
     * Growing things: a lilting ostinato on the marimba - long-short, long-short, in triplets - a
     * flute stating the tune over it, birds now and then and leaves moving.
     */
    private static void lilt(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;

        if (score.random.nextDouble() < 0.5d + 0.5d * chord.density)
        {
            final int[] steps = {0, 4, 2, 4};

            for (int count = 0; count < 8; count++)
            {
                final double at = chord.start + count * beat;
                final int degree = chord.root() + steps[count % steps.length];

                score.add(at, beat * 0.6d, score.mode.note(score.tonic + 12, degree), 0.3f, style.texture(), -0.3f);
                score.add(at + beat * 2d / 3d, beat * 0.3d, score.mode.note(score.tonic + 12, degree + 2), 0.22f,
                        style.texture(), -0.3f);
            }
        }

        if (score.random.nextDouble() < chord.density)
        {
            motif.state(score, chord.degree, chord.start, style.lead(), 0.5f, 1d);
        }

        final int birds = score.random.nextDouble() < 0.6d ? 1 + score.random.nextInt(2) : 0;

        for (int bird = 0; bird < birds; bird++)
        {
            score.add(chord.start + score.random.nextDouble() * 8d * beat, 0.3d, 88 + score.random.nextInt(5),
                    (float) (0.35d + 0.2d * score.random.nextDouble()), Instrument.CHIRP, score.pan());
        }

        if (score.random.nextDouble() < 0.35d)
        {
            score.add(chord.start + score.random.nextDouble() * 6d * beat, beat * 2d, 70, 0.4f, Instrument.RUSTLE, score.pan());
        }
    }

    /** Emptied places: a low bell tolling on each downbeat, a fifth answering, water dripping. */
    private static void toll(Score score, Chord chord, MusicStyle style)
    {
        final double beat = score.beat;

        for (int bar = 0; bar < 2; bar++)
        {
            final double downbeat = chord.start + bar * 4d * beat;

            score.add(downbeat, beat, score.mode.note(score.tonic, chord.degree), 0.55f, style.lead(), 0f);

            if (bar == 1 && score.random.nextDouble() < 0.6d)
            {
                score.add(downbeat + 2d * beat, beat, score.mode.note(score.tonic, chord.degree + 4), 0.38f, style.lead(), 0.2f);
            }
        }

        if (score.random.nextDouble() < chord.density * 0.6d)
        {
            score.add(chord.start + beat * (1 + score.random.nextInt(6)), beat,
                    score.mode.note(score.lead(), chord.root() + new int[] {0, 2, 4}[score.random.nextInt(3)]),
                    0.3f, style.texture(), score.pan());
        }

        final int drips = score.random.nextDouble() < 0.7d ? 1 + score.random.nextInt(3) : 0;

        for (int drip = 0; drip < drips; drip++)
        {
            score.add(chord.start + score.random.nextDouble() * 8d * beat, 0.1d, 74 + score.random.nextInt(6),
                    (float) (0.35d + 0.2d * score.random.nextDouble()), Instrument.DRIP, score.pan());
        }

        if (chord.index % 2 == 0)
        {
            // the low wind of a place with nothing in it
            score.add(chord.start, 8d * beat, 52, 0.35f, Instrument.WIND, 0f);
        }
    }

    /**
     * Steam: warm's flicker thinned out and answered by a bell, a soft piston on the downbeat, and
     * vents chuffing on the off-beats of two and four like an engine at rest.
     */
    private static void vent(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;
        final double position = chord.index / (double) Math.max(1, chord.of - 1);

        flicker(score, chord, style, motif, 0.55d);

        for (int bar = 0; bar < 2; bar++)
        {
            final double downbeat = chord.start + bar * 4d * beat;

            score.add(downbeat, 0.2d, score.tonic, 0.45f, Instrument.THUMP, 0f);

            if (position > 0.15d && position < 0.9d)
            {
                score.add(downbeat + 1.5d * beat, beat * 0.35d, 60, 0.36f, Instrument.HISS, -0.3f);
                score.add(downbeat + 3.5d * beat, beat * 0.35d, 60, 0.4f, Instrument.HISS, 0.3f);
            }

            if (score.random.nextDouble() < 0.35d)
            {
                score.add(downbeat + 2d * beat, beat, score.mode.note(score.lead(), chord.root() + 4), 0.36f,
                        style.texture(), score.pan());
            }
        }
    }

    /**
     * Worked matter and the arcane: a clock - tick on every eighth, tock on the off-beat - under a
     * plucked ostinato, a small clank at the top of each chord, and glass carrying the tune.
     */
    private static void clockwork(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;
        final int[] ostinato = {0, 4, 7, 4};

        for (int eighth = 0; eighth < 16; eighth++)
        {
            final double at = chord.start + eighth * beat / 2d;

            score.add(at, 0.02d, eighth % 2 == 0 ? 90 : 85, eighth % 2 == 0 ? 0.3f : 0.22f, Instrument.TICK,
                    eighth % 2 == 0 ? 0.3f : -0.3f);
            score.add(at, beat * 0.4d, score.mode.note(score.tonic, chord.degree + ostinato[eighth % 4]),
                    eighth % 4 == 0 ? 0.42f : 0.32f, style.texture(), -0.2f);
        }

        score.add(chord.start, 0.3d, score.mode.note(score.tonic + 12, chord.degree), 0.42f, Instrument.CLANK, 0.2f);

        if (score.random.nextDouble() < chord.density)
        {
            motif.state(score, chord.degree, chord.start + 4d * beat, style.lead(), 0.45f, 1d);
        }
    }

    /** Overgrowth: a sparse marimba over the drone, the tune once in a while, drips and leaves. */
    private static void damp(Score score, Chord chord, MusicStyle style, Motif motif)
    {
        final double beat = score.beat;

        for (int bar = 0; bar < 2; bar++)
        {
            for (int hit = 0; hit < 2; hit++)
            {
                if (score.random.nextDouble() < 0.3d + 0.5d * chord.density)
                {
                    score.add(chord.start + (bar * 4 + score.random.nextInt(4)) * beat, beat,
                            score.mode.note(score.lead(), chord.root() + new int[] {0, 2, 4}[score.random.nextInt(3)]),
                            0.4f, style.lead(), score.pan());
                }
            }
        }

        if (score.random.nextDouble() < chord.density * 0.4d)
        {
            motif.state(score, chord.degree, chord.start + 4d * beat, style.lead(), 0.45f, 1d);
        }

        if (score.random.nextDouble() < 0.6d)
        {
            score.add(chord.start + score.random.nextDouble() * 8d * beat, 0.1d, 76, 0.4f, Instrument.DRIP, score.pan());
        }

        if (score.random.nextDouble() < 0.4d)
        {
            score.add(chord.start + score.random.nextDouble() * 6d * beat, beat * 2d, 68, 0.36f, Instrument.RUSTLE, score.pan());
        }
    }

    /**
     * The runner-up voice, answering under the lead (#214's "a blend, not a lottery", in music): its
     * own instrument in this piece's mode, and a light layer of its own room's foley - so a hearth
     * soul with a workshop in it hears the odd anvil under its fire.
     */
    private static void answer(Score score, Chord chord, MusicStyle support, MusicStyle style, double share)
    {
        final double beat = score.beat;

        if (score.random.nextDouble() < chord.density * 0.5d * share)
        {
            final Instrument answer = support.lead() == style.lead() ? support.texture() : support.lead();

            arpeggio(score, chord, 7, answer, 0.28f, 1d);
        }

        if (score.random.nextDouble() >= 0.5d * share)
        {
            return;
        }

        final double at = chord.start + beat * (1 + score.random.nextInt(6));

        switch (support.groove())
        {
            case FORGE -> score.add(at, 0.3d, score.mode.note(score.tonic + 12, chord.degree), 0.38f, Instrument.CLANK, score.pan());
            case FLICKER -> score.add(at, 0.05d, 60, 0.5f, Instrument.CRACKLE, score.pan());
            case DRIFT -> score.add(at, 6d * beat, 67, 0.3f, Instrument.WIND, score.pan());
            case SHIMMER -> score.add(chord.start + 4d * beat, 4d * beat, 60, 0.3f, Instrument.SWELL, 0f);
            case LILT -> score.add(at, 0.3d, 89, 0.3f, Instrument.CHIRP, score.pan());
            case TOLL, DAMP -> score.add(at, 0.1d, 76, 0.35f, Instrument.DRIP, score.pan());
            case VENT -> score.add(at, beat * 0.5d, 60, 0.3f, Instrument.HISS, score.pan());
            case CLOCKWORK ->
            {
                for (int tick = 0; tick < 4; tick++)
                {
                    score.add(at + tick * beat / 2d, 0.02d, tick % 2 == 0 ? 90 : 85, 0.24f, Instrument.TICK, score.pan());
                }
            }
            case CALM ->
            {
                // the place has no room of its own to be heard from
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Harmony
    // ---------------------------------------------------------------------------------------------

    /**
     * The pad's chord: a bass note, then the chord's triad spread open, crowned with the style's
     * colour - a seventh, or the fifth again an octave up. Held a little past the chord so the next
     * one arrives under it. In a forge it is struck instead, a short stab on each bar, because a
     * workshop's harmony keeps time with its hammer.
     */
    private static void pad(Score score, Chord chord, MusicStyle style, boolean struck)
    {
        final MusicMode mode = score.mode;
        final int tonic = score.tonic;
        final float velocity = (float) (0.42d + 0.08d * score.random.nextDouble());
        final float spread = score.spread;
        final int crown = chord.degree + (style.seventh() ? 13 : 11);

        if (struck)
        {
            for (int bar = 0; bar < 2; bar++)
            {
                final double at = chord.start + bar * 4d * score.beat;

                score.add(at, 1.5d * score.beat, mode.note(tonic, chord.degree + 4), velocity * 0.55f, Instrument.PAD, spread * 0.6f);
                score.add(at, 1.5d * score.beat, mode.note(tonic, chord.degree + 9), velocity * 0.5f, Instrument.PAD, -spread * 0.3f);
            }

            return;
        }

        final double held = 8d * score.beat * 1.12d;

        // open voicing: the third goes up an octave (a tenth) rather than sitting a third above the
        // root, which is what keeps a low triad from turning to mud on a small speaker
        score.add(chord.start, held, mode.note(tonic - 12, chord.degree), velocity, Instrument.PAD, 0f);
        score.add(chord.start, held, mode.note(tonic, chord.degree), velocity * 0.8f, Instrument.PAD, -spread * 0.6f);
        score.add(chord.start, held, mode.note(tonic, chord.degree + 4), velocity * 0.75f, Instrument.PAD, spread * 0.6f);
        score.add(chord.start, held, mode.note(tonic, chord.degree + 9), velocity * 0.6f, Instrument.PAD, -spread * 0.3f);

        // the crown is the note #261 heard warbling overhead: quieter, and only while it stays low
        if (mode.note(tonic, crown) < HIGH_NOTE)
        {
            score.add(chord.start, held, mode.note(tonic, crown), velocity * 0.35f, Instrument.PAD, spread * 0.3f);
        }
    }

    /**
     * An arpeggio over one bar: up the chord, sometimes back down, a note a beat or a half beat,
     * the mode's colour folded in now and then.
     *
     * @param offset scale degrees above the tonic the chord's root is taken from - 7 for the lead's
     *               octave, 4 to start from the fifth under it
     */
    private static void arpeggio(Score score, Chord chord, int offset, Instrument instrument, float velocity, double pace)
    {
        final int[] shape = score.random.nextBoolean() ? new int[] {0, 2, 4, 7} : new int[] {0, 4, 2, 4, 7, 4};
        final double step = (shape.length > 4 ? score.beat * 0.5d : score.beat) * pace;
        final float pan = score.pan();

        for (int index = 0; index < shape.length; index++)
        {
            int degree = chord.degree + offset + shape[index];

            if (index == shape.length - 1 && score.random.nextDouble() < 0.35d)
            {
                degree = score.mode.colourDegree() + 7 * Math.floorDiv(degree, 7);
            }

            score.add(chord.start + 4d * score.beat + index * step + score.humanise(), step * 1.5d,
                    score.mode.note(score.tonic, degree), velocity * (float) (0.85d + 0.3d * score.random.nextDouble()),
                    instrument, pan * (1f - index / (float) shape.length));
        }
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

    /** A chord walk that starts on the tonic and ends on it, never onto a diminished chord. */
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
     * The next chord, never one whose fifth is diminished. Every mode has exactly one such degree,
     * and a diminished triad held for two bars under a pad is the one chord here that would sound
     * like a mistake rather than a mood.
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

    /**
     * A pitched note folded down an octave at a time until it is under {@link #CEILING} - an octave
     * rather than a clamp, so it stays in the mode. {@code Score#add} also cuts short anything still
     * at or above {@link #HIGH_NOTE}.
     */
    static int voice(int midi)
    {
        int voiced = midi;

        while (voiced > CEILING)
        {
            voiced -= 12;
        }

        return voiced;
    }

    /** How much of the runner-up is heard: its weight against the lead's, never more than the lead. */
    private static double supportShare(SoulMusicBrief brief, SoulVoice voice, SoulVoice support)
    {
        final double lead = brief.weight(voice);
        final double second = brief.weight(support);

        return lead <= 0d ? 0d : Math.min(1d, 0.4d + second / lead);
    }

    private static double lerp(double from, double to, double amount)
    {
        return from + (to - from) * Math.max(0d, Math.min(1d, amount));
    }

    /** Where in the piece a chord falls, and how busy that part of the piece is. */
    private record Chord(double start, int degree, int index, int of, double density)
    {
        /**
         * The chord's root taken into the lead's own octave, from a third under it to a fourth over:
         * a chord on the sixth degree voiced from the sixth below, not the sixth above. The difference
         * is the whole of a bell part sitting in the middle of the range or climbing out of it.
         */
        int root()
        {
            return this.degree > 3 ? this.degree - 7 : this.degree;
        }
    }

    /**
     * The piece being written: its key, its pulse, and the events so far. Every note goes in through
     * {@link #add}, which is where the register rules are kept.
     */
    private static final class Score
    {
        final MusicMode mode;
        final int tonic;
        final double beat;
        final float spread;
        final SplittableRandom random;
        final List<NoteEvent> events = new ArrayList<>();

        Score(MusicMode mode, int tonic, double beat, float spread, SplittableRandom random)
        {
            this.mode = mode;
            this.tonic = tonic;
            this.beat = beat;
            this.spread = spread;
            this.random = random;
        }

        /** Where leads, arpeggios and bells sit: one octave over the tonic, never two (#261). */
        int lead()
        {
            return this.tonic + 12;
        }

        /**
         * A tune's note, kept in its band: folded up an octave if it has wandered more than a fourth
         * under the lead's register. The ceiling in {@link #add} keeps the top; this keeps a fire's
         * quick notes from dropping into the pad's range, which #261 asked for as much as the ceiling.
         */
        int tune(int midi)
        {
            int voiced = midi;

            while (voiced < lead() - 5)
            {
                voiced += 12;
            }

            return voiced;
        }

        void add(double start, double duration, int midi, float velocity, Instrument instrument, float pan)
        {
            double held = duration;
            int pitch = midi;

            if (instrument.pitched())
            {
                pitch = voice(midi);

                if (pitch >= HIGH_NOTE)
                {
                    held = Math.min(held, HIGH_NOTE_BEATS * this.beat);
                }
            }

            this.events.add(new NoteEvent(Math.max(0d, start), held, pitch, velocity, instrument, pan));
        }

        /** A few milliseconds either side of the grid, so a struck note does not land like a metronome. */
        double humanise()
        {
            return (this.random.nextDouble() - 0.5d) * 0.03d;
        }

        float pan()
        {
            return this.spread * (this.random.nextBoolean() ? 1f : -1f) * (0.4f + 0.6f * (float) this.random.nextDouble());
        }
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
            int degree = random.nextBoolean() ? 0 : 2;

            for (int index = 0; index < length; index++)
            {
                degrees[index] = degree;
                rhythm[index] = MOTIF_RHYTHMS[random.nextInt(MOTIF_RHYTHMS.length)];

                // mostly steps, sometimes a third, rarely a leap - and a leap turns back. Kept inside
                // a sixth either side of where it started, so the tune never climbs out of the range
                final double shape = random.nextDouble();
                final int move = shape < 0.6d ? 1 : shape < 0.88d ? 2 : 4;
                final int direction = degree > 3 ? -1 : degree < -1 ? 1 : random.nextBoolean() ? 1 : -1;

                degree += move * direction;
            }

            // the phrase lands on a longer note, so it sounds finished - long, not held
            rhythm[length - 1] = Math.max(rhythm[length - 1], 1.5d);

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
         *
         * @param pace a multiplier on its rhythm - a half for the fire's quick version of the tune
         */
        void state(Score score, int chordDegree, double start, Instrument instrument, float velocity, double pace)
        {
            final SplittableRandom random = score.random;
            final boolean invert = random.nextDouble() < 0.25d;
            final int skip = random.nextDouble() < 0.3d ? 1 + random.nextInt(this.degrees.length - 1) : -1;
            final int shift = chordDegree > 4 ? chordDegree - 7 : chordDegree;
            final float pan = score.pan() * 0.5f;
            double at = start + score.beat * random.nextInt(2);

            for (int index = 0; index < this.degrees.length; index++)
            {
                final double length = this.rhythm[index] * score.beat * pace;

                if (index != skip)
                {
                    final int relative = this.degrees[index] - this.degrees[0];
                    int degree = this.degrees[0] + (invert ? -relative : relative) + shift;

                    // where the phrase leans hardest - its first note and the one it lands on - it
                    // sits on the chord, so the tune agrees with the pad at the moments it is heard
                    if (index == 0 || index == this.degrees.length - 1)
                    {
                        degree = snapToChord(degree, chordDegree);
                    }

                    score.add(at + score.humanise(), length * 0.95d, score.tune(score.mode.note(score.lead(), degree)),
                            velocity * (float) (0.9d + 0.2d * random.nextDouble()) * (index == 0 ? 1.08f : 1f),
                            instrument, pan);
                }

                at += length;
            }
        }
    }
}
