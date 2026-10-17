# File created ~ 17 - 10 - 2026
"""
Mixes the soul's ambience out of real recordings (#163, replacing the synthesis of #213).

    python3 tools/soul_music/fetch_sources.py /path/to/sources
    python3 tools/ambience/mix.py --sources /path/to/sources

writes the same files ``generate.py`` used to, under the same names, so ``sounds.json`` and every
line of Java that plays them are untouched:

* ``<voice>_1..3.ogg`` - one-shots: recorded sound effects (a flame catching, ice cracking, a
  hammer on an anvil, a clock being wound), placed in a room. Sound effects, not instruments
  imitating them - a fire assembled from clave strikes was the first attempt, and sounded like claves.
* ``bed_close.ogg``, ``bed_open.ogg`` - the rank layers: field recordings of a still breeze and of
  wind across a mountain at night. Places, not textures - the first beds were washes of ocean drum
  and cymbal, and a minute of that is white noise.
* ``character_<voice>.ogg`` - the nine character layers, one place each: a wood stove, arctic wind,
  a hot spring, a blacksmith's, a pendulum clock, a dawn wood, a dripping cave, frogs at night.

The field recordings and effects are CC0 sounds from Freesound, each checked as CC0 on its own page
when fetched and credited in ``SOURCES.md``; the harp, timpani and bass drum are from VSCO-2 and
VCSL. The suppression set is still ``generate.py --set suppression``; nothing here touches it.

Everything is mono, because vanilla only places mono sounds in the world, and mastered to the
targets the Java was balanced against (#163's playtest): one-shots -20 dBFS RMS with peaks under
-3, the rank beds -24, the character layers -28. Turn a target here rather than adding a gain
stage in the game.
"""
from __future__ import annotations

import argparse
import hashlib
import sys
from datetime import date
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'soul_music'))

import audio  # noqa: E402
from audio import RATE  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/soulhome/sounds/ambience'
SEED = 0x50_4C_4B_21

ONE_SHOT_RMS = -20.0
ONE_SHOT_PEAK = -3.0
BED_RMS = -24.0
CHARACTER_RMS = -28.0
BED_PEAK = -8.0

BED_SECONDS = 68.0
BED_OVERLAP = 6.0

VSCO = 'VSCO-2-CE'
VCSL = 'VCSL'
SRC: Path = Path('.')


def rng_for(name: str) -> np.random.Generator:
    digest = hashlib.sha256(f'{SEED}:{name}'.encode()).digest()
    return np.random.default_rng(int.from_bytes(digest[:8], 'big'))


_cache: dict[str, np.ndarray] = {}
USED: set[int] = set()


def rec(rel: str) -> np.ndarray:
    """One recording, mono, with its leading silence cut. Cached: the beds reuse a handful a lot."""
    if rel not in _cache:
        _cache[rel] = audio.trim_head(audio.highpass(audio.load(SRC / rel), 30.0))
    if rel.startswith('freesound/'):
        USED.add(int(Path(rel).stem))
    return _cache[rel]


def note(rel: str, semis: float = 0.0, length: float | None = None, gain_db: float = 0.0,
         fade: float = 0.3, reverse: bool = False) -> np.ndarray:
    x = rec(rel)
    if semis:
        x = audio.shift(x, semis)
    if length is not None:
        x = audio.trim_tail(x, length, fade=min(fade, length / 2))
    x = x / max(audio.peak(x), 1e-9) * audio.undb(gain_db)
    if reverse:
        x = audio.fade_in(x[::-1], 0.05)
    return x


def place(seconds: float, *parts: tuple[float, np.ndarray]) -> np.ndarray:
    y = np.zeros(int(seconds * RATE))
    for at, x in parts:
        a = int(at * RATE)
        if a >= len(y):
            continue
        n = min(len(x), len(y) - a)
        y[a:a + n] += x[:n]
    return y


ROOMS = {
    'near': audio.impulse_response(1.4, 0.9, 5200.0, 11, predelay=0.008),
    'hall': audio.impulse_response(3.6, 2.6, 3800.0, 23, predelay=0.018),
    'vast': audio.impulse_response(7.0, 5.5, 2600.0, 37, predelay=0.035, early=4),
}


def room(x: np.ndarray, name: str, wet: float, seconds: float) -> np.ndarray:
    y = audio.reverb(x, ROOMS[name], wet)[: int(seconds * RATE)]
    if len(y) < int(seconds * RATE):
        y = np.concatenate([y, np.zeros(int(seconds * RATE) - len(y))])
    return y


def master_one_shot(x: np.ndarray) -> np.ndarray:
    x = audio.highpass(x, 45.0, 3)
    x = audio.fade_in(x, 0.02)
    n = int(0.12 * RATE)
    x[-n:] *= 0.5 * (1.0 + np.cos(np.linspace(0.0, np.pi, n)))
    return level(x, ONE_SHOT_RMS, ONE_SHOT_PEAK)


def level(x: np.ndarray, rms_target: float, peak_db: float) -> np.ndarray:
    """Normalise, limit, and correct for what the limiter took - twice, so the delivered RMS is the
    target and not "the target minus whatever the loudest strike cost", which is how the synthesised
    wrought one-shots ended up 2.5 dB short."""
    y = x
    for _ in range(6):
        if abs(audio.rms_db(y) - rms_target) < 0.25:
            break
        y = y * audio.undb(rms_target - audio.rms_db(y))
        # A fast release on the later passes is light compression, which is what a clock's ticks or
        # a fire's pops need to reach the same loudness as a sustained sound without a louder peak.
        y = audio.limit(y, peak_db, release=0.12 if _ == 0 else 0.04)
    return y


# --- one-shots --------------------------------------------------------------------------------

HARP = f'{VSCO}/Strings/Harp'
TIMPANI = f'{VCSL}/Membranophones/Struck Membranophones/Timpani 1/Hit'
BASSDRUM = f'{VCSL}/Membranophones/Struck Membranophones/Bass Drum 2'


def fs(sound_id: int) -> str:
    """A Freesound recording, by id. Every one is CC0, checked on its page when fetched, and listed
    with its author in SOURCES.md."""
    return f'freesound/{sound_id}.ogg'


def loudest(x: np.ndarray, length: float, lead: float = 0.15, skip: float = 0.0, nth: int = 0) -> np.ndarray:
    """The `length` seconds of a recording around its nth-loudest transient, starting `lead` before it.

    How a pop, a crack or a strike is found inside a long take: by the take's own envelope, so a
    re-fetch of the same recording picks the same moment.
    """
    env = audio.envelope_db(audio.highpass(x, 300.0), 0.01)
    env[: int(skip * RATE)] = -200.0
    env[len(env) - int(length * RATE):] = -200.0
    for _ in range(nth):
        k = int(np.argmax(env))
        env[max(0, k - int(0.5 * RATE)): k + int(0.5 * RATE)] = -200.0
    k = int(np.argmax(env))
    a = max(0, k - int(lead * RATE))
    return x[a:a + int(length * RATE)].copy()


def clip(rel: str, length: float, gain_db: float = 0.0, fade: float = 0.3, at: float | None = None,
         nth: int = 0, lead: float = 0.15, semis: float = 0.0) -> np.ndarray:
    """A piece of a recording: from `at` seconds, or around its nth-loudest moment."""
    x = rec(rel)
    if semis:
        x = audio.shift(x, semis)
    if at is None:
        y = loudest(x, length, lead=lead, nth=nth)
    else:
        y = x[int(at * RATE): int((at + length) * RATE)].copy()
    y = audio.fade_in(y, 0.005)
    n = min(int(fade * RATE), len(y) // 2)
    if n:
        y[-n:] *= 0.5 * (1.0 + np.cos(np.linspace(0.0, np.pi, n)))
    return y / max(audio.peak(y), 1e-9) * audio.undb(gain_db)


# --- one-shots --------------------------------------------------------------------------------
#
# Recorded sound effects, not instruments imitating them: the first version built a fire out of
# clave transients and a forge out of an anvil sample under a timpani, and a fire built from claves
# sounds like claves. Only arcane keeps a played instrument, because nothing in the world records
# the sound of enchantment and a soft harp figure is the closest an orchestra comes.

def base(v: int) -> np.ndarray:
    # The place breathing: one gust of mountain wind, taken from a different minute of the take each
    # time, in a room large enough that it arrives rather than blows.
    take = [fs(165526), fs(181252), fs(165526)][v]
    gust = audio.lowpass(clip(take, 4.3, -2.0, fade=1.6, nth=[2, 1, 6][v], lead=1.6), 3500.0)
    return room(audio.fade_in(gust, 1.2), 'hall', 0.4, 4.5)


def warm(v: int) -> np.ndarray:
    # A fire catching, or a log settling in it: a flame's burst, and two pops out of real fires.
    if v == 0:
        x = audio.fade_in(clip(fs(828434), 2.9, -1.0, fade=1.0, at=0.0), 0.08)
    else:
        x = clip([fs(532191), fs(414767)][v - 1], 2.9, -1.0, fade=1.2, nth=[0, 1][v - 1], lead=0.25)
        x = audio.lowpass(x, 7000.0)
    return room(x, 'near', 0.2, 3.0)


def cold(v: int) -> np.ndarray:
    # Ice giving way, in a large, cold room.
    take = [fs(342546), fs(329744), fs(422623)][v]
    x = audio.lowpass(clip(take, 3.4, -1.0, fade=1.2, lead=0.2), 8000.0)
    return room(x, 'hall', 0.4, 4.0)


def steam(v: int) -> np.ndarray:
    # A valve letting go.
    take = [fs(365071), fs(234782), fs(398077)][v]
    x = audio.bandpass(clip(take, 2.9, -1.0, fade=1.0, lead=0.05), 250.0, 6500.0)
    return room(x, 'near', 0.25, 3.4)


def arcane(v: int) -> np.ndarray:
    # Something arriving: a cymbal played backwards swells into a short harp figure. The chime and
    # bell tree the first version had are gone - a soul should not ding at its owner.
    take = [fs(383903), fs(110218), fs(9523)][v]
    swell = audio.lowpass(clip(take, 2.0, -5.0, fade=0.15, nth=0, lead=1.85), 6000.0)
    swell = audio.fade_in(swell, 0.6)
    figures = [['KSHarp_C3_mf.wav', 'KSHarp_E3_mf.wav', 'KSHarp_G3_mf.wav'],
               ['KSHarp_C3_mf.wav', 'KSHarp_G3_mf.wav', 'KSHarp_D4_mf.wav'],
               ['KSHarp_A2_mf.wav', 'KSHarp_E3_mf.wav', 'KSHarp_B3_mf.wav']][v]
    parts = [(0.0, swell)]
    for i, f in enumerate(figures):
        parts.append((1.9 + 0.13 * i, audio.lowpass(note(f'{HARP}/{f}', 0.0, 2.2, -7.0 - 2 * i, fade=1.0), 4500.0)))
    return room(place(4.2, *parts), 'vast', 0.5, 4.2)


def wrought(v: int) -> np.ndarray:
    # A hammer on an anvil, with a timpani's weight under it as the stone it stands on.
    take = [fs(270588), fs(270589), fs(521972)][v]
    hit = clip(take, 1.6, -1.0, fade=0.6, lead=0.01)
    timp = ['Timpani1_Hit_v2_rr1_Sum.wav', 'Timpani3_Hit_v2_rr1_Sum.wav', 'Timpani5_Hit_v2_rr1_Sum.wav'][v]
    x = place(3.2, (0.0, hit), (0.0, audio.lowpass(note(f'{TIMPANI}/{timp}', -3.0, 2.8, -10.0, fade=1.2), 500.0)))
    return room(x, 'near', 0.25, 3.2)


def quickened(v: int) -> np.ndarray:
    # A mechanism being wound.
    take = [fs(414260), fs(734580), fs(586150)][v]
    x = clip(take, 3.6, -1.0, fade=1.0, at=[0.0, 0.0, None][v], lead=0.4)
    return room(audio.lowpass(x, 7000.0), 'near', 0.3, 4.0)


def verdant(v: int) -> np.ndarray:
    # A bird somewhere in the soul, and the leaves it left.
    bird = [fs(233255), fs(233254), fs(812033)][v]
    leaves = [fs(437356), fs(106114), fs(443065)][v]
    x = place(3.4, (0.3, clip(bird, 1.0, -3.0, fade=0.3, lead=0.02)),
              (0.0, audio.lowpass(clip(leaves, 2.8, -9.0, fade=1.0, lead=0.3), 6000.0)))
    return room(x, 'near', 0.3, 3.4)


def hollow(v: int) -> np.ndarray:
    # Old wood taking a weight, a long way off, and one heartbeat under it.
    creak = [fs(377552), fs(502507), fs(502507)][v]
    drum = ['bassdrum_hit_pp1.wav', 'bassdrum_hit_pp2.wav', 'bassdrum_hit_pp1.wav'][v]
    x = place(4.2, (0.0, audio.lowpass(clip(creak, 2.6, -2.0, fade=1.0, nth=[0, 0, 2][v], lead=0.1), 4000.0)),
              (0.6, note(f'{BASSDRUM}/{drum}', 0.0, 2.4, -8.0, fade=1.0)))
    return room(x, 'vast', 0.5, 4.2)


def overgrown(v: int) -> np.ndarray:
    # Something growing through something dead: leaves moving, and water finding its way down.
    leaves = [fs(106114), fs(437356), fs(443065)][v]
    drop = [fs(174718), fs(166325), fs(533146)][v]
    x = place(4.0, (0.0, audio.lowpass(clip(leaves, 3.0, -4.0, fade=1.2, lead=0.3), 5000.0)),
              (1.2 + 0.3 * v, clip(drop, 0.8, -6.0, fade=0.3, lead=0.02)))
    return room(x, 'hall', 0.35, 4.0)


VOICES = {'base': base, 'warm': warm, 'cold': cold, 'steam': steam, 'arcane': arcane, 'wrought': wrought,
          'quickened': quickened, 'verdant': verdant, 'hollow': hollow, 'overgrown': overgrown}

DESCRIPTIONS = {
    'base': 'a gust of mountain wind (Freesound)',
    'warm': 'a flame bursting, or a pop out of a real fire (Freesound)',
    'cold': 'ice cracking (Freesound)',
    'steam': 'a burst of steam (Freesound)',
    'arcane': 'a reversed cymbal (Freesound) into a soft harp figure (VSCO-2)',
    'wrought': 'a hammer on an anvil (Freesound) over a lowpassed timpani (VCSL)',
    'quickened': 'a clockwork mechanism being wound (Freesound)',
    'verdant': 'a bird chirp and rustling leaves (Freesound)',
    'hollow': 'creaking wood far off (Freesound) and a soft bass drum (VCSL)',
    'overgrown': 'rustling leaves and a water drop (Freesound)',
}


# --- beds -------------------------------------------------------------------------------------
#
# Each bed is a field recording of a real place, not a texture assembled from instrument noise:
# the first version washed ocean-drum and cymbal recordings into one another, and what that
# sounds like for a minute at a time is white noise. A place has events in it - a log shifting, a
# drip, a bird, a gust - and that is what makes it a place.

def seamless(x: np.ndarray, seconds: float, overlap: float) -> np.ndarray:
    """Fold the tail past the loop point back over the head, equal power, so the loop has no seam.
    Vanilla loops the whole file and has no loop points of its own."""
    n, m = int(seconds * RATE), int(overlap * RATE)
    y = x[:n].copy()
    t = np.linspace(0.0, np.pi / 2.0, m)
    y[:m] = y[:m] * np.sin(t) + x[n:n + m] * np.cos(t)
    return y


def take(rel: str, seconds: float, start: float = 4.0) -> np.ndarray:
    """`seconds` of one recording. A take shorter than that is joined to a later stretch of itself
    with a long crossfade, rather than looped on itself mid-event."""
    x = rec(rel)
    n = int(seconds * RATE)
    a = int(start * RATE)
    if len(x) - a >= n:
        return x[a:a + n].copy()
    xfade = int(3.0 * RATE)
    out = x[a:].copy()
    pos = int(0.5 * len(x))
    while len(out) < n:
        nxt = x[pos:].copy()
        t = np.linspace(0.0, np.pi / 2.0, xfade)
        head = out[-xfade:] * np.cos(t) + nxt[:xfade] * np.sin(t)
        out = np.concatenate([out[:-xfade], head, nxt[xfade:]])
        pos = int(0.25 * len(x)) if pos != int(0.25 * len(x)) else int(0.5 * len(x))
    return out[:n]


def scatter(seconds: float, rng: np.random.Generator, every: tuple[float, float], make) -> np.ndarray:
    """Occasional events at irregular intervals, as a place has them."""
    y = np.zeros(int(seconds * RATE) + 10 * RATE)
    t = rng.uniform(*every) * rng.random()
    while t < seconds:
        x = make(rng)
        a = int(t * RATE)
        y[a:a + len(x)] += x[: len(y) - a]
        t += rng.uniform(*every)
    return y[: int(seconds * RATE)]


def bed(make, rms: float) -> np.ndarray:
    total = BED_SECONDS + BED_OVERLAP
    x = make(total)[: int(total * RATE)]
    x = audio.highpass(x, 40.0, 3)
    return level(seamless(x, BED_SECONDS, BED_OVERLAP), rms, BED_PEAK)


def bed_close(seconds: float) -> np.ndarray:
    # Close: a small place on a still night. A gentle breeze, darkened until it is felt more than
    # heard, and crickets somewhere past the edge of the island so it is never only weather.
    breeze = audio.lowpass(take(fs(181252), seconds, 6.0), 1800.0, 2)
    crickets = audio.lowpass(take(fs(333221), seconds, 12.0), 6000.0)
    crickets *= audio.undb(audio.rms_db(breeze) - audio.rms_db(crickets) - 9.0)
    return breeze + crickets


def bed_open(seconds: float) -> np.ndarray:
    # Open: the soul gone huge. Wind singing across a mountain top at night, with crickets a long way
    # below - felix.blume's recording, which is a place rather than a weather.
    return audio.lowpass(room(take(fs(165526), seconds, 20.0), 'vast', 0.35, seconds), 6000.0)


def character_warm(seconds):
    # A wood stove, close, with a second fire further off filling in between its pops.
    near = take(fs(532191), seconds)
    far = audio.lowpass(take(fs(414767), seconds), 4000.0)
    return near + far * 0.35


def character_cold(seconds):
    return audio.lowpass(take(fs(117136), seconds), 5000.0)


def character_steam(seconds):
    # A hot spring bubbling, with a boiler's rumble under it.
    spring = take(fs(414762), seconds, 30.0)
    boil = audio.lowpass(take(fs(398684), seconds), 2500.0)
    return spring + boil * 0.3


def character_arcane(seconds):
    # The air of a stone room nobody uses, and now and then a cymbal swelling backwards out of it.
    rng = rng_for('character_arcane')
    air = take(fs(530161), seconds, 10.0)
    swells = scatter(seconds, rng, (9.0, 16.0), lambda r: audio.lowpass(audio.fade_in(clip(
        [fs(383903), fs(110218), fs(9523)][r.integers(3)], 2.5, r.uniform(-18.0, -12.0), fade=0.4, lead=2.2),
        1.0), 5000.0))
    return room(air + swells, 'vast', 0.4, seconds)


def character_wrought(seconds):
    return audio.lowpass(take(fs(137836), seconds, 8.0), 7000.0)


def character_quickened(seconds):
    # A pendulum clock in the next room.
    return audio.lowpass(room(take(fs(657255), seconds), 'near', 0.3, seconds), 6000.0)


def character_verdant(seconds):
    return audio.lowpass(take(fs(353311), seconds), 9000.0)


def character_hollow(seconds):
    return take(fs(177958), seconds, 2.0)


def character_overgrown(seconds):
    # Frogs and crickets in a wet place at night.
    return audio.lowpass(take(fs(32655), seconds), 7000.0)


CHARACTERS = {'warm': character_warm, 'cold': character_cold, 'steam': character_steam,
              'arcane': character_arcane, 'wrought': character_wrought, 'quickened': character_quickened,
              'verdant': character_verdant, 'hollow': character_hollow, 'overgrown': character_overgrown}

CHARACTER_DESCRIPTIONS = {
    'warm': 'a wood stove close by and a second fire further off (Freesound)',
    'cold': 'arctic wind (Freesound)',
    'steam': 'a bubbling hot spring over a water boiler (Freesound)',
    'arcane': 'dungeon air with the occasional reversed cymbal swell (Freesound)',
    'wrought': 'a blacksmith\'s workshop (Freesound)',
    'quickened': 'a pendulum clock in the next room (Freesound)',
    'verdant': 'an early-summer wood at dawn (Freesound)',
    'hollow': 'water dripping in a cave (Freesound)',
    'overgrown': 'frogs and crickets at night (Freesound)',
}


# --- writing ----------------------------------------------------------------------------------

def write(name: str, x: np.ndarray, rows: list[tuple], what: str) -> None:
    path = OUT / f'{name}.ogg'
    audio.encode(x, path, quality=5.0)
    pcm = np.round(np.clip(x, -1, 1) * 32767).astype('<i2').tobytes()
    rows.append((path.name, what, len(x) / RATE, audio.rms_db(x), audio.db(audio.peak(x)),
                 hashlib.sha256(pcm).hexdigest()[:16]))
    print(f'  {path.name:22s} {len(x) / RATE:6.2f}s rms {audio.rms_db(x):6.1f} peak {audio.db(audio.peak(x)):5.1f}')


def freesound_rows() -> list[str]:
    import json
    out = []
    for sid in sorted(USED):
        meta = json.loads((SRC / 'freesound' / f'{sid}.json').read_text())
        title = meta['title'].removeprefix('Freesound - ').replace('|', '/').removesuffix(f" by {meta['user']}")
        out.append(f"| [{sid}](https://freesound.org/s/{sid}/) | {meta['user']} | {title} |")
    return out


def sources_md(rows: list[tuple]) -> str:
    lines = [
        '# Where these sounds came from',
        '',
        'Every file in this directory is a mix of real recordings, made by `tools/ambience/mix.py`',
        '(#163). Every recording is released under **CC0 1.0** (public domain dedication), so',
        'nothing here carries an attribution requirement - the credit below is given because it is',
        'owed, not because it is required. Instruments come from two libraries:',
        '',
        '* **VSCO-2 Community Edition**, Versilian Studios -',
        '  <https://github.com/sgossner/VSCO-2-CE>, commit `440300901dfe9275fd84e0b7763af1f8443ae62e`',
        '* **Versilian Community Sample Library (VCSL)**, Versilian Studios and contributors -',
        '  <https://github.com/sgossner/VCSL>, commit `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`',
        '',
        'and the field recordings and sound effects from Freesound, each one checked as CC0 on its',
        'own page when it was fetched (`fetch_sources.py` refuses any that is not):',
        '',
        '| Freesound | Author | Recording |',
        '| --- | --- | --- |',
        *freesound_rows(),
        '',
        '#209 asks that provenance be recorded because "CC0" and "CC-BY" are different promises.',
        'This set makes the CC0 one. The mixes themselves (the choice of recordings, edits,',
        'placement, room and levels) are the mod\'s own, under its licence. To check or rebuild:',
        '',
        '```',
        'python3 tools/soul_music/fetch_sources.py /path/to/sources',
        'python3 tools/ambience/mix.py --sources /path/to/sources',
        '```',
        '',
        'Mono, 44100 Hz, Ogg Vorbis at libvorbis quality 5. The checksum is of the 16-bit PCM',
        'handed to the encoder, not of the `.ogg` container.',
        '',
        '| File | What it is | Length | RMS | Peak | PCM sha256 |',
        '| --- | --- | --- | --- | --- | --- |',
    ]
    for name, what, length, rms, pk, sha in rows:
        lines.append(f'| `{name}` | {what} | {length:.2f}s | {rms:.1f} dB | {pk:.1f} dB | `{sha}…` |')
    lines.append('')
    return '\n'.join(lines)


LENGTHS = {'base': 4.5, 'warm': 3.0, 'cold': 4.0, 'steam': 3.4, 'arcane': 4.2, 'wrought': 3.2,
           'quickened': 4.0, 'verdant': 3.4, 'hollow': 4.2, 'overgrown': 4.0}


def main() -> None:
    global SRC
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--sources', type=Path, required=True)
    p.add_argument('--only', help='comma-separated asset names to re-mix (default: everything)')
    args = p.parse_args()
    SRC = args.sources
    only = set(args.only.split(',')) if args.only else None
    rows: list[tuple] = []
    for voice, make in VOICES.items():
        for v in range(3):
            name = f'{voice}_{v + 1}'
            if only and name not in only:
                continue
            x = master_one_shot(make(v))
            assert abs(len(x) / RATE - LENGTHS[voice]) < 0.01, name
            write(name, x, rows, f'one-shot, {voice}, variant {v + 1}: {DESCRIPTIONS[voice]}')
    if not only or 'bed_close' in only:
        write('bed_close', bed(bed_close, BED_RMS), rows,
              'rank bed, close: a gentle breeze, darkened, with crickets far off (Freesound), seamless loop')
    if not only or 'bed_open' in only:
        write('bed_open', bed(bed_open, BED_RMS), rows,
              'rank bed, open: wind singing across a mountain top at night (Freesound), seamless loop')
    for voice, make in CHARACTERS.items():
        name = f'character_{voice}'
        if only and name not in only:
            continue
        write(name, bed(make, CHARACTER_RMS), rows, f'character layer, {voice}: {CHARACTER_DESCRIPTIONS[voice]}')
    if not only:
        (OUT / 'SOURCES.md').write_text(sources_md(rows), encoding='utf-8')


if __name__ == '__main__':
    main()
