/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.structures.core.music.SoulSynth;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link SoulSynth}'s output, as the sound engine reads a streamed file (#163).
 *
 * <p>Read on the sound engine's own thread, a second of audio at a time, which is the whole of this
 * class's contract with vanilla: {@code Channel} asks for a buffer, gets one, queues it. The synth
 * runs at forty-odd times realtime, so a second of audio costs a couple of dozen milliseconds on a
 * thread that is otherwise waiting.
 *
 * <p>Never ends on its own. The music rests between pieces rather than stopping, so the stream is
 * silence during a rest, and it is the sound instance being stopped that ends it.
 */
public final class SoulMusicStream implements AudioStream
{
    private static final AudioFormat FORMAT = new AudioFormat(SoulSynth.SAMPLE_RATE, 16, 2, true, false);

    private static final int BYTES_PER_FRAME = 4;

    private final SoulSynth synth;
    private float[] left = new float[0];
    private float[] right = new float[0];

    /**
     * Reused from one read to the next. The sound engine copies a buffer into OpenAL the moment it is
     * returned (see {@code SoundBuffer}, which calls {@code alBufferData} straight away) and keeps no
     * reference to it, so one buffer can serve the whole stream rather than a fresh allocation a second.
     */
    private ByteBuffer out;

    public SoulMusicStream(SoulSynth synth)
    {
        this.synth = synth;
    }

    @Override
    public AudioFormat getFormat()
    {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int size)
    {
        final int frames = Math.max(1, size / BYTES_PER_FRAME);

        if (this.left.length < frames)
        {
            this.left = new float[frames];
            this.right = new float[frames];
        }

        this.synth.render(this.left, this.right, frames);

        final int bytes = frames * BYTES_PER_FRAME;

        // direct, because OpenAL is handed the address rather than the array
        if (this.out == null || this.out.capacity() < bytes)
        {
            this.out = BufferUtils.createByteBuffer(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        this.out.clear();

        for (int frame = 0; frame < frames; frame++)
        {
            this.out.putShort(toPcm(this.left[frame]));
            this.out.putShort(toPcm(this.right[frame]));
        }

        this.out.flip();

        return this.out;
    }

    @Override
    public void close()
    {
        // nothing is held open: the synth is plain arrays, and goes with this object
    }

    private static short toPcm(float sample)
    {
        return (short) Math.round(Math.max(-1f, Math.min(1f, sample)) * 32_767f);
    }
}
