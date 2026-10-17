# File created ~ 17 - 9 - 2026
"""
The signal-processing vocabulary the suppression cues are written in (#188).

Kept apart from ``suppression.py`` so that the file describing what suppression sounds like reads as
a description rather than as arithmetic. Nothing here knows about souls or Minecraft.

The soul's ambience used to be written in this vocabulary too (#213). It is recorded and mixed now
by ``mix.py``, which has its own helpers in ``tools/soul_music/audio.py``; nothing there is shared
with this file, so changing one does not re-render the other.

One decision worth stating, because the obvious alternative is worse: filtering is done in the
spectrum, not with a running IIR. A one-pole lowpass over a long buffer is a Python loop over every
sample, and the usual escape (scipy) is a dependency this script would then owe for a handful of
files. An FFT magnitude shape is exact, takes a fraction of a second, and - the part that actually
matters - is deterministic, which a numerically-accumulating recursion is not.
"""

from __future__ import annotations

import numpy as np

SAMPLE_RATE = 44_100


def white(rng: np.random.Generator, count: int) -> np.ndarray:
    """Plain Gaussian noise. Every other noise colour here is this, shaped."""
    return rng.standard_normal(count)


def spectral_shape(signal: np.ndarray, sample_rate: int, shape) -> np.ndarray:
    """
    Multiply a signal's spectrum by ``shape(frequencies)``.

    The shaping function is handed frequencies in Hz with DC already set aside, so a caller writes
    the response it wants rather than a filter that approximates it.
    """
    spectrum = np.fft.rfft(signal)
    frequencies = np.fft.rfftfreq(signal.size, d=1.0 / sample_rate)

    gains = np.zeros_like(frequencies)
    gains[1:] = shape(frequencies[1:])

    return np.fft.irfft(spectrum * gains, n=signal.size)


def band(low: float, high: float, edge: float = 0.35, slope: float = 0.0):
    """
    A soft-edged band. ``edge`` is the width of each skirt as a share of the corner frequency, so
    the band has no cliff at either end - a brick wall rings, and a ringing filter on a noise bed is
    audible as a pitch.
    """

    def shape(frequencies: np.ndarray) -> np.ndarray:
        rising = _smoothstep(low * (1.0 - edge), low * (1.0 + edge), frequencies)
        falling = 1.0 - _smoothstep(high * (1.0 - edge), high * (1.0 + edge), frequencies)
        response = rising * falling

        if slope:
            response = response * (1_000.0 / frequencies) ** slope

        return response

    return shape


def lowpass(cutoff: float, order: float = 2.0):
    """A gentle rolloff above ``cutoff`` - ``order`` sets how gentle."""

    def shape(frequencies: np.ndarray) -> np.ndarray:
        return 1.0 / (1.0 + (frequencies / cutoff) ** (2.0 * order)) ** 0.5

    return shape


def smooth(signal: np.ndarray, sample_rate: int, seconds: float) -> np.ndarray:
    """
    Exponential smoothing, as a convolution rather than a recursion - see the module docstring.

    Used on envelopes and control signals, where the point is to take the corners off something
    rather than to be a particular filter.
    """
    if seconds <= 0.0:
        return signal

    decay = np.exp(-1.0 / (seconds * sample_rate))
    length = int(min(signal.size, np.ceil(np.log(1e-5) / np.log(decay))))
    length = max(length, 1)

    kernel = decay ** np.arange(length)
    kernel /= kernel.sum()

    return np.convolve(signal, kernel, mode="full")[: signal.size]


def lfo(count: int, sample_rate: int, rate: float, phase: float, low: float, high: float) -> np.ndarray:
    """
    One slow sine, mapped into ``[low, high]``.

    Callers are expected to pick rates that share no small whole-number ratio with each other. That
    is the single piece of advice the procedural-sound literature gives about drifting layers and
    the one that is easiest to ignore: LFOs at 1/30 Hz and 1/60 Hz realign every minute, and a
    listener will find that minute long before they can say why.
    """
    t = np.arange(count) / sample_rate
    centre = 0.5 * (low + high)
    swing = 0.5 * (high - low)

    return centre + swing * np.sin(2.0 * np.pi * rate * t + phase)


def seamless(signal: np.ndarray, length: int, overlap: int) -> np.ndarray:
    """
    Fold a rendered buffer's tail back over its own head so the loop has no seam.

    ``signal`` is rendered ``overlap`` samples longer than the loop needs to be; those extra samples
    are what the start of the loop fades up out of. Equal-power (sine/cosine) rather than linear
    fades, because two decorrelated noise beds crossfaded linearly dip by 3 dB in the middle - and a
    dip once a minute is exactly the audible cycle the whole design is trying not to have.
    """
    if overlap <= 0 or signal.size < length + overlap:
        return signal[:length]

    loop = signal[:length].copy()
    ramp = np.linspace(0.0, np.pi / 2.0, overlap)

    loop[:overlap] = loop[:overlap] * np.sin(ramp) + signal[length : length + overlap] * np.cos(ramp)

    return loop


def rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal)))) if signal.size else 0.0


def normalise(signal: np.ndarray, target_rms_db: float, peak_ceiling_db: float = -6.0) -> np.ndarray:
    """
    Bring a rendered asset to a stated loudness, then make sure its peak still has headroom.

    RMS first and peak second, because the mod's volume knob should be choosing how loud the soul
    is against the rest of the game, not making up for one asset that was mastered louder than its
    neighbours. #213's words: the volume knob should not have to do the work of a mastering pass.
    """
    level = rms(signal)

    if level <= 0.0:
        return signal

    scaled = signal * (10.0 ** (target_rms_db / 20.0) / level)
    peak = float(np.max(np.abs(scaled)))
    ceiling = 10.0 ** (peak_ceiling_db / 20.0)

    if peak > ceiling:
        scaled *= ceiling / peak

    return scaled


def edges(signal: np.ndarray, sample_rate: int, fade: float = 0.01) -> np.ndarray:
    """Fade a one-shot's first and last few milliseconds, so no asset starts or ends on a click."""
    width = min(int(fade * sample_rate), signal.size // 2)

    if width <= 0:
        return signal

    ramp = np.linspace(0.0, 1.0, width)
    out = signal.copy()
    out[:width] *= ramp
    out[-width:] *= ramp[::-1]

    return out


def _smoothstep(low: float, high: float, values: np.ndarray) -> np.ndarray:
    if high <= low:
        return (values >= high).astype(float)

    t = np.clip((values - low) / (high - low), 0.0, 1.0)

    return t * t * (3.0 - 2.0 * t)
