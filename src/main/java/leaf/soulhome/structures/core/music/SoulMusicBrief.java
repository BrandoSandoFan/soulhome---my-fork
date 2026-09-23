/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulAxis;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulVoice;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a soul's next piece of music should be about (#163): the same two inputs as everything else
 * in the Ambience epic - what is built, and how far the soul has climbed - plus the one thing that
 * makes it <i>this</i> soul's music rather than any soul's with the same rooms.
 *
 * <h2>Why a lottery here, when the bed refused one</h2>
 *
 * <p>{@code SoulAmbience.characterBedMix} mixes every voice at once because a bed is never not
 * playing, and a bed that rolled for one voice at a time would be a soul that cannot make up its
 * mind. A piece of music is the opposite case: it is three minutes of one thing and then silence,
 * and a piece in two modes at once is not a blend, it is a wrong note. So each piece draws its
 * <i>mode and lead</i> from the blend by weight, and the blend still sounds in every piece through
 * {@link #runnerUp}: the second-strongest voice's instrument answers under the lead. A soul of
 * hearths and aquariums hears the piano in one piece with bells under it, and bells in the next with
 * the piano under them - which is what that soul is.
 *
 * <h2>Why a seed</h2>
 *
 * <p>Two souls with the same rooms would otherwise have the same music, and "nothing about my soul
 * is unique" is exactly the complaint this exists to answer. The soul's own dimension id picks its
 * key and seeds every piece, so a soul's music is recognisably its own from one visit to the next
 * without ever repeating a piece.
 *
 * @param weights      each voice's share of the draw, summing to one
 * @param rankFraction 0 at rank 0, 1 at the configured ceiling
 * @param soulSeed     stable per soul
 */
public record SoulMusicBrief(Map<SoulVoice, Double> weights, double rankFraction, long soulSeed)
{
    /**
     * How much of the draw a fully-built soul's rooms take from the base voice. Higher than the 0.75
     * the one-shots use ({@code SoulAmbience.voiceFor}): the base voice never leaves - a soul is a
     * place before it is a mix of rooms - but a player with a soul full of rooms should hear those
     * rooms in most of its pieces, which was the whole of #163's complaint.
     */
    public static final double CHARACTER_SHARE = 0.85d;

    /** Contested reading takes over across this band of tension - the same band the colour uses. */
    private static final double TENSION_FLOOR = 0.35d;

    private static final double TENSION_FULL = 0.95d;

    public SoulMusicBrief
    {
        final Map<SoulVoice, Double> copy = new EnumMap<>(SoulVoice.class);

        if (weights != null)
        {
            weights.forEach((voice, weight) ->
            {
                if (voice != null && weight != null && weight > 0d)
                {
                    copy.put(voice, weight);
                }
            });
        }

        if (copy.isEmpty())
        {
            copy.put(SoulVoice.BASE, 1d);
        }

        weights = Map.copyOf(copy);
        rankFraction = Math.max(0d, Math.min(1d, rankFraction));
    }

    /**
     * The brief for one soul.
     *
     * @param character its blend over every classified room
     * @param rank      its ascension rank
     * @param maxRank   the configured ceiling
     * @param soulId    anything stable and unique per soul - the dimension id is what the game passes
     */
    public static SoulMusicBrief of(SoulCharacter character, int rank, int maxRank, String soulId)
    {
        final Map<SoulVoice, Double> weights = new EnumMap<>(SoulVoice.class);
        final double depth = character == null ? 0d : character.depth();
        final double characterShare = CHARACTER_SHARE * depth;

        weights.put(SoulVoice.BASE, 1d - characterShare);

        if (character != null && !character.isEmpty() && characterShare > 0d)
        {
            final Map<SoulVoice, Double> raw = new EnumMap<>(SoulVoice.class);
            double total = 0d;

            for (SoulAxis axis : SoulAxis.values())
            {
                final double share = character.share(axis);

                if (share <= 0d)
                {
                    continue;
                }

                final double lean = character.lean(axis);
                final double contested = smoothstep(character.tension(axis));
                final double poleShare = share * (1d - contested);

                // by which way the axis leans rather than by |lean|: a soul four-fifths hearth and
                // one-fifth aquarium is mostly warm, not three-fifths warm and silent otherwise
                raw.merge(voice(axis, true), poleShare * (1d + lean) / 2d, Double::sum);
                raw.merge(voice(axis, false), poleShare * (1d - lean) / 2d, Double::sum);
                raw.merge(contestedVoice(axis), share * contested, Double::sum);
            }

            for (double value : raw.values())
            {
                total += value;
            }

            if (total > 0d)
            {
                for (Map.Entry<SoulVoice, Double> entry : raw.entrySet())
                {
                    weights.merge(entry.getKey(), characterShare * entry.getValue() / total, Double::sum);
                }
            }
            else
            {
                weights.put(SoulVoice.BASE, 1d);
            }
        }

        final double fraction = maxRank <= 0 ? 1d : (double) rank / (double) maxRank;

        return new SoulMusicBrief(weights, fraction, seedOf(soulId));
    }

    public double weight(SoulVoice voice)
    {
        return this.weights.getOrDefault(voice, 0d);
    }

    /** The voice a piece is written for, drawn by weight. {@code roll} is in {@code [0, 1)}. */
    public SoulVoice pick(double roll)
    {
        double total = 0d;

        for (double weight : this.weights.values())
        {
            total += weight;
        }

        double target = Math.max(0d, Math.min(0.999999d, roll)) * total;

        // walked in enum order so the same roll always lands on the same voice, whatever order the
        // map happens to iterate in
        for (SoulVoice voice : SoulVoice.values())
        {
            final double weight = weight(voice);

            if (weight <= 0d)
            {
                continue;
            }

            if (target < weight)
            {
                return voice;
            }

            target -= weight;
        }

        return SoulVoice.BASE;
    }

    /**
     * The strongest voice other than {@code chosen} - a room's voice before the base one, since the
     * base voice is the place and is already under every piece in the pad - or null when there is
     * none, and an empty soul's music is a solo. Ties go to the earlier voice in the enum, so the
     * answer never depends on map order.
     */
    public SoulVoice runnerUp(SoulVoice chosen)
    {
        SoulVoice best = null;
        double bestWeight = 0d;

        for (SoulVoice voice : SoulVoice.values())
        {
            final double weight = weight(voice);

            if (voice != chosen && voice != SoulVoice.BASE && weight > bestWeight)
            {
                best = voice;
                bestWeight = weight;
            }
        }

        if (best == null && chosen != SoulVoice.BASE && weight(SoulVoice.BASE) > 0d)
        {
            return SoulVoice.BASE;
        }

        return best;
    }

    /**
     * This soul's tonic, somewhere from C3 to G3: low enough that the pad sits under the game, high
     * enough that the lead an octave or two up is still in the range a small speaker plays.
     */
    public int tonic()
    {
        return 48 + (int) Math.floorMod(mix(this.soulSeed), 8L);
    }

    /** A seed for this soul's {@code index}-th piece: distinct per piece, and the same every visit. */
    public long pieceSeed(long index)
    {
        return mix(this.soulSeed ^ mix(index + 0x9E3779B97F4A7C15L));
    }

    /**
     * {@link String#hashCode} is specified to the letter, so this is the same number on every JVM;
     * it is spread over 64 bits afterwards so two ids differing in one character do not land in
     * neighbouring keys.
     */
    public static long seedOf(String soulId)
    {
        return mix(soulId == null ? 0L : soulId.hashCode());
    }

    /** SplitMix64's finaliser - a cheap, well-studied way to turn a counter into noise. */
    static long mix(long value)
    {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static SoulVoice voice(SoulAxis axis, boolean positive)
    {
        return switch (positive ? axis.positive() : axis.negative())
        {
            case WARM -> SoulVoice.WARM;
            case COLD -> SoulVoice.COLD;
            case ARCANE -> SoulVoice.ARCANE;
            case WROUGHT -> SoulVoice.WROUGHT;
            case VERDANT -> SoulVoice.VERDANT;
            case HOLLOW -> SoulVoice.HOLLOW;
        };
    }

    private static SoulVoice contestedVoice(SoulAxis axis)
    {
        return switch (axis)
        {
            case THERMAL -> SoulVoice.STEAM;
            case ESSENCE -> SoulVoice.QUICKENED;
            case VITALITY -> SoulVoice.OVERGROWN;
        };
    }

    private static double smoothstep(double value)
    {
        final double t = Math.max(0d, Math.min(1d, (value - TENSION_FLOOR) / (TENSION_FULL - TENSION_FLOOR)));

        return t * t * (3d - 2d * t);
    }
}
