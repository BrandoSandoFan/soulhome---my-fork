# The ambience

Mixes every `.ogg` under `src/main/resources/assets/soulhome/sounds/ambience` - the soul's one-shots, the
two rank beds and the nine character beds - out of recordings: field recordings of places and sound effects
from Freesound, and the instruments of two CC0 libraries. The output is committed and is what ships; the
game never runs any of this.

```sh
python3 -m pip install -r tools/ambience/requirements.txt
python3 tools/soul_music/fetch_sources.py /path/to/sources   # the same fetch as the bank: two CC0 libraries and Freesound
python3 tools/ambience/mix.py --sources /path/to/sources
```

It overwrites the assets in place, and rewrites `SOURCES.md` beside them. `git status` after a re-run
with unchanged sources and an unchanged script is empty - see **Determinism** below.

The suppression cues (#188) are the one sound this directory still synthesises, and `generate.py` renders
them into their own folder. See **Suppression** below.

## What it mixes

| | |
| --- | --- |
| `<voice>_1..3.ogg` | 30 one-shots, three variants for each of the ten `SoulVoice`s, so vanilla's own random pick out of `sounds.json` supplies the variety |
| `bed_close.ogg`, `bed_open.ogg` | the two rank layers of the bed, 68-second seamless loops |
| `character_<voice>.ogg` | nine character layers, one per non-`base` voice, streamed continuously under the rank layers |
| `SOURCES.md` | written by `mix.py`: which library each file was built from, its length, levels and a checksum of its samples |

Every file is built from a recording of the thing it is. The rank beds are places - a still night with a
breeze and crickets for `bed_close`, mountain wind at night for `bed_open` - and each character layer is one
place: a wood stove, arctic wind, a hot spring, a blacksmith's, a pendulum clock, a dawn wood, a dripping
cave, frogs at night. The one-shots are sound effects: a flame catching, ice cracking, a hammer on an anvil,
a clock being wound. The one thing that is played is `arcane`, which ends in a soft harp figure, because
nothing records enchantment. `mix.py`'s `DESCRIPTIONS` and `CHARACTER_DESCRIPTIONS` say what each one is made
of, and `SOURCES.md` says which recordings went into it. The choices of recording, placement and room are the
mod's own; the recordings are CC0.

`--only warm_2,bed_close` re-mixes just those files and leaves `SOURCES.md` alone, which is how to hear
one change without a full run.

All mono (a positional sound in Minecraft has to be), 44.1 kHz, Ogg Vorbis at quality 5, encoded by ffmpeg
with libvorbis. Mastered to the same targets the Java was balanced against:

- one-shots to −20 dBFS RMS, transients held under −3 dBFS peak (`level`, which limits and re-levels
  twice, so the delivered RMS is the target and not "the target minus whatever the loudest strike cost")
- rank beds to −24 dBFS RMS, peaks under −8
- character beds to −28 dBFS RMS

Turn a target here rather than adding a gain stage in the game. The first real hour of building under
#163 found the whole ambience quieter than Minecraft's own music: the bed had been mastered 38 dB down and
then multiplied by three more ceilings in Java, so it reached the ear around −58 dBFS against music at
about −20. Quiet is a property to have relative to the game, and these numbers are where it is set.

## Why recordings, and why the synthesis went

#213 rendered the ambience from parameters, so that a soul's blend could be a sound rather than a pick
between files. That was the right instinct and the wrong medium. The synthesised sounds sounded synthesised, and
the question the whole epic turns on - does it sound like a
soul - is only answered by listening. A recording does not have to be made to sound real, which is most
of the case for it.

Real things are recorded, not imitated. A fire, an anvil, a clock or a bird is a Freesound recording of that
thing: a fire assembled from clave strikes sounded like claves, which is what the first attempt at `warm` did.
Only enchantment, which nothing records, is played.

The rule that survived the change is the one from #213's voices: a contested voice is one sound, not two
finished sounds played together. Steam's one-shot is a single recording of a valve letting go, and its
character layer is a hot spring, not a warm sound and a cold one under each other. Break that and the sound
says "this soul cannot make up its mind", which is the one thing the mod never says about a soul.

## Freesound

Most of the recordings are Freesound sounds, read by id (`fs(<id>)` in `mix.py`) from
`<sources>/freesound/<id>.ogg`. `fetch_sources.py` fetches each one's HQ preview from its own page, and
refuses any page whose licence link is not CC0 (`creativecommons.org/publicdomain/zero/`); `FREESOUND_IDS`
lists them, and the run checks that every id `mix.py` and `build_bank.py` read is in that list before it
downloads anything. `SOURCES.md` credits each author from the sound's `.json`, and lists the recordings by id.

Previews are what is fetched, not the originals, and they are not pinned the way the libraries are. A sound
Freesound re-encodes is a different ambience, and `git status` after the rebuild is where that shows.

## Determinism

The one-shots are deterministic functions of their recordings. The beds and character layers draw their
random placement from the master seed **and their own name**, so changing one leaves the others
byte-identical. Each file is encoded through ffmpeg in
bit-exact mode, and its checksum in `SOURCES.md` is of the 16-bit PCM handed to the encoder, rounded the
same way `build_bank.py` rounds its own. The checksum is the guarantee that would catch a real change; the
`.ogg` bytes are whatever one libvorbis makes of those samples.

## Suppression

`generate.py` renders suppression's two cues (#188) and nothing else:

```sh
python3 tools/ambience/generate.py                              # writes sounds/suppression
python3 tools/ambience/generate.py --suppression-out /tmp/check # writes somewhere else
```

- `drone.ogg` - the field around a suppressed player, a seamless loop. Their rank.
- `throb.ogg` - one beat of the ring count. Your legibility.

What they are made of, and why neither is an ambience voice, is in `suppression.py`. `dsp.py` is the small
signal vocabulary it uses. `--set ambience` and `--set all` are refused with an error: the ambience is
`mix.py`'s, and a second writer under the same file names would decide the result by whichever ran last.

`generate.py` writes `.ogg` with libsndfile, and `canonicalise` rewrites each page's stream serial and CRC
so that re-rendering is a diff of nothing. Ogg's CRC32 is not `zlib`'s - same polynomial, but unreflected
and with no initial or final inversion - and reaching for the familiar one produces files every player
rejects. The Vorbis encoder also segfaults on a single write much past thirty seconds, with no Python
traceback, so writes are blocked at five seconds.

## Re-rendering is not checked by CI

`runData` is checked on every build because ForgeGradle is already set up by then and the check is free.
This is not: it would mean numpy, scipy and ffmpeg in the build image for a directory that changes a few
times a year. What CI does check is the half that actually goes wrong - `AmbienceAssetsTest` fails the
build if a `SoulVoice` has no sound event, or an event names a file that is not in the tree. An unshipped
asset fails the build rather than the player's ears.
