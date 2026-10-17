# File created ~ 17 - 9 - 2026
"""
Renders the suppression cues (#188) - the only sound this script still makes.

    python3 tools/ambience/generate.py

writes two files and a ``SOURCES.md`` under ``src/main/resources/assets/soulhome/sounds/suppression``:

* ``drone.ogg`` - the field around a suppressed player, a seamless loop. Their rank.
* ``throb.ogg`` - one beat of the ring count. Your legibility.

What they are made of, and why neither is an ambience voice, is in ``suppression.py``.

The soul's ambience used to be rendered here too, from ``voices.py`` (#213). It is not any more. It
is recorded and mixed by ``tools/ambience/mix.py`` (#163) from two CC0 sample libraries, and that
script writes the same file names this one used to. Both writing the ambience would be a race
between two sets of files under one name, and the one that ran last would win silently - so this
script refuses ``--set ambience`` and ``--set all`` rather than rendering anything. The mixes are
under ``tools/ambience/README.md``.

``--set suppression`` is the default and the only set; ``--suppression-out`` sends the files
somewhere other than the mod's own assets, which is how to check a re-render without touching them.

On determinism
--------------

Each asset draws from a generator seeded by the master seed *and its own name*, so the bytes of
``throb.ogg`` do not depend on whether ``drone.ogg`` was rendered first. Changing one cue re-renders
one file and leaves the other byte-identical, which is what makes a re-render a diff of nothing.

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

import suppression as suppression_lib  # noqa: E402
from dsp import SAMPLE_RATE  # noqa: E402

#: The one number the whole palette hangs off. Changing it re-renders every asset.
DEFAULT_SEED = 0x50_4C_4B_21

SUPPRESSION_OUTPUT = Path("src/main/resources/assets/soulhome/sounds/suppression")


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


def render_suppression(output: Path, seed: int, sample_rate: int) -> list[dict]:
    """
    Suppression's drone and throb (#188). One throb, not three variants: it is a count, and a count
    is easier to follow when every beat of it is the same beat.
    """
    return [
        _emit(output, "drone", suppression_lib.drone(asset_rng(seed, "suppression/drone"), sample_rate),
              sample_rate, "the field around a suppressed player, seamless loop"),
        _emit(output, "throb", suppression_lib.throb(asset_rng(seed, "suppression/throb"), sample_rate),
              sample_rate, "one beat of the ring count"),
    ]


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


def write_sources(output: Path, rendered: list[dict], seed: int, sample_rate: int,
                  origin: str = "(#213)") -> None:
    lines = [
        "# Where these sounds came from",
        "",
        f"Every file in this directory was generated by `tools/ambience/generate.py` {origin}. None of",
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
    parser = argparse.ArgumentParser(description="Render suppression's cues (#188).")
    parser.add_argument("--seed", type=lambda value: int(value, 0), default=DEFAULT_SEED,
                        help="master seed; every asset's own seed is derived from this and its name")
    parser.add_argument("--sample-rate", type=int, default=SAMPLE_RATE)
    parser.add_argument("--set", choices=("suppression", "ambience", "all"), default="suppression",
                        help="which set to render; only suppression is still rendered here")
    parser.add_argument("--suppression-out", type=Path, default=SUPPRESSION_OUTPUT,
                        help="where suppression's .ogg files go")

    arguments = parser.parse_args()

    if arguments.set != "suppression":
        parser.error("the ambience is no longer rendered here: tools/ambience/mix.py mixes it from "
                     "recordings (#163). This script renders --set suppression, which is the default.")

    print(f"Rendering suppression to {arguments.suppression_out} (seed 0x{arguments.seed:X})")

    rendered = render_suppression(arguments.suppression_out, arguments.seed, arguments.sample_rate)
    write_sources(arguments.suppression_out, rendered, arguments.seed, arguments.sample_rate,
                  origin="(#188, on the same machinery as #213)")
    _report(arguments.suppression_out, rendered)

    return 0


def _report(output: Path, rendered: list[dict]) -> None:
    total = sum((output / entry["file"]).stat().st_size for entry in rendered)
    print(f"\n{len(rendered)} assets, {total / 1024:.1f} KiB in total.")


if __name__ == "__main__":
    raise SystemExit(main())
