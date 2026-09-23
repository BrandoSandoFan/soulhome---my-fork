/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulTrait;
import leaf.soulhome.structures.core.SoulVoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soul's music, as a score (#163). Everything here that can be decided without listening is
 * decided here - which is most of what keeps generated music from sounding wrong: every note in the
 * mode, no diminished chord held under a pad, a piece that closes on home, and a soul full of rooms
 * that is heard in most of its pieces.
 */
class SoulComposerTest
{
    private static final int PIECES = 40;

    @Test
    @DisplayName("every pitched note of every voice's pieces is in that piece's mode")
    void everyNoteIsInTheMode()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, 7L);

            for (int index = 0; index < PIECES; index++)
            {
                final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

                for (NoteEvent event : piece.events())
                {
                    if (!event.instrument().pitched())
                    {
                        // foley: a clank's pitch is a colour, and a crackle has none
                        continue;
                    }

                    assertTrue(piece.mode().contains(piece.tonic(), event.midi()),
                            voice + " piece " + index + " plays " + event.midi() + " outside " + piece.mode());
                }
            }
        }
    }

    @Test
    @DisplayName("no chord the pad holds is diminished, in any mode")
    void noDiminishedChords()
    {
        for (MusicMode mode : MusicMode.values())
        {
            final SplittableRandom random = new SplittableRandom(mode.ordinal());

            for (int trial = 0; trial < 200; trial++)
            {
                for (int degree : SoulComposer.progression(12 + trial % 8, random, mode))
                {
                    assertTrue(mode.stable(degree), mode + " walked onto its diminished degree " + degree);
                }
            }
        }
    }

    @Test
    @DisplayName("every mode has exactly one unstable degree, so the guard is not guarding nothing")
    void eachModeHasOneDiminishedDegree()
    {
        for (MusicMode mode : MusicMode.values())
        {
            int unstable = 0;

            for (int degree = 0; degree < 7; degree++)
            {
                if (!mode.stable(degree))
                {
                    unstable++;
                }
            }

            assertEquals(1, unstable, mode.name());
        }
    }

    @Test
    @DisplayName("a progression starts and ends on home")
    void progressionsCloseOnTheTonic()
    {
        final SplittableRandom random = new SplittableRandom(3L);

        for (int trial = 0; trial < 200; trial++)
        {
            final int[] chords = SoulComposer.progression(10 + trial % 10, random, MusicMode.values()[trial % 6]);

            assertEquals(0, chords[0]);
            assertEquals(0, chords[chords.length - 1]);
        }
    }

    @Test
    @DisplayName("the same brief and seed write the same piece; another seed writes another")
    void deterministic()
    {
        final SoulMusicBrief brief = SoulMusicBrief.of(character(SoulTrait.WARM, 400d, SoulTrait.COLD, 100d), 2, 5, "a");

        final SoulComposer.Piece first = SoulComposer.compose(brief, 99L);
        final SoulComposer.Piece again = SoulComposer.compose(brief, 99L);

        assertNotSame(first, again);
        assertEquals(first, again);
        assertNotEquals(first.events(), SoulComposer.compose(brief, 100L).events());
    }

    @Test
    @DisplayName("a soul full of one kind of room hears that room in most of its pieces")
    void aFullSoulHearsItsRooms()
    {
        final SoulMusicBrief brief = SoulMusicBrief.of(character(SoulTrait.COLD, 1_500d), 2, 5, "cold soul");
        int cold = 0;

        for (int index = 0; index < 200; index++)
        {
            if (SoulComposer.compose(brief, brief.pieceSeed(index)).voice() == SoulVoice.COLD)
            {
                cold++;
            }
        }

        assertTrue(cold > 120, "only " + cold + " of 200 pieces were the cold soul's own - #163's complaint");
        assertTrue(cold < 200, "and the base voice is never crowded out: a soul is a place first");
    }

    @Test
    @DisplayName("an empty soul plays the base voice alone")
    void anEmptySoulIsTheBaseVoice()
    {
        final SoulMusicBrief brief = SoulMusicBrief.of(SoulCharacter.EMPTY, 0, 5, "empty");

        assertEquals(1d, brief.weight(SoulVoice.BASE), 1e-9d);

        final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(0));

        assertEquals(SoulVoice.BASE, piece.voice());
        assertEquals(null, piece.support(), "nothing else is built, so nothing answers");
    }

    @Test
    @DisplayName("warm and cold built alike are steam, not the two taking turns")
    void contestedIsItsOwnVoice()
    {
        final SoulMusicBrief brief = SoulMusicBrief.of(
                character(SoulTrait.WARM, 600d, SoulTrait.COLD, 600d), 2, 5, "steam");

        assertTrue(brief.weight(SoulVoice.STEAM) > brief.weight(SoulVoice.WARM) + brief.weight(SoulVoice.COLD),
                "a soul built both ways at once is a third thing: " + brief.weights());
    }

    @Test
    @DisplayName("the runner-up is the soul's other half, not the base voice it already has")
    void theRunnerUpIsARoom()
    {
        final SoulMusicBrief brief = SoulMusicBrief.of(
                character(SoulTrait.WARM, 500d, SoulTrait.ARCANE, 120d), 2, 5, "two rooms");

        assertEquals(SoulVoice.ARCANE, brief.runnerUp(SoulVoice.WARM));
        assertEquals(SoulVoice.WARM, brief.runnerUp(SoulVoice.BASE));
    }

    @Test
    @DisplayName("larger, never louder: a higher rank is slower, longer and roomier, and no note is harder")
    void rankIsSpaceNotVolume()
    {
        final SoulMusicBrief ground = new SoulMusicBrief(Map.of(SoulVoice.WARM, 1d), 0d, 11L);
        final SoulMusicBrief summit = new SoulMusicBrief(Map.of(SoulVoice.WARM, 1d), 1d, 11L);

        double groundLength = 0d;
        double summitLength = 0d;
        double groundVelocity = 0d;
        double summitVelocity = 0d;

        for (int index = 0; index < PIECES; index++)
        {
            final SoulComposer.Piece low = SoulComposer.compose(ground, ground.pieceSeed(index));
            final SoulComposer.Piece high = SoulComposer.compose(summit, summit.pieceSeed(index));

            assertTrue(high.bpm() < low.bpm());
            assertTrue(high.space() > low.space());

            groundLength += low.length();
            summitLength += high.length();
            groundVelocity += meanVelocity(low.events()) / PIECES;
            summitVelocity += meanVelocity(high.events()) / PIECES;
        }

        assertTrue(summitLength > groundLength * 1.5d, "a rank V soul's pieces take their time");
        assertEquals(groundVelocity, summitVelocity, 0.03d, "and they are not struck any harder");
    }

    @Test
    @DisplayName("a soul's music is its own: another soul with the same rooms is in another key")
    void soulsDiffer()
    {
        final SoulCharacter rooms = character(SoulTrait.VERDANT, 400d);
        int distinctKeys = 0;
        int previous = -1;

        for (int soul = 0; soul < 16; soul++)
        {
            final int tonic = SoulMusicBrief.of(rooms, 1, 5, "soulhome:soul_" + soul).tonic();

            assertTrue(tonic >= 45 && tonic <= 52, "tonic " + tonic + " is outside the range the pad was voiced for");

            if (tonic != previous)
            {
                distinctKeys++;
            }

            previous = tonic;
        }

        assertTrue(distinctKeys > 4, "sixteen souls with the same rooms landed in too few keys");
    }

    @Test
    @DisplayName("nothing pitched goes above G5, and nothing from C5 up is held (#261)")
    void nothingPiercingAndNothingHeldHigh()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            for (double rank : new double[] {0d, 1d})
            {
                final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), rank, 13L);

                for (int index = 0; index < PIECES; index++)
                {
                    final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

                    for (NoteEvent event : piece.events())
                    {
                        if (!event.instrument().pitched())
                        {
                            continue;
                        }

                        assertTrue(event.midi() <= SoulComposer.CEILING,
                                voice + " plays " + event.midi() + " on " + event.instrument());

                        if (event.midi() >= SoulComposer.HIGH_NOTE)
                        {
                            assertTrue(event.duration() <= SoulComposer.HIGH_NOTE_BEATS * piece.beat() + 1e-9d,
                                    voice + " holds " + event.midi() + " for " + event.duration() + "s on " + event.instrument());
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the tune sits where a voice would sing it, not up in the rafters")
    void leadsSitInTheMiddle()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, 21L);
            final List<Integer> pitches = new java.util.ArrayList<>();

            for (int index = 0; index < 10; index++)
            {
                for (NoteEvent event : SoulComposer.compose(brief, brief.pieceSeed(index)).events())
                {
                    if (event.instrument().pitched() && event.instrument() != Instrument.PAD && event.instrument() != Instrument.DRONE)
                    {
                        pitches.add(event.midi());
                    }
                }
            }

            pitches.sort(null);

            final int median = pitches.get(pitches.size() / 2);

            assertTrue(median >= 45 && median <= 70, voice + "'s tune has a median of MIDI " + median);
        }
    }

    @Test
    @DisplayName("a workshop is rhythmic: a hammer on one and three, the anvil on two and four, a driving bass")
    void theForgeKeepsTime()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.WROUGHT, 1d), 0.3d, 5L);
        final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(0));
        final double beat = piece.beat();

        int hammers = 0;
        int anvils = 0;
        int bass = 0;

        for (NoteEvent event : piece.events())
        {
            final double onBeat = event.start() / beat;
            final double offGrid = Math.abs(onBeat - Math.round(onBeat * 2d) / 2d);

            switch (event.instrument())
            {
                case THUMP ->
                {
                    hammers++;
                    assertTrue(offGrid < 1e-6d, "a hammer off the grid at beat " + onBeat);
                }
                case CLANK ->
                {
                    anvils++;
                    assertTrue(offGrid < 1e-6d, "an anvil off the grid at beat " + onBeat);
                }
                case PLUCK -> bass++;
                default ->
                {
                }
            }
        }

        final double bars = (piece.length() - SoulComposer.TAIL_SECONDS) / (4d * beat);

        assertTrue(piece.bpm() >= 90d, "a workshop at " + piece.bpm() + " bpm is the chill #261 complained of");
        assertTrue(hammers >= bars * 1.5d, hammers + " hammer strikes in " + bars + " bars");
        assertTrue(anvils >= bars, anvils + " anvil strikes in " + bars + " bars");
        assertTrue(bass >= bars * 6d, "the bass drives in eighths");
    }

    @Test
    @DisplayName("fire is quick notes in the middle of the range over a calm base, crackling the whole way")
    void theFireFlickers()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.WARM, 1d), 0.3d, 5L);
        final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(0));
        final double body = piece.length() - SoulComposer.TAIL_SECONDS;

        double crackling = 0d;
        int quick = 0;
        int lead = 0;

        for (NoteEvent event : piece.events())
        {
            if (event.instrument() == Instrument.CRACKLE)
            {
                crackling = Math.max(crackling, event.duration());
            }

            if (event.instrument() == Instrument.EPIANO)
            {
                lead++;

                if (event.duration() <= piece.beat())
                {
                    quick++;
                }

                // no lower than a fourth under the lead's own octave, no higher than the ceiling
                assertTrue(event.midi() >= piece.tonic() + 7 && event.midi() <= SoulComposer.CEILING,
                        "neither too high nor too low: " + event.midi() + " over a tonic of " + piece.tonic());
            }
        }

        assertTrue(crackling >= body * 0.9d, "the fire crackles under the whole piece");
        assertTrue(quick >= lead * 0.8d, quick + " of " + lead + " piano notes are quick");
        assertTrue(piece.events().stream().anyMatch(e -> e.instrument() == Instrument.PAD && e.duration() > 4d * piece.beat()),
                "over a calm base that holds");
    }

    @Test
    @DisplayName("every room's voice brings its own foley, and the place itself brings none")
    void everyRoomSoundsLikeItself()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.4d, 9L);
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(0));
            final boolean foley = piece.events().stream().anyMatch(event -> !event.instrument().pitched());

            assertEquals(voice != SoulVoice.BASE, foley, voice.name());
        }
    }

    @Test
    @DisplayName("a hearth soul with a workshop in it hears the odd anvil under its fire")
    void theRunnerUpBringsItsFoley()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.WARM, 0.7d, SoulVoice.WROUGHT, 0.3d), 0.4d, 9L);
        int anvils = 0;

        for (int index = 0; index < 20; index++)
        {
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

            if (piece.voice() == SoulVoice.WARM)
            {
                anvils += (int) piece.events().stream().filter(e -> e.instrument() == Instrument.CLANK).count();
            }
        }

        assertTrue(anvils > 0);
    }

    @Test
    @DisplayName("the moods keep their tempi apart: a workshop drives, a hearth is quick, an ossuary crawls")
    void tempiMatchTheMoods()
    {
        final double forge = MusicStyle.of(SoulVoice.WROUGHT).bpm();
        final double fire = MusicStyle.of(SoulVoice.WARM).bpm();
        final double cold = MusicStyle.of(SoulVoice.COLD).bpm();
        final double hollow = MusicStyle.of(SoulVoice.HOLLOW).bpm();

        assertTrue(forge > fire && fire > cold && cold > hollow);
    }

    @Test
    @DisplayName("the lead lands on the chord where it leans hardest")
    void snapFindsAChordTone()
    {
        for (int chord = 0; chord < 7; chord++)
        {
            for (int degree = -7; degree < 14; degree++)
            {
                final int snapped = SoulComposer.snapToChord(degree, chord);
                final int relative = Math.floorMod(snapped - chord, 7);

                assertTrue(relative == 0 || relative == 2 || relative == 4);
                assertTrue(Math.abs(snapped - degree) <= 2, "snapping moved a note further than a third");
            }
        }
    }

    private static double meanVelocity(List<NoteEvent> events)
    {
        double total = 0d;
        int count = 0;

        for (NoteEvent event : events)
        {
            if (event.instrument().pitched())
            {
                total += event.velocity();
                count++;
            }
        }

        return count == 0 ? 0d : total / count;
    }

    private static SoulCharacter character(Object... traitsAndPulls)
    {
        final Map<SoulTrait, Double> pulls = new EnumMap<>(SoulTrait.class);

        for (int index = 0; index < traitsAndPulls.length; index += 2)
        {
            pulls.put((SoulTrait) traitsAndPulls[index], (Double) traitsAndPulls[index + 1]);
        }

        return new SoulCharacter(pulls);
    }
}
