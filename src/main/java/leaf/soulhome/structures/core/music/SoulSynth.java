/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plays a soul's music as it is composed (#163): pieces from {@link SoulComposer}, rendered to
 * stereo samples a block at a time, with a rest between each.
 *
 * <h2>Why at runtime, when the ambience assets are rendered offline</h2>
 *
 * <p>{@code tools/ambience} renders offline because a bed is one sound per voice and can be judged
 * as a file. Music is not: a soul's pieces are drawn from its own blend, in its own key, and there
 * are as many of them as the soul has visits. Rendering a grid of them ahead of time would be
 * hundreds of megabytes and would still be every soul's music rather than this one's. So this runs
 * on the sound engine's streaming thread, and what keeps it auditable is that it is a pure function
 * of a seed - {@code SoulSynthTest} renders it, and so can anyone who wants to listen before playing.
 *
 * <h2>Levels</h2>
 *
 * <p>Mastered to sit where Minecraft's own music sits, around -20 dBFS RMS through a piece, because
 * it replaces that music and the Music slider a player already set should mean the same thing. A
 * soft limiter holds the peaks - {@code SoulSynthTest} checks that nothing ever reaches full scale,
 * and that a piece at any rank lands inside the band.
 *
 * <p>Not thread-safe. One instance belongs to one stream, and the stream is read from one thread.
 */
public final class SoulSynth
{
    public static final int SAMPLE_RATE = 44_100;

    /** Rest between two pieces, in seconds. Long enough to be a rest rather than a gap in a loop. */
    public static final double MIN_REST = 12d;

    public static final double MAX_REST = 40d;

    /** Before the first piece: a moment for a player arriving in their soul to hear where they are. */
    public static final double FIRST_REST = 3d;

    /**
     * The whole mix is multiplied by this before the limiter. Found by measuring rather than
     * derived: it puts every voice's pieces between about -19 and -24 dBFS RMS at every rank, which
     * is where Minecraft's own tracks sit, and {@code SoulSynthTest} holds it there.
     */
    static final float MASTER = 1.2f;

    /**
     * The master's one-pole lowpass, at about 6 kHz. Nothing musical lives up there once the
     * register ceiling is in place (#261); what does is the top of the foley's noise and the edge on
     * a struck note, which is exactly what "ear piercing" is made of.
     */
    static final float TONE = (float) (1d - Math.exp(-2d * Math.PI * 6_000d / SAMPLE_RATE));

    /** Where a piece is supplied from - the stream asks for the next one when the rest runs out. */
    @FunctionalInterface
    public interface PieceSource
    {
        SoulComposer.Piece next(long index);
    }

    private final PieceSource source;
    private final SplittableRandom random;
    private final Reverb reverb = new Reverb();
    private final List<Voice> voices = new ArrayList<>();

    private float toneL;
    private float toneR;

    private SoulComposer.Piece piece;
    private MusicStyle style;
    private long pieceIndex;
    private long pieceSample;
    private int cursor;
    private long restRemaining;

    public SoulSynth(PieceSource source, long seed)
    {
        this.source = source;
        this.random = new SplittableRandom(seed);
        this.restRemaining = (long) (FIRST_REST * SAMPLE_RATE);
    }

    /** The piece now playing, or null during a rest. */
    public SoulComposer.Piece piece()
    {
        return this.piece;
    }

    /** Fill {@code frames} samples of each channel. */
    public void render(float[] left, float[] right, int frames)
    {
        for (int frame = 0; frame < frames; frame++)
        {
            advance();

            float dryL = 0f;
            float dryR = 0f;

            for (int index = this.voices.size() - 1; index >= 0; index--)
            {
                final Voice voice = this.voices.get(index);
                final float sample = voice.next();

                dryL += sample * voice.gainL;
                dryR += sample * voice.gainR;

                if (voice.done())
                {
                    this.voices.remove(index);
                }
            }

            this.reverb.process(dryL, dryR);

            this.toneL += TONE * ((dryL * this.reverb.dry + this.reverb.outL) - this.toneL);
            this.toneR += TONE * ((dryR * this.reverb.dry + this.reverb.outR) - this.toneR);

            left[frame] = limit(this.toneL * MASTER);
            right[frame] = limit(this.toneR * MASTER);
        }
    }

    /** One sample of the piece's clock: start whatever is due, and turn to the next piece when it is time. */
    private void advance()
    {
        if (this.piece == null)
        {
            if (--this.restRemaining > 0)
            {
                return;
            }

            this.piece = this.source.next(this.pieceIndex++);
            this.style = MusicStyle.of(this.piece.voice());
            this.pieceSample = 0L;
            this.cursor = 0;
            this.reverb.setSpace(this.piece.space());
        }

        final double now = this.pieceSample / (double) SAMPLE_RATE;
        final List<NoteEvent> events = this.piece.events();

        while (this.cursor < events.size() && events.get(this.cursor).start() <= now)
        {
            final NoteEvent event = events.get(this.cursor++);

            this.voices.add(Voice.of(event, this.style.brightness(), this.random.nextLong()));
        }

        if (++this.pieceSample >= (long) (this.piece.length() * SAMPLE_RATE))
        {
            this.piece = null;
            this.restRemaining = (long) ((MIN_REST + this.random.nextDouble() * (MAX_REST - MIN_REST)) * SAMPLE_RATE);
        }
    }

    /**
     * A soft knee rather than a wall: straight through below 0.5, bending smoothly toward 0.95
     * above it. Chosen over {@code tanh} so that everything a piece normally does passes through
     * untouched, and only a pile-up of notes is bent.
     */
    static float limit(float sample)
    {
        final float magnitude = Math.abs(sample);

        if (magnitude <= 0.5f)
        {
            return sample;
        }

        final float over = magnitude - 0.5f;
        final float bent = 0.5f + 0.45f * (over / (over + 0.45f));

        return Math.copySign(bent, sample);
    }

    // ---------------------------------------------------------------------------------------------
    // Oscillators
    // ---------------------------------------------------------------------------------------------

    /** A sine by table: {@code Math.sin} per partial per sample is most of the budget otherwise. */
    static final class Sine
    {
        private static final int SIZE = 4096;
        private static final float[] TABLE = new float[SIZE + 1];

        static
        {
            for (int index = 0; index <= SIZE; index++)
            {
                TABLE[index] = (float) Math.sin(2d * Math.PI * index / SIZE);
            }
        }

        private Sine()
        {
        }

        /** {@code sin(2 pi phase)}, for any phase. */
        static float at(double phase)
        {
            final double wrapped = phase - Math.floor(phase);
            final double position = wrapped * SIZE;
            final int index = (int) position;
            final float fraction = (float) (position - index);

            return TABLE[index] + (TABLE[index + 1] - TABLE[index]) * fraction;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Voices
    // ---------------------------------------------------------------------------------------------

    /**
     * One sounding note. Each instrument is a few lines of a well-known recipe; the constants in
     * them are the ones worth turning if something sounds wrong, and they are all here.
     */
    abstract static class Voice
    {
        final double frequency;
        final float velocity;
        final long hold;
        final float gainL;
        final float gainR;

        long age;

        Voice(NoteEvent event, float level)
        {
            this.frequency = event.frequency();
            this.velocity = event.velocity() * level;
            this.hold = (long) (Math.max(0.01d, event.duration()) * SAMPLE_RATE);

            // equal-power pan, so a note placed at the edge is no louder than one in the middle
            final double angle = (Math.max(-1d, Math.min(1d, event.pan())) + 1d) * Math.PI / 4d;
            this.gainL = (float) Math.cos(angle);
            this.gainR = (float) Math.sin(angle);
        }

        static Voice of(NoteEvent event, double brightness, long seed)
        {
            return switch (event.instrument())
            {
                case PAD -> new Pad(event, brightness);
                case EPIANO -> new ElectricPiano(event);
                case BELL -> new Bell(event);
                case GLASS -> new Glass(event);
                case PLUCK -> new Pluck(event, seed);
                case MALLET -> new Mallet(event);
                case DRONE -> new Drone(event, seed);
                case FLUTE -> new Flute(event, seed);
                case THUMP -> new Thump(event);
                case CLANK -> new Clank(event, seed);
                case TICK -> new Tick(event, seed);
                case CRACKLE -> new Crackle(event, seed);
                case HISS -> new Hiss(event, seed);
                case WIND -> new Wind(event, seed);
                case DRIP -> new Drip(event);
                case CHIRP -> new Chirp(event, seed);
                case RUSTLE -> new Rustle(event, seed);
                case SWELL -> new Swell(event, seed);
            };
        }

        final float next()
        {
            final float sample = sample(this.age / (double) SAMPLE_RATE);

            this.age++;

            return sample;
        }

        /** The voice's output at {@code seconds} since it started, envelope included. */
        abstract float sample(double seconds);

        abstract boolean done();

        /** A held note's envelope: a rise, a plateau for as long as it is held, and a fall. */
        final float sustained(double seconds, double attack, double release)
        {
            final double held = this.hold / (double) SAMPLE_RATE;

            if (seconds < attack)
            {
                final double t = seconds / attack;

                return (float) (t * t * (3d - 2d * t));
            }

            if (seconds < held)
            {
                return 1f;
            }

            final double out = (seconds - held) / release;

            return out >= 1d ? 0f : (float) ((1d - out) * (1d - out));
        }

        final boolean pastRelease(double release)
        {
            return this.age > this.hold + (long) (release * SAMPLE_RATE);
        }
    }

    /**
     * Two saws a couple of cents apart, through a lowpass that breathes slowly. Band-limited by
     * PolyBLEP so the low notes do not fizz; the filter is the TPT state-variable form, which stays
     * stable however its cutoff is moved.
     *
     * <p>It was three saws half a percent apart, which is a chorus: they beat against each other a
     * couple of times a second, and #261 heard that as a warble on every held chord. Two cents apart
     * they drift against each other once every few seconds, which reads as warmth rather than wobble.
     */
    static final class Pad extends Voice
    {
        private static final double ATTACK = 2.2d;
        private static final double RELEASE = 3.2d;
        static final double[] DETUNE = {-0.0012d, 0.0012d};

        /** How far the filter's slow sweep moves the cutoff either way, as a share of it. */
        static final double SWEEP = 0.08d;

        /** {@code 1/Q}: a touch under Butterworth's 1.41, so the cutoff has a little body without ringing. */
        private static final double RESONANCE = 1.2d;

        private final double[] phase = {0.13d, 0.61d};
        private final double cutoff;
        private final double sweepRate;
        private double a1;
        private double a2;
        private double a3;
        private double ic1;
        private double ic2;

        Pad(NoteEvent event, double brightness)
        {
            super(event, 0.2f);
            this.cutoff = 180d + 900d * brightness + this.frequency * 1.2d;
            this.sweepRate = 0.07d + 0.05d * ((event.midi() * 7) % 5) / 5d;
        }

        @Override
        float sample(double seconds)
        {
            double saw = 0d;

            for (int index = 0; index < DETUNE.length; index++)
            {
                final double increment = this.frequency * (1d + DETUNE[index]) / SAMPLE_RATE;

                this.phase[index] += increment;

                if (this.phase[index] >= 1d)
                {
                    this.phase[index] -= 1d;
                }

                saw += 2d * this.phase[index] - 1d - polyBlep(this.phase[index], increment);
            }

            // the filter's coefficients only move with the slow sweep, so they are worked out a few
            // hundred times a second rather than on every sample - tan() is not free
            if ((this.age & 63L) == 0L)
            {
                final double sweep = 1d - SWEEP + SWEEP * Sine.at(seconds * this.sweepRate);
                final double g = Math.tan(Math.PI * Math.min(0.45d * SAMPLE_RATE, this.cutoff * sweep) / SAMPLE_RATE);

                this.a1 = 1d / (1d + g * (g + RESONANCE));
                this.a2 = g * this.a1;
                this.a3 = g * this.a2;
            }

            // Cytomic's form of the TPT state-variable filter: v1 is the band, v2 the low
            final double v3 = saw / DETUNE.length - this.ic2;
            final double v1 = this.a1 * this.ic1 + this.a2 * v3;
            final double v2 = this.ic2 + this.a2 * this.ic1 + this.a3 * v3;

            this.ic1 = 2d * v1 - this.ic1;
            this.ic2 = 2d * v2 - this.ic2;

            return (float) v2 * this.velocity * sustained(seconds, ATTACK, RELEASE);
        }

        @Override
        boolean done()
        {
            return pastRelease(RELEASE);
        }

        private static double polyBlep(double phase, double increment)
        {
            if (phase < increment)
            {
                final double t = phase / increment;

                return t + t - t * t - 1d;
            }

            if (phase > 1d - increment)
            {
                final double t = (phase - 1d) / increment;

                return t * t + t + t + 1d;
            }

            return 0d;
        }
    }

    /**
     * FM at a 1:1 ratio with a falling index - the round, close tone of a tine piano - plus a faint
     * bell of the fourteenth partial on the strike, which is what makes it sound struck.
     */
    static final class ElectricPiano extends Voice
    {
        private final double decay;

        ElectricPiano(NoteEvent event)
        {
            super(event, 0.34f);
            this.decay = Math.max(0.9d, Math.min(3.2d, 2.2d * Math.pow(220d / this.frequency, 0.35d)));
        }

        @Override
        float sample(double seconds)
        {
            final double index = 1.4d * Math.exp(-seconds / 0.5d) + 0.25d;
            final double modulator = Sine.at(this.frequency * seconds) * index / (2d * Math.PI);
            double tone = Sine.at(this.frequency * seconds + modulator);

            // the tine, only where it stays under the range #261 found piercing
            if (this.frequency * 14d < 6_000d)
            {
                tone += 0.04d * Sine.at(this.frequency * 14d * seconds) * Math.exp(-seconds / 0.04d);
            }

            return (float) (tone * strike(seconds, this.decay)) * this.velocity * sustained(seconds, 0.003d, 0.45d);
        }

        @Override
        boolean done()
        {
            return pastRelease(0.45d) || this.age > 6L * SAMPLE_RATE;
        }
    }

    /**
     * FM at 1:3.5, the classic inharmonic bell, at a low index so it rings rather than stings.
     *
     * <p>It had a second carrier 0.7 Hz sharp "so it shimmers", which is a beat - a wobble in the
     * level - at the one rate the ear is surest to catch, on every bell for three seconds (#261). The
     * shimmer is now a quiet second harmonic that holds still, and a high bell dies sooner than a low
     * one, as a small bell does.
     */
    static final class Bell extends Voice
    {
        private final double decay;

        Bell(NoteEvent event)
        {
            super(event, 0.26f);
            this.decay = 2.6d * Math.min(1d, Math.sqrt(262d / this.frequency));
        }

        @Override
        float sample(double seconds)
        {
            final double index = 1.1d * Math.exp(-seconds / 0.6d) + 0.1d;
            final double modulator = Sine.at(this.frequency * 3.5d * seconds) * index / (2d * Math.PI);
            final double tone = Sine.at(this.frequency * seconds + modulator)
                    + 0.15d * Sine.at(this.frequency * 2d * seconds);

            return (float) (tone / 1.15d * strike(seconds, this.decay) * attackOf(seconds, 0.002d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > 9L * SAMPLE_RATE;
        }
    }

    /**
     * Three partials, each dying at its own rate, held still.
     *
     * <p>This is where #261's "wobbly high note held too long" came from, and it was a bug rather
     * than a taste: the vibrato multiplied the frequency and then the elapsed time, so the pitch swing
     * grew the longer a note rang - about a third of the pitch either way four seconds in, on the
     * highest notes in the piece. Glass now has no vibrato at all, a third fewer partials, and a high
     * note dies sooner than a low one.
     */
    static final class Glass extends Voice
    {
        private static final double[] RATIOS = {1d, 2.0d, 3.01d};
        private static final double[] LEVELS = {1d, 0.25d, 0.08d};
        private static final double[] DECAYS = {3.5d, 1.8d, 0.9d};

        private final double shorten;

        Glass(NoteEvent event)
        {
            super(event, 0.24f);
            this.shorten = Math.min(1d, Math.sqrt(262d / this.frequency));
        }

        @Override
        float sample(double seconds)
        {
            double tone = 0d;

            for (int index = 0; index < RATIOS.length; index++)
            {
                tone += LEVELS[index] * Sine.at(this.frequency * RATIOS[index] * seconds)
                        * Math.exp(-seconds / (DECAYS[index] * this.shorten));
            }

            return (float) (tone / 1.33d * attackOf(seconds, 0.06d)) * this.velocity * sustained(seconds, 0.001d, 1.4d);
        }

        @Override
        boolean done()
        {
            return pastRelease(1.4d) || this.age > 8L * SAMPLE_RATE;
        }
    }

    /**
     * Karplus-Strong: a burst of softened noise round a delay line one period long, averaged on
     * every pass. It is the cheapest convincing string there is, and it decays on its own.
     */
    static final class Pluck extends Voice
    {
        private final float[] line;
        private final float loss;
        private int position;
        private float smoothed;

        Pluck(NoteEvent event, long seed)
        {
            super(event, 0.5f);

            final SplittableRandom random = new SplittableRandom(seed);
            final int length = Math.max(2, (int) Math.round(SAMPLE_RATE / this.frequency));

            this.line = new float[length];

            // well lowpassed noise, so the strike is a thumb rather than a pick - a workshop's bass
            // plays this every eighth note, and a bright pluck that often is a hiss (#261)
            float last = 0f;

            for (int index = 0; index < length; index++)
            {
                last = last * 0.8f + (float) (random.nextDouble() * 2d - 1d) * 0.2f;
                this.line[index] = last;
            }

            // loss per pass chosen for about two and a half seconds to die away, whatever the pitch
            this.loss = (float) Math.pow(10d, -3d / (2.5d * this.frequency));
        }

        @Override
        float sample(double seconds)
        {
            final int next = (this.position + 1) % this.line.length;
            final float out = this.line[this.position];

            this.line[this.position] = this.loss * 0.5f * (out + this.line[next]);
            this.position = next;

            this.smoothed += 0.14f * (out - this.smoothed);

            return this.smoothed * this.velocity * 5f * sustained(seconds, 0.001d, 0.6d);
        }

        @Override
        boolean done()
        {
            return pastRelease(0.6d) || this.age > 5L * SAMPLE_RATE;
        }
    }

    /** A marimba's three partials - 1, 4 and 10 - with the upper two gone almost as they arrive. */
    static final class Mallet extends Voice
    {
        private final double decay;

        Mallet(NoteEvent event)
        {
            super(event, 0.34f);
            this.decay = Math.max(0.35d, Math.min(1.6d, Math.pow(330d / this.frequency, 0.5d)));
        }

        @Override
        float sample(double seconds)
        {
            final double tone = Sine.at(this.frequency * seconds) * Math.exp(-seconds / this.decay)
                    + 0.28d * Sine.at(this.frequency * 3.93d * seconds) * Math.exp(-seconds / 0.18d)
                    + 0.04d * Sine.at(this.frequency * 9.2d * seconds) * Math.exp(-seconds / 0.05d);

            return (float) (tone * attackOf(seconds, 0.0015d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (this.decay * 7d * SAMPLE_RATE);
        }
    }

    /**
     * The tonic and its fifth, an octave apart from each other, each swelling on a slow cycle of its
     * own, over a breath of filtered noise. Never quite settles, which is the point of it.
     */
    static final class Drone extends Voice
    {
        private final SplittableRandom random;
        private double breath;

        Drone(NoteEvent event, long seed)
        {
            super(event, 0.3f);
            this.random = new SplittableRandom(seed);
        }

        @Override
        float sample(double seconds)
        {
            this.breath += 0.02d * ((this.random.nextDouble() * 2d - 1d) - this.breath);

            final double tone = Sine.at(this.frequency * seconds) * (0.8d + 0.2d * Sine.at(seconds * 0.061d))
                    + 0.45d * Sine.at(this.frequency * 1.5d * seconds) * (0.6d + 0.4d * Sine.at(seconds * 0.043d + 0.3d))
                    + 0.2d * Sine.at(this.frequency * 2d * seconds) * (0.5d + 0.5d * Sine.at(seconds * 0.027d + 0.6d))
                    + 0.6d * this.breath;

            return (float) (tone / 1.6d) * this.velocity * sustained(seconds, 6d, 6d);
        }

        @Override
        boolean done()
        {
            return pastRelease(6d);
        }
    }

    /**
     * A sine with a little of its second and third harmonics, breath on the onset, and a vibrato
     * that arrives late and stays light.
     *
     * <p>The vibrato is integrated into a running phase. It used to multiply the elapsed time, which
     * made its swing grow for as long as the note was held - the same fault as {@link Glass}, heard
     * on a flute as a note sliding further off pitch the longer it was held (#261).
     */
    static final class Flute extends Voice
    {
        /** How far the pitch moves either way at the vibrato's peak - a tenth of a percent, two cents. */
        static final double VIBRATO = 0.0012d;

        private final SplittableRandom random;
        private double breath;
        private double phase;

        Flute(NoteEvent event, long seed)
        {
            super(event, 0.24f);
            this.random = new SplittableRandom(seed);
        }

        @Override
        float sample(double seconds)
        {
            final double vibrato = 1d + VIBRATO * Sine.at(5.1d * seconds) * Math.min(1d, Math.max(0d, seconds - 0.3d) / 0.5d);

            this.phase += this.frequency * vibrato / SAMPLE_RATE;

            final double phase = this.phase;

            this.breath += 0.3d * ((this.random.nextDouble() * 2d - 1d) - this.breath);

            final double tone = Sine.at(phase) + 0.22d * Sine.at(2d * phase) + 0.06d * Sine.at(3d * phase)
                    + this.breath * (0.05d + 0.2d * Math.exp(-seconds / 0.08d));

            return (float) (tone / 1.3d) * this.velocity * sustained(seconds, 0.12d, 0.35d);
        }

        @Override
        boolean done()
        {
            return pastRelease(0.35d);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Foley (#261)
    // ---------------------------------------------------------------------------------------------

    /**
     * Cytomic's TPT state-variable filter, for the foley's noise. The same form the pad uses, kept
     * separately because a foley voice moves its cutoff per pop or per sweep rather than slowly.
     */
    static final class Filter
    {
        private double k = 1.4d;
        private double a1;
        private double a2;
        private double a3;
        private double ic1;
        private double ic2;

        double low;
        double band;

        Filter tune(double cutoff, double q)
        {
            final double g = Math.tan(Math.PI * Math.max(20d, Math.min(0.45d * SAMPLE_RATE, cutoff)) / SAMPLE_RATE);

            this.k = 1d / Math.max(0.1d, q);
            this.a1 = 1d / (1d + g * (g + this.k));
            this.a2 = g * this.a1;
            this.a3 = g * this.a2;

            return this;
        }

        void process(double input)
        {
            final double v3 = input - this.ic2;
            final double v1 = this.a1 * this.ic1 + this.a2 * v3;
            final double v2 = this.ic2 + this.a2 * this.ic1 + this.a3 * v3;

            this.ic1 = 2d * v1 - this.ic1;
            this.ic2 = 2d * v2 - this.ic2;
            this.band = v1;
            this.low = v2;
        }
    }

    /** A hammer on wood: a sine falling from a knock to a thud, over a click of noise. The workshop's beat. */
    static final class Thump extends Voice
    {
        private double phase;

        Thump(NoteEvent event)
        {
            super(event, 0.45f);
        }

        @Override
        float sample(double seconds)
        {
            this.phase += (48d + 95d * Math.exp(-seconds / 0.025d)) / SAMPLE_RATE;

            final double body = Sine.at(this.phase) * Math.exp(-seconds / 0.11d);
            final double knock = Sine.at(this.phase * 3.1d) * 0.25d * Math.exp(-seconds / 0.012d);

            return (float) ((body + knock) * attackOf(seconds, 0.0015d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (0.6d * SAMPLE_RATE);
        }
    }

    /**
     * A struck anvil: the modes of a struck bar (1, 2.76, 5.4, 8.93) over a short click, the upper
     * modes gone in a few tens of milliseconds. Pitched to the chord, so two anvils a fifth apart
     * trading the backbeat are part of the harmony rather than noise laid over it.
     */
    static final class Clank extends Voice
    {
        private static final double[] RATIOS = {1d, 2.76d, 5.4d, 8.93d};
        private static final double[] LEVELS = {1d, 0.55d, 0.28d, 0.06d};
        private static final double[] DECAYS = {0.32d, 0.16d, 0.07d, 0.03d};

        private final SplittableRandom random;
        private final double base;
        private final Filter click = new Filter().tune(1_600d, 0.8d);

        Clank(NoteEvent event, long seed)
        {
            super(event, 0.68f);
            this.random = new SplittableRandom(seed);

            // anvils ring between these, whatever note the chord hands them - lower, and it is a
            // gong; higher, and it is the piercing edge #261 asked to have taken off
            double base = this.frequency;

            while (base < 330d)
            {
                base *= 2d;
            }

            while (base > 660d)
            {
                base /= 2d;
            }

            this.base = base;
        }

        @Override
        float sample(double seconds)
        {
            double tone = 0d;

            for (int index = 0; index < RATIOS.length; index++)
            {
                tone += LEVELS[index] * Sine.at(this.base * RATIOS[index] * seconds) * Math.exp(-seconds / DECAYS[index]);
            }

            this.click.process(this.random.nextDouble() * 2d - 1d);

            final double strike = this.click.band * 0.9d * Math.exp(-seconds / 0.004d);

            return (float) ((tone / 1.6d + strike) * attackOf(seconds, 0.0008d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (1.4d * SAMPLE_RATE);
        }
    }

    /** A ratchet's tooth or a clock's tick: a few milliseconds of band-passed noise at the note's pitch. */
    static final class Tick extends Voice
    {
        private final SplittableRandom random;
        private final Filter filter;

        Tick(NoteEvent event, long seed)
        {
            super(event, 1.25f);
            this.random = new SplittableRandom(seed);
            this.filter = new Filter().tune(Math.min(2_400d, this.frequency), 3d);
        }

        @Override
        float sample(double seconds)
        {
            this.filter.process(this.random.nextDouble() * 2d - 1d);

            return (float) (this.filter.band * 2.5d * Math.exp(-seconds / 0.005d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (0.06d * SAMPLE_RATE);
        }
    }

    /**
     * Fire. Held, it is a bed: a low roar of filtered noise with pops scattered through it at random,
     * each a burst of noise at its own pitch dying in a few milliseconds, a few of them bigger than
     * the rest - which is the whole difference between a crackle and a hiss. Struck (a note shorter
     * than a fifth of a second), it is one sharp pop with a couple of smaller ones on its tail.
     */
    static final class Crackle extends Voice
    {
        /** Pops per second in the bed. */
        private static final double BED_RATE = 11d;

        private final SplittableRandom random;
        private final boolean bed;
        private final Filter roar = new Filter().tune(260d, 0.7d);
        private final Filter pop = new Filter();
        private double popLevel;
        private double popDecay = 0.004d;
        private int trailing;

        Crackle(NoteEvent event, long seed)
        {
            super(event, 1.5f);
            this.random = new SplittableRandom(seed);
            this.bed = event.duration() > 0.2d;
            this.trailing = this.bed ? 0 : 2 + this.random.nextInt(3);

            if (!this.bed)
            {
                ignite(0.9d + 0.1d * this.random.nextDouble(), 0.006d);
            }
        }

        private void ignite(double level, double decay)
        {
            this.popLevel = Math.max(this.popLevel, level);
            this.popDecay = decay;

            // pitched low to middling: an ember snapping, not a spark whistling
            this.pop.tune(600d + 1_300d * this.random.nextDouble(), 1.1d);
        }

        @Override
        float sample(double seconds)
        {
            final double noise = this.random.nextDouble() * 2d - 1d;

            if (this.bed && this.random.nextDouble() < BED_RATE / SAMPLE_RATE)
            {
                // mostly small pops, now and then a loud one - cubed, so the loud ones are rare
                final double size = this.random.nextDouble();

                ignite(0.15d + 0.85d * size * size * size, 0.003d + 0.006d * this.random.nextDouble());
            }
            else if (!this.bed && this.trailing > 0 && this.random.nextDouble() < 60d / SAMPLE_RATE)
            {
                this.trailing--;
                ignite(0.3d + 0.3d * this.random.nextDouble(), 0.003d);
            }

            this.pop.process(noise);
            this.popLevel *= Math.exp(-1d / (this.popDecay * SAMPLE_RATE));

            double out = this.pop.band * 3d * this.popLevel;

            if (this.bed)
            {
                this.roar.process(noise);
                out = out * 0.55d + this.roar.low * 0.5d;
                out *= sustained(seconds, 1.5d, 2d);
            }

            return (float) out * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.bed ? pastRelease(2d) : this.age > (long) (0.25d * SAMPLE_RATE);
        }
    }

    /** A steam vent: noise in a middling band, opening quickly and closing more slowly. */
    static final class Hiss extends Voice
    {
        private final SplittableRandom random;
        private final Filter filter = new Filter().tune(1_600d, 0.7d);

        Hiss(NoteEvent event, long seed)
        {
            super(event, 0.75f);
            this.random = new SplittableRandom(seed);
        }

        @Override
        float sample(double seconds)
        {
            this.filter.process(this.random.nextDouble() * 2d - 1d);

            return (float) (this.filter.band * 1.4d) * this.velocity * sustained(seconds, 0.03d, 0.18d);
        }

        @Override
        boolean done()
        {
            return pastRelease(0.18d);
        }
    }

    /**
     * Wind: low-passed noise that swells and falls over the note, its cutoff opening as it swells
     * the way a gust brightens. The note's pitch sets where it sits - a gust round a cold room, or
     * the low rumble of an empty one.
     */
    static final class Wind extends Voice
    {
        private final SplittableRandom random;
        private final Filter filter = new Filter();
        private final double length;

        Wind(NoteEvent event, long seed)
        {
            super(event, 1.0f);
            this.random = new SplittableRandom(seed);
            this.length = Math.max(0.5d, event.duration());
        }

        @Override
        float sample(double seconds)
        {
            final double through = Math.max(0d, Math.min(1d, seconds / this.length));
            final double swell = Math.sin(Math.PI * through);

            if ((this.age & 31L) == 0L)
            {
                this.filter.tune(this.frequency * (0.6d + 0.9d * swell), 0.9d);
            }

            this.filter.process(this.random.nextDouble() * 2d - 1d);

            return (float) (this.filter.low * 1.2d * swell * swell) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (this.length * SAMPLE_RATE);
        }
    }

    /** A drop of water: a sine that rises in pitch as it dies, the "plink" a drip makes in a cave. */
    static final class Drip extends Voice
    {
        private double phase;

        Drip(NoteEvent event)
        {
            super(event, 1.0f);
        }

        @Override
        float sample(double seconds)
        {
            this.phase += this.frequency * (1d + 0.9d * (1d - Math.exp(-seconds / 0.03d))) / SAMPLE_RATE;

            return (float) (Sine.at(this.phase) * Math.exp(-seconds / 0.045d) * attackOf(seconds, 0.001d)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (0.3d * SAMPLE_RATE);
        }
    }

    /**
     * A bird: two or three short whistles, each dipping and rising. Pitched at the note - the
     * composer keeps it around 1.3 to 1.8 kHz, so its highest glint stays under 2.5 kHz and it is
     * heard as a bird in the distance rather than one at the player's ear.
     */
    static final class Chirp extends Voice
    {
        private final int calls;
        private final double gap;
        private double phase;

        Chirp(NoteEvent event, long seed)
        {
            super(event, 0.45f);

            final SplittableRandom random = new SplittableRandom(seed);

            this.calls = 2 + random.nextInt(2);
            this.gap = 0.09d + 0.05d * random.nextDouble();
        }

        @Override
        float sample(double seconds)
        {
            final int call = (int) (seconds / this.gap);
            final double within = seconds - call * this.gap;
            final double length = this.gap * 0.7d;

            if (call >= this.calls || within > length)
            {
                return 0f;
            }

            final double t = within / length;

            this.phase += this.frequency * (1.25d - 0.5d * t + 0.6d * t * t) / SAMPLE_RATE;

            return (float) (Sine.at(this.phase) * Math.sin(Math.PI * t)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (this.calls * this.gap * SAMPLE_RATE) + 1;
        }
    }

    /** Leaves: soft bursts of middling noise, a few to a note, each fading in and out. */
    static final class Rustle extends Voice
    {
        private final SplittableRandom random;
        private final Filter filter = new Filter().tune(1_400d, 0.6d);
        private final double length;
        private double gust;
        private double gustTarget;

        Rustle(NoteEvent event, long seed)
        {
            super(event, 2.0f);
            this.random = new SplittableRandom(seed);
            this.length = Math.max(0.3d, event.duration());
        }

        @Override
        float sample(double seconds)
        {
            if ((this.age & 1023L) == 0L)
            {
                this.gustTarget = this.random.nextDouble() < 0.5d ? this.random.nextDouble() : 0d;
            }

            this.gust += (this.gustTarget - this.gust) * 0.0015d;
            this.filter.process(this.random.nextDouble() * 2d - 1d);

            final double through = Math.min(1d, seconds / this.length);

            return (float) (this.filter.band * this.gust * Math.sin(Math.PI * through)) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (this.length * SAMPLE_RATE);
        }
    }

    /**
     * The arcane breathing in: noise rising from a low hush to the middle of the range and growing
     * as it rises, then gone in an instant - timed by the composer to end on the next chord.
     */
    static final class Swell extends Voice
    {
        private final SplittableRandom random;
        private final Filter filter = new Filter();
        private final double length;

        Swell(NoteEvent event, long seed)
        {
            super(event, 0.7f);
            this.random = new SplittableRandom(seed);
            this.length = Math.max(0.5d, event.duration());
        }

        @Override
        float sample(double seconds)
        {
            final double through = Math.max(0d, Math.min(1d, seconds / this.length));

            if ((this.age & 31L) == 0L)
            {
                this.filter.tune(250d + 1_300d * through * through, 1.8d);
            }

            this.filter.process(this.random.nextDouble() * 2d - 1d);

            final double fade = through > 0.97d ? (1d - through) / 0.03d : 1d;

            return (float) (this.filter.band * 1.6d * through * through * fade) * this.velocity;
        }

        @Override
        boolean done()
        {
            return this.age > (long) (this.length * SAMPLE_RATE);
        }
    }

    /** An exponential decay: what every struck instrument does once it has been struck. */
    private static double strike(double seconds, double decay)
    {
        return Math.exp(-seconds / decay);
    }

    /** A few milliseconds of rise, so even a strike does not click. */
    private static double attackOf(double seconds, double attack)
    {
        return seconds >= attack ? 1d : seconds / attack;
    }

    // ---------------------------------------------------------------------------------------------
    // Space
    // ---------------------------------------------------------------------------------------------

    /**
     * Freeverb - Jezar's public-domain Schroeder-Moorer reverb, eight damped combs and four
     * allpasses a side, with the right channel's delays spread a few samples longer so the two sides
     * decorrelate. Rank sets the room size and how much of it is heard (#216's "larger").
     */
    static final class Reverb
    {
        private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        private static final int[] ALLPASSES = {556, 441, 341, 225};
        private static final int SPREAD = 23;
        private static final float INPUT = 0.015f;
        private static final float DAMP = 0.35f;

        private final float[][] combL = new float[COMBS.length][];
        private final float[][] combR = new float[COMBS.length][];
        private final float[] storeL = new float[COMBS.length];
        private final float[] storeR = new float[COMBS.length];
        private final int[] combIndexL = new int[COMBS.length];
        private final int[] combIndexR = new int[COMBS.length];
        private final float[][] passL = new float[ALLPASSES.length][];
        private final float[][] passR = new float[ALLPASSES.length][];
        private final int[] passIndexL = new int[ALLPASSES.length];
        private final int[] passIndexR = new int[ALLPASSES.length];

        private float feedback = 0.84f;
        private float wet = 1f;
        float dry = 1f;

        float outL;
        float outR;

        Reverb()
        {
            for (int index = 0; index < COMBS.length; index++)
            {
                this.combL[index] = new float[COMBS[index]];
                this.combR[index] = new float[COMBS[index] + SPREAD];
            }

            for (int index = 0; index < ALLPASSES.length; index++)
            {
                this.passL[index] = new float[ALLPASSES[index]];
                this.passR[index] = new float[ALLPASSES[index] + SPREAD];
            }

            setSpace(0d);
        }

        /**
         * 0 is a modest room at rank 0; 1 is a hall at the ceiling. The dry signal gives way as it
         * grows, and the wet gain is scaled down against the feedback so the room's energy stays put
         * - a comb's output power goes as {@code 1 / (1 - feedback^2)}, and a longer tail left at the
         * same gain was ten decibels louder at the top of the ladder than at the bottom. Larger,
         * never louder (#216).
         */
        void setSpace(double space)
        {
            final double s = Math.max(0d, Math.min(1d, space));
            final double feedback = 0.80d + 0.12d * s;
            final double hold = Math.sqrt((1d - feedback * feedback) / (1d - 0.80d * 0.80d));

            this.feedback = (float) feedback;
            this.wet = (float) ((0.9d + 0.4d * s) * hold);
            this.dry = (float) (0.85d - 0.25d * s);
        }

        void process(float inputL, float inputR)
        {
            final float input = (inputL + inputR) * INPUT;
            float left = 0f;
            float right = 0f;

            for (int index = 0; index < COMBS.length; index++)
            {
                left += comb(this.combL[index], this.combIndexL, this.storeL, index, input);
                right += comb(this.combR[index], this.combIndexR, this.storeR, index, input);
            }

            for (int index = 0; index < ALLPASSES.length; index++)
            {
                left = allpass(this.passL[index], this.passIndexL, index, left);
                right = allpass(this.passR[index], this.passIndexR, index, right);
            }

            this.outL = left * this.wet;
            this.outR = right * this.wet;
        }

        private float comb(float[] buffer, int[] positions, float[] store, int index, float input)
        {
            final int position = positions[index];
            final float output = buffer[position];

            store[index] = output * (1f - DAMP) + store[index] * DAMP;
            buffer[position] = input + store[index] * this.feedback;
            positions[index] = position + 1 == buffer.length ? 0 : position + 1;

            return output;
        }

        private static float allpass(float[] buffer, int[] positions, int index, float input)
        {
            final int position = positions[index];
            final float delayed = buffer[position];
            final float output = delayed - input;

            buffer[position] = input + delayed * 0.5f;
            positions[index] = position + 1 == buffer.length ? 0 : position + 1;

            return output;
        }
    }
}
