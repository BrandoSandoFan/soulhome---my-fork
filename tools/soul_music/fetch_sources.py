# File created ~ 17 - 10 - 2026
"""
Fetches the two CC0 sample libraries the soul's music and ambience are recorded from (#163).

    python3 tools/soul_music/fetch_sources.py /path/to/sources

clones VSCO-2-CE and VCSL into ``/path/to/sources/VSCO-2-CE`` and ``/path/to/sources/VCSL``, each
checked out at the commit pinned below, and no further: the two libraries together are several
gigabytes, and the builds read a few hundred megabytes of them. So the clones are blobless (history
without file contents) and sparse (only the folders listed below have a working tree), and the
contents of those folders arrive as the checkout asks for them.

Run it again and it does what is missing: an existing clone is reused, the pinned commit is fetched
only if it is not already there, and the sparse list is re-applied. Nothing is deleted.

The folder lists were read off the path constants in ``build_bank.py`` (the instrument bank) and
``tools/ambience/mix.py`` (the ambience), not kept as a second opinion of their own. If one of those
scripts starts reading a recording from a folder that is not listed here, its build fails with a
``FileNotFoundError`` naming the file - add the folder to the list, and to the pins if the commit
has to move.

Both libraries are CC0 1.0. The commits are pinned because a rebuild from a moved ``main`` is a
different bank, and ``SOURCES.md`` records the pins so the bank can say what it was recorded from.
"""
from __future__ import annotations

import argparse
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Library:
    name: str
    url: str
    commit: str
    folders: tuple[str, ...]


LIBRARIES: tuple[Library, ...] = (
    Library(
        name='VSCO-2-CE',
        url='https://github.com/sgossner/VSCO-2-CE.git',
        commit='440300901dfe9275fd84e0b7763af1f8443ae62e',
        folders=(
            # build_bank.py: pad, epiano, drone, flute, pluck, chirp
            'Strings/Cello Section/susvib',
            'Strings/Violin Section/susVib',
            'Strings/Harp',
            'Keys/Upright Piano',
            'Keys/Organ/Quiet',
            'Woodwinds/Flute/susNV',
            'Woodwinds/Flute/stac',
            'Percussion/Glock',
            # mix.py: the horn of warm, the gong under base and the big beds
            'Brass/F Horn/sus',
            'VSCO 1 Percussion/varMetal/Gong',
        ),
    ),
    Library(
        name='VCSL',
        url='https://github.com/sgossner/VCSL.git',
        commit='c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e',
        folders=(
            # build_bank.py
            'Idiophones/Struck Idiophones/Hand Chimes',
            'Idiophones/Struck Idiophones/Vibraphone/Soft Mallets',
            'Idiophones/Struck Idiophones/Marimba',
            'Idiophones/Struck Idiophones/Anvil',
            'Idiophones/Struck Idiophones/Woodblock',
            'Idiophones/Struck Idiophones/Claves',
            'Idiophones/Struck Idiophones/Cabasa',
            'Idiophones/Struck Idiophones/Shaker, Small',
            'Idiophones/Struck Idiophones/Suspended Cymbal 1',
            'Idiophones/Friction Idiophones/Wine Glasses',
            'Membranophones/Struck Membranophones/Frame Drum',
            'Membranophones/Struck Membranophones/Bass Drum 2',
            'Membranophones/Other Membranophones/Ocean Drum',
            # mix.py only
            'Idiophones/Struck Idiophones/Vibraphone/Bowed',
            'Idiophones/Struck Idiophones/Triangles',
            'Idiophones/Struck Idiophones/Tubular Bells 1',
            'Idiophones/Struck Idiophones/Bell Tree',
            'Membranophones/Struck Membranophones/Timpani 1',
        ),
    ),
)


def git(*args: str, cwd: Path | None = None, stdin: str | None = None) -> str:
    """Runs git and returns its stdout; a failing command stops the fetch rather than limping on."""
    result = subprocess.run(['git', *args], cwd=cwd, input=stdin, text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f'git {" ".join(args)} failed in {cwd or "."}:\n{result.stderr.strip()}')
    return result.stdout.strip()


def fetch(library: Library, root: Path) -> None:
    checkout = root / library.name
    print(f'{library.name} at {library.commit[:12]} -> {checkout}')

    if not (checkout / '.git').exists():
        if checkout.exists() and any(checkout.iterdir()):
            raise SystemExit(f'{checkout} exists and is not a git clone; move it aside and run again')
        checkout.mkdir(parents=True, exist_ok=True)
        git('clone', '--filter=blob:none', '--no-checkout', library.url, str(checkout))
    elif git('remote', 'get-url', 'origin', cwd=checkout) != library.url:
        raise SystemExit(f'{checkout} is a clone of another repository; move it aside and run again')

    git('sparse-checkout', 'set', '--cone', '--stdin', cwd=checkout, stdin='\n'.join(library.folders) + '\n')

    if not _has_commit(checkout, library.commit):
        git('fetch', '--filter=blob:none', 'origin', library.commit, cwd=checkout)

    git('-c', 'advice.detachedHead=false', 'checkout', '--quiet', '--detach', library.commit, cwd=checkout)

    head = git('rev-parse', 'HEAD', cwd=checkout)
    if head != library.commit:
        raise SystemExit(f'{checkout} is at {head}, not the pinned {library.commit}')


def _has_commit(checkout: Path, commit: str) -> bool:
    result = subprocess.run(['git', 'cat-file', '-e', f'{commit}^{{commit}}'], cwd=checkout,
                            capture_output=True)
    return result.returncode == 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('directory', type=Path, help='where the two checkouts go')
    root = parser.parse_args().directory.resolve()
    root.mkdir(parents=True, exist_ok=True)

    for library in LIBRARIES:
        fetch(library, root)
        print('  sparse folders:')
        for folder in library.folders:
            print(f'    {folder}')

    print(f'done: {root}')


if __name__ == '__main__':
    main()
