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
# written against; then moved by ear where a recording wanted it - glass 2 dB under, so it recedes
# rather than dings; the crackle down and darker,
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
    pick: int | None = None       # a long take: start at its nth-loudest transient (0 = loudest)
    lead: float = 0.02            # seconds kept before that transient
    at: float | None = None       # a long take: or start this many seconds in
    loop: tuple[float, float, float] | None = None   # overrides the instrument's loop for this zone


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


def fsnd(sound_id: int, **kw) -> Source:
    """A Freesound recording, by id. Every one is CC0, checked on its own page when fetched
    (fetch_sources.py refuses one that is not), and credited by author in SOURCES.md."""
    return Source(f'freesound/{sound_id}.ogg', **kw)


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

    # Clarinet long tones at its softest dynamic, looped. The bell's part - the lead of a cold soul and
    # of a hollow one - was hand chimes, and a lead line of chimes is a soul that dings at its owner.
    # The clarinet's low register is as hollow as a sound gets without striking anything.
    Spec('bell',
         vs('Woodwinds/Clarinet/susLong',
            ['DCClar_susLong_D2_v1_rr1_sum.wav', 'DCClar_susLong_F2_v1_rr1_sum.wav',
             'DCClar_susLong_A#2_v1_rr1_sum.wav', 'DCClar_susLong_D3_v1_rr1_sum.wav',
             'DCClar_susLong_F3_v1_rr1_sum.wav', 'DCClar_susLong_A#3_v1_rr1_sum.wav',
             'DCClar_susLong_D4_v1_rr1_sum.wav', 'DCClar_susLong_F4_v1_rr1_sum.wav']),
         envelope='sustain', attack=0.08, release=0.9, level=0.284, tone=(1500.0, 7000.0),
         max_seconds=4.0, loop=(1.2, 3.8, 0.6), body=(0.4, 1.4)),

    # A held glass note is a bowed vibraphone, which swells instead of striking; only a quick one is
    # still struck, with a soft mallet, 4 dB down. The first bank struck every glass note, and between
    # that and the chimes the soul dinged far too much.
    Spec('glass',
         [*vc('Idiophones/Struck Idiophones/Vibraphone/Bowed',
              ['Vibes_bowed_A2_rr1_Main.wav', 'Vibes_bowed_E3_rr1_Main.wav', 'Vibes_bowed_G3_rr1_Main.wav',
               'Vibes_bowed_D4_rr1_Main.wav', 'Vibes_bowed_A4_rr1_Main.wav', 'Vibes_bowed_E5_rr1_Main.wav'],
              dur=(1.2, 1e6), loop=(2.0, 4.4, 0.8)),
          *vc('Idiophones/Struck Idiophones/Vibraphone/Soft Mallets',
              ['Vibes_soft_F2_v2_rr1_Main.wav', 'Vibes_soft_A2_v2_rr1_Main.wav', 'Vibes_soft_C3_v2_rr1_Main.wav',
               'Vibes_soft_E3_v2_rr1_Main.wav', 'Vibes_soft_G3_v2_rr1_Main.wav', 'Vibes_soft_B3_v2_rr1_Main.wav',
               'Vibes_soft_D4_v2_rr1_Main.wav', 'Vibes_soft_F4_v2_rr1_Main.wav'], dur=(0.0, 1.2), gain_db=-4.0)],
         envelope='sustain', attack=0.0, release=1.0, level=0.267, tone=(1600.0, 5000.0),
         max_seconds=2.2, body=(0.0, 4.0)),

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
    # Real sound effects from Freesound from here on, where a recording of the thing itself exists:
    # an anvil from a percussion library, played with a stick, reads as a bell, and a fire assembled
    # from clave strikes reads as claves.
    Spec('clank',
         [fsnd(270588, root=72), fsnd(270589, root=72), fsnd(521972, root=72),
          fsnd(363169, root=72, pick=0), fsnd(363169, root=72, pick=2), fsnd(103071, root=72, pick=0)],
         track_pitch=False, rr=True, level=0.665, tone=(2000.0, 6000.0), max_seconds=0.9, body=(0.0, 0.2),
         tune=False),
    # A clock's own ticks, cut from a close-miked minute of one.
    Spec('tick',
         [fsnd(380782, root=84, pick=n) for n in range(4)],
         track_pitch=False, rr=True, level=0.27, tone=(2000.0, 8000.0), max_seconds=0.3, body=(0.0, 0.05),
         tune=False),
    Spec('hiss',
         [fsnd(365071, root=72, pick=0, lead=0.05), fsnd(234782, root=72), fsnd(398077, root=72, pick=0, lead=0.05)],
         envelope='swell', track_pitch=False, rr=True, level=0.986, tone=(2000.0, 4500.0), max_seconds=0.9,
         body=(0.0, 0.4), highpass=300.0, lowpass=6000.0, tune=False),
    Spec('wind',
         [fsnd(117136, root=60, at=10.0), fsnd(181252, root=60, at=20.0)],
         envelope='swell', track_pitch=False, rr=True, level=0.347, tone=(700.0, 2600.0), max_seconds=7.0,
         loop=(1.0, 6.5, 1.5), body=(1.0, 4.0), lowpass=3000.0, tune=False),
    Spec('drip',
         [fsnd(i, root=76, pick=0, lead=0.005) for i in (174718, 166325)],
         track_pitch=False, rr=True, level=0.296, max_seconds=0.6, body=(0.0, 0.1), highpass=200.0, tune=False),
    Spec('chirp',
         [fsnd(233255, root=88), fsnd(233254, root=88), fsnd(812033, root=88)],
         track_pitch=False, rr=True, level=0.207, max_seconds=1.0, body=(0.0, 0.3), highpass=800.0, tune=False),
    Spec('rustle',
         [fsnd(437356, root=72, pick=0, lead=0.2), fsnd(443065, root=72, at=0.3), fsnd(106114, root=72)],
         envelope='swell', track_pitch=False, rr=True, level=0.408, tone=(1200.0, 6000.0), max_seconds=3.0,
         body=(0.3, 1.5), tune=False),
    Spec('swell',
         vc('Idiophones/Struck Idiophones/Suspended Cymbal 1', ['susCymb1_roll_mp1_nloop.wav'], root=60),
         envelope='swell', track_pitch=False, level=0.35, tone=(1500.0, 7000.0), max_seconds=7.0,
         loop=(1.5, 6.5, 1.5), body=(1.5, 4.0), tune=False),
]


# --- assembled foley ------------------------------------------------------------------------------

def assemble_crackle(src: Path) -> list[tuple[np.ndarray, dict]]:
    """A real fire: a wood stove recorded close, with a second fire under it. The bed is a loop cut
    from the two takes together; the struck pops are the brighter take's own loudest moments.
    """
    stove_rel, fire_rel = 'freesound/532191.ogg', 'freesound/414767.ogg'
    stove = audio.highpass(audio.load(src / stove_rel), 60.0)
    fire = audio.highpass(audio.load(src / fire_rel), 60.0)
    sources = (stove_rel, fire_rel)
    out = []
    a, n = int(10.0 * RATE), int(9.8 * RATE)
    bed = stove[a:a + n] + audio.lowpass(fire[a:a + n], 4000.0) * 0.35
    bed, la, lb = audio.make_loop(bed, 1.0, 9.6, 1.0)
    out.append((bed, dict(root=60, min_duration=0.2, loop=(la, lb), sources=sources)))
    for nth in range(3):
        pop = audio.trim_tail(audio.trim_head(from_transient(fire, nth, 0.01, 0.5)), 0.5, fade=0.2)
        out.append((audio.fade_in(pop, 0.002), dict(root=60, max_duration=0.2, sources=(fire_rel,))))
    return out


ASSEMBLED = {
    'crackle': (assemble_crackle, dict(envelope='sustain', attack=0.0, release=0.3, level=0.17, tone=[1500.0, 5000.0],
                                       track_pitch=False, body=(0.0, 0.6))),
}


NOTES = {
    'pad': 'Strings: a cello section below middle C and a violin section above it, at their softest '
           'recorded dynamic. Sustained and crossfade-looped; the section\'s own vibrato is the movement '
           'the old pad\'s filter sweep stood in for.',
    'epiano': 'Felt-soft upright piano, two velocity layers, so a soft note is a different strike rather '
              'than a quieter copy of a hard one.',
    'bell': 'Clarinet long tones at its softest dynamic, looped: the bell\'s part with nothing struck, because a lead line of chimes dinged.',
    'glass': 'A bowed vibraphone for held notes, and the soft-mallet vibraphone, 4 dB down, only for quick ones.',
    'pluck': 'Concert harp.',
    'mallet': 'Marimba, medium strikes.',
    'drone': 'Quiet organ pedal flue, looped: a note held for as long as a whole piece needs.',
    'flute': 'Flute without vibrato, looped, so a held note holds still.',
    'thump': 'Foley: a frame drum, muted, round-robin.',
    'clank': 'Foley: a hammer on an anvil, recorded by smiths on Freesound, round-robin.',
    'tick': 'Foley: a clock\'s own ticks, cut from a close-miked take, round-robin.',
    'hiss': 'Foley: bursts of steam, rolled off above 6 kHz so a vent breathes rather than sizzles.',
    'wind': 'Foley: arctic wind and a gentle breeze, low-passed and looped.',
    'drip': 'Foley: single water drops.',
    'rustle': 'Foley: rustling leaves.',
    'swell': 'Foley: a suspended cymbal roll, looped.',
    'crackle': 'A real fire: a wood stove close by with a second fire under it, one looped bed and three of its own pops.',
    'chirp': 'Single bird chirps.',
}

LIBRARY_TITLES = {
    'VSCO-2-CE': 'VSCO-2 Community Edition, Versilian Studios',
    'VCSL': 'Versilian Community Sample Library (VCSL), Versilian Studios and contributors',
}


def midi_name(number: int) -> str:
    """Scientific pitch, C4 = 60 - the convention note_of reads the file names in."""
    names = ('C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B')
    return f'{names[number % 12]}{number // 12 - 1}'


SOURCES_ROOT: Path = Path('.')


def freesound_meta(sound_id: int) -> dict:
    meta = json.loads((SOURCES_ROOT / 'freesound' / f'{sound_id}.json').read_text())
    meta['title'] = meta['title'].removeprefix('Freesound - ').replace('|', '/')
    meta['title'] = meta['title'].removesuffix(f" by {meta['user']}")
    return meta


def write_sources(sections: list[tuple[str, dict, list[dict]]]) -> None:
    """
    SOURCES.md: where every file in this directory came from, and how to check it.

    No date in it, on purpose. A rebuild from the same commits must leave this file as well as the
    .ogg files untouched, and a "last built" line would make that false on every run.
    """
    lines = [
        '# Where these sounds came from',
        '',
        'Every `.ogg` in this directory is made from real recordings, cut, tuned, looped and levelled by '
        '`tools/soul_music/build_bank.py` (#163). Every recording is released under **CC0 1.0** (public '
        'domain dedication), so nothing here carries an attribution requirement. The credit below is '
        'given because it is owed, not because it is required. The instruments come from two libraries:',
        '',
    ]

    for library in LIBRARIES:
        url = library.url.removesuffix('.git')
        lines.append(f'* **{LIBRARY_TITLES[library.name]}** - <{url}>, commit `{library.commit}`')

    used = sorted({int(Path(path).stem) for _, _, rows in sections for row in rows for path in row['sources']
                   if path.startswith('freesound/')})
    lines += [
        '',
        'and the foley from Freesound recordings, each checked as CC0 on its own page when it was fetched '
        '(`fetch_sources.py` refuses one that is not):',
        '',
        '| Freesound | Author | Recording |',
        '| --- | --- | --- |',
    ]
    for sid in used:
        meta = freesound_meta(sid)
        lines.append(f"| [{sid}](https://freesound.org/s/{sid}/) | {meta['user']} | {meta['title']} |")
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
            source = '; '.join(
                f"Freesound [{Path(path).stem}](https://freesound.org/s/{Path(path).stem}/) by "
                f"{freesound_meta(int(Path(path).stem))['user']}" if path.startswith('freesound/') else f'`{path}`'
                for path in dict.fromkeys(row['sources']))
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


def from_transient(x: np.ndarray, nth: int, lead: float, length: float) -> np.ndarray:
    """A long take from just before its nth-loudest transient: how one strike of a hammer, one tick
    of a clock or one pop of a fire is found in a recording of many. Found by the take's own
    envelope, so a rebuild picks the same moment."""
    env = audio.envelope_db(audio.highpass(x, 300.0), 0.01)
    env[max(0, len(env) - int(length * RATE)):] = -200.0
    for _ in range(nth):
        k = int(np.argmax(env))
        env[max(0, k - RATE // 2): k + RATE // 2] = -200.0
    k = int(np.argmax(env))
    return x[max(0, k - int(lead * RATE)):]


def prepare(spec: Spec, s: Source, src: Path) -> Zone:
    x = audio.load(src / s.path)
    x = audio.highpass(x, spec.highpass, 2)
    if spec.lowpass:
        x = audio.lowpass(x, spec.lowpass, 2)
    if s.at is not None:
        x = x[int(s.at * RATE):]
    if s.pick is not None:
        x = from_transient(x, s.pick, s.lead, spec.max_seconds)
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
    zone_loop = s.loop or spec.loop
    if zone_loop:
        start, end, xfade = zone_loop
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
    """Split the keyboard between zones, separately for each velocity and duration layer - a held
    glass note and a quick one are played from different recordings with different roots, and one
    layer's split must not leave gaps in the other's."""
    if rr:
        return
    for layer in {(z.vel, z.dur) for z in zones}:
        members = [z for z in zones if (z.vel, z.dur) == layer]
        roots = sorted({z.root for z in members})
        for z in members:
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
    global SOURCES_ROOT
    SOURCES_ROOT = src
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
