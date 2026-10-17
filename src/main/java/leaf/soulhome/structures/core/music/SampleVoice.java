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

        double value = read(this.position) * this.amplitude * SCALE * envelope(seconds);

        if (this.filtered)
        {
            this.tone += this.toneAlpha * (value - this.tone);
            value = this.tone;
        }

        this.position += this.step;

        if (wraps(seconds) && this.position >= this.loopEnd)
        {
            // the loop is crossfaded in the asset, so a plain wrap is seamless
            final double length = this.loopEnd - this.loopStart;

            this.position = this.loopStart + (this.position - this.loopStart) % length;
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
     * whatever its zone says. A held note loops only for as long as it is held, so its release is
     * heard over the recording's tail rather than over a loop that never ends. A swell loops for its
     * whole life, since its envelope is what ends it.
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
            case SUSTAIN -> seconds < this.duration;
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
     * Four-point cubic Hermite interpolation, reading past either end as silence. The taps are the
     * recording's own frames, so a read that straddles the loop point simply reads the frames beyond
     * the loop, which the asset has been crossfaded to expect.
     */
    private float read(double position)
    {
        final int index = (int) Math.floor(position);
        final double t = position - index;

        final double x0 = at(index - 1);
        final double x1 = at(index);
        final double x2 = at(index + 1);
        final double x3 = at(index + 2);

        final double c1 = 0.5d * (x2 - x0);
        final double c2 = x0 - 2.5d * x1 + 2d * x2 - 0.5d * x3;
        final double c3 = 0.5d * (x3 - x0) + 1.5d * (x1 - x2);

        return (float) (((c3 * t + c2) * t + c1) * t + x1);
    }

    private double at(int index)
    {
        return index < 0 || index >= this.samples.length ? 0d : this.samples[index];
    }
}
