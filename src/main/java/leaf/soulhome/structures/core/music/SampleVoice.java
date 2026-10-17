/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

/**
 * One note played from a recording (#163). It is a {@link SoulSynth.Voice} like the oscillator
 * recipes, so {@link SoulSynth} pans, reverbs and limits it in exactly the same way.
 *
 * <h2>Why cubic interpolation</h2>
 *
 * <p>A pitch-shifted read lands between recorded frames. Linear interpolation of a sample that is
 * being played up an octave audibly dulls it; four-point Hermite keeps the top of the recording
 * without the zipper of nearest-frame reads.
 *
 * <h2>Why the envelope is applied here, not left to the asset</h2>
 *
 * <p>The same recording serves a held note and a struck one, and the bank says which by the
 * instrument's envelope. Shaping it in the asset would mean one file per envelope.
 */
final class SampleVoice extends SoulSynth.Voice
{
    private static final float SCALE = 1f / 32767f;

    private final short[] samples;
    private final SampleBank.Envelope envelope;
    private final double attack;
    private final double release;
    private final double duration;
    private final double step;
    private final double amplitude;
    private final boolean looped;
    private final int loopStart;
    private final int loopEnd;
    private final boolean filtered;
    private final double toneAlpha;

    private double position;
    private double tone;

    /**
     * Whether the read has come round the loop at least once. Frames before loopStart are the intro,
     * which plays once; only after a wrap is the frame before loopStart the loop's own last frame.
     */
    private boolean wrapped;

    private SampleVoice(NoteEvent event, SampleBank.Kit kit, SampleBank.Zone zone)
    {
        // the oscillator constructor sets the pan; its level is not used, since amplitude below is
        // computed from the clamped velocity rather than the raw one
        super(event, 1f);

        this.samples = zone.samples;
        this.envelope = kit.envelope;
        this.attack = kit.attack;
        this.release = kit.release;
        this.duration = Math.max(0d, event.duration());

        // a pitched instrument whose bank says track_pitch false keeps its recording's pitch, so a bell
        // of one recorded note is the same bell at every note of the piece
        this.step = kit.trackPitch ? Math.pow(2d, (event.midi() - zone.root) / 12d) : 1d;

        final double velocity = Math.max(0d, Math.min(1d, event.velocity()));

        this.amplitude = velocity * kit.level * zone.gain;
        this.looped = zone.looped();
        this.loopStart = zone.loopStart;
        this.loopEnd = zone.loopEnd;

        // a soft note is a darker note: the cutoff glides from the low end of the range to the high one
        // as velocity rises, the way a struck or bowed instrument's own brightness does
        this.filtered = kit.filtered();
        this.toneAlpha = this.filtered
                ? 1d - Math.exp(-2d * Math.PI * kit.toneLow * Math.pow(kit.toneHigh / kit.toneLow, velocity) / SoulSynth.SAMPLE_RATE)
                : 0d;
    }

    /** The voice for {@code event}, from the zone the instrument's kit selects for it. */
    static SampleVoice play(NoteEvent event, SampleBank.Kit kit, long seed)
    {
        return new SampleVoice(event, kit, kit.select(event, seed));
    }

    @Override
    float sample(double seconds)
    {
        if (this.position >= this.samples.length)
        {
            return 0f;
        }

        final boolean looping = wraps(seconds);

        double value = read(this.position, looping) * this.amplitude * SCALE * envelope(seconds);

        if (this.filtered)
        {
            this.tone += this.toneAlpha * (value - this.tone);
            value = this.tone;
        }

        this.position += this.step;

        if (looping && this.position >= this.loopEnd)
        {
            // the loop is a cycle, and read() takes its taps from the same cycle, so the wrap is seamless
            // for the interpolation as well as for the read position
            final double length = this.loopEnd - this.loopStart;

            this.position = this.loopStart + (this.position - this.loopStart) % length;
            this.wrapped = true;
        }

        return (float) value;
    }

    @Override
    boolean done()
    {
        if (this.position >= this.samples.length)
        {
            return true;
        }

        final double rate = SoulSynth.SAMPLE_RATE;

        return switch (this.envelope)
        {
            case STRUCK -> false;
            case SUSTAIN -> this.age >= Math.ceil((this.duration + this.release) * rate);
            case SWELL -> this.age >= Math.ceil(this.duration * rate);
        };
    }

    /**
     * Whether the read position wraps at this moment. A struck note plays to the end of the recording
     * whatever its zone says. A held note loops for its duration and through its release. The loop is
     * all there is of a held note in the asset - the build tool cuts a looped recording at loop_end, so
     * there is no recorded tail to release over - and the release is the sampler's envelope, not the
     * recording's. Stopping the loop at the end of the hold would run the read off the end of the file
     * and end the voice at once, which is a click. Only the release ends a held note. A swell loops for
     * its whole life, since its envelope is what ends it.
     */
    private boolean wraps(double seconds)
    {
        if (!this.looped)
        {
            return false;
        }

        return switch (this.envelope)
        {
            case STRUCK -> false;
            case SUSTAIN -> seconds < this.duration + this.release;
            case SWELL -> true;
        };
    }

    private double envelope(double seconds)
    {
        final double rise = rise(seconds);

        return switch (this.envelope)
        {
            case STRUCK -> rise;
            case SUSTAIN -> rise * fall(seconds);
            case SWELL ->
            {
                if (seconds >= this.duration)
                {
                    yield 0d;
                }

                final double sine = Math.sin(Math.PI * seconds / this.duration);

                yield sine * sine;
            }
        };
    }

    /** A raised-cosine fade in from 0 to 1 over the attack; a note with no attack starts at full level. */
    private double rise(double seconds)
    {
        if (this.attack <= 0d || seconds >= this.attack)
        {
            return 1d;
        }

        return 0.5d - 0.5d * Math.cos(Math.PI * seconds / this.attack);
    }

    /** Holds at 1 for the duration, then a raised-cosine fade out to 0 over the release. */
    private double fall(double seconds)
    {
        if (seconds < this.duration)
        {
            return 1d;
        }

        if (this.release <= 0d || seconds >= this.duration + this.release)
        {
            return 0d;
        }

        return 0.5d + 0.5d * Math.cos(Math.PI * (seconds - this.duration) / this.release);
    }

    /**
     * Four-point cubic Hermite interpolation, reading past either end of the recording as silence. A
     * tap is the frame the voice hears at that index: while the voice loops, the loop is a cycle, so a
     * tap past loopEnd is the frame after loopStart and a tap before it is the frame before loopEnd.
     * Without that, the last frames of every loop interpolate toward the silence past the end of the
     * array, and each wrap is a dip.
     */
    private float read(double position, boolean looping)
    {
        final int index = (int) Math.floor(position);
        final double t = position - index;

        final double x0 = tap(index - 1, looping);
        final double x1 = tap(index, looping);
        final double x2 = tap(index + 1, looping);
        final double x3 = tap(index + 2, looping);

        final double c1 = 0.5d * (x2 - x0);
        final double c2 = x0 - 2.5d * x1 + 2d * x2 - 0.5d * x3;
        final double c3 = 0.5d * (x3 - x0) + 1.5d * (x1 - x2);

        return (float) (((c3 * t + c2) * t + c1) * t + x1);
    }

    /**
     * The frame at {@code index} as the voice hears it. Outside a loop, or inside it, this is the
     * recording itself. Past loopEnd while looping, the index is folded back into the loop, so the
     * taps at the seam are the frames the wrap is about to play. Below loopStart it is folded only once
     * the voice has wrapped: before that, those frames are the intro and are heard as they are.
     */
    private double tap(int index, boolean looping)
    {
        final boolean inLoop = index >= this.loopStart && index < this.loopEnd;

        if (!looping || inLoop || index < this.loopStart && !this.wrapped)
        {
            return at(index);
        }

        final int length = this.loopEnd - this.loopStart;

        return at(this.loopStart + Math.floorMod(index - this.loopStart, length));
    }

    private double at(int index)
    {
        return index < 0 || index >= this.samples.length ? 0d : this.samples[index];
    }
}
