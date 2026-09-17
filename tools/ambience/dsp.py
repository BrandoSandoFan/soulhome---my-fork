# File created ~ 17 - 9 - 2026
"""
The small signal-processing vocabulary the ambience generator is written in (#213).

Kept apart from ``generate.py`` so that the file describing *what a soul sounds like* reads as a
description rather than as arithmetic. Nothing here knows about souls, voices or Minecraft.

Two decisions worth stating, because the obvious alternative is worse in both cases:

* **Filtering is done in the spectrum, not with a running IIR.** A one-pole lowpass over a
  sixty-second buffer is a Python loop over two and a half million samples, and the usual escape
  (scipy) is a dependency this repo would then owe to a script that renders a handful of files. An
  FFT magnitude shape is exact, takes a fraction of a second, and - the part that actually matters -
  is trivially deterministic, which a numerically-accumulating recursion is not.

* **Delay lines are run a block at a time.** A comb or allpass whose delay is D samples cannot see
  its own output until D samples later, so a whole block of D samples can be computed at once and
  the loop runs once per block instead of once per sample. That is what makes the reverb here cheap
  enough to be spectral filtering's neighbour rather than the reason a render takes a minute.
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


def tilt(exponent: float, reference: float = 1_000.0):
    """
    A ``1 / f**exponent`` slope through unity at ``reference``.

    Pink is 0.5, brown is 1.0. Both are here because white noise is the one noise colour nothing in
    an ambience should ever be: its energy climbs with frequency the way nothing in a room does.
    """

    def shape(frequencies: np.ndarray) -> np.ndarray:
        return (reference / frequencies) ** exponent

    return shape


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


def highpass(cutoff: float, order: float = 2.0):
    """The same, below."""

    def shape(frequencies: np.ndarray) -> np.ndarray:
        ratio = (cutoff / frequencies) ** (2.0 * order)
        return 1.0 / (1.0 + ratio) ** 0.5

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


def comb(
    signal: np.ndarray,
    sample_rate: int,
    delay: int,
    feedback: float,
    damping_hz: float = 0.0,
) -> np.ndarray:
    """
    A feedback comb, run a block at a time.

    ``damping_hz`` is where the top comes off each repeat: air absorbs the high end of a reflection,
    and without that a tail keeps its brightness all the way down and reads as a delay effect rather
    than as a room. It is applied once per block, and a block here *is* one trip round the delay
    line, so once per block is once per repeat - which is what damping means.
    """
    out = np.zeros_like(signal)
    previous = np.zeros(delay)
    seconds = 0.0 if damping_hz <= 0.0 else 1.0 / (2.0 * np.pi * damping_hz)

    for start in range(0, signal.size, delay):
        stop = min(start + delay, signal.size)
        width = stop - start

        block = previous[:width]

        if seconds > 0.0:
            block = smooth(block, sample_rate, seconds)

        out[start:stop] = signal[start:stop] + feedback * block
        previous = np.zeros(delay)
        previous[:width] = out[start:stop]

    return out


def allpass(signal: np.ndarray, delay: int, gain: float) -> np.ndarray:
    """A Schroeder allpass, same blockwise trick - it thickens a tail without colouring it."""
    out = np.zeros_like(signal)
    delayed_in = np.zeros(delay)
    delayed_out = np.zeros(delay)

    for start in range(0, signal.size, delay):
        stop = min(start + delay, signal.size)
        width = stop - start

        block = -gain * signal[start:stop] + delayed_in[:width] + gain * delayed_out[:width]
        out[start:stop] = block

        delayed_in = np.zeros(delay)
        delayed_in[:width] = signal[start:stop]
        delayed_out = np.zeros(delay)
        delayed_out[:width] = block

    return out


# Prime-ish comb lengths in samples at 44.1 kHz. Coprime on purpose: shared factors put the
# repeats of different combs on top of each other and the tail acquires a pitch.
_COMB_DELAYS = (1_557, 1_617, 1_691, 1_781, 1_867, 1_949)
_ALLPASS_DELAYS = (557, 441, 341)


def reverb(
    signal: np.ndarray,
    sample_rate: int,
    size: float = 1.0,
    decay: float = 0.84,
    damping_hz: float = 3_000.0,
    combs: int = 4,
) -> np.ndarray:
    """
    A small Schroeder network: parallel combs into a chain of allpasses.

    Not a good reverb. It is a *cheap* one, and the thing it is asked to do here - make one bed
    sound like a larger space than another - it does honestly. Anything better would be a second
    project inside this one.
    """
    wet = np.zeros_like(signal)

    for index in range(combs):
        delay = max(2, int(round(_COMB_DELAYS[index % len(_COMB_DELAYS)] * size)))
        wet += comb(signal, sample_rate, delay, decay, damping_hz)

    wet /= combs

    for delay in _ALLPASS_DELAYS:
        wet = allpass(wet, max(2, int(round(delay * size))), 0.5)

    return wet


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


def envelope(count: int, sample_rate: int, attack: float, decay: float, curve: float = 2.0) -> np.ndarray:
    """
    A one-shot's shape: a soft rise and an exponential fall.

    The rise is never allowed to be instant. #166's rule is that nothing in this palette may have an
    attack sharp enough to read as an event, and an attack is the one thing a listener notices
    across a room full of other sound.
    """
    t = np.arange(count) / sample_rate

    attack = max(attack, 1.0 / sample_rate)
    rise = np.clip(t / attack, 0.0, 1.0) ** curve
    fall = np.exp(-np.maximum(t - attack, 0.0) / max(decay, 1e-4))

    return rise * fall


def partial(count: int, sample_rate: int, frequency: float, decay: float, phase: float = 0.0) -> np.ndarray:
    """One exponentially-damped sinusoid - a mode of something that was struck."""
    t = np.arange(count) / sample_rate

    return np.sin(2.0 * np.pi * frequency * t + phase) * np.exp(-t / max(decay, 1e-4))


def onsets(
    rng: np.random.Generator,
    count: int,
    sample_rate: int,
    mean_gap: float,
    jitter: float = 0.9,
    start: float = 0.0,
) -> list[int]:
    """
    Sample indices for a run of grains, with the gap between them drawn afresh each time.

    Exponentially-distributed gaps rather than a fixed rate with a wobble on it: the literature's
    standing complaint about procedural ambience is that it sounds metronomic even when it is not
    meant to, and a Poisson process is the one arrival pattern a listener cannot hear a rate in.
    """
    positions: list[int] = []
    cursor = start * sample_rate

    while cursor < count:
        positions.append(int(cursor))
        gap = mean_gap * (1.0 - jitter + jitter * 2.0 * rng.random()) if jitter < 1.0 else rng.exponential(mean_gap)
        cursor += max(gap, 1.0 / sample_rate) * sample_rate

    return positions


def place(target: np.ndarray, grain: np.ndarray, at: int, gain: float = 1.0) -> None:
    """Add a grain into a buffer at a sample index, clipped to what fits. In place."""
    if at >= target.size:
        return

    start = max(0, at)
    width = min(grain.size, target.size - start)

    if width <= 0:
        return

    target[start : start + width] += gain * grain[:width]


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
