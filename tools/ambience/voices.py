# File created ~ 17 - 9 - 2026
"""
What each of the soul's voices is made of (#213).

One function per pole of a :class:`SoulAxis`, and one per voice. The poles are the *primitives*;
the voices are written in terms of them.

**The contested voices are built from both poles' generators, not from a mix of their outputs.**
This is the one rule in this file that is easy to break by doing the obvious thing, and it is the
same rule ``SoulAxis`` holds for colour: warm and cold at once is steam, and steam is not the
average of a crackle and a chime. So ``steam`` does not render ``warm`` and ``cold`` and add them
together - it drives a hiss with the warm generator's own grain envelope and damps the cold
generator's partials down to the ring inside it. ``quickened`` strikes the wrought generator's metal
with the arcane generator's sweep. ``overgrown`` hears the verdant generator's rustle through the
hollow generator's resonant body. Each is a third sound, made out of both machineries.

The palette itself is #213's, given almost verbatim in the issue, and the constraint over all of it
is #166's: quiet, distant, and no attack sharp enough to read as an event. A player should
half-notice one of these and not turn round to look for it.
"""

from __future__ import annotations

import numpy as np

from dsp import (
    band,
    edges,
    envelope,
    highpass,
    lfo,
    lowpass,
    normalise,
    onsets,
    partial,
    place,
    reverb,
    smooth,
    spectral_shape,
    tilt,
    white,
)

#: One-shots are mastered well down. They are heard against a game that is otherwise silent, and
#: the mod's own volume knob sits on top of this - see AmbienceSettings.DEFAULT_SOUND_VOLUME.
ONE_SHOT_RMS_DB = -29.0

#: And the beds lower still, because they are never not playing.
BED_RMS_DB = -38.0


# --------------------------------------------------------------------------------------------
# The poles
# --------------------------------------------------------------------------------------------


def warm(rng: np.random.Generator, count: int, sample_rate: int, density: float = 0.055,
         brightness: float = 2_600.0, body: float = 190.0) -> np.ndarray:
    """
    Low crackle: filtered noise through an envelope, with random micro-onsets.

    Not a campfire. A campfire recording is a sound with a place already in it, and what is wanted
    here is the ember rather than the hearth - so the grains are sparse, the band is narrow, and
    there is no bed of hiss under them to say "fire".
    """
    out = np.zeros(count)
    noise = spectral_shape(white(rng, count), sample_rate, band(body, brightness, slope=0.3))

    for at in onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0):
        length = int((0.03 + 0.09 * rng.random()) * sample_rate)
        grain = noise[at : at + length]

        if grain.size < 8:
            continue

        shape = envelope(grain.size, sample_rate, attack=0.004 + 0.01 * rng.random(),
                         decay=0.02 + 0.05 * rng.random())

        place(out, grain * shape, at, gain=0.4 + 0.6 * rng.random())

    return out


def cold(rng: np.random.Generator, count: int, sample_rate: int, density: float = 0.55,
         centre: float = 1_450.0, decay: float = 1.1, detune: float = 0.004) -> np.ndarray:
    """
    Sparse high partials with a long decay and a little detune - a chime with the block taken off it.

    The detune is what stops it being a synthesiser: two partials a few cents apart beat slowly
    against each other, which is how a struck physical thing behaves and how an oscillator does not.
    """
    out = np.zeros(count)

    for at in onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0):
        length = int(min(count - at, (decay * 4.0) * sample_rate))

        if length < 64:
            continue

        frequency = centre * (0.6 + 1.6 * rng.random())
        voiced = np.zeros(length)

        for offset in (-detune, 0.0, detune):
            voiced += partial(length, sample_rate, frequency * (1.0 + offset),
                              decay * (0.7 + 0.6 * rng.random()), phase=rng.random() * 6.283)

        # a touch of the octave above, at a fifth of the level: a single sine is a test tone
        voiced += 0.2 * partial(length, sample_rate, frequency * 2.01, decay * 0.4)

        shape = envelope(length, sample_rate, attack=0.012, decay=decay, curve=1.4)

        place(out, voiced * shape / 3.2, at, gain=0.5 + 0.5 * rng.random())

    return out


def arcane(rng: np.random.Generator, count: int, sample_rate: int, density: float = 1.4,
           carrier: float = 320.0, index: float = 4.0) -> np.ndarray:
    """
    Slow FM sweeps: a carrier whose modulation index rises and falls over a second or more.

    Slow is the whole of it. FM with a fast envelope is a bell or a laser; FM with an envelope
    measured in seconds is something arriving, which is what an arcane room ought to sound like.
    """
    out = np.zeros(count)

    for at in onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0):
        length = int(min(count - at, (1.6 + 1.8 * rng.random()) * sample_rate))

        if length < 256:
            continue

        t = np.arange(length) / sample_rate
        base = carrier * (0.7 + 0.9 * rng.random())
        ratio = 1.0 + 0.61803 * (1 + int(2 * rng.random()))  # irrational-ish: no harmonic lock

        swell = np.sin(np.pi * np.clip(t / (length / sample_rate), 0.0, 1.0)) ** 1.5
        modulator = np.sin(2.0 * np.pi * base * ratio * t) * index * swell

        voiced = np.sin(2.0 * np.pi * base * t + modulator)
        shape = envelope(length, sample_rate, attack=0.35 + 0.4 * rng.random(),
                         decay=0.9, curve=1.2)

        place(out, voiced * shape * swell, at, gain=0.5 + 0.5 * rng.random())

    return out


def wrought(rng: np.random.Generator, count: int, sample_rate: int, density: float = 1.1,
            fundamental: float = 210.0, decay: float = 0.35,
            strikes: np.ndarray | None = None) -> np.ndarray:
    """
    Damped metallic modes: a few inharmonic partials with a fast decay.

    ``strikes`` lets another voice decide *when* the metal is struck while this decides what it
    sounds like - that is how ``quickened`` is built, and why it is not two sounds played together.
    """
    out = np.zeros(count)
    positions = list(strikes) if strikes is not None else onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0)

    for at in positions:
        length = int(min(count - int(at), (decay * 6.0) * sample_rate))

        if length < 64:
            continue

        base = fundamental * (0.8 + 0.5 * rng.random())
        voiced = np.zeros(length)

        # ratios off a struck bar rather than a harmonic series - this is why it reads as metal
        for ratio, level in ((1.0, 1.0), (2.76, 0.6), (5.40, 0.33), (8.93, 0.16)):
            voiced += level * partial(length, sample_rate, base * ratio,
                                      decay / (1.0 + 0.5 * ratio), phase=rng.random() * 6.283)

        shape = envelope(length, sample_rate, attack=0.006, decay=decay, curve=1.6)

        place(out, voiced * shape / 2.1, int(at), gain=0.4 + 0.6 * rng.random())

    return out


def verdant(rng: np.random.Generator, count: int, sample_rate: int, density: float = 0.4,
            centre: float = 3_200.0, width: float = 2.4) -> np.ndarray:
    """
    Soft broadband rustle - leaves moving, with nothing behind them.

    Deliberately the least eventful thing in the palette. A rustle that can be located is a mob.
    """
    out = np.zeros(count)
    noise = spectral_shape(white(rng, count), sample_rate,
                           band(centre / width, centre * width, edge=0.5, slope=0.2))

    for at in onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0):
        length = int((0.25 + 0.6 * rng.random()) * sample_rate)
        grain = noise[at : at + length]

        if grain.size < 64:
            continue

        shape = envelope(grain.size, sample_rate, attack=0.09 + 0.12 * rng.random(),
                         decay=0.22 + 0.2 * rng.random(), curve=1.5)

        # slow amplitude wander inside the grain, so it breathes rather than swells once
        wander = lfo(grain.size, sample_rate, rate=1.7 + 2.3 * rng.random(),
                     phase=rng.random() * 6.283, low=0.55, high=1.0)

        place(out, grain * shape * wander, at, gain=0.5 + 0.5 * rng.random())

    return out


def hollow(rng: np.random.Generator, count: int, sample_rate: int, density: float = 2.2,
           frequency: float = 58.0, decay: float = 1.5) -> np.ndarray:
    """
    A single low resonant thud with air around it.

    The air matters more than the thud: a low sine on its own is a subwoofer test, and what makes
    this read as an empty space is the breath of filtered noise that comes in with it and outlasts it.
    """
    out = np.zeros(count)
    air = spectral_shape(white(rng, count), sample_rate, band(120.0, 900.0, edge=0.6, slope=0.6))

    for at in onsets(rng, count, sample_rate, mean_gap=density, jitter=1.0):
        length = int(min(count - at, (decay * 4.0) * sample_rate))

        if length < 128:
            continue

        base = frequency * (0.85 + 0.35 * rng.random())
        thud = partial(length, sample_rate, base, decay) + 0.35 * partial(length, sample_rate, base * 1.48, decay * 0.5)
        thud *= envelope(length, sample_rate, attack=0.02, decay=decay, curve=1.3)

        breath = air[at : at + length]
        breath = breath * envelope(breath.size, sample_rate, attack=0.18, decay=decay * 1.6, curve=1.2)

        place(out, thud * 0.8 + breath[: length] * 0.45, at, gain=0.5 + 0.5 * rng.random())

    return out


def base(rng: np.random.Generator, count: int, sample_rate: int) -> np.ndarray:
    """
    The soul itself, whatever is built in it - the voice that is always in the draw.

    It is the only one that is not made of a room, so it is not made of grains either: a single very
    slow swell of low noise, with a thin partial drifting through it. A place, not a thing in one.
    """
    out = np.zeros(count)

    bedding = spectral_shape(white(rng, count), sample_rate, band(70.0, 1_100.0, edge=0.7, slope=0.7))
    swell = envelope(count, sample_rate, attack=0.9 + 0.5 * rng.random(), decay=1.4, curve=1.1)

    drift = partial(count, sample_rate, 96.0 * (0.9 + 0.2 * rng.random()), 2.2)
    drift += 0.4 * partial(count, sample_rate, 143.0, 1.7)

    out += bedding * swell * 0.9
    out += drift * swell * 0.25

    return out


# --------------------------------------------------------------------------------------------
# The contested voices - built from both poles' generators
# --------------------------------------------------------------------------------------------


def steam(rng: np.random.Generator, count: int, sample_rate: int) -> np.ndarray:
    """
    Warm and cold at once. Not a crackle plus a chime: the crackle is what opens the hiss.

    The warm generator is run for its *envelope* - where its grains fall and how hard - and that
    envelope is what a narrow band of high noise is played through. The cold generator supplies the
    ring inside the hiss, damped to a fraction of its own decay so it is a resonance rather than a
    chime. What comes out is neither pole and is not between them.
    """
    grains = warm(rng, count, sample_rate, density=0.05, brightness=3_400.0, body=260.0)
    gate = smooth(np.abs(grains), sample_rate, 0.035)
    gate /= max(float(np.max(gate)), 1e-9)

    hiss = spectral_shape(white(rng, count), sample_rate, band(1_900.0, 7_000.0, edge=0.5, slope=0.4))
    ring = cold(rng, count, sample_rate, density=0.8, centre=2_100.0, decay=0.22, detune=0.011)

    return hiss * gate * 0.9 + ring * 0.45


def quickened(rng: np.random.Generator, count: int, sample_rate: int) -> np.ndarray:
    """
    Worked matter and the arcane at once: the sweep is what strikes the metal.

    The arcane generator is rendered first and its own onsets are handed to the wrought generator as
    strike positions, so every piece of metal here rings because something arrived - which is a
    different sound from a forge and a portal in the same room.
    """
    sweeps = arcane(rng, count, sample_rate, density=1.3, carrier=270.0, index=3.2)

    energy = smooth(np.abs(sweeps), sample_rate, 0.12)
    threshold = float(np.max(energy)) * 0.45
    above = energy > threshold
    strikes = np.flatnonzero(above & ~np.roll(above, 1))

    metal = wrought(rng, count, sample_rate, fundamental=340.0, decay=0.5, strikes=strikes)

    return sweeps * 0.75 + metal * 0.7


def overgrown(rng: np.random.Generator, count: int, sample_rate: int) -> np.ndarray:
    """
    Growing things and emptied ones at once: the rustle is heard through the hollow.

    The hollow generator's resonance is used as the body the verdant generator's rustle sounds
    inside, rather than as a thud beside it - decay that is also alive, which is what the axis says
    this reading is.
    """
    rustle = verdant(rng, count, sample_rate, density=0.35, centre=2_400.0, width=2.8)
    body = hollow(rng, count, sample_rate, density=2.6, frequency=72.0, decay=1.9)

    # the hollow's own envelope opens a low resonance in the rustle, so the two are one sound
    opening = smooth(np.abs(body), sample_rate, 0.25)
    opening /= max(float(np.max(opening)), 1e-9)

    resonant = spectral_shape(rustle, sample_rate, band(180.0, 1_400.0, edge=0.6))

    return rustle * 0.45 + resonant * opening * 1.4 + body * 0.55


#: Voice id (matching ``SoulVoice``, lowercased) to the function that renders one.
VOICES = {
    "base": base,
    "warm": warm,
    "cold": cold,
    "steam": steam,
    "arcane": arcane,
    "wrought": wrought,
    "quickened": quickened,
    "verdant": verdant,
    "hollow": hollow,
    "overgrown": overgrown,
}

#: How long one of each voice runs, in seconds. Short: a one-shot is a moment, not a passage.
VOICE_SECONDS = {
    "base": 4.5,
    "warm": 3.0,
    "cold": 4.0,
    "steam": 3.4,
    "arcane": 4.2,
    "wrought": 3.2,
    "quickened": 4.0,
    "verdant": 3.4,
    "hollow": 4.2,
    "overgrown": 4.0,
}

#: A little room on each one-shot, so the palette shares one sense of space. Nothing like the bed's.
VOICE_REVERB = {
    "base": 0.35,
    "warm": 0.18,
    "cold": 0.45,
    "steam": 0.3,
    "arcane": 0.5,
    "wrought": 0.4,
    "quickened": 0.45,
    "verdant": 0.2,
    "hollow": 0.55,
    "overgrown": 0.4,
}


def render_voice(voice: str, rng: np.random.Generator, sample_rate: int) -> np.ndarray:
    """One finished one-shot: rendered, given its room, trimmed and mastered."""
    count = int(VOICE_SECONDS[voice] * sample_rate)
    dry = VOICES[voice](rng, count, sample_rate)

    wet_share = VOICE_REVERB[voice]

    if wet_share > 0.0:
        wet = reverb(dry, sample_rate, size=0.6 + wet_share, decay=0.6 + 0.25 * wet_share,
                     damping_hz=2_400.0, combs=4)
        dry = dry * (1.0 - 0.5 * wet_share) + wet * wet_share

    # nothing below 45 Hz survives: it is felt rather than heard, costs bitrate, and on the small
    # speakers most of this will be played through it is only distortion
    dry = spectral_shape(dry, sample_rate, highpass(45.0, order=3.0))

    return normalise(edges(dry, sample_rate, fade=0.02), ONE_SHOT_RMS_DB)


# --------------------------------------------------------------------------------------------
# The beds
# --------------------------------------------------------------------------------------------

#: Bands the bed is layered out of, as (low Hz, high Hz, level). Layered filtered noise, per #213.
_BED_BANDS = (
    (40.0, 150.0, 1.00),
    (120.0, 380.0, 0.72),
    (330.0, 900.0, 0.44),
    (800.0, 2_100.0, 0.26),
    (1_900.0, 5_200.0, 0.13),
)

#: Drift rates in Hz, one per band, sharing no small whole-number ratio - see ``dsp.lfo``.
_BED_DRIFTS = (0.0131, 0.0189, 0.0233, 0.0307, 0.0419)


def render_bed(kind: str, rng: np.random.Generator, sample_rate: int, seconds: float,
               overlap: float) -> np.ndarray:
    """
    One of the two rank layers: ``close`` or ``open``.

    Both are the same construction - brown-ish noise split into bands, each band drifting in level
    on its own slow LFO, the whole thing crossfaded back over itself so the loop has no seam. What
    separates them is #210's own description: the open one is a larger space with the tail baked in
    and less low end, the close one is small, dry and has a little more body.

    The drift is what makes a sixty-second loop unhummable. Five bands wandering at rates with no
    common factor never present the same spectrum twice inside the loop, so the thing a listener
    would have to notice in order to hear the loop is not the loop but the drift, and the drift does
    not repeat inside it.
    """
    from dsp import seamless

    count = int(seconds * sample_rate)
    tail = int(overlap * sample_rate)
    total = count + tail

    source = spectral_shape(white(rng, total), sample_rate, tilt(0.9))
    mixed = np.zeros(total)

    open_bed = kind == "open"

    for index, (low, high, level) in enumerate(_BED_BANDS):
        layer = spectral_shape(source, sample_rate, band(low, high, edge=0.45))

        depth = 0.45 + 0.2 * rng.random()
        drift = lfo(total, sample_rate, rate=_BED_DRIFTS[index],
                    phase=rng.random() * 6.283, low=1.0 - depth, high=1.0)

        weight = level

        if open_bed:
            # the open soul has had the floor taken out from under it and the air let in
            weight *= 0.45 if low < 200.0 else 1.25 if low > 700.0 else 1.0

        mixed += layer * drift * weight

    # and a second, slower wander over the whole bed, so its overall level breathes as well as its
    # colour - a bed at one level for a minute is a hum, whatever its spectrum is doing
    mixed *= lfo(total, sample_rate, rate=0.0071, phase=rng.random() * 6.283, low=0.72, high=1.0)

    if open_bed:
        wet = reverb(mixed, sample_rate, size=1.35, decay=0.88, damping_hz=1_900.0, combs=6)
        mixed = mixed * 0.35 + wet * 0.85
        mixed = spectral_shape(mixed, sample_rate, highpass(70.0, order=2.0))
    else:
        wet = reverb(mixed, sample_rate, size=0.55, decay=0.62, damping_hz=3_400.0, combs=3)
        mixed = mixed * 0.85 + wet * 0.3
        mixed = spectral_shape(mixed, sample_rate, lowpass(6_000.0, order=2.0))

    mixed = spectral_shape(mixed, sample_rate, highpass(38.0, order=3.0))

    return normalise(seamless(mixed, count, tail), BED_RMS_DB, peak_ceiling_db=-9.0)
