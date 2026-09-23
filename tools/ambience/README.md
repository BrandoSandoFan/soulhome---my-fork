# The ambience generator

Renders every `.ogg` under `src/main/resources/assets/soulhome/sounds/ambience` — the soul's
one-shots (#209) and the two rank layers of its ambient bed (#210). The output is committed and is
what ships; the game never runs any of this.

```sh
python3 -m pip install -r tools/ambience/requirements.txt
python3 tools/ambience/generate.py
```

Twenty seconds, and it overwrites the assets in place. `git status` after a re-render with an
unchanged script and seed is empty — see **Determinism** below, which is a property this is built
to have rather than a hope.

## Why there is a generator at all

#213's argument, which is the right one: a soul's sound is a blend of continuous quantities — a rank
fraction, three axis leanings, three tensions. Recorded audio gives one file per voice, and a blend
then has to be faked by picking between files. A generator takes the quantities as parameters and
renders the sound *for* that blend, which is the same thing `SoulAxis` does for colour and for the
same reason: a soul that is warm and cold at once is steam, and steam is not the average of a
crackle and a chime.

It also means the next person tuning this changes one number and re-renders the set, consistently,
instead of hunting for thirty new recordings that sit together.

The starting point was the procedural-audio chapter of *Procedural Generation in Video Games*
(Short & Adams, eds.) rather than a synthesiser tutorial, and the one piece of its advice that
shaped this most is negative: the failure mode of procedural ambience is not that it sounds cheap,
it is that it sounds *periodic*. Everything here that looks fussy — Poisson-distributed grain
onsets, LFO rates chosen to share no small whole-number ratio, a crossfade folded back over the
loop's own head — is that one warning being taken seriously.

## Why offline and not at runtime

Synthesising into OpenAL buffers on the fly would buy a sound unique to each soul, at the price of
a pipeline nobody can listen to before it ships. The whole open question on #166 is *taste*, and
taste is judged by playing a file. Rendering offline to a grid of parameter values keeps almost all
of the benefit and stays auditable: the assets in the tree are exactly what a player hears.

## What it renders

| | |
| --- | --- |
| `<voice>_1..3.ogg` | 30 one-shots, three variants for each of the ten `SoulVoice`s, so vanilla's own random pick out of `sounds.json` supplies the variety |
| `bed_close.ogg`, `bed_open.ogg` | the two rank layers of the bed, 68-second seamless loops |
| `SOURCES.md` | written by the generator: what each file is and the checksum of the samples behind it |
| `../suppression/drone.ogg`, `throb.ogg` | suppression's field and one beat of its ring count (#188), with a `SOURCES.md` of their own - see `suppression.py` |

`--set ambience` or `--set suppression` renders one set and leaves the other's files untouched.

All mono (a positional sound in Minecraft has to be), 44.1 kHz, Vorbis. One-shots are mastered to
−20 dBFS RMS with their transients limited to −3 dBFS peak (`dsp.limit`), the rank beds to −24 and
the character beds to −28, so the mod's own volume knob is choosing how loud the soul sits against
the game rather than making up for assets that disagree with each other.

They used to be −29, −38 and −46. That was chosen by measurement alone, and the first hour of real
building with it (#163) found the whole ambience quieter than Minecraft's music - after the mod's
knobs and four to eleven blocks of distance, the bed reached the ear around −58 dBFS. Quiet is a
property to have relative to the game, not in absolute terms, and the knobs are where it is set.

## The files

- `dsp.py` — the signal-processing vocabulary. Knows nothing about souls.
- `voices.py` — what each voice is *made of*. This is the file to read, and the file to change if
  something sounds wrong.
- `generate.py` — the driver: seeds, lengths, encoding, `SOURCES.md`.

The rule `voices.py` exists to keep is the one in its own docstring: **the contested voices are
built from both poles' generators, not from a mix of their outputs.** Steam is not warm plus cold —
the warm generator's grain envelope is what opens the hiss, and the cold generator's partials are
damped down to the ring inside it. Break that and the sound starts saying "this soul cannot make up
its mind", which is the one thing the mod never says about a soul.

## Determinism

Each asset draws from a generator seeded by the master seed **and its own name**, so adding an
eleventh voice re-renders one file and leaves the other thirty-one byte-identical.

Two things had to be dealt with to make "byte-identical" true rather than nearly true:

- **The encoder is handed integers, not floats.** `generate.py` rounds to 16-bit PCM itself, so
  "the same samples" means the same integers on every machine.
- **libvorbis stamps a random stream serial into every Ogg page.** `canonicalise` rewrites it to a
  fixed value and recomputes each page's CRC. Without it, two renders of identical audio are two
  different files, and the promise would be false for a reason that has nothing to do with sound.

What survives a change of libsndfile version is the PCM, and that is what `SOURCES.md` records a
checksum of. The `.ogg` bytes are whatever libvorbis makes of those samples.

## Two things found the hard way

- **libsndfile's Vorbis encoder segfaults** on a single write much past thirty seconds. No Python
  traceback, just a dead process — so the writes here are blocked at five seconds. The next person
  to lengthen a bed would otherwise go looking for it in the synthesis.
- **Ogg's CRC32 is not `zlib`'s.** Same polynomial, but unreflected and without the initial and
  final inversions. Reaching for the familiar one produces files every player rejects.

## Re-rendering is not checked by CI

`runData` is checked on every build because ForgeGradle is already set up by then and the check is
free. This is not: it would mean numpy and libsndfile in the build image for a directory that
changes once a year. What CI does check is the half that actually goes wrong — `AmbienceAssetsTest`
fails the build if a `SoulVoice` has no sound event, or an event names a file that is not in the
tree. An unshipped asset fails the build rather than the player's ears.
