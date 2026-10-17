# File created ~ 17 - 10 - 2026
"""
Mixes the soul's ambience out of recorded instruments (#163, replacing the synthesis of #213).

    python3 tools/soul_music/fetch_sources.py /path/to/sources
    python3 tools/ambience/mix.py --sources /path/to/sources

writes the same files ``generate.py`` used to, under the same names, so ``sounds.json`` and every
line of Java that plays them are untouched:

* ``<voice>_1..3.ogg`` - one-shots. Each is two or three real strikes placed in a room: an anvil
  under a timpani for ``wrought``, a horn breathing over a hand on a frame drum for ``warm``.
* ``bed_close.ogg``, ``bed_open.ogg`` - the rank layers, built from instruments that have no pitch
  to speak of (an ocean drum, a bass drum's skin being rubbed, a cymbal under a soft roll). A soul's
  key is its own (``SoulMusicBrief``), and a bed with a note in it would be out of tune with half
  the souls that played it.
* ``character_<voice>.ogg`` - the nine character layers, the same rule: texture, not harmony. Where
  something pitched does appear (a triangle, a bell tree) it is sparse, distant and inharmonic.

The suppression set is still ``generate.py --set suppression``; nothing here touches it.

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


def rec(rel: str) -> np.ndarray:
    """One recording, mono, with its leading silence cut. Cached: the beds reuse a handful a lot."""
    if rel not in _cache:
        _cache[rel] = audio.trim_head(audio.highpass(audio.load(SRC / rel), 30.0))
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
    for _ in range(3):
        y = y * audio.undb(rms_target - audio.rms_db(y))
        y = audio.limit(y, peak_db, release=0.12)
    return y


# --- one-shots --------------------------------------------------------------------------------

VIBES_BOWED = f'{VCSL}/Idiophones/Struck Idiophones/Vibraphone/Bowed'
GLOCK = f'{VSCO}/Percussion/Glock'
GLASSES = f'{VCSL}/Idiophones/Friction Idiophones/Wine Glasses'
HORN = f'{VSCO}/Brass/F Horn/sus'
FRAME = f'{VCSL}/Membranophones/Struck Membranophones/Frame Drum'
ANVIL = f'{VCSL}/Idiophones/Struck Idiophones/Anvil'
TIMPANI = f'{VCSL}/Membranophones/Struck Membranophones/Timpani 1/Hit'
CHIMES = f'{VCSL}/Idiophones/Struck Idiophones/Hand Chimes'
TUBULAR = f'{VCSL}/Idiophones/Struck Idiophones/Tubular Bells 1'
HARP = f'{VSCO}/Strings/Harp'
BELLTREE = f'{VCSL}/Idiophones/Struck Idiophones/Bell Tree'
WOOD = f'{VCSL}/Idiophones/Struck Idiophones/Woodblock'
CLAVES = f'{VCSL}/Idiophones/Struck Idiophones/Claves'
FLUTE_STAC = f'{VSCO}/Woodwinds/Flute/stac'
MARIMBA = f'{VCSL}/Idiophones/Struck Idiophones/Marimba'
SHAKER = f'{VCSL}/Idiophones/Struck Idiophones/Shaker, Small'
CABASA = f'{VCSL}/Idiophones/Struck Idiophones/Cabasa'
OCEAN = f'{VCSL}/Membranophones/Other Membranophones/Ocean Drum'
BASSDRUM = f'{VCSL}/Membranophones/Struck Membranophones/Bass Drum 2'
GONG = f'{VSCO}/VSCO 1 Percussion/varMetal/Gong'
CYMBAL = f'{VCSL}/Idiophones/Struck Idiophones/Suspended Cymbal 1'
TRIANGLE = f'{VCSL}/Idiophones/Struck Idiophones/Triangles'


def base(v: int) -> np.ndarray:
    # The soul itself: a bowed vibraphone bar, which swells rather than strikes, over the faintest
    # touch of a gong. Nothing in it says fire or ice; it is the sound of the place being there.
    bar = ['Vibes_bowed_E3_rr1_Main.wav', 'Vibes_bowed_G3_rr1_Main.wav', 'Vibes_bowed_D4_rr1_Main.wav'][v]
    gong = ['gong_hit_pppp.wav', 'gong_hit_ppp.wav', 'gong_hit_pp.wav'][v]
    x = place(4.5, (0.0, note(f'{VIBES_BOWED}/{bar}', -12.0 if v == 2 else 0.0, 4.2, fade=1.6)),
              (0.25, audio.lowpass(note(f'{GONG}/{gong}', -5.0, 4.2, -15.0, fade=1.5), 900.0)))
    return room(x, 'hall', 0.45, 4.5)


def warm(v: int) -> np.ndarray:
    # A hearth: a horn breathing low and soft, a hand on a frame drum, and one pop from the fire.
    horn = ['MOHorn_sus_F2_v1_1.wav', 'MOHorn_sus_D2_v1_1.wav', 'MOHorn_sus_A#1_v1_1.wav'][v]
    hand = ['HDrumL_Hand_rr1_Sum.wav', 'HDrumS_Hand_v1_rr1_Sum.wav', 'HDrumL_HitMuted_v2_rr1_Sum.wav'][v]
    x = place(3.0, (0.0, audio.fade_in(note(f'{HORN}/{horn}', 0.0, 2.7, -4.0, fade=1.2), 0.35)),
              (0.08 + 0.05 * v, audio.lowpass(note(f'{FRAME}/{hand}', 0.0, 0.8, -6.0), 2500.0)),
              (0.9 + 0.3 * v, audio.highpass(note(f'{CLAVES}/Claves1_Hit_v1_rr1_Mid.wav', 7.0 - 3 * v, 0.03,
                                                  -14.0, fade=0.01), 900.0)))
    return room(x, 'near', 0.3, 3.0)


def cold(v: int) -> np.ndarray:
    # Ice: a glockenspiel bar struck once, then a wine glass rubbed until it sings, which is the
    # nearest thing an orchestra has to the sound of thin air.
    bar = ['glock_medium_G5.wav', 'glock_medium_C6.wav', 'glock_medium_G6.wav'][v]
    glass = ['glass1_D#4_Slow_1_Main.wav', 'glass3_A#4_Slow_1_Main.wav', 'glass4_D5_Slow_2_Main.wav'][v]
    sing = audio.fade_in(note(f'{GLASSES}/Sustains/Slow/{glass}', 0.0, 3.2, -9.0, fade=1.4), 0.9)
    x = place(4.0, (0.0, note(f'{GLOCK}/{bar}', 0.0, 3.0, -3.0, fade=1.5)), (0.4, sing))
    return room(x, 'hall', 0.5, 4.0)


def steam(v: int) -> np.ndarray:
    # Warm and cold at once is a third thing (SoulAxis): a glass's ring cut short by a breath of
    # cabasa, the way a hot iron hisses when it meets cold water.
    glass = ['glass2_F#4_Fast_1_Decay.wav', 'glass3_A#4_Fast_1_Decay.wav', 'glass1_D#4_Fast_1_Decay.wav'][v]
    rub = ['Cabasa1_Rub_v2_rr1_Mid.wav', 'Cabasa1_Rub_v2_rr2_Mid.wav', 'Cabasa1_Rub_v1_rr2_Mid.wav'][v]
    hiss = audio.bandpass(note(f'{CABASA}/{rub}', -3.0, 0.9, -7.0, fade=0.5), 1200.0, 6000.0)
    hiss2 = audio.bandpass(note(f'{CABASA}/{rub}', -5.0, 0.9, -13.0, fade=0.5), 1200.0, 6000.0)
    x = place(3.4, (0.0, note(f'{GLASSES}/Releases/Fast/{glass}', 0.0, 1.6, -4.0, fade=0.8)),
              (0.18, hiss), (0.55 + 0.1 * v, hiss2))
    return room(x, 'near', 0.35, 3.4)


def arcane(v: int) -> np.ndarray:
    # Something arriving: a hand chime played backwards swells into a quick harp figure in lydian
    # (its raised fourth is the "not quite of this world" the music's arcane mode uses too).
    chime = ['sus_C4_r01_main.wav', 'sus_E4_r01_main.wav', 'sus_D4_r01_main.wav'][v]
    swell = note(f'{CHIMES}/{chime}', 0.0, 1.6, -6.0, fade=0.2, reverse=True)
    figures = [['KSHarp_C5_mf.wav', 'KSHarp_E5_mf.wav', 'KSHarp_G5_mf.wav'],
               ['KSHarp_D4_mf.wav', 'KSHarp_A4_mf.wav', 'KSHarp_E5_mf.wav'],
               ['KSHarp_G3_mf.wav', 'KSHarp_D4_mf.wav', 'KSHarp_B3_mf.wav']][v]
    # The raised fourth: one harp note in each figure is lifted a semitone off its natural.
    lift = [0.0, 1.0, 0.0]
    parts = [(0.0, swell)]
    for i, f in enumerate(figures):
        parts.append((1.55 + 0.11 * i, note(f'{HARP}/{f}', lift[i] if v != 2 else 0.0, 2.4, -5.0 - 2 * i, fade=1.0)))
    parts.append((1.6, audio.highpass(note(f'{BELLTREE}/Stroke/BellTree_Stroke_{[1, 3, 6][v]}_Mid.wav',
                                            0.0, 2.0, -16.0, fade=1.0), 2500.0)))
    return room(place(4.2, *parts), 'vast', 0.55, 4.2)


def wrought(v: int) -> np.ndarray:
    # Worked metal and stone: an anvil, softly, with a timpani's weight under it as the stone.
    anvil = ['Anvil_Hit1_v1_rr1_Mid.wav', 'Anvil_Hit2_v1_rr1_Mid.wav', 'Anvil_Hit3_v2_rr1_Mid.wav'][v]
    timp = ['Timpani1_Hit_v2_rr1_Sum.wav', 'Timpani3_Hit_v2_rr1_Sum.wav', 'Timpani5_Hit_v2_rr1_Sum.wav'][v]
    x = place(3.2, (0.0, note(f'{ANVIL}/{anvil}', -2.0, 1.6, -2.0, fade=0.8)),
              (0.004, audio.lowpass(note(f'{TIMPANI}/{timp}', -3.0, 2.8, -6.0, fade=1.2), 700.0)))
    if v == 1:
        x += place(3.2, (0.42, note(f'{ANVIL}/Anvil_Hit1_v1_rr1_Mid.wav', 1.0, 1.2, -12.0, fade=0.6)))
    return room(x, 'near', 0.25, 3.2)


def quickened(v: int) -> np.ndarray:
    # A mechanism waking: wood ticks that hurry, then a glockenspiel bar and a tap of the anvil as
    # it catches. Arcane and wrought at once - the enchanted mechanism SoulAxis names.
    ticks = []
    t = 0.0
    gap = [0.34, 0.28, 0.4][v]
    for i in range(5 + v):
        w = [f'{WOOD}/wood_click_pp_rr1.wav', f'{WOOD}/wood_click_pp_rr2.wav', f'{WOOD}/wood_click_pp_rr3.wav'][i % 3]
        ticks.append((t, audio.lowpass(note(w, 2.0 * (i % 2), 0.2, -8.0 + i * 0.8, fade=0.05), 3500.0)))
        t += gap
        gap *= 0.78
    bar = ['glock_medium_G5.wav', 'glock_medium_C5.wav', 'glock_medium_G4.wav'][v]
    ticks.append((t + 0.05, note(f'{GLOCK}/{bar}', 0.0, 2.6, -3.0, fade=1.2)))
    ticks.append((t + 0.05, audio.lowpass(note(f'{ANVIL}/Anvil_Hit2_v1_rr1_Mid.wav', 3.0, 1.0, -11.0, fade=0.5),
                                          4500.0)))
    return audio.lowpass(room(place(4.0, *ticks), 'hall', 0.4, 4.0), 7000.0)


def verdant(v: int) -> np.ndarray:
    # Growing things: a flautist's two-note call, the shape of a small bird, over a soft marimba
    # and a shake of seeds.
    calls = [[('LDFlute_stac_E5_v1_rr1.wav', 0.0), ('LDFlute_stac_A5_v1_rr1.wav', 0.0)],
             [('LDFlute_stac_C6_v1_rr1.wav', 0.0), ('LDFlute_stac_A5_v1_rr2.wav', 0.0),
              ('LDFlute_stac_C6_v1_rr2.wav', 0.0)],
             [('LDFlute_stac_A5_v1_rr1.wav', -2.0), ('LDFlute_stac_E5_v1_rr2.wav', 0.0)]][v]
    parts = []
    t = 0.35
    for name, semis in calls:
        parts.append((t, note(f'{FLUTE_STAC}/{name}', semis, 0.5, -5.0, fade=0.2)))
        t += 0.16
    mar = ['Marimba_hit_Outrigger_G4_soft_01.wav', 'Marimba_hit_Outrigger_C4_soft_01.wav',
           'Marimba_hit_Outrigger_F3_soft_01.wav'][v]
    parts.append((0.0, note(f'{MARIMBA}/{mar}', 0.0, 2.0, -6.0, fade=0.8)))
    parts.append((0.05, audio.highpass(note(f'{SHAKER}/Mid_ShakerDouble_{["Up", "Down", "Up"][v]}_rr{1 + v % 2}.wav',
                                            0.0, 0.6, -14.0, fade=0.3), 2000.0)))
    return room(place(3.4, *parts), 'near', 0.3, 3.4)


def hollow(v: int) -> np.ndarray:
    # Bone and dust: one heartbeat on a bass drum, and a tubular bell a long way off, tuned down
    # until it is more weight than pitch.
    drum = ['bassdrum_hit_pp1.wav', 'bassdrum_hit_pp2.wav', 'bassdrum_hit_mp1.wav'][v]
    bell = ['chimes_C3_f_rr1.wav', 'chimes_E3_pp_rr1.wav', 'chimes_D3_pp_rr3.wav'][v]
    x = place(4.2, (0.0, note(f'{BASSDRUM}/{drum}', 0.0, 2.4, -1.0, fade=1.0)),
              (0.12, audio.lowpass(note(f'{TUBULAR}/{bell}', -7.0, 4.0, -9.0, fade=1.8), 2200.0)))
    if v == 0:
        x += place(4.2, (0.55, note(f'{BASSDRUM}/bassdrum_hit_pp2.wav', 0.0, 2.0, -9.0, fade=0.8)))
    return audio.lowpass(room(x, 'vast', 0.55, 4.2), 4000.0)


def overgrown(v: int) -> np.ndarray:
    # Verdant and hollow at once: something growing through something dead. A low, soft marimba,
    # leaves, and one drop of water from somewhere above.
    mar = ['Marimba_hit_Outrigger_C2_soft_01.wav', 'Marimba_hit_Outrigger_G2_soft_01.wav',
           'Marimba_hit_Outrigger_F1_soft_01.wav'][v]
    rustle = audio.highpass(note(f'{SHAKER}/Mid_Shaker_Roll_Fast_rr{1 + v % 2}.wav', -3.0, 1.8, -10.0,
                                 fade=0.9), 1200.0)
    drop = note(f'{GLOCK}/{["glock_medium_C6.wav", "glock_medium_G5.wav", "glock_medium_C6.wav"][v]}', 0.0, 0.3,
                -12.0, fade=0.22)
    x = place(4.0, (0.0, note(f'{MARIMBA}/{mar}', 0.0, 3.4, -3.0, fade=1.4)), (0.3, audio.fade_in(rustle, 0.5)),
              (1.6 + 0.3 * v, drop))
    return audio.lowpass(room(x, 'hall', 0.4, 4.0), 5500.0)


VOICES = {'base': base, 'warm': warm, 'cold': cold, 'steam': steam, 'arcane': arcane, 'wrought': wrought,
          'quickened': quickened, 'verdant': verdant, 'hollow': hollow, 'overgrown': overgrown}

DESCRIPTIONS = {
    'base': 'bowed vibraphone over a barely-touched gong (VCSL, VSCO-2)',
    'warm': 'low F horn, a hand on a frame drum, one clave pop (VSCO-2, VCSL)',
    'cold': 'glockenspiel strike into a rubbed wine glass (VSCO-2, VCSL)',
    'steam': 'wine-glass ring cut short by cabasa breaths (VCSL)',
    'arcane': 'reversed hand chime into a lydian harp figure and bell tree (VCSL, VSCO-2)',
    'wrought': 'anvil over a lowpassed timpani (VCSL)',
    'quickened': 'accelerating woodblock into glockenspiel and anvil (VCSL, VSCO-2)',
    'verdant': 'flute staccato call over soft marimba and shaker (VSCO-2, VCSL)',
    'hollow': 'bass drum heartbeat and a tubular bell tuned down, far off (VCSL)',
    'overgrown': 'low soft marimba, shaker leaves, a damped glockenspiel drop (VCSL, VSCO-2)',
}


# --- beds -------------------------------------------------------------------------------------

def scatter(seconds: float, rng: np.random.Generator, every: tuple[float, float],
            make, start: float = 0.0) -> np.ndarray:
    """Events at irregular intervals, as a texture rather than a rhythm."""
    y = np.zeros(int(seconds * RATE) + 10 * RATE)
    t = start + rng.uniform(*every) * rng.random()
    while t < seconds:
        x = make(rng)
        a = int(t * RATE)
        y[a:a + len(x)] += x[: len(y) - a]
        t += rng.uniform(*every)
    return y[: int(seconds * RATE)]


def wash(seconds: float, rng: np.random.Generator, files: list[str], piece: tuple[float, float],
         xfade: float, semis: tuple[float, float] = (0.0, 0.0), gains: tuple[float, float] = (-4.0, 0.0)) -> np.ndarray:
    """A continuous texture from overlapping pieces of sustained recordings, each one faded in and
    out over `xfade`, so the bed breathes the way the synthesised one's LFOs pretended to."""
    y = np.zeros(int(seconds * RATE) + 12 * RATE)
    t = 0.0
    while t < seconds:
        f = files[rng.integers(len(files))]
        x = rec(f)
        if semis != (0.0, 0.0):
            x = audio.shift(x, rng.uniform(*semis))
        length = rng.uniform(*piece)
        usable = max(0, len(x) - int(length * RATE) - 1)
        off = int(rng.uniform(0.15, 0.85) * usable)
        seg = x[off:off + int(length * RATE)].copy()
        if len(seg) < int(2 * xfade * RATE):
            t += 0.5
            continue
        n = int(xfade * RATE)
        env = np.ones(len(seg))
        env[:n] = np.sin(np.linspace(0, np.pi / 2, n)) ** 2
        env[-n:] = np.cos(np.linspace(0, np.pi / 2, n)) ** 2
        seg *= env / (np.sqrt(np.mean(seg ** 2)) + 1e-12) * audio.undb(rng.uniform(*gains))
        a = int(t * RATE)
        y[a:a + len(seg)] += seg[: len(y) - a]
        t += len(seg) / RATE - xfade
    return y[: int(seconds * RATE)]


def seamless(x: np.ndarray, seconds: float, overlap: float) -> np.ndarray:
    """Fold the tail past the loop point back over the head, equal power, so the loop has no seam.
    Vanilla loops the whole file and has no loop points of its own."""
    n, m = int(seconds * RATE), int(overlap * RATE)
    y = x[:n].copy()
    t = np.linspace(0.0, np.pi / 2.0, m)
    y[:m] = y[:m] * np.sin(t) + x[n:n + m] * np.cos(t)
    return y


def bed(make, rms: float) -> np.ndarray:
    total = BED_SECONDS + BED_OVERLAP
    x = make(total)[: int(total * RATE)]
    x = audio.highpass(x, 40.0, 3)
    return level(seamless(x, BED_SECONDS, BED_OVERLAP), rms, BED_PEAK)


def bed_close(seconds: float) -> np.ndarray:
    # Close: the room you are standing in. An ocean drum barely tilting, a bass drum's skin being
    # rubbed under it, both dark.
    rng = rng_for('bed_close')
    ocean = audio.lowpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_{i}_Mid.wav' for i in (1, 2, 3)],
                               (7.0, 12.0), 3.0, (-5.0, -2.0)), 900.0, 3)
    rub = audio.lowpass(wash(seconds, rng, [f'{BASSDRUM}/bassdrum_rub{i}.wav' for i in (2, 7, 10, 12)],
                             (4.0, 7.0), 1.8, (-3.0, 0.0), (-10.0, -5.0)), 500.0, 3)
    return room(ocean + rub, 'near', 0.35, seconds)


def bed_open(seconds: float) -> np.ndarray:
    # Open: the soul gone huge. The same ocean drum lifted out of the floor, a cymbal under a soft
    # roll a long way away, and a gong rolled so gently it is felt as air moving.
    rng = rng_for('bed_open')
    air = audio.highpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_{i}_Mid.wav' for i in (1, 2, 3)],
                              (8.0, 13.0), 3.5), 280.0, 2)
    shimmer = audio.lowpass(wash(seconds, rng, [f'{CYMBAL}/susCymb1_roll_mp1_nloop.wav'], (5.0, 8.0), 2.5,
                                 (-12.0, -7.0), (-14.0, -9.0)), 5000.0)
    gong = audio.lowpass(wash(seconds, rng, [f'{GONG}/gong_roll_long_f.wav', f'{GONG}/gong_roll_medium_f.wav'],
                              (6.0, 10.0), 3.0, (-7.0, -4.0), (-16.0, -11.0)), 1200.0)
    return room(air + shimmer + gong, 'vast', 0.85, seconds)


def grain_fire(seconds: float, rng: np.random.Generator, rate: float) -> np.ndarray:
    grains = []
    for rel in [f'{CLAVES}/Claves1_Hit_v1_rr1_Mid.wav', f'{CLAVES}/Claves2_Hit_v1_rr1_Mid.wav',
                f'{WOOD}/wood_click_pp_rr1.wav', f'{CABASA}/Cabasa1_Hit_rr1_Mid.wav',
                f'{SHAKER}/Mid_Shaker_Slap_rr1.wav']:
        x = rec(rel)
        for length in (0.004, 0.009, 0.018):
            g = x[: int(length * RATE)].copy()
            g *= np.hanning(len(g) * 2)[len(g):] ** 0.5
            grains.append(audio.highpass(g / audio.peak(g), 700.0))
    y = np.zeros(int(seconds * RATE) + RATE)
    t = 0.0
    while t < seconds:
        t += rng.exponential(1.0 / rate)
        g = audio.shift(grains[rng.integers(len(grains))], rng.uniform(-5.0, 7.0))
        size = min(0.5, 0.08 * rng.pareto(3.0) + 0.05)
        a = int(t * RATE)
        y[a:a + len(g)] += g[: len(y) - a] * size
    return y[: int(seconds * RATE)]


def character_warm(seconds):
    rng = rng_for('character_warm')
    roar = audio.lowpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_3_Mid.wav'], (6.0, 10.0), 2.5,
                              (-9.0, -6.0), (-9.0, -5.0)), 380.0, 3)
    fire = grain_fire(seconds, rng, 9.0)
    return room(fire + roar[: len(fire)] * 0.6, 'near', 0.3, seconds)


def character_cold(seconds):
    rng = rng_for('character_cold')
    wind = audio.bandpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_{i}_Mid.wav' for i in (1, 2)],
                               (8.0, 12.0), 3.5, (3.0, 6.0)), 1600.0, 6500.0)
    soft = ['Legacy/1/triangle1_hit_pp.wav', 'Legacy/2/triangle2_hit_p.wav', 'Legacy/2/triangle2_hit_p_2.wav',
            'Legacy/3/triangle3_hit_p.wav', 'Legacy/3/triangle3_hit_pp.wav']
    tings = scatter(seconds, rng, (4.0, 9.0), lambda r: note(
        f'{TRIANGLE}/{soft[r.integers(len(soft))]}', 0.0, 2.5, r.uniform(-16.0, -10.0), fade=1.2))
    return room(wind + tings[: len(wind)], 'hall', 0.55, seconds)


def character_steam(seconds):
    rng = rng_for('character_steam')
    hiss = audio.bandpass(wash(seconds, rng, [f'{SHAKER}/Mid_Shaker_Roll_Fast_rr1.wav',
                                              f'{SHAKER}/Mid_Shaker_Roll_Fast_rr2.wav'], (1.2, 2.2), 0.5,
                               (-7.0, -3.0), (-14.0, -4.0)), 1000.0, 4500.0) * 0.5
    rings = scatter(seconds, rng, (6.0, 12.0), lambda r: note(
        f'{GLASSES}/Releases/Fast/{["glass1_D#4_Fast_1_Decay.wav", "glass2_F#4_Fast_1_Decay.wav"][r.integers(2)]}',
        0.0, 1.4, r.uniform(-14.0, -9.0), fade=0.7))
    fire = audio.lowpass(grain_fire(seconds, rng, 3.0), 5000.0)
    return room(hiss + rings[: len(hiss)] * 1.5 + fire[: len(hiss)] * 1.2, 'near', 0.35, seconds)


def character_arcane(seconds):
    rng = rng_for('character_arcane')
    bowed = audio.highpass(wash(seconds, rng, [f'{CYMBAL}/susCymb1_bow_{i}.wav' for i in (7, 13, 17, 20)],
                                (4.0, 7.0), 2.0, (-2.0, 0.0), (-8.0, -3.0)), 600.0)
    strokes = scatter(seconds, rng, (7.0, 14.0), lambda r: audio.highpass(note(
        f'{BELLTREE}/Stroke/BellTree_Stroke_{[1, 3, 4, 6, 10][r.integers(5)]}_Mid.wav', 0.0, 3.0,
        r.uniform(-18.0, -12.0), fade=1.4), 2500.0))
    return room(bowed + strokes[: len(bowed)], 'vast', 0.7, seconds)


def character_wrought(seconds):
    rng = rng_for('character_wrought')
    rumble = audio.lowpass(wash(seconds, rng, [f'{BASSDRUM}/bassdrum_roll_pp.wav', f'{BASSDRUM}/bassdrum_roll_mp.wav'],
                                (5.0, 9.0), 2.0, (-3.0, 0.0), (-6.0, -2.0)), 260.0, 3)
    strikes = scatter(seconds, rng, (3.0, 8.0), lambda r: audio.lowpass(note(
        f'{ANVIL}/Anvil_Hit{r.integers(1, 4)}_v1_rr1_Mid.wav', r.uniform(-4.0, 0.0), 1.2,
        r.uniform(-20.0, -13.0), fade=0.6), 3500.0))
    return room(rumble + strikes[: len(rumble)], 'hall', 0.5, seconds)


def character_quickened(seconds):
    rng = rng_for('character_quickened')

    def burst(r):
        k = int(r.integers(3, 8))
        gap = r.uniform(0.16, 0.3)
        parts = [(i * gap, note(f'{WOOD}/wood_click_pp_rr{1 + i % 3}.wav', 2.0 * (i % 2), 0.15,
                                r.uniform(-20.0, -14.0), fade=0.05)) for i in range(k)]
        return audio.lowpass(place(k * gap + 0.3, *parts), 3200.0)

    ticks = scatter(seconds, rng, (2.0, 5.0), burst)
    metal = scatter(seconds, rng, (8.0, 15.0), lambda r: audio.lowpass(note(
        f'{TRIANGLE}/Legacy/1/triangle1_hit_mp_muted{r.integers(1, 4)}.wav', 0.0, 0.8, r.uniform(-20.0, -14.0),
        fade=0.4), 5000.0))
    n = int(seconds * RATE)
    return room(ticks[:n] + metal[:n], 'hall', 0.4, seconds)


def character_verdant(seconds):
    rng = rng_for('character_verdant')
    leaves = audio.bandpass(wash(seconds, rng, [f'{SHAKER}/Mid_Shaker_Roll_Fast_rr1.wav',
                                                f'{SHAKER}/Mid_Shaker_Roll_Fast_rr2.wav'], (1.5, 3.0), 0.8,
                                 (-8.0, -4.0), (-14.0, -4.0)), 900.0, 3800.0)

    def call(r):
        names = ['LDFlute_stac_E5_v1_rr1.wav', 'LDFlute_stac_A5_v1_rr1.wav', 'LDFlute_stac_C6_v1_rr1.wav']
        parts = [(i * 0.13, note(f'{FLUTE_STAC}/{names[r.integers(3)]}', r.uniform(-1.0, 3.0), 0.12, -4.0,
                                 fade=0.06)) for i in range(int(r.integers(2, 4)))]
        return audio.highpass(place(0.6, *parts), 1500.0) * audio.undb(r.uniform(-22.0, -15.0))

    birds = scatter(seconds, rng, (5.0, 12.0), call)
    return room(leaves + birds[: len(leaves)], 'hall', 0.45, seconds)


def character_hollow(seconds):
    rng = rng_for('character_hollow')
    low = audio.lowpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_{i}_Mid.wav' for i in (1, 3)], (8.0, 12.0),
                             3.5, (-10.0, -7.0), (-8.0, -4.0)), 450.0, 3)
    beats = scatter(seconds, rng, (4.0, 9.0), lambda r: note(
        f'{BASSDRUM}/bassdrum_hit_pp{r.integers(1, 3)}.wav', 0.0, 2.0, r.uniform(-12.0, -7.0), fade=0.9))
    gong = scatter(seconds, rng, (14.0, 24.0), lambda r: audio.lowpass(note(
        f'{GONG}/gong_hit_pppp.wav', r.uniform(-8.0, -5.0), 6.0, -16.0, fade=3.0), 800.0))
    n = len(low)
    return room(low + beats[:n] + gong[:n], 'vast', 0.6, seconds)


def character_overgrown(seconds):
    rng = rng_for('character_overgrown')
    leaves = audio.lowpass(wash(seconds, rng, [f'{SHAKER}/Mid_Shaker_Roll_Fast_rr1.wav'], (1.5, 3.0), 0.8,
                                (-9.0, -5.0), (-16.0, -6.0)), 4000.0)
    damp = audio.lowpass(wash(seconds, rng, [f'{OCEAN}/OceanDrum_Sus_2_Mid.wav'], (8.0, 12.0), 3.0,
                              (-8.0, -5.0), (-10.0, -6.0)), 600.0)
    drips = scatter(seconds, rng, (2.5, 7.0), lambda r: note(
        f'{GLOCK}/{["glock_medium_C6.wav", "glock_medium_G5.wav", "glock_medium_C5.wav"][r.integers(3)]}',
        r.uniform(-0.3, 0.3), 0.25, r.uniform(-24.0, -16.0), fade=0.2))
    n = len(leaves)
    return room(leaves + damp[:n] + drips[:n], 'hall', 0.45, seconds)


CHARACTERS = {'warm': character_warm, 'cold': character_cold, 'steam': character_steam,
              'arcane': character_arcane, 'wrought': character_wrought, 'quickened': character_quickened,
              'verdant': character_verdant, 'hollow': character_hollow, 'overgrown': character_overgrown}

CHARACTER_DESCRIPTIONS = {
    'warm': 'fire built from claves, woodblock, cabasa and shaker transients over a low ocean drum',
    'cold': 'high-passed ocean drum for thin wind, sparse distant triangle',
    'steam': 'shaker hiss in gusts, sparse wine-glass rings, a few embers',
    'arcane': 'bowed cymbal shimmer, sparse bell tree strokes, a very large room',
    'wrought': 'pp bass drum roll for the forge floor, distant lowpassed anvil',
    'quickened': 'bursts of woodblock ticking, a muted triangle now and then',
    'verdant': 'shaker leaves and far-off flute-staccato birds',
    'hollow': 'low ocean drum, slow bass drum heartbeats, a gong tuned down to a pressure change',
    'overgrown': 'dark shaker leaves, damp ocean drum, damped glockenspiel drips',
}


# --- writing ----------------------------------------------------------------------------------

def write(name: str, x: np.ndarray, rows: list[tuple], what: str) -> None:
    path = OUT / f'{name}.ogg'
    audio.encode(x, path, quality=5.0)
    pcm = np.round(np.clip(x, -1, 1) * 32767).astype('<i2').tobytes()
    rows.append((path.name, what, len(x) / RATE, audio.rms_db(x), audio.db(audio.peak(x)),
                 hashlib.sha256(pcm).hexdigest()[:16]))
    print(f'  {path.name:22s} {len(x) / RATE:6.2f}s rms {audio.rms_db(x):6.1f} peak {audio.db(audio.peak(x)):5.1f}')


def sources_md(rows: list[tuple]) -> str:
    lines = [
        '# Where these sounds came from',
        '',
        'Every file in this directory is a mix of recordings of real instruments, made by',
        '`tools/ambience/mix.py` (#163). The recordings come from two libraries, both released',
        'under **CC0 1.0** (public domain dedication), so nothing here carries an attribution',
        'requirement - the credit below is given because it is owed, not because it is required:',
        '',
        '* **VSCO-2 Community Edition**, Versilian Studios -',
        '  <https://github.com/sgossner/VSCO-2-CE>, commit `440300901dfe9275fd84e0b7763af1f8443ae62e`',
        '* **Versilian Community Sample Library (VCSL)**, Versilian Studios and contributors -',
        '  <https://github.com/sgossner/VCSL>, commit `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`',
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
              'rank bed, close: dark ocean drum and a rubbed bass drum skin, seamless loop')
    if not only or 'bed_open' in only:
        write('bed_open', bed(bed_open, BED_RMS), rows,
              'rank bed, open: lifted ocean drum, distant cymbal roll and gong roll, very wet, seamless loop')
    for voice, make in CHARACTERS.items():
        name = f'character_{voice}'
        if only and name not in only:
            continue
        write(name, bed(make, CHARACTER_RMS), rows, f'character layer, {voice}: {CHARACTER_DESCRIPTIONS[voice]}')
    if not only:
        (OUT / 'SOURCES.md').write_text(sources_md(rows), encoding='utf-8')


if __name__ == '__main__':
    main()
