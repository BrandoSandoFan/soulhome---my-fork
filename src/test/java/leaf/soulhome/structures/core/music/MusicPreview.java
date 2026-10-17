/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A listening aid for whoever mixes the sample bank (#163), run as {@code sh gradlew musicPreview}
 * and never as part of the build. It renders the soul's music with the shipped bank to WAV files
 * under {@code build/music-preview}, and prints the numbers a mix is judged by, so a change to a
 * recording or a level can be heard and measured in the same sitting.
 *
 * <h2>What is written</h2>
 *
 * <p>Every voice at rank 0, 50 and 100 with the shipped bank; every voice at rank 50 with
 * {@link SampleBank#EMPTY}, the oscillator recipe, for an A/B; and for every instrument a stem with
 * the shipped bank and the same stem with {@link SampleBank#EMPTY}. A stem is one instrument's own
 * events from a single non-base voice's pieces, with everything else removed.
 *
 * <h2>Arguments</h2>
 *
 * <p>{@code --only stems|voices|all} (default {@code all}) picks which set is rendered, and
 * {@code --no-wav} measures and writes the report without writing any audio. The Gradle task passes
 * them through the {@code previewArgs} property, split on spaces.
 *
 * <h2>What is measured</h2>
 *
 * <p>The level figures are the ones {@code SoulSynthTest} holds - the body after the first rest, and
 * seven seconds on, which is where the mix has settled - so a number here is the number a test would
 * have failed on. The rest are for mixing: how often the limiter has to bend (samples past 0.5), and
 * where the energy sits (the magnitude-weighted spectral centroid of the mono sum, averaged over
 * 4096-sample Hann frames that are above -60 dBFS). On a stem row, {@code vs osc dB} is the sampled
 * stem's rms>7s minus the oscillator stem's rms>7s, so a positive number is the recording sitting
 * louder than the recipe it replaces.
 */
public final class MusicPreview
{
    private static final int RATE = SoulSynth.SAMPLE_RATE;

    /** The same brief seed as {@code SoulSynthTest#levels}, so a file here is a file the test renders. */
    private static final long BRIEF_SEED = 5L;

    /** The same synth seed as {@code SoulSynthTest#synth}. */
    private static final long SYNTH_SEED = 17L;

    private static final double FULL_SECONDS = 75d;
    private static final double STEM_SECONDS = 60d;

    private static final double[] RANKS = {0d, 0.5d, 1d};
    private static final String[] RANK_NAMES = {"r0", "r50", "r100"};

    private static final int FFT_SIZE = 4096;
    private static final int HOP = 2048;

    private static final double[] COS = new double[FFT_SIZE / 2];
    private static final double[] SIN = new double[FFT_SIZE / 2];
    private static final double[] HANN = new double[FFT_SIZE];

    /** Where the files land, relative to the working directory the Gradle task sets to the project. */
    private static final Path OUT = Path.of("build", "music-preview");

    private static final String USAGE = "usage: MusicPreview [--only stems|voices|all] [--no-wav]";

    private static final String HEADER = String.format(Locale.ROOT, "%-26s %11s %7s %10s %11s %11s %10s",
            "file", "rms>3s dBFS", "peak", "|x|>0.5 %", "centroid Hz", "rms>7s dBFS", "vs osc dB");

    static
    {
        for (int index = 0; index < FFT_SIZE / 2; index++)
        {
            COS[index] = Math.cos(-2d * Math.PI * index / FFT_SIZE);
            SIN[index] = Math.sin(-2d * Math.PI * index / FFT_SIZE);
        }

        for (int index = 0; index < FFT_SIZE; index++)
        {
            HANN[index] = 0.5d - 0.5d * Math.cos(2d * Math.PI * index / (FFT_SIZE - 1));
        }
    }

    private MusicPreview()
    {
    }

    /** Which sets to render, and whether to write audio. */
    private record Options(boolean voices, boolean stems, boolean wav)
    {
        static Options parse(String[] args)
        {
            String only = "all";
            boolean wav = true;

            for (int index = 0; index < args.length; index++)
            {
                switch (args[index])
                {
                    case "--only" ->
                    {
                        if (index + 1 >= args.length)
                        {
                            throw new IllegalArgumentException("--only needs a value. " + USAGE);
                        }

                        only = args[++index];
                    }

                    case "--no-wav" -> wav = false;

                    default -> throw new IllegalArgumentException("unknown argument " + args[index] + ". " + USAGE);
                }
            }

            return switch (only)
            {
                case "all" -> new Options(true, true, wav);
                case "voices" -> new Options(true, false, wav);
                case "stems" -> new Options(false, true, wav);
                default -> throw new IllegalArgumentException("--only takes stems, voices or all, not " + only + ". " + USAGE);
            };
        }
    }

    /** One rendered file's measurements. */
    private record Stats(double rmsBody, double peak, double hot, double centroid, double rmsSettled)
    {
        static Stats of(float[][] audio)
        {
            return new Stats(
                    rmsDb(audio, (int) (SoulSynth.FIRST_REST * RATE), audio[0].length),
                    MusicPreview.peak(audio),
                    hotShare(audio),
                    MusicPreview.centroid(audio),
                    rmsDb(audio, (int) (SoulSynth.FIRST_REST * RATE) + 4 * RATE, audio[0].length));
        }
    }

    public static void main(String[] args) throws IOException
    {
        final long started = System.nanoTime();
        final Options options = Options.parse(args);

        Files.createDirectories(OUT);

        final SampleBank bank = JorbisDecoder.shipped();
        final List<String> rows = new ArrayList<>();

        System.out.println(HEADER);

        if (options.voices())
        {
            for (SoulVoice voice : SoulVoice.values())
            {
                for (int rank = 0; rank < RANKS.length; rank++)
                {
                    final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), RANKS[rank], BRIEF_SEED);
                    final float[][] audio = render(brief, bank, FULL_SECONDS, event -> true);

                    emit(lower(voice) + "_" + RANK_NAMES[rank], audio, Stats.of(audio), null, options, rows);
                }
            }

            for (SoulVoice voice : SoulVoice.values())
            {
                final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, BRIEF_SEED);
                final float[][] audio = render(brief, SampleBank.EMPTY, FULL_SECONDS, event -> true);

                emit(lower(voice) + "_r50_oscillator", audio, Stats.of(audio), null, options, rows);
            }
        }

        if (options.stems())
        {
            for (Instrument instrument : Instrument.values())
            {
                final SoulVoice voice = stemVoice(instrument);
                final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, BRIEF_SEED);
                final Predicate<NoteEvent> keep = event -> event.instrument() == instrument;

                // both renders are measured before either row is printed, so the sampled row can carry its difference
                final float[][] sampled = render(brief, bank, STEM_SECONDS, keep);
                final float[][] oscillator = render(brief, SampleBank.EMPTY, STEM_SECONDS, keep);
                final Stats sampledStats = Stats.of(sampled);
                final Stats oscillatorStats = Stats.of(oscillator);

                emit("stem_" + lower(instrument), sampled, sampledStats,
                        sampledStats.rmsSettled() - oscillatorStats.rmsSettled(), options, rows);
                emit("stem_" + lower(instrument) + "_oscillator", oscillator, oscillatorStats, null, options, rows);
            }
        }

        final double seconds = (System.nanoTime() - started) / 1e9d;
        final String footer = String.format(Locale.ROOT, "runtime %.1f s", seconds);

        System.out.println(footer);

        final List<String> report = new ArrayList<>();

        report.add(HEADER);
        report.addAll(rows);
        report.add("");
        report.add("rms>3s: RMS over both channels from 3 s to the end. rms>7s: the same from 7 s to the end,");
        report.add("as SoulSynthTest.levels measures it. |x|>0.5: share of all samples past the limiter's knee.");
        report.add("centroid: magnitude-weighted, mono sum, Hann 4096 frames hopped 2048, frames above -60 dBFS.");
        report.add("vs osc dB: sampled stem rms>7s minus the oscillator stem's rms>7s, on sampled stem rows only.");
        report.add("stems are 60 s, so their rms>7s covers 7 s to 60 s.");
        report.add(footer);

        Files.write(OUT.resolve("report.txt"), report, StandardCharsets.UTF_8);

        System.out.println("wrote " + rows.size() + " rows" + (options.wav() ? " and their WAVs" : ", no WAVs")
                + " to " + OUT.toAbsolutePath());
    }

    /** Writes the file (unless --no-wav) and prints its row. */
    private static void emit(String name, float[][] audio, Stats stats, Double versusOscillator, Options options,
            List<String> rows) throws IOException
    {
        if (options.wav())
        {
            writeWav(OUT.resolve(name + ".wav"), audio);
        }

        final String row = String.format(Locale.ROOT, "%-26s %11.2f %7.3f %10.3f %11s %11.2f %10s",
                name,
                stats.rmsBody(),
                stats.peak(),
                100d * stats.hot(),
                Double.isNaN(stats.centroid()) ? "n/a" : String.format(Locale.ROOT, "%.0f", stats.centroid()),
                stats.rmsSettled(),
                versusOscillator == null ? "" : String.format(Locale.ROOT, "%+.2f", versusOscillator));

        rows.add(row);
        System.out.println(row);
        System.out.flush();
    }

    /**
     * The voice whose pieces play this instrument most: counted over the first three pieces at rank
     * 0.5, non-base voices first. Falls back to the base draw only when nothing else plays it, which
     * leaves the stem silent - and the table says so.
     */
    private static SoulVoice stemVoice(Instrument instrument)
    {
        SoulVoice best = null;
        int bestCount = 0;

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            final int count = playedBy(voice, instrument);

            if (count > bestCount)
            {
                best = voice;
                bestCount = count;
            }
        }

        return best != null ? best : SoulVoice.BASE;
    }

    private static int playedBy(SoulVoice voice, Instrument instrument)
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, BRIEF_SEED);
        int count = 0;

        for (long index = 0; index < 3; index++)
        {
            for (NoteEvent event : SoulComposer.compose(brief, brief.pieceSeed(index)).events())
            {
                if (event.instrument() == instrument)
                {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * {@code SoulSynthTest}'s render, with the bank and the event filter as parameters. The piece is
     * rebuilt rather than mutated, so a stem is the same composition as the mix it came from.
     */
    private static float[][] render(SoulMusicBrief brief, SampleBank bank, double seconds, Predicate<NoteEvent> keep)
    {
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        new SoulSynth(index ->
        {
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

            return new SoulComposer.Piece(piece.voice(), piece.support(), piece.mode(), piece.tonic(), piece.bpm(),
                    piece.space(), piece.length(), piece.events().stream().filter(keep).toList());
        }, SYNTH_SEED, bank).render(left, right, frames);

        return new float[][] {left, right};
    }

    /** 16-bit stereo PCM. Samples are already inside the limiter's ceiling, so the clamp is a guard only. */
    private static void writeWav(Path path, float[][] audio) throws IOException
    {
        final int frames = audio[0].length;
        final int dataBytes = frames * 2 * 2;
        final ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataBytes)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 2).putInt(RATE).putInt(RATE * 2 * 2)
                .putShort((short) 4).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);

        for (int frame = 0; frame < frames; frame++)
        {
            buffer.putShort(pcm(audio[0][frame]));
            buffer.putShort(pcm(audio[1][frame]));
        }

        Files.write(path, buffer.array());
    }

    private static short pcm(float sample)
    {
        final float clamped = Math.max(-1f, Math.min(1f, sample));

        return (short) Math.round(clamped * 32767f);
    }

    /** Matches {@code SoulSynthTest}'s measure exactly: mean square over both channels, in dB. */
    private static double rmsDb(float[][] audio, int from, int to)
    {
        double sum = 0d;

        for (int index = from; index < to; index++)
        {
            sum += audio[0][index] * audio[0][index] + audio[1][index] * audio[1][index];
        }

        return 10d * Math.log10(sum / (2d * Math.max(1, to - from)) + 1e-30d);
    }

    private static double peak(float[][] audio)
    {
        double peak = 0d;

        for (float[] channel : audio)
        {
            for (float sample : channel)
            {
                peak = Math.max(peak, Math.abs(sample));
            }
        }

        return peak;
    }

    /** The share of samples, over both channels, past the knee where {@code SoulSynth.limit} starts to bend. */
    private static double hotShare(float[][] audio)
    {
        long hot = 0L;

        for (float[] channel : audio)
        {
            for (float sample : channel)
            {
                if (Math.abs(sample) > 0.5f)
                {
                    hot++;
                }
            }
        }

        return hot / (double) (2L * audio[0].length);
    }

    /**
     * Magnitude-weighted mean frequency of the mono sum, averaged over the frames that are loud enough
     * to mean anything. NaN when no frame is above -60 dBFS.
     */
    private static double centroid(float[][] audio)
    {
        final int frames = audio[0].length;
        final float[] mono = new float[frames];

        for (int index = 0; index < frames; index++)
        {
            mono[index] = 0.5f * (audio[0][index] + audio[1][index]);
        }

        final double[] re = new double[FFT_SIZE];
        final double[] im = new double[FFT_SIZE];
        double sum = 0d;
        int counted = 0;

        for (int start = 0; start + FFT_SIZE <= frames; start += HOP)
        {
            double energy = 0d;

            for (int index = 0; index < FFT_SIZE; index++)
            {
                final double sample = mono[start + index];

                energy += sample * sample;
                re[index] = sample * HANN[index];
                im[index] = 0d;
            }

            if (10d * Math.log10(energy / FFT_SIZE + 1e-30d) <= -60d)
            {
                continue;
            }

            fft(re, im);

            double weighted = 0d;
            double total = 0d;

            for (int bin = 1; bin < FFT_SIZE / 2; bin++)
            {
                final double magnitude = Math.sqrt(re[bin] * re[bin] + im[bin] * im[bin]);

                weighted += magnitude * bin;
                total += magnitude;
            }

            if (total > 0d)
            {
                sum += weighted / total * RATE / FFT_SIZE;
                counted++;
            }
        }

        return counted == 0 ? Double.NaN : sum / counted;
    }

    /** In-place radix-2 FFT of {@link #FFT_SIZE} points, with the twiddles precomputed. */
    private static void fft(double[] re, double[] im)
    {
        final int n = re.length;

        for (int index = 1, j = 0; index < n; index++)
        {
            int bit = n >> 1;

            for (; (j & bit) != 0; bit >>= 1)
            {
                j ^= bit;
            }

            j ^= bit;

            if (index < j)
            {
                final double swapRe = re[index];
                final double swapIm = im[index];

                re[index] = re[j];
                im[index] = im[j];
                re[j] = swapRe;
                im[j] = swapIm;
            }
        }

        for (int len = 2; len <= n; len <<= 1)
        {
            final int half = len >> 1;
            final int stride = n / len;

            for (int start = 0; start < n; start += len)
            {
                for (int k = 0; k < half; k++)
                {
                    final double wr = COS[k * stride];
                    final double wi = SIN[k * stride];
                    final int a = start + k;
                    final int b = a + half;
                    final double tr = re[b] * wr - im[b] * wi;
                    final double ti = re[b] * wi + im[b] * wr;

                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }

    private static String lower(Enum<?> value)
    {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
