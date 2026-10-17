"""Build the soul's sampled instrument bank (#163) from two CC0 orchestral libraries.

    python3 tools/soul_music/fetch_sources.py /path/to/sources     # sparse clones, ~3 GB
    python3 tools/soul_music/build_bank.py --sources /path/to/sources

writes src/main/resources/assets/soulhome/soul_music/{bank.json,<instrument>/*.ogg,SOURCES.md}. The
manifest's contract is the javadoc on SampleBank; this script is the only thing that writes it, and
it is the only thing that should be changed to change the bank - bank.json is output, not source.

SOURCES.md is written by the same run, from the same decisions: which recording each zone came from,
its root, its checksum, and the commits the libraries were pinned at (in fetch_sources.py). A rebuild
from the same commits is byte-identical, including SOURCES.md, so `git status` after one is the check.

What this file decides, instrument by instrument, is which recording plays a note and how loud it
sits beside the others. The composer still writes every note; nothing here composes, and nothing
here synthesises - the point of the whole bank is that the soul stop sounding like an
oscillator.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

import audio
from audio import RATE
from fetch_sources import LIBRARIES

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / 'src/main/resources/assets/soulhome/soul_music'

VSCO = 'VSCO-2-CE'
VCSL = 'VCSL'

NOTE = re.compile(r'(?<![A-Za-z])([A-G])(#|b)?(-?\d)(?![0-9])')
PITCH_CLASS = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}

# A whole-instrument reference: a velocity-1 note's zone gain brings its body to this RMS, so an
# instrument's `level` reads on the same scale as the oscillator level it replaced (a full-scale sine
# is -3 dBFS RMS). Balancing instruments against each other is `level`'s job, not the zones'.
UNIT_RMS_DB = -3.0

# Each instrument's `level` below was first set so its stem (MusicPreview, every voice at rank 0.5)
# measured what the oscillator it replaces measured, which keeps the balance SoulSynthTest was
# written against; then moved by ear where a recording wanted it - the crackle down and darker,
# because real wood transients are far brighter than the filtered noise they replace, and the
# anvil a little under the old clank, because a real anvil is piercing and the old one was not.
STORE_PEAK = 0.89


def note_of(name: str) -> int:
    match = None
    for match in NOTE.finditer(name):
        pass
    if match is None:
        raise ValueError(f'no note name in {name}')
    pc = PITCH_CLASS[match.group(1)] + {'#': 1, 'b': -1, None: 0}[match.group(2)]
    return 12 * (int(match.group(3)) + 1) + pc


@dataclass
class Source:
    path: str
    root: int | None = None       # MIDI; derived from the file name when None
    octave: int = 0               # added to a name-derived root: VCSL and most of VSCO name middle C "C3"
    vel: tuple[float, float] = (0.0, 1.0)
    dur: tuple[float, float] = (0.0, 1e6)
    gain_db: float = 0.0          # trim relative to the instrument's matched loudness


@dataclass
class Spec:
    name: str
    sources: list[Source]
    envelope: str = 'struck'
    attack: float = 0.0
    release: float = 0.25
    level: float = 1.0
    track_pitch: bool = True
    tone: tuple[float, float] | None = None
    max_seconds: float = 4.0
    loop: tuple[float, float, float] | None = None   # (start s, end s, crossfade s) for sustains
    body: tuple[float, float] = (0.0, 0.5)           # window (s) whose RMS is matched across zones
    highpass: float = 30.0
    lowpass: float | None = None
    tune: bool = True
    shape: callable = None                           # optional extra processing of the trimmed note
    rr: bool = False                                 # sources are round-robins of one zone


def vs(group: str, names: list[str], octave: int = 12, **kw) -> list[Source]:
    return [Source(f'{VSCO}/{group}/{n}', octave=octave, **kw) for n in names]


def vc(group: str, names: list[str], octave: int = 12, **kw) -> list[Source]:
    return [Source(f'{VCSL}/{group}/{n}', octave=octave, **kw) for n in names]


def piano(key_numbers: list[int], dyn: int) -> list[Source]:
    # The upright's files are numbered, not named: MappingChart.txt gives number n as MIDI 21 + 2n.
    return [Source(f'{VSCO}/Keys/Upright Piano/Player_dyn{dyn}_rr1_{n:03d}.wav', root=21 + 2 * n)
            for n in key_numbers]


def organ(numbers: list[int]) -> list[Source]:
    # The organ's pedal files count in thirds from 064 = C2; measured, since nothing documents it.
    return [Source(f'{VSCO}/Keys/Organ/Quiet/NT5_PedalQuiet_{n:03d}_rr1.wav', root=36 + (n - 64)) for n in numbers]


# --- the bank -----------------------------------------------------------------------------------

SPECS: list[Spec] = [
    # Strings, not a synth pad: a cello section below middle C and a violin section above it, both
    # at their softest recorded dynamic. The section's own vibrato is the movement the old pad's
    # filter sweep was standing in for.
    Spec('pad',
         vs('Strings/Cello Section/susvib',
            ['susvib_C1_v1_1.wav', 'susvib_E1_v1_1.wav', 'susvib_G1_v1_1.wav', 'susvib_B1_v1_1.wav',
             'susvib_D2_v1_1.wav', 'susvib_F2_v1_1.wav', 'susvib_A2_v1_1.wav'])
         + vs('Strings/Violin Section/susVib',
              ['VlnEns_susVib_D3_v1.wav', 'VlnEns_susVib_F#3_v1.wav', 'VlnEns_susVib_A3_v1.wav',
               'VlnEns_susVib_C4_v1.wav', 'VlnEns_susVib_E4_v1.wav']),
         envelope='sustain', attack=0.8, release=2.2, level=0.135, tone=(1400.0, 6000.0),
         max_seconds=3.8, loop=(1.3, 3.6, 0.8), body=(0.6, 2.0), tune=True),

    # The upright from VSCO, felt-soft: the hearth's instrument should sound like a room with a
    # piano in it, not an electric one. Two layers, so a soft note is a different strike rather than
    # a quieter copy of a hard one.
    Spec('epiano',
         [*(Source(s.path, s.root, vel=(0.0, 0.5)) for s in piano(list(range(12, 32, 2)), 1)),
          *(Source(s.path, s.root, vel=(0.5, 1.0)) for s in piano(list(range(12, 32, 2)), 2))],
         level=0.274, tone=(1800.0, 9000.0), max_seconds=2.6, body=(0.0, 0.6), tune=False),

    # Hand chimes: struck aluminium tubes with almost no strike noise. They are tuned bells that do
    # not clang, which is what both a cold soul and a hollow one want.
    Spec('bell',
         vc('Idiophones/Struck Idiophones/Hand Chimes',
            ['sus_C3_r01_main.wav', 'sus_E3_r01_main.wav', 'sus_G#3_r01_main.wav', 'sus_C4_r01_main.wav',
             'sus_E4_r01_main.wav', 'sus_G#4_r01_main.wav', 'sus_C5_r01_main.wav']),
         level=0.226, tone=(2500.0, 9000.0), max_seconds=3.6, body=(0.0, 1.0)),

    # Vibraphone with soft mallets and the motor off: the glassy, sustained metal the old three-sine
    # "glass" was imitating.
    Spec('glass',
         vc('Idiophones/Struck Idiophones/Vibraphone/Soft Mallets',
            ['Vibes_soft_F2_v2_rr1_Main.wav', 'Vibes_soft_A2_v2_rr1_Main.wav', 'Vibes_soft_C3_v2_rr1_Main.wav',
             'Vibes_soft_E3_v2_rr1_Main.wav', 'Vibes_soft_G3_v2_rr1_Main.wav', 'Vibes_soft_B3_v2_rr1_Main.wav',
             'Vibes_soft_D4_v2_rr1_Main.wav', 'Vibes_soft_F4_v2_rr1_Main.wav']),
         level=0.155, tone=(2200.0, 8000.0), max_seconds=3.6, body=(0.0, 1.0)),

    # Concert harp. VSCO names this one in scientific pitch (C4 = middle C), unlike its neighbours.
    Spec('pluck',
         vs('Strings/Harp',
            ['KSHarp_G1_mp.wav', 'KSHarp_D2_mf.wav', 'KSHarp_F2_mf.wav', 'KSHarp_A2_mf.wav',
             'KSHarp_C3_mf.wav', 'KSHarp_E3_mf.wav', 'KSHarp_G3_mf.wav', 'KSHarp_B3_mf.wav', 'KSHarp_D4_mf.wav',
             'KSHarp_F4_mf.wav', 'KSHarp_A4_mf.wav', 'KSHarp_C5_mf.wav', 'KSHarp_E5_mf.wav'],
            octave=0),
         level=0.312, tone=(1500.0, 8000.0), max_seconds=2.8, body=(0.0, 0.5)),

    # Marimba, medium strikes: wood where the old mallet was a sine with a partial.
    Spec('mallet',
         vc('Idiophones/Struck Idiophones/Marimba',
            ['Marimba_hit_Outrigger_F1_med_01.wav', 'Marimba_hit_Outrigger_C2_med_01.wav',
             'Marimba_hit_Outrigger_G2_med_01.wav', 'Marimba_hit_Outrigger_B2_med_01.wav',
             'Marimba_hit_Outrigger_F3_med_01.wav', 'Marimba_hit_Outrigger_C4_med_01.wav',
             'Marimba_hit_Outrigger_G4_med_01.wav', 'Marimba_hit_Outrigger_B4_med_01.wav']),
         level=0.574, tone=(1600.0, 8000.0), max_seconds=2.4, body=(0.0, 0.4)),

    # A quiet organ pedal flue: the one sustained instrument that holds a note for two minutes
    # without a bow change, which is what a drone under a whole piece needs.
    Spec('drone',
         organ([64, 67, 70, 73]),
         envelope='sustain', attack=4.0, release=5.0, level=0.21, tone=(700.0, 2400.0),
         max_seconds=5.0, loop=(1.5, 4.6, 1.2), body=(1.5, 2.5), highpass=24.0),

    # Flute without vibrato (VSCO "susNV"), so a held note holds still.
    Spec('flute',
         vs('Woodwinds/Flute/susNV',
            ['LDFlute_susNV_C3_v1_1.wav', 'LDFlute_susNV_E3_v1_1.wav', 'LDFlute_susNV_A3_v1_1.wav',
             'LDFlute_susNV_C4_v1_1.wav', 'LDFlute_susNV_E4_v1_1.wav', 'LDFlute_susNV_A4_v1_1.wav']),
         envelope='sustain', attack=0.06, release=0.5, level=0.185, tone=(2000.0, 9000.0),
         max_seconds=4.0, loop=(1.2, 3.8, 0.6), body=(0.4, 1.4)),

    # --- foley: a room's own sound, placed on the music's grid by the composer -------------------

    Spec('thump',
         vc('Membranophones/Struck Membranophones/Frame Drum',
            ['HDrumL_HitMuted_v2_rr1_Sum.wav', 'HDrumL_HitMuted_v2_rr2_Sum.wav',
             'HDrumL_HitMuted_v3_rr1_Sum.wav', 'HDrumL_HitMuted_v3_rr2_Sum.wav'], root=48),
         track_pitch=False, rr=True, level=0.4, tone=(500.0, 4000.0), max_seconds=0.9, body=(0.0, 0.15),
         tune=False),
    Spec('clank',
         [*vc('Idiophones/Struck Idiophones/Anvil',
              ['Anvil_Hit1_v1_rr1_Mid.wav', 'Anvil_Hit2_v1_rr1_Mid.wav', 'Anvil_Hit3_v1_rr1_Mid.wav'],
              root=72, vel=(0.0, 0.6)),
          *vc('Idiophones/Struck Idiophones/Anvil',
              ['Anvil_Hit1_v2_rr1_Mid.wav', 'Anvil_Hit2_v2_rr1_Mid.wav', 'Anvil_Hit3_v2_rr1_Mid.wav'],
              root=72, vel=(0.6, 1.0))],
         track_pitch=False, rr=True, level=0.77, tone=(2500.0, 6000.0), max_seconds=1.6, body=(0.0, 0.2),
         tune=False),
    Spec('tick',
         [*vc('Idiophones/Struck Idiophones/Woodblock',
              ['wood_click_pp_rr1.wav', 'wood_click_pp_rr2.wav', 'wood_click_pp_rr3.wav'], root=84),
          *vc('Idiophones/Struck Idiophones/Claves', ['Claves1_Hit_v1_rr1_Mid.wav'], root=84)],
         track_pitch=False, rr=True, level=0.28, tone=(2000.0, 9000.0), max_seconds=0.35, body=(0.0, 0.05),
         tune=False),
    # crackle, chirp and the swells are assembled below from several recordings each.
    # A cabasa is far brighter than the band of noise it replaces, so it is rolled off until a vent
    # breathes rather than sizzles - SoulSynthTest#nothingPiercingRecorded caught the first cut.
    Spec('hiss',
         vc('Idiophones/Struck Idiophones/Cabasa',
            ['Cabasa1_Rub_v2_rr1_Mid.wav', 'Cabasa1_Rub_v2_rr2_Mid.wav', 'Cabasa1_Rub_v1_rr2_Mid.wav'], root=72),
         envelope='swell', track_pitch=False, rr=True, level=1.8, tone=(2000.0, 4500.0), max_seconds=0.9,
         body=(0.0, 0.4), highpass=900.0, lowpass=6000.0, tune=False),
    Spec('wind',
         vc('Membranophones/Other Membranophones/Ocean Drum',
            ['OceanDrum_Sus_1_Mid.wav', 'OceanDrum_Sus_2_Mid.wav'], root=60),
         envelope='swell', track_pitch=False, rr=True, level=0.4, tone=(700.0, 2600.0), max_seconds=7.0,
         loop=(1.0, 6.5, 1.5), body=(1.0, 4.0), lowpass=3000.0, tune=False),
    # Glockenspiel bars damped to their first half-second: a drop landing on something tuned. The
    # kalimba was tried first and is in a traditional tuning, so no root could be honest about it.
    Spec('drip',
         vs('Percussion/Glock', ['glock_medium_C5.wav', 'glock_medium_G5.wav', 'glock_medium_C6.wav'], octave=0),
         level=0.61, max_seconds=0.45, body=(0.0, 0.1), highpass=400.0, tune=False),
    Spec('rustle',
         vc('Idiophones/Struck Idiophones/Shaker, Small',
            ['Mid_Shaker_Roll_Fast_rr1.wav', 'Mid_Shaker_Roll_Fast_rr2.wav'], root=72),
         envelope='swell', track_pitch=False, rr=True, level=0.8, tone=(1200.0, 6000.0), max_seconds=3.0,
         loop=(0.4, 2.6, 0.5), body=(0.3, 1.5), tune=False),
    Spec('swell',
         vc('Idiophones/Struck Idiophones/Suspended Cymbal 1', ['susCymb1_roll_mp1_nloop.wav'], root=60),
         envelope='swell', track_pitch=False, level=0.35, tone=(1500.0, 7000.0), max_seconds=7.0,
         loop=(1.5, 6.5, 1.5), body=(1.5, 4.0), tune=False),
]


# --- assembled foley ------------------------------------------------------------------------------

CRACKLE_GRAINS = (
    'Idiophones/Struck Idiophones/Claves/Claves1_Hit_v1_rr1_Mid.wav',
    'Idiophones/Struck Idiophones/Claves/Claves2_Hit_v1_rr1_Mid.wav',
    'Idiophones/Struck Idiophones/Woodblock/wood_click_pp_rr1.wav',
    'Idiophones/Struck Idiophones/Woodblock/wood_click_mp.wav',
    'Idiophones/Struck Idiophones/Cabasa/Cabasa1_Hit_rr1_Mid.wav',
    'Idiophones/Struck Idiophones/Cabasa/Cabasa1_Hit_rr2_Mid.wav',
    'Idiophones/Struck Idiophones/Shaker, Small/Mid_Shaker_Slap_rr1.wav',
    'Idiophones/Struck Idiophones/Shaker, Small/Mid_Shaker_Slap_rr2.wav',
)
CRACKLE_BED = 'Membranophones/Other Membranophones/Ocean Drum/OceanDrum_Sus_3_Mid.wav'


def assemble_crackle(src: Path) -> list[tuple[np.ndarray, dict]]:
    """A fire from percussion: the first few milliseconds of claves, woodblock, cabasa and shaker
    strikes, scattered with power-law sizes the way a fire's pops are, over the hush of an ocean
    drum barely moving. Each grain is a real strike; only the arrangement is the fire.
    """
    grains = []
    sources = tuple(f'{VCSL}/{rel}' for rel in (*CRACKLE_GRAINS, CRACKLE_BED))
    for rel in CRACKLE_GRAINS:
        x = audio.trim_head(audio.load(src / VCSL / rel))
        for length in (0.004, 0.008, 0.016):
            g = x[: int(length * RATE)].copy()
            g *= np.hanning(len(g) * 2)[len(g):] ** 0.5
            grains.append(audio.highpass(g / audio.peak(g), 700.0, 2))
    roar_src = audio.load(src / VCSL / CRACKLE_BED)
    roar_src = audio.lowpass(audio.highpass(roar_src, 60.0), 380.0, 3)
    rng = np.random.default_rng(0x0F17E)

    def scatter(seconds: float, rate: float, roar_db: float) -> np.ndarray:
        n = int(seconds * RATE)
        y = np.zeros(n + RATE)
        t = 0.0
        while t < seconds:
            t += rng.exponential(1.0 / rate)
            size = min(0.5, 0.08 * rng.pareto(3.0) + 0.05)
            g = grains[rng.integers(len(grains))]
            pitch = rng.uniform(-5.0, 7.0)
            g = audio.shift(g, pitch)
            at = int(t * RATE)
            if at + len(g) < len(y):
                y[at:at + len(g)] += g * size * (1 if rng.random() > 0.5 else -1)
        y = y[:n]
        roar = roar_src[int(1.0 * RATE): int(1.0 * RATE) + n]
        roar = roar / (np.sqrt(np.mean(roar ** 2)) + 1e-12) * audio.undb(roar_db) * np.sqrt(np.mean(y ** 2))
        return y + roar[: n]

    out = []
    # The bed: eleven pops a second over a low hush, folded into a loop the sampler can hold.
    bed = scatter(9.0, 11.0, -6.0)
    bed, a, b = audio.make_loop(bed, 1.0, 8.6, 0.8)
    out.append((bed, dict(root=60, min_duration=0.2, loop=(a, b), sources=sources)))
    # Struck: one pop and a short trail of smaller ones, three ways.
    for _ in range(3):
        y = np.zeros(int(0.6 * RATE))
        g = grains[rng.integers(len(grains))]
        y[: len(g)] += g
        t = 0.0
        for _ in range(int(rng.integers(2, 5))):
            t += rng.uniform(0.03, 0.12)
            h = audio.shift(grains[rng.integers(len(grains))], rng.uniform(-3.0, 6.0)) * rng.uniform(0.15, 0.45)
            at = int(t * RATE)
            y[at:at + len(h)] += h[: len(y) - at]
        out.append((audio.trim_tail(y, 0.6, fade=0.05), dict(root=60, max_duration=0.2, sources=sources)))
    return out


def assemble_chirp(src: Path) -> list[tuple[np.ndarray, dict]]:
    """Birdsong from flute staccato: two or three clipped high notes in a falling-then-rising
    contour, the same shape the old chirp traced, but each note now a flautist's tongued attack.
    """
    names = ['LDFlute_stac_E5_v1_rr1.wav', 'LDFlute_stac_E5_v1_rr2.wav', 'LDFlute_stac_A5_v1_rr1.wav',
             'LDFlute_stac_C6_v1_rr1.wav']
    sources = tuple(f'{VSCO}/Woodwinds/Flute/stac/{n}' for n in names)
    notes = []
    for n in names:
        x = audio.trim_head(audio.load(src / VSCO / 'Woodwinds/Flute/stac' / n))
        root = note_of(n) + 12
        notes.append((x, root))
    rng = np.random.default_rng(0xB1BD)
    out = []
    contours = [[3.9, -5.0, 5.2], [0.0, 4.0], [5.0, 0.0, -2.0]]
    for contour in contours:
        y = np.zeros(int(0.9 * RATE))
        t = 0.0
        for step in contour:
            x, root = notes[rng.integers(len(notes))]
            # Every call is expressed relative to E5 (MIDI 88), the zone's root.
            k = audio.shift(x, (88 - root) + step)
            k = k[: int(rng.uniform(0.07, 0.12) * RATE)]
            k *= np.hanning(len(k) * 2)[len(k):]
            at = int(t * RATE)
            y[at:at + len(k)] += k[: len(y) - at]
            t += rng.uniform(0.09, 0.14)
        out.append((audio.trim_tail(audio.highpass(y, 1200.0), 0.9, fade=0.05), dict(root=88, sources=sources)))
    return out


ASSEMBLED = {
    'crackle': (assemble_crackle, dict(envelope='sustain', attack=0.0, release=0.3, level=0.17, tone=[1500.0, 5000.0],
                                       track_pitch=False, body=(0.0, 0.6))),
    'chirp': (assemble_chirp, dict(envelope='struck', level=0.63, track_pitch=True, body=(0.0, 0.3))),
}


# --- what each instrument is for -----------------------------------------------------------------

# One line per instrument, for SOURCES.md. The composer is the authority on what an instrument is
# asked to play; these say what recording stands in for it, which is what a reader of the bank needs.
NOTES = {
    'pad': 'Strings: a cello section below middle C and a violin section above it, at their softest '
           'recorded dynamic. Sustained and crossfade-looped; the section\'s own vibrato is the movement '
           'the old pad\'s filter sweep stood in for.',
    'epiano': 'Felt-soft upright piano, two velocity layers, so a soft note is a different strike rather '
              'than a quieter copy of a hard one.',
    'bell': 'Hand chimes: struck aluminium tubes with almost no strike noise.',
    'glass': 'Vibraphone with soft mallets and the motor off.',
    'pluck': 'Concert harp.',
    'mallet': 'Marimba, medium strikes.',
    'drone': 'Quiet organ pedal flue, looped: a note held for as long as a whole piece needs.',
    'flute': 'Flute without vibrato, looped, so a held note holds still.',
    'thump': 'Foley: a frame drum, muted, round-robin.',
    'clank': 'Foley: an anvil, two velocity layers, round-robin.',
    'tick': 'Foley: woodblock and claves, round-robin.',
    'hiss': 'Foley: a cabasa rub, high-passed and swelled.',
    'wind': 'Foley: an ocean drum swell, low-passed and looped.',
    'drip': 'Foley: glockenspiel bars damped to their first half-second, a drop landing on something tuned.',
    'rustle': 'Foley: a small shaker roll, looped.',
    'swell': 'Foley: a suspended cymbal roll, looped.',
    'crackle': 'A fire assembled from percussion: the first milliseconds of claves, woodblock, cabasa and '
               'shaker strikes, scattered with power-law sizes over the hush of an ocean drum. One looped '
               'bed and three struck pops; each grain is a real strike, only the arrangement is the fire.',
    'chirp': 'Birdsong from flute staccato: two or three clipped high notes in a falling-then-rising '
             'contour.',
}

LIBRARY_TITLES = {
    'VSCO-2-CE': 'VSCO-2 Community Edition, Versilian Studios',
    'VCSL': 'Versilian Community Sample Library (VCSL), Versilian Studios and contributors',
}


def midi_name(number: int) -> str:
    """Scientific pitch, C4 = 60 - the convention note_of reads the file names in."""
    names = ('C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B')
    return f'{names[number % 12]}{number // 12 - 1}'


def write_sources(sections: list[tuple[str, dict, list[dict]]]) -> None:
    """
    SOURCES.md: where every file in this directory came from, and how to check it.

    No date in it, on purpose. A rebuild from the same commits must leave this file as well as the
    .ogg files untouched, and a "last built" line would make that false on every run.
    """
    lines = [
        '# Where these sounds came from',
        '',
        'Every `.ogg` in this directory is made from recordings of real instruments, cut, tuned, looped '
        'and levelled by `tools/soul_music/build_bank.py` (#163). The recordings come from two libraries, '
        'both released under **CC0 1.0** (public domain dedication), so nothing here carries an '
        'attribution requirement. The credit below is given because it is owed, not because it is '
        'required:',
        '',
    ]

    for library in LIBRARIES:
        url = library.url.removesuffix('.git')
        lines.append(f'* **{LIBRARY_TITLES[library.name]}** - <{url}>, commit `{library.commit}`')

    lines += [
        '',
        '#209 asks that provenance be recorded because "CC0" and "CC-BY" are different promises. This '
        'bank makes the CC0 one, and it does so by construction: only CC0 libraries are read, each is '
        'pinned to a commit in `tools/soul_music/fetch_sources.py`, and every file says which recording '
        'it came from. A new source has to be CC0, and is recorded in both places before it is used.',
        '',
        '## Rebuilding',
        '',
        '```',
        'python3 tools/soul_music/fetch_sources.py /path/to/sources',
        'python3 tools/soul_music/build_bank.py --sources /path/to/sources',
        '```',
        '',
        'The second command rewrites `bank.json`, these files and this one. Run it on a clean tree and '
        '`git status` should show nothing; if it does not, the sources have moved or the scripts have, '
        'and that is worth finding out before a build ships. Needs numpy, scipy and soundfile, and an '
        '`ffmpeg` built with libvorbis - see `tools/soul_music/requirements.txt`.',
        '',
        '## Format and levels',
        '',
        'Pitches are scientific pitch with middle C = C4 = MIDI 60, whatever a library calls the same '
        'note in its file names (VSCO and VCSL do not agree with each other on that).',
        '',
        'Mono, 44100 Hz, Ogg Vorbis at libvorbis quality 5, encoded through ffmpeg in bit-exact mode so '
        'a rebuild is byte-identical. The checksum is of the samples as 16-bit PCM, and it is the same '
        'rounding `mix.py` uses for its own checksums; it is not a checksum of the `.ogg` file.',
        '',
        'Each instrument\'s `level` was first set so its stem, rendered by `MusicPreview` '
        '(`sh gradlew musicPreview -PpreviewArgs="--only stems --no-wav"`), measured what the oscillator '
        'it replaced measured, and then moved by ear. Each zone\'s `gain` brings its loudest 50 ms to one '
        'reference, so `level` is the one number per instrument a tuner changes. Do not edit `bank.json` '
        'or the `.ogg` files: change the script, and rebuild.',
        '',
        '## Instruments',
        '',
    ]

    for name, fields, rows in sections:
        pitched = 'pitched' if fields['track_pitch'] else 'unpitched'
        tone = f", tone {fields['tone'][0]:.0f}-{fields['tone'][1]:.0f} Hz" if fields.get('tone') else ''
        lines += [
            f'### {name}',
            '',
            NOTES[name],
            '',
            f'`level` {fields["level"]}, {fields["envelope"]} envelope, {pitched}{tone}.',
            '',
            '| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |',
            '| --- | --- | --- | --- | --- | --- | --- |',
        ]

        for row in rows:
            keys = 'all' if (row['lo'], row['hi']) == (0, 127) else (
                f"{midi_name(row['lo'])}+" if row['hi'] == 127 else
                f"-{midi_name(row['hi'])}" if row['lo'] == 0 else
                f"{midi_name(row['lo'])}-{midi_name(row['hi'])}")
            vel = 'all' if row['vel'] == (0.0, 1.0) else f"{row['vel'][0]:.1f}-{row['vel'][1]:.1f}"
            source = '; '.join(f'`{path}`' for path in row['sources'])
            lines.append(
                f"| `{Path(row['file']).name}` | {midi_name(row['root'])} ({row['root']}) | {keys} | {vel} | "
                f"{row['gain']:.3f} | `{row['sha'][:16]}…` | {source} |")

        lines.append('')

    lines += ['', '_Written by `build_bank.py`; see the note on rebuilding above._', '']
    (OUT / 'SOURCES.md').write_text('\n'.join(lines), encoding='utf-8')


# --- building -----------------------------------------------------------------------------------

@dataclass
class Zone:
    file: str
    root: int
    audio: np.ndarray
    vel: tuple[float, float] = (0.0, 1.0)
    dur: tuple[float, float] = (0.0, 1e6)
    loop: tuple[int, int] | None = None
    gain: float = 1.0
    lo: int = 0
    hi: int = 127
    report: dict = field(default_factory=dict)
    sources: tuple[str, ...] = ()                    # library-relative paths, from the library's root


def loudness_db(x: np.ndarray, body: tuple[float, float]) -> float:
    """The loudest 50 ms inside the body window.

    Not the window's RMS: a fire's pops and a cabasa's scrape are mostly gaps, and an average over
    the gaps turns them up until the pops clip. The loudest short stretch is what an ear levels by.
    """
    a, b = int(body[0] * RATE), max(int(body[1] * RATE), int(body[0] * RATE) + 2205)
    env = audio.envelope_db(x[a:b] if b <= len(x) else x[a:])
    return float(np.max(env))


def matched_gain(x: np.ndarray, body: tuple[float, float], trim_db: float) -> float:
    return audio.undb(UNIT_RMS_DB - loudness_db(x, body) + trim_db)


def prepare(spec: Spec, s: Source, src: Path) -> Zone:
    x = audio.load(src / s.path)
    x = audio.highpass(x, spec.highpass, 2)
    if spec.lowpass:
        x = audio.lowpass(x, spec.lowpass, 2)
    x = audio.trim_head(x)
    root = s.root if s.root is not None else note_of(Path(s.path).stem) + s.octave
    report = {}
    if spec.tune and spec.track_pitch:
        measured = audio.fundamental(x, root)
        if measured is not None:
            cents = 100.0 * (root - measured)
            report['cents'] = round(cents, 1)
            if 3.0 < abs(cents) < 45.0:
                x = audio.retune(x, cents)
    loop = None
    if spec.loop:
        start, end, xfade = spec.loop
        x = x[: int((end + 0.1) * RATE)]
        x = audio.fade_in(x, 0.002)
        x, a, b = audio.make_loop(x, start, end, xfade)
        loop = (a, b)
    else:
        x = audio.trim_tail(x, spec.max_seconds)
        x = audio.fade_in(x, 0.0015)
    if spec.shape:
        x = spec.shape(x)
    scale = STORE_PEAK / max(audio.peak(x), 1e-9)
    x = x * scale
    gain = matched_gain(x, spec.body, s.gain_db)
    return Zone(file='', root=root, audio=x, vel=s.vel, dur=s.dur, loop=loop, gain=gain, report=report,
                sources=(s.path,))


def key_ranges(zones: list[Zone], rr: bool) -> None:
    if rr:
        return
    roots = sorted({z.root for z in zones})
    for z in zones:
        i = roots.index(z.root)
        z.lo = 0 if i == 0 else (roots[i - 1] + z.root) // 2 + 1
        z.hi = 127 if i == len(roots) - 1 else (z.root + roots[i + 1]) // 2


def pcm_sha256(x: np.ndarray) -> str:
    """The checksum SOURCES.md gives: the samples as 16-bit PCM, rounded the way mix.py rounds them."""
    return hashlib.sha256(np.round(np.clip(x, -1.0, 1.0) * 32767.0).astype('<i2').tobytes()).hexdigest()


def write_instrument(name: str, zones: list[Zone], fields: dict) -> tuple[dict, list[dict]]:
    entries = []
    rows = []
    for i, z in enumerate(sorted(zones, key=lambda z: (z.root, z.vel, z.dur))):
        rel = f'{name}/{name}_{i:02d}.ogg'
        path = OUT / rel
        audio.encode(z.audio, path)
        frames = audio.decoded_length(path)
        entry = {'file': rel, 'root': z.root}
        if z.lo != 0 or z.hi != 127:
            entry.update(lo=z.lo, hi=z.hi)
        if z.vel != (0.0, 1.0):
            entry.update(vel_lo=z.vel[0], vel_hi=z.vel[1])
        if z.dur != (0.0, 1e6):
            entry.update(min_duration=z.dur[0], max_duration=z.dur[1])
        entry['gain'] = round(float(z.gain), 4)
        if z.loop:
            a, b = z.loop
            if b > frames:
                raise SystemExit(f'{rel}: loop end {b} past decoded length {frames}')
            entry.update(loop_start=int(a), loop_end=int(b))
        entries.append(entry)
        rows.append(dict(file=rel, root=z.root, lo=z.lo, hi=z.hi, vel=z.vel, gain=z.gain,
                         sha=pcm_sha256(z.audio), sources=z.sources))
        print(f'  {rel:28s} root={z.root:3d} {z.lo:3d}-{z.hi:<3d} {len(z.audio) / RATE:5.2f}s '
              f'gain={z.gain:6.3f} {z.report}')
    out = {k: v for k, v in fields.items() if v is not None}
    out['zones'] = entries
    return out, rows


def check_pins(src: Path) -> None:
    """
    A bank cut from a different commit is a different bank, and SOURCES.md would name the wrong one.

    A checkout made by fetch_sources.py is checked. An unpacked archive has no history to check, so it
    is taken on trust and SOURCES.md still names the pinned commit - which is the commit it must be.
    """
    for library in LIBRARIES:
        checkout = src / library.name

        if not (checkout / '.git').exists():
            print(f'{library.name}: not a git checkout, so its commit is taken on trust')
            continue

        head = subprocess.run(['git', '-C', str(checkout), 'rev-parse', 'HEAD'], capture_output=True,
                              text=True, check=True).stdout.strip()

        if head != library.commit:
            raise SystemExit(f'{checkout} is at {head}, not the pinned {library.commit}; check the pinned '
                             'commit out, or re-pin it in fetch_sources.py')


def build(src: Path) -> None:
    check_pins(src)

    for old in OUT.glob('*/*.ogg'):
        old.unlink()
    instruments = {}
    sections = []
    for spec in SPECS:
        print(spec.name)
        zones = [prepare(spec, s, src) for s in spec.sources]
        key_ranges(zones, spec.rr)
        fields = dict(
            level=spec.level, envelope=spec.envelope, attack=spec.attack, release=spec.release,
            track_pitch=spec.track_pitch, tone=list(spec.tone) if spec.tone else None)
        instruments[spec.name], rows = write_instrument(spec.name, zones, fields)
        sections.append((spec.name, fields, rows))
    for name, (make, f) in ASSEMBLED.items():
        print(name)
        body = f.pop('body')
        zones = []
        for x, meta in make(src):
            x = x * (STORE_PEAK / max(audio.peak(x), 1e-9))
            z = Zone(file='', root=meta['root'], audio=x, gain=matched_gain(x, body, 0.0),
                     sources=meta['sources'])
            if 'loop' in meta:
                z.loop = meta['loop']
            z.dur = (meta.get('min_duration', 0.0), meta.get('max_duration', 1e6))
            zones.append(z)
        instruments[name], rows = write_instrument(name, zones, f)
        sections.append((name, dict(f), rows))
        f['body'] = body
    manifest = {'format': 1, 'sample_rate': RATE, 'instruments': instruments}
    (OUT / 'bank.json').write_text(json.dumps(manifest, indent=2) + '\n')
    write_sources(sections)
    total = sum(audio.decoded_length(p) for p in OUT.glob('*/*.ogg')) / RATE
    size = sum(p.stat().st_size for p in OUT.glob('*/*.ogg')) / 1e6
    print(f'{total:.1f} s of audio, {size:.2f} MB on disk')


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--sources', type=Path, required=True,
                   help='directory holding the VSCO-2-CE and VCSL checkouts (see fetch_sources.py)')
    build(p.parse_args().sources)


if __name__ == '__main__':
    main()
