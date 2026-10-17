# The sample bank

Builds the instrument bank the soul's music plays from: `src/main/resources/assets/soulhome/soul_music/`
- a `bank.json` manifest, one `.ogg` per zone under a folder per instrument, and a `SOURCES.md`
saying where every zone came from. The game reads those files through `SampleBank`; nothing here runs
at game time, and the build and CI never run it.

```sh
python3 -m pip install -r tools/soul_music/requirements.txt
python3 tools/soul_music/fetch_sources.py /path/to/sources   # two CC0 libraries (sparse, ~2.5 GB) and Freesound
python3 tools/soul_music/build_bank.py --sources /path/to/sources
git status --short src/main/resources/assets/soulhome/soul_music
```

The last line is the check. A rebuild from the pinned commits is byte-identical, so after one nothing
should be modified; a `SOURCES.md` that is new is the only thing a change to the script should add.

## The files

| | |
| --- | --- |
| `fetch_sources.py` | Blobless, sparse clones of VSCO-2-CE and VCSL at the commits pinned in it, with the folders listed for them. Also fetches the Freesound recordings the builds read into `<sources>/freesound/`, each one checked as CC0 on its own page. Run it again and it fills in what is missing. |
| `build_bank.py` | Every decision about the bank: which recording plays which note, its key range and velocity layer, its loop, its level. Writes the manifest, the zones and `SOURCES.md`. |
| `audio.py` | Trimming, tuning, looping, filtering and encoding a recording. No sound is made here; it is the helpers `build_bank.py` and `tools/ambience/mix.py` share. |
| `requirements.txt` | numpy, scipy and soundfile. ffmpeg is not a pip package: it must be on `PATH`, built with libvorbis (`ffmpeg -encoders | grep libvorbis` should print a line). |

Python 3.9 or later. The bank is built with ffmpeg rather than libsndfile's Vorbis encoder because the
rebuild has to be byte-identical, and ffmpeg's bit-exact mode makes that true for one libvorbis version.
A different libvorbis can encode the same samples to different bytes; the samples are what do not change.

## Why recordings

The soul's music used to be oscillators - a sine with a filter sweep standing in for a bow, a sine
with a partial standing in for a mallet - and it sounded like them. A recorded harp, clarinet or marimba
does not have to be made to sound real, so the bank is recorded instruments and the composer is unchanged:
`SoulComposer` still writes every note, in the soul's own mode, and the bank only decides what each note
sounds like.

The foley is recorded too, and it is a recording of the thing rather than an instrument made to stand in for
it: an anvil's hammer, a clock's own ticks, a wood stove, steam from a valve, wind, drips, a bird. Those are
Freesound recordings (see the rules below). A fire assembled from clave strikes sounded like claves, which
was the first attempt.

The oscillator recipes in `SoulSynth` are still there and still play any instrument the bank does not
have. A missing or broken bank is a quieter-sounding soul, never a silent one.

## Rules

- **Only CC0 sources.** `build_bank.py` reads VSCO-2-CE and VCSL, both CC0 1.0, and the Freesound
  recordings described below, each checked as CC0 on its own page. Another source goes in only if it is
  CC0 too, is pinned or listed in `fetch_sources.py`, and is written into `SOURCES.md` by the build.
  A CC-BY recording is an attribution the mod would owe and is not put in here.
- **Freesound recordings are checked when fetched, and listed by id.** The foley `clank`, `tick`, `hiss`,
  `wind`, `drip`, `chirp`, `rustle` and `crackle` read Freesound sounds by id: `fsnd(<id>)` in
  `build_bank.py`, and the two `freesound/<id>.ogg` paths that `crackle` assembles its stove and fire from.
  `fetch_sources.py` takes each sound's HQ preview (the `-hq.ogg` of the page's preview link, not the
  `-lq` one), but only from a page whose licence link is `creativecommons.org/publicdomain/zero/`; a page
  that links anything else is refused, nothing is written for it, and the run exits non-zero. Each
  recording is saved as `<id>.ogg` with a `<id>.json` beside it holding its title, author, page and licence,
  and `SOURCES.md` credits the author from that file. Every id a script reads must be in `FREESOUND_IDS`;
  the run checks that before it downloads anything, and names the line that reads a missing one.
- **Freesound previews are not pinned.** A library is pinned to a commit; a preview is whatever Freesound
  serves at the time, so if a preview is re-encoded upstream, the bank built from it is a different bank,
  and `git status` after the rebuild is where that shows.
- **Commits are pinned.** A rebuild from a moved `main` is a different bank. The build refuses a git
  checkout that is not at the pinned commit.
- **Never hand-edit `bank.json` or a zone.** Change the `Spec` in `build_bank.py` and rebuild. The
  manifest's contract is the javadoc on `SampleBank`.
- **Foley is not pitched.** `thump`, `clank`, `tick`, `hiss`, `wind`, `drip`, `chirp`, `rustle`, `swell` and
  `crackle` are `track_pitch: false`. A hammer on an anvil or a clock's tick has no note, and a pitched copy
  of one would be out of tune with the soul's key; the composer places them on the music's grid instead.
- **Levels are calibrated against the oscillators, then by ear.** Each `level` was first matched so its
  stem, rendered by `MusicPreview`, measured what the oscillator it replaced measured - that keeps the
  balance `SoulSynthTest` was written against. To re-check after a change:

  ```sh
  sh gradlew musicPreview -PpreviewArgs="--only stems --no-wav"
  ```

  Then move a `level`, rebuild, and listen. The `gain` of each zone is set from the recording and is not
  the thing to tune; the `level` on the instrument is.
- **The pitch is measured, and only nudged.** A pitched zone is retuned to its name's pitch when the
  measured fundamental is between 3 and 45 cents off. Less than that is not worth touching, and more is
  more likely a misread note than a sample out of tune, so it is left as recorded.

## When the bank is wrong

- A zone that clicks at its loop point: check the `loop` on its `Spec` against the recording's sustain,
  and the crossfade. `audio.make_loop` crossfades the material before the loop end with the material
  before the loop start, so the wrap lands on audio that was already there.
- An instrument that sounds too loud next to the rest: its `level`, not its zones.
- A bank that does not load in the game: `SoulSampleBankLoader` logs "Soul music samples did not load, so
  the soul plays from its oscillators instead" with the cause, and the soul plays the oscillators.
