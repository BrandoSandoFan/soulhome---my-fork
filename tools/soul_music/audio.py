"""Sample handling shared by the music bank and the ambience mixes (#163).

Nothing here makes a sound. Every function takes a recording of a real instrument and trims, tunes,
loops, levels or places it. The synthesis this replaced sounded like oscillators because it was
oscillators, and a recorded harp does not have that problem to solve.
"""
from __future__ import annotations

import subprocess
import tempfile
from fractions import Fraction
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

RATE = 44_100


def db(x: float) -> float:
    return 20.0 * np.log10(max(x, 1e-12))


def undb(d: float) -> float:
    return 10.0 ** (d / 20.0)


def load(path: Path) -> np.ndarray:
    """Mono float64 at RATE. The libraries record in stereo; the soul pans every note itself."""
    x, sr = sf.read(str(path), always_2d=True, dtype='float64')
    x = x.mean(axis=1)
    if sr != RATE:
        frac = Fraction(RATE, sr).limit_denominator(1000)
        x = signal.resample_poly(x, frac.numerator, frac.denominator)
    return x - np.mean(x[: min(len(x), 2048)])


def highpass(x: np.ndarray, hz: float, order: int = 2) -> np.ndarray:
    sos = signal.butter(order, hz, 'highpass', fs=RATE, output='sos')
    return signal.sosfiltfilt(sos, x)


def lowpass(x: np.ndarray, hz: float, order: int = 2) -> np.ndarray:
    sos = signal.butter(order, hz, 'lowpass', fs=RATE, output='sos')
    return signal.sosfiltfilt(sos, x)


def bandpass(x: np.ndarray, lo: float, hi: float, order: int = 2) -> np.ndarray:
    sos = signal.butter(order, [lo, hi], 'bandpass', fs=RATE, output='sos')
    return signal.sosfiltfilt(sos, x)


def trim_head(x: np.ndarray, floor_db: float = -48.0, preroll: float = 0.004) -> np.ndarray:
    """Cut the silence before the attack, keeping a few milliseconds so the transient is whole.

    The libraries leave up to half a second of room tone before a note; left in, every note in the
    soul's music would land late by a different amount and the forge would stop keeping time.
    """
    peak = np.max(np.abs(x))
    above = np.nonzero(np.abs(x) > peak * undb(floor_db))[0]
    if len(above) == 0:
        return x
    start = max(0, above[0] - int(preroll * RATE))
    return x[start:]


def envelope_db(x: np.ndarray, window: float = 0.05) -> np.ndarray:
    n = max(1, int(window * RATE))
    power = np.convolve(x * x, np.ones(n) / n, mode='same')
    return 10.0 * np.log10(power + 1e-20)


def trim_tail(x: np.ndarray, max_seconds: float, floor_db: float = -60.0, fade: float = 0.35) -> np.ndarray:
    """End a decaying note where it falls under the floor, or at max_seconds, with a fade.

    The fade is long on purpose: a ring cut short is the one thing a listener hears as an edit.
    """
    env = envelope_db(x)
    ref = np.max(env)
    below = np.nonzero(env < ref + floor_db)[0]
    below = below[below > int(0.05 * RATE)]
    end = below[0] if len(below) else len(x)
    end = min(end, int(max_seconds * RATE), len(x))
    y = x[:end].copy()
    n = min(int(fade * RATE), end // 2)
    if n > 0:
        y[-n:] *= 0.5 * (1.0 + np.cos(np.linspace(0.0, np.pi, n)))
    return y


def fade_in(x: np.ndarray, seconds: float) -> np.ndarray:
    n = min(int(seconds * RATE), len(x))
    y = x.copy()
    if n > 0:
        y[:n] *= 0.5 * (1.0 - np.cos(np.linspace(0.0, np.pi, n)))
    return y


def fundamental(x: np.ndarray, nominal_midi: float, start: float = 0.2, length: float = 0.6) -> float | None:
    """Measured pitch in fractional MIDI, searched only within a semitone of what the file claims.

    Searching near the nominal note is what makes this safe on a double bass, whose fundamental is
    weaker than its octave: an open search finds the octave and "corrects" a perfectly tuned note.
    """
    a = int(start * RATE)
    seg = x[a:a + int(length * RATE)]
    if len(seg) < 4096:
        seg = x[: int(length * RATE)]
    if len(seg) < 4096 or np.max(np.abs(seg)) < 1e-5:
        return None
    seg = (seg - seg.mean()) * np.hanning(len(seg))
    n = 1 << (2 * len(seg)).bit_length()
    spec = np.fft.rfft(seg, n)
    ac = np.fft.irfft(spec * np.conj(spec))
    f0 = 440.0 * 2.0 ** ((nominal_midi - 69.0) / 12.0)
    lo = int(RATE / (f0 * 2 ** (1 / 12)))
    hi = int(RATE / (f0 * 2 ** (-1 / 12))) + 1
    if hi + 1 >= len(ac) or lo < 2:
        return None
    k = lo + int(np.argmax(ac[lo:hi]))
    if ac[k] < 0.25 * ac[0]:
        return None
    y0, y1, y2 = ac[k - 1], ac[k], ac[k + 1]
    denom = y0 - 2 * y1 + y2
    shift = 0.5 * (y0 - y2) / denom if denom != 0 else 0.0
    return 69.0 + 12.0 * np.log2(RATE / (k + shift) / 440.0)


def retune(x: np.ndarray, cents: float) -> np.ndarray:
    """Raise the pitch by `cents` by resampling. Small corrections only, so the timbre is unmoved."""
    if abs(cents) < 0.5:
        return x
    ratio = 2.0 ** (cents / 1200.0)
    frac = Fraction(1.0 / ratio).limit_denominator(4000)
    return signal.resample_poly(x, frac.numerator, frac.denominator)


def shift(x: np.ndarray, semitones: float) -> np.ndarray:
    """Varispeed - pitch and length together, as a sampler does. Used for one-shots, not tuning."""
    return retune(x, semitones * 100.0)


def nearest_zero(x: np.ndarray, at: int, rising: bool = True, span: int = 2000) -> int:
    a, b = max(1, at - span), min(len(x) - 1, at + span)
    seg = x[a - 1:b]
    if rising:
        idx = np.nonzero((seg[:-1] <= 0) & (seg[1:] > 0))[0]
    else:
        idx = np.nonzero((seg[:-1] >= 0) & (seg[1:] < 0))[0]
    if len(idx) == 0:
        return at
    cands = idx + a
    return int(cands[np.argmin(np.abs(cands - at))])


def make_loop(x: np.ndarray, start: float, end: float, crossfade: float) -> tuple[np.ndarray, int, int]:
    """Fold a sustained note into a loop a sampler can wrap without a click.

    The material before the loop end is crossfaded, equal power, with the material before the loop
    start, so wrapping from end back to start lands on exactly the audio that was fading in. The
    file is cut at the loop end: a release is the sampler's envelope, not the recording's.
    Returns (audio, loop_start, loop_end) in frames.
    """
    a = nearest_zero(x, int(start * RATE))
    b = nearest_zero(x, int(end * RATE))
    n = int(crossfade * RATE)
    n = min(n, a, (b - a) // 2)
    y = x[:b].copy()
    t = np.linspace(0.0, np.pi / 2.0, n)
    fade_out, fade_in_ = np.cos(t), np.sin(t)
    y[b - n:b] = x[b - n:b] * fade_out + x[a - n:a] * fade_in_
    return y, a, b


def rms_db(x: np.ndarray, start: float = 0.0, length: float | None = None) -> float:
    a = int(start * RATE)
    seg = x[a:] if length is None else x[a:a + int(length * RATE)]
    if len(seg) == 0:
        return -120.0
    return db(float(np.sqrt(np.mean(seg * seg))))


def peak(x: np.ndarray) -> float:
    return float(np.max(np.abs(x))) if len(x) else 0.0


def impulse_response(seconds: float, decay: float, damp_hz: float, seed: int, predelay: float = 0.012,
                     early: int = 9) -> np.ndarray:
    """A diffuse room tail for placing dry recordings in a space.

    Exponentially decaying noise, darkened over time the way air and walls darken a real tail, with
    a handful of discrete early reflections ahead of it. Mixing practice, not instrument design:
    the notes themselves stay recordings.
    """
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    tail = rng.standard_normal(n) * np.exp(-6.91 * t / decay)
    # Darken progressively: split into a bright and a dark copy and crossfade over the tail.
    dark = lowpass(tail, damp_hz, 2)
    mix = np.clip(t / (0.5 * decay), 0.0, 1.0)
    tail = tail * (1.0 - mix) + dark * mix
    ir = np.zeros(n + int(predelay * RATE))
    ir[int(predelay * RATE):] = tail
    for i in range(early):
        at = int(predelay * RATE * (0.4 + 2.2 * rng.random()))
        ir[at] += (0.55 - 0.04 * i) * (1 if rng.random() > 0.5 else -1)
    ir = highpass(ir, 120.0, 2)
    return ir / np.sqrt(np.sum(ir * ir))


def reverb(x: np.ndarray, ir: np.ndarray, wet: float) -> np.ndarray:
    """Equal-power dry/wet blend through a convolution tail; the output is longer by the tail."""
    w = signal.fftconvolve(x, ir)
    dry = np.zeros_like(w)
    dry[: len(x)] = x
    wet_gain = np.sin(wet * np.pi / 2.0)
    dry_gain = np.cos(wet * np.pi / 2.0)
    # Scale the wet to the dry's loudness first, so "wet" means share and not level.
    ref = np.sqrt(np.mean(x * x)) + 1e-12
    w *= ref / (np.sqrt(np.mean(w[: len(x)] ** 2)) + 1e-12)
    return dry * dry_gain + w * wet_gain


def limit(x: np.ndarray, ceiling_db: float = -3.0, release: float = 0.25, lookahead: float = 0.004) -> np.ndarray:
    """Lookahead peak limiter with a smooth release, so a loud strike is turned down, not bent."""
    ceiling = undb(ceiling_db)
    la = int(lookahead * RATE)
    need = np.maximum(np.abs(x) / ceiling, 1.0)
    # Gain must already be down when the peak arrives: take the running max over the lookahead.
    padded = np.concatenate([need, np.ones(la)])
    ahead = np.lib.stride_tricks.sliding_window_view(padded, la + 1).max(axis=1)[: len(x)]
    target = 1.0 / ahead
    gain = np.empty_like(target)
    coeff = np.exp(-1.0 / (release * RATE))
    g = 1.0
    for i, tg in enumerate(target):
        g = tg if tg < g else tg + (g - tg) * coeff
        gain[i] = g
    return x * gain


def encode(x: np.ndarray, path: Path, quality: float = 5.0) -> None:
    """Ogg Vorbis, mono, via ffmpeg's libvorbis in bitexact mode, so a rebuild is byte-identical.

    Quality 5 (about 112 kbps mono at this rate) rather than libsndfile's default, because the
    ambience's noisy material went watery at the default and nobody wants that in an orchestra.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    clipped = np.clip(x, -1.0, 1.0)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / 'in.wav'
        sf.write(str(wav), clipped, RATE, subtype='PCM_24')
        subprocess.run(
            ['ffmpeg', '-v', 'error', '-y', '-i', str(wav), '-map_metadata', '-1',
             '-fflags', '+bitexact', '-flags:a', '+bitexact', '-c:a', 'libvorbis', '-q:a', str(quality),
             '-ac', '1', '-ar', str(RATE), '-serial_offset', '1', str(path)],
            check=True)


def decoded_length(path: Path) -> int:
    return sf.info(str(path)).frames
