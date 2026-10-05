# File created ~ 23 - 9 - 2026
"""
What suppression sounds like (#188).

Two sounds, one per half of rule 4 of #181, and they were chosen against what the mod already had:

* **The drone** is *their* rank - the amount. It used to be ``BEACON_AMBIENT``, which is the sound
  #114 gave the ascension ritual, so a suppressed player walking past hummed exactly like an
  ascension in progress. Two meanings on one sound is one too many. This is a pressure rather than a
  hum: a stack of low partials detuned against each other so they beat slowly, the way the air
  thickens near something heavy, with a band of breath over them that swells and settles. The
  beating is what keeps it moving: a steady tone held for as long as you stand near someone becomes
  a tone you stop hearing.

* **The throb** is *your* legibility - the count. It used to be ``AMETHYST_BLOCK_CHIME``, which the
  lens already rings on every scan, and which #188's own thread asked to be rid of. A chime also
  rings for the better part of a second, and the field sounds once per ring a quarter of a second
  apart: nine chimes that long are a smear, not a number. So this is a short, low, falling pulse - a
  heartbeat heard through a wall - that is over before the next one starts. Its pitch glides down
  rather than holding, which is what makes it read as weight arriving rather than as a note, and a
  short, soft tick of band-limited noise at its front gives it enough edge to count on laptop speakers
  that would never reproduce its fundamental.

Neither is made of any ambience voice. Suppression is heard in the overworld, beside the game's own
sounds, and a player who has just come home from their soul should not hear it in someone else.
"""

from __future__ import annotations

import numpy as np

from dsp import (
    band,
    edges,
    lfo,
    lowpass,
    normalise,
    seamless,
    smooth,
    spectral_shape,
    white,
)

#: Louder than the ambience one-shots: this is a cue meant to be counted, heard over a game that is
#: not silent, and it is scaled in Java by how legible the field is before it plays at all.
THROB_RMS_DB = -20.0

#: The drone sits well under the throb, since it loops for as long as someone suppressed is in view
#: and is scaled by the strength of their field on top of this.
DRONE_RMS_DB = -27.0

#: Loop length and crossfade for the drone. Twenty-three seconds, because no LFO below divides it
#: evenly and a loop whose own length is a multiple of its slowest wobble is a loop you can hear.
DRONE_SECONDS = 23.0
DRONE_OVERLAP = 3.0

#: Long enough to hold the pulse and its fall, short enough that at the Java side's five-tick spacing
#: one has fully gone before the next arrives.
THROB_SECONDS = 0.24


def throb(rng: np.random.Generator, sample_rate: int) -> np.ndarray:
    """One pulse of the count: a falling low body, its octave, and a soft tick to count it by."""
    count = int(THROB_SECONDS * sample_rate)
    t = np.arange(count) / sample_rate

    # the body falls from 150 Hz toward 88 across its first eighty milliseconds - the glide is what
    # reads as weight, where a held pitch would read as a note. No lower than that: a laptop speaker
    # gives up somewhere under a hundred and fifty, and a count nobody can hear counts nothing
    start, end, glide = 150.0, 88.0, 0.08
    frequency = end + (start - end) * np.exp(-t / (glide / 3.0))
    phase = 2.0 * np.pi * np.cumsum(frequency) / sample_rate

    attack = 0.006
    rise = np.clip(t / attack, 0.0, 1.0) ** 1.5
    fall = np.exp(-np.maximum(t - attack, 0.0) / 0.055)
    body_shape = rise * fall

    body = (np.sin(phase) + 0.7 * np.sin(2.0 * phase + 0.3) + 0.45 * np.sin(3.0 * phase + 1.1)
            + 0.2 * np.sin(4.0 * phase + 2.3))
    body *= body_shape

    # a mid resonance that dies faster than the body, as a struck skin does: most of what small
    # speakers actually reproduce of this sound, so it is most of how it is heard
    skin_decay = np.exp(-t / 0.03) * rise
    body += 0.5 * np.sin(2.0 * np.pi * 380.0 * t + 0.7) * skin_decay

    # the tick: a few milliseconds of mid-band noise, so the count survives speakers with no bass
    tick_length = int(0.018 * sample_rate)
    tick = spectral_shape(white(rng, count), sample_rate, band(700.0, 2_400.0, edge=0.5, slope=0.4))
    tick_shape = np.zeros(count)
    tick_shape[:tick_length] = np.hanning(2 * tick_length)[tick_length:] * np.clip(t[:tick_length] / 0.002, 0.0, 1.0)
    tick *= tick_shape

    out = body + 0.9 * tick / max(float(np.max(np.abs(tick))), 1e-9)
    out = spectral_shape(out, sample_rate, lowpass(3_200.0, order=1.5))

    return edges(normalise(out, THROB_RMS_DB, peak_ceiling_db=-3.0), sample_rate, fade=0.003)


def drone(rng: np.random.Generator, sample_rate: int) -> np.ndarray:
    """The field itself: beating low partials under a slow swell of breath, as a seamless loop."""
    length = int(DRONE_SECONDS * sample_rate)
    overlap = int(DRONE_OVERLAP * sample_rate)
    count = length + overlap
    t = np.arange(count) / sample_rate

    out = np.zeros(count)

    # partials on a slightly stretched series rather than an exact one, each doubled by a detuned
    # twin: the pairs beat at rates between a third and one hertz, none a small multiple of another
    for ratio, level, beat in ((1.0, 1.0, 0.37), (2.01, 0.8, 0.61), (2.98, 0.6, 0.83), (4.07, 0.4, 0.53), (5.11, 0.25, 0.71)):
        frequency = 73.0 * ratio
        phase = rng.random() * 2.0 * np.pi
        out += level * np.sin(2.0 * np.pi * frequency * t + phase)
        out += level * np.sin(2.0 * np.pi * (frequency + beat) * t + phase + rng.random())

    # the breath: brown-ish noise in the low mids, swelling on two LFOs that never realign in a loop
    breath = spectral_shape(white(rng, count), sample_rate, band(180.0, 1_100.0, edge=0.5, slope=0.4))
    breath /= max(float(np.max(np.abs(breath))), 1e-9)
    swell = lfo(count, sample_rate, 1.0 / 7.3, rng.random() * 6.283, 0.25, 1.0)
    swell *= lfo(count, sample_rate, 1.0 / 11.9, rng.random() * 6.283, 0.6, 1.0)
    out += 2.4 * breath * smooth(swell, sample_rate, 0.2)

    # and a slow pressure in the whole thing, so it leans rather than sits
    out *= lfo(count, sample_rate, 1.0 / 5.1, rng.random() * 6.283, 0.8, 1.0)
    out = spectral_shape(out, sample_rate, lowpass(1_400.0, order=2.0))

    loop = seamless(out, length, overlap)

    return normalise(loop, DRONE_RMS_DB, peak_ceiling_db=-6.0)
