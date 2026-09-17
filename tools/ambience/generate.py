# File created ~ 17 - 9 - 2026
"""
Renders the soul's ambience assets (#213).

Run it, commit what it writes, and the game plays the committed files. The generator is never on
the runtime path - see ``README.md`` for why offline beat runtime synthesis, and ``voices.py`` for
what each sound is actually made of.

    python3 tools/ambience/generate.py

What comes out, under ``src/main/resources/assets/soulhome/sounds/ambience``:

* ``<voice>_1..3.ogg`` - one-shots, three variants for each of the ten ``SoulVoice``s, so vanilla's
  own random pick out of ``sounds.json`` supplies the variety the code used to get from a coin flip
  between two vanilla events (#209).
* ``bed_close.ogg`` and ``bed_open.ogg`` - the two rank layers of the ambient bed (#210), each a
  seamless minute-and-a-bit loop.
* ``character_<voice>.ogg`` - the nine character layers of the bed (#214), one per non-``base``
  voice, mixed continuously under the rank layers rather than rolled for one at a time.
* ``SOURCES.md`` - what every file is, what made it, and the checksum of the samples that went into
  the encoder. That is this repo's answer to #209's licence question: nothing was downloaded, so
  there is no third-party licence to honour, and the provenance is a script in the tree.

On determinism
--------------

Each asset draws from a generator seeded by the master seed *and its own name*, so the bytes of
``warm_2.ogg`` do not depend on how many voices were rendered before it. Adding an eleventh voice
re-renders one file and leaves the other thirty-one identical, which is what makes a re-render a
diff of nothing.

The PCM this script produces is bit-identical across runs and machines for a given seed, and that
is what ``SOURCES.md`` records a checksum of. The ``.ogg`` bytes are one step further out: they are
whatever libvorbis makes of that PCM, so a different libsndfile can encode the same samples to
different bytes. Checking the samples rather than the container is the honest guarantee, and it is
the one that would catch a real change.
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from datetime import date
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

import voices as voice_lib  # noqa: E402  (after the path fix-up, on purpose)
from dsp import SAMPLE_RATE  # noqa: E402

#: The one number the whole palette hangs off. Changing it re-renders every asset.
DEFAULT_SEED = 0x50_4C_4B_21

#: Variants per voice. Three is enough that a player building for an evening never hears a repeat
#: at these gaps, and small enough that the whole palette is under a megabyte.
VARIANTS = 3

#: Loop length and the crossfade folded back over its head - see ``dsp.seamless``.
BED_SECONDS = 68.0
BED_OVERLAP = 6.0

DEFAULT_OUTPUT = Path("src/main/resources/assets/soulhome/sounds/ambience")


def asset_rng(seed: int, name: str) -> np.random.Generator:
    """A generator for one named asset - see the note on determinism in the module docstring."""
    digest = hashlib.sha256(f"{seed}:{name}".encode("utf-8")).digest()

    return np.random.default_rng(int.from_bytes(digest[:8], "big"))


def to_pcm(samples: np.ndarray) -> np.ndarray:
    """
    16-bit signed, which is what the checksum is taken over and what the encoder is handed.

    Rounded half-away-from-zero and clipped rather than left to the encoder's own conversion, so
    that "the same samples" means the same integers on every machine rather than the same floats.
    """
    scaled = np.clip(samples, -1.0, 1.0) * 32_767.0

    return np.round(scaled).astype(np.int16)


#: Samples per write. Five seconds at a time, because libsndfile's Vorbis encoder segfaults on a
#: single write much past thirty seconds - found the hard way rendering the first sixty-second bed,
#: and worth writing down: the failure is a hard crash with no Python traceback, so the next person
#: to lengthen a bed would otherwise spend an afternoon looking for it in the synthesis.
_WRITE_BLOCK = 5 * 44_100


#: What every Ogg stream in this directory is stamped with - see ``canonicalise``.
_STREAM_SERIAL = 0x_50_4C_4B_21


def write_ogg(path: Path, pcm: np.ndarray, sample_rate: int) -> None:
    import soundfile

    path.parent.mkdir(parents=True, exist_ok=True)

    with soundfile.SoundFile(str(path), "w", samplerate=sample_rate, channels=1,
                             format="OGG", subtype="VORBIS") as handle:
        for start in range(0, pcm.size, _WRITE_BLOCK):
            handle.write(pcm[start : start + _WRITE_BLOCK])

    path.write_bytes(canonicalise(path.read_bytes()))


def canonicalise(container: bytes) -> bytes:
    """
    Stamp every Ogg page with the same stream serial, and fix up the page checksums.

    libvorbis picks a stream serial at random for each file it writes. The encoded audio underneath
    is a pure function of the samples, but those four random bytes appear in every page header, so
    two renders of identical audio are two different files - and the promise this generator makes,
    that re-rendering is a diff of nothing, would be false for a reason that has nothing to do with
    sound. Rewriting the serial costs a pass over the file and makes the promise true.

    A page is ``OggS``, a fixed 27-byte header whose bytes 14-17 are the serial and 22-25 the CRC,
    then a segment table, then the segments. The CRC covers the whole page with its own field
    zeroed, which is why it has to be recomputed rather than carried over.
    """
    out = bytearray(container)
    cursor = 0

    while cursor < len(out):
        if out[cursor : cursor + 4] != b"OggS":
            raise ValueError(f"not an Ogg page at offset {cursor}")

        segments = out[cursor + 26]
        table = out[cursor + 27 : cursor + 27 + segments]
        size = 27 + segments + sum(table)
        page = out[cursor : cursor + size]

        page[14:18] = _STREAM_SERIAL.to_bytes(4, "little")
        page[22:26] = b"\0\0\0\0"
        page[22:26] = _ogg_crc(page).to_bytes(4, "little")

        out[cursor : cursor + size] = page
        cursor += size

    return bytes(out)


def _build_crc_table() -> list[int]:
    table = []

    for index in range(256):
        value = index << 24

        for _ in range(8):
            value = ((value << 1) ^ 0x04C11DB7) & 0xFFFFFFFF if value & 0x80000000 else (value << 1) & 0xFFFFFFFF

        table.append(value)

    return table


_CRC_TABLE = _build_crc_table()


def _ogg_crc(page: bytes) -> int:
    """
    Ogg's own CRC32: polynomial 0x04C11DB7, unreflected, no initial value and no final inversion.

    Spelled out here rather than reached for in a library because it is not the CRC32 in ``zlib`` -
    that one is reflected and inverted, and using it would produce a file every player rejects.
    """
    crc = 0

    for byte in page:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ _CRC_TABLE[((crc >> 24) & 0xFF) ^ byte]

    return crc


def render(output: Path, seed: int, sample_rate: int) -> list[dict]:
    rendered: list[dict] = []

    for voice in voice_lib.VOICES:
        for variant in range(1, VARIANTS + 1):
            name = f"{voice}_{variant}"
            samples = voice_lib.render_voice(voice, asset_rng(seed, name), sample_rate)
            rendered.append(_emit(output, name, samples, sample_rate,
                                  f"one-shot, {voice} voice, variant {variant}"))

    for kind in ("close", "open"):
        name = f"bed_{kind}"
        samples = voice_lib.render_bed(kind, asset_rng(seed, name), sample_rate,
                                       BED_SECONDS, BED_OVERLAP)
        rendered.append(_emit(output, name, samples, sample_rate,
                              f"ambient bed, {kind} rank layer, seamless loop"))

    for voice in voice_lib.VOICES:
        if voice == "base":
            continue

        name = f"character_{voice}"
        samples = voice_lib.render_character_bed(voice, asset_rng(seed, name), sample_rate,
                                                  BED_SECONDS, BED_OVERLAP)
        rendered.append(_emit(output, name, samples, sample_rate,
                              f"ambient bed, {voice} character layer, seamless loop"))

    return rendered


def _emit(output: Path, name: str, samples: np.ndarray, sample_rate: int, description: str) -> dict:
    pcm = to_pcm(samples)
    path = output / f"{name}.ogg"

    write_ogg(path, pcm, sample_rate)

    print(f"  {path.name:<22} {pcm.size / sample_rate:6.2f}s  {path.stat().st_size / 1024:7.1f} KiB")

    return {
        "file": path.name,
        "description": description,
        "seconds": pcm.size / sample_rate,
        "sha256": hashlib.sha256(pcm.tobytes()).hexdigest(),
    }


def write_sources(output: Path, rendered: list[dict], seed: int, sample_rate: int) -> None:
    lines = [
        "# Where these sounds came from",
        "",
        "Every file in this directory was generated by `tools/ambience/generate.py` (#213). None of",
        "it was recorded, sampled or downloaded, so there is no third-party licence attached to any",
        "of it - these assets are covered by the mod's own licence like any other file in the tree.",
        "",
        "#209 asks that provenance be recorded because \"CC0\" and \"CC-BY\" are different promises and",
        "the repo should be able to prove which one it made. This is that record, and the promise it",
        "makes is the simplest one available: the source of these files is a script in this",
        "repository, and re-running it is how you check.",
        "",
        "```",
        "python3 tools/ambience/generate.py",
        "```",
        "",
        f"Rendered with seed `0x{seed:X}` at {sample_rate} Hz, mono. The checksum below is of the",
        "16-bit PCM handed to the encoder, not of the `.ogg` file - see the note on determinism in",
        "the generator's own docstring for why that is the guarantee worth making.",
        "",
        "| File | What it is | Length | PCM sha256 |",
        "| --- | --- | --- | --- |",
    ]

    for entry in rendered:
        lines.append(
            f"| `{entry['file']}` | {entry['description']} | {entry['seconds']:.2f}s | "
            f"`{entry['sha256'][:16]}…` |"
        )

    lines += ["", f"_Last rendered {date.today().isoformat()}._", ""]

    (output / "SOURCES.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Render the soul's ambience assets (#213).")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT,
                        help="where the .ogg files go (default: the mod's own assets directory)")
    parser.add_argument("--seed", type=lambda value: int(value, 0), default=DEFAULT_SEED,
                        help="master seed; every asset's own seed is derived from this and its name")
    parser.add_argument("--sample-rate", type=int, default=SAMPLE_RATE)

    arguments = parser.parse_args()

    print(f"Rendering the soul's ambience to {arguments.out} (seed 0x{arguments.seed:X})")

    rendered = render(arguments.out, arguments.seed, arguments.sample_rate)
    write_sources(arguments.out, rendered, arguments.seed, arguments.sample_rate)

    total = sum((arguments.out / entry["file"]).stat().st_size for entry in rendered)
    print(f"\n{len(rendered)} assets, {total / 1024:.1f} KiB in total.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
