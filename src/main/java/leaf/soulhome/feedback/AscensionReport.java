/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a soulhome still needs for its next rank, and what its residue is worth right now (#83),
 * built on the server and sent to the Soul Anchor's screen as-is.
 *
 * <p>The same arrangement {@link AttunementReport} has, for the same reason: the client's copy of a
 * soulhome exists to be drawn. Nothing here is recomputed on the client, so the screen cannot end up
 * telling a player they are ready while the server refuses to start a ritual - every number below is
 * the one the ritual itself was judged against, taken from the same {@code Readiness}.
 *
 * <p>The pillar arrives as {@code PillarInspector.Result}'s own two booleans and a gap rather than as
 * a state name, because that is exactly the shape the inspector answers in - "there is no base" and
 * "the base is there and the column stops short" are different failures and want different words.
 */
public record AscensionReport(
        boolean enabled,
        boolean owner,
        int rank,
        int targetRank,
        boolean maxed,
        boolean ritualElsewhere,
        boolean pillarValid,
        boolean pillarHasBase,
        int pillarGap,
        int willpowerHave,
        int willpowerRequired,
        int essenceHave,
        int essenceRequired,
        Residue residue)
{
    /** Ascension switched off in the config, or no soulhome to report on at all. */
    public static final AscensionReport EMPTY =
            new AscensionReport(false, false, 0, 0, false, false, false, false, 0, 0, 0, 0, 0, Residue.NONE);

    public static final Codec<AscensionReport> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.BOOL.optionalFieldOf("enabled", false).forGetter(AscensionReport::enabled),
                    Codec.BOOL.optionalFieldOf("owner", false).forGetter(AscensionReport::owner),
                    Codec.INT.optionalFieldOf("rank", 0).forGetter(AscensionReport::rank),
                    Codec.INT.optionalFieldOf("target_rank", 0).forGetter(AscensionReport::targetRank),
                    Codec.BOOL.optionalFieldOf("maxed", false).forGetter(AscensionReport::maxed),
                    Codec.BOOL.optionalFieldOf("ritual_elsewhere", false).forGetter(AscensionReport::ritualElsewhere),
                    Codec.BOOL.optionalFieldOf("pillar_valid", false).forGetter(AscensionReport::pillarValid),
                    Codec.BOOL.optionalFieldOf("pillar_has_base", false).forGetter(AscensionReport::pillarHasBase),
                    Codec.INT.optionalFieldOf("pillar_gap", 0).forGetter(AscensionReport::pillarGap),
                    Codec.INT.optionalFieldOf("willpower_have", 0).forGetter(AscensionReport::willpowerHave),
                    Codec.INT.optionalFieldOf("willpower_required", 0).forGetter(AscensionReport::willpowerRequired),
                    Codec.INT.optionalFieldOf("essence_have", 0).forGetter(AscensionReport::essenceHave),
                    Codec.INT.optionalFieldOf("essence_required", 0).forGetter(AscensionReport::essenceRequired),
                    Residue.CODEC.optionalFieldOf("residue", Residue.NONE).forGetter(AscensionReport::residue))
            .apply(instance, AscensionReport::new));

    public boolean willpowerMet()
    {
        return this.willpowerHave >= this.willpowerRequired;
    }

    public boolean essenceMet()
    {
        return this.essenceHave >= this.essenceRequired;
    }

    /** Everything the ritual asks for is in place, and the only thing left is to stand on the cap. */
    public boolean ready()
    {
        return this.enabled && !this.maxed && !this.ritualElsewhere && this.pillarValid
                && willpowerMet() && essenceMet();
    }

    /** The same report, saying what a conversion that just happened at this anchor yielded. */
    public AscensionReport withCollected(int collected)
    {
        return new AscensionReport(
                this.enabled, this.owner, this.rank, this.targetRank, this.maxed, this.ritualElsewhere,
                this.pillarValid, this.pillarHasBase, this.pillarGap, this.willpowerHave, this.willpowerRequired,
                this.essenceHave, this.essenceRequired, this.residue.withCollected(collected));
    }

    /**
     * The soul-residue tap (#82) as the anchor's screen needs it.
     *
     * @param tap       whether residue is still accruing at all, so a soulhome that has banked
     *                  nothing yet is told residue exists rather than told nothing
     * @param banked    residue accrued and not yet spent
     * @param essence   whole units of Essence I {@code banked} would convert into right now -
     *                  computed server-side, because the conversion rate is a server config value
     *                  and a client that worked it out itself would eventually disagree with it
     * @param collected what the conversion the player just asked for actually yielded, and 0 on
     *                  every report that is not the answer to one. Transient on purpose: the screen
     *                  says what happened while the answer is on it, and nothing remembers it after
     */
    public record Residue(boolean tap, double banked, int essence, int collected)
    {
        public static final Residue NONE = new Residue(false, 0d, 0, 0);

        public static final Codec<Residue> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.BOOL.optionalFieldOf("tap", false).forGetter(Residue::tap),
                        Codec.DOUBLE.optionalFieldOf("banked", 0d).forGetter(Residue::banked),
                        Codec.INT.optionalFieldOf("essence", 0).forGetter(Residue::essence),
                        Codec.INT.optionalFieldOf("collected", 0).forGetter(Residue::collected))
                .apply(instance, Residue::new));

        /** Whether the anchor has anything to say about residue - a dead tap with nothing banked has not. */
        public boolean worthSaying()
        {
            return this.tap || this.banked > 0 || this.collected > 0;
        }

        public Residue withCollected(int collected)
        {
            return new Residue(this.tap, this.banked, this.essence, collected);
        }
    }
}
