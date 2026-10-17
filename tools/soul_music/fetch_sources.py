# File created ~ 17 - 10 - 2026
"""
Fetches the two CC0 sample libraries the soul's music and ambience are recorded from (#163), and the
Freesound recordings the ambience and the bank's foley are mixed from.

    python3 tools/soul_music/fetch_sources.py /path/to/sources

clones VSCO-2-CE and VCSL into ``/path/to/sources/VSCO-2-CE`` and ``/path/to/sources/VCSL``, each
checked out at the commit pinned below, and no further: the two libraries together are several
gigabytes, and the builds read a few hundred megabytes of them. So the clones are blobless (history
without file contents) and sparse (only the folders listed below have a working tree), and the
contents of those folders arrive as the checkout asks for them.

It also fetches each Freesound recording in ``FREESOUND_IDS`` into ``/path/to/sources/freesound``: the
sound's HQ preview as ``<id>.ogg`` and a ``<id>.json`` with its title, author, page and licence, which
both builds read to credit the author in ``SOURCES.md``. A recording is written only if its own page
links a Creative Commons CC0 licence; a page that does not is refused, nothing is written for it, and
the run exits non-zero. Previews are not pinned to anything the way the libraries are: if Freesound
re-encodes a preview, the bank changes, and the rebuild's ``git status`` shows it.

Run it again and it does what is missing: an existing clone is reused, the pinned commit is fetched
only if it is not already there, the sparse list is re-applied, and a Freesound recording already on
disk is skipped. Nothing is deleted.

The folder lists were read off the path constants in ``build_bank.py`` (the instrument bank) and
``tools/ambience/mix.py`` (the ambience), not kept as a second opinion of their own. If one of those
scripts starts reading a recording from a folder that is not listed here, its build fails with a
``FileNotFoundError`` naming the file - add the folder to the list, and to the pins if the commit
has to move. A Freesound id the scripts read that is not in ``FREESOUND_IDS`` stops this run before
anything is cloned, naming the line that reads it.

Both libraries are CC0 1.0. The commits are pinned because a rebuild from a moved ``main`` is a
different bank, and ``SOURCES.md`` records the pins so the bank can say what it was recorded from.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]


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
            # build_bank.py: pad, epiano, bell, pluck, drone, flute; mix.py: the harp of arcane
            'Strings/Cello Section/susvib',
            'Strings/Violin Section/susVib',
            'Strings/Harp',
            'Keys/Upright Piano',
            'Woodwinds/Clarinet/susLong',
            'Keys/Organ/Quiet',
            'Woodwinds/Flute/susNV',
            # no script reads these now; kept so a re-run does not take them out of an existing checkout
            'Woodwinds/Flute/stac',
            'Percussion/Glock',
            'Brass/F Horn/sus',
            'VSCO 1 Percussion/varMetal/Gong',
        ),
    ),
    Library(
        name='VCSL',
        url='https://github.com/sgossner/VCSL.git',
        commit='c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e',
        folders=(
            # build_bank.py: glass (bowed and soft-mallet vibraphone), mallet, thump, swell
            'Idiophones/Struck Idiophones/Vibraphone/Bowed',
            'Idiophones/Struck Idiophones/Vibraphone/Soft Mallets',
            'Idiophones/Struck Idiophones/Marimba',
            'Membranophones/Struck Membranophones/Frame Drum',
            'Idiophones/Struck Idiophones/Suspended Cymbal 1',
            # mix.py: the timpani under wrought, the bass drum under hollow
            'Membranophones/Struck Membranophones/Timpani 1',
            'Membranophones/Struck Membranophones/Bass Drum 2',
            # no script reads these now; kept so a re-run does not take them out of an existing checkout
            'Idiophones/Struck Idiophones/Hand Chimes',
            'Idiophones/Struck Idiophones/Anvil',
            'Idiophones/Struck Idiophones/Woodblock',
            'Idiophones/Struck Idiophones/Claves',
            'Idiophones/Struck Idiophones/Cabasa',
            'Idiophones/Struck Idiophones/Shaker, Small',
            'Idiophones/Friction Idiophones/Wine Glasses',
            'Membranophones/Other Membranophones/Ocean Drum',
            'Idiophones/Struck Idiophones/Triangles',
            'Idiophones/Struck Idiophones/Tubular Bells 1',
            'Idiophones/Struck Idiophones/Bell Tree',
        ),
    ),
)

# Every Freesound recording either script reads. check_freesound_references() fails the run if a script
# names one that is not here, so this list cannot quietly fall behind the code.
FREESOUND_IDS: tuple[int, ...] = (
    9523, 32655, 103071, 106114, 110218, 117136, 137836, 165526, 166325, 174718, 177958, 181252,
    233254, 233255, 234782, 270588, 270589, 329744, 333221, 342546, 353311, 363169, 365071, 377552,
    380782, 383903, 398077, 398684, 414260, 414762, 414767, 422623, 437356, 443065, 502507, 521972,
    530161, 532191, 533146, 586150, 657255, 734580, 812033, 828434,
)

REFERENCING_SCRIPTS: tuple[Path, ...] = (TOOLS / 'soul_music' / 'build_bank.py', TOOLS / 'ambience' / 'mix.py')

# A recording named by its id: fs(270588) in mix.py, fsnd(270588, root=72) in build_bank.py, and the
# literal paths build_bank.py assembles from. `def fs(` and `def fsnd(` are the helpers, not uses.
_LITERAL_ID = re.compile(r'(?<!def )\bfs(?:nd)?\(\s*(\d+)')
_PATH_ID = re.compile(r'freesound/(\d+)\.ogg')
# A call whose id is a variable - `fsnd(i, ...) for i in (174718, 166325)` - is checked against the
# tuple its loop walks. A variable with no tuple on its line cannot be checked, so it stops the run.
_VARIABLE_ID = re.compile(r'(?<!def )\bfs(?:nd)?\((?!\s*\d)')
_LOOP_TUPLE = re.compile(r'\bfor\s+\w+\s+in\s+\(([\d,\s]+)\)')

FREESOUND_PAGE = 'https://freesound.org/s/{}/'
FREESOUND_USER_AGENT = {'User-Agent': 'Mozilla/5.0 (soulhome asset fetch; CC0 previews)'}
FREESOUND_PAUSE = 1.5
CC0 = 'creativecommons.org/publicdomain/zero/'


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


def referenced_freesound_ids(script: Path) -> dict[int, str]:
    """Each Freesound id a script reads, mapped to the line that names it, for the error message."""
    found: dict[int, str] = {}
    for number, line in enumerate(script.read_text(encoding='utf-8').splitlines(), start=1):
        where = f'{script.name}:{number}'
        ids = {int(i) for i in _LITERAL_ID.findall(line)} | {int(i) for i in _PATH_ID.findall(line)}
        if _VARIABLE_ID.search(line):
            loops = _LOOP_TUPLE.findall(line)
            if not loops:
                raise SystemExit(f'{where}: a Freesound call takes a variable id and no loop tuple is on the '
                                 'line, so the id cannot be checked against FREESOUND_IDS')
            for group in loops:
                ids |= {int(i) for i in re.findall(r'\d+', group)}
        for i in ids:
            found.setdefault(i, where)
    return found


def check_freesound_references() -> None:
    """Stops the run unless every Freesound recording the two scripts read is in FREESOUND_IDS.

    Runs on every invocation, before anything is cloned or downloaded, so a sound added to a script
    without its id here fails the fetch rather than the build that would not find the file.
    """
    referenced: dict[int, str] = {}
    for script in REFERENCING_SCRIPTS:
        for sound_id, where in referenced_freesound_ids(script).items():
            referenced.setdefault(sound_id, where)
    if not referenced:
        raise SystemExit('found no Freesound references in build_bank.py or mix.py; the scan no longer '
                         'matches how they name them')
    missing = sorted((sound_id, where) for sound_id, where in referenced.items() if sound_id not in FREESOUND_IDS)
    if missing:
        listing = '\n'.join(f'  {sound_id}  (read at {where})' for sound_id, where in missing)
        raise SystemExit(f'these Freesound recordings are read by the scripts but not listed in FREESOUND_IDS '
                         f'in fetch_sources.py:\n{listing}')


def freesound_get(url: str) -> bytes:
    """Fetches one URL, politely. Freesound has been seen to answer a bare 403 with no body to a request
    that succeeds a moment later, so a failure is retried with a growing pause; only the fourth stops it."""
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers=FREESOUND_USER_AGENT)
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
        except (urllib.error.URLError, TimeoutError) as error:
            if attempt == 3:
                raise SystemExit(f'{url} failed four times; the last error was: {error}')
            time.sleep(10 * (attempt + 1))
            continue
        time.sleep(FREESOUND_PAUSE)
        return data
    raise AssertionError('unreachable: the loop returns or exits')


def fetch_freesound(sound_id: int, folder: Path) -> str | None:
    """Writes one recording's HQ preview and its metadata. Returns why it was refused, or None."""
    ogg, meta_path = folder / f'{sound_id}.ogg', folder / f'{sound_id}.json'
    page = freesound_get(FREESOUND_PAGE.format(sound_id)).decode('utf-8', 'replace')

    licence_match = re.search(r'href="(https?://creativecommons\.org/[^"]+)"', page)
    licence = licence_match.group(1) if licence_match else ''
    if CC0 not in licence:
        return f'{sound_id}: refused, its page links the licence {licence or "(none found)"}, not CC0'

    preview_match = re.search(r'data-ogg="([^"]+)"', page)
    if preview_match is None:
        return f'{sound_id}: refused, its page names no preview'
    # The page links the low-quality preview; the high-quality one has the same name with -hq.
    preview = preview_match.group(1).replace('-lq.', '-hq.')

    title_match = re.search(r'<title>([^<]*)</title>', page)
    title = html.unescape(title_match.group(1)).strip() if title_match else ''
    user_match = re.search(r'href="/people/([^/"]+)/sounds/%d/"' % sound_id, page)
    user = user_match.group(1) if user_match else ''

    audio = freesound_get(preview)
    if not audio.startswith(b'OggS'):
        return f'{sound_id}: refused, its preview is not an Ogg file'

    ogg.write_bytes(audio)
    meta = dict(id=sound_id, title=title, user=user, url=FREESOUND_PAGE.format(sound_id), licence=licence,
                preview=preview)
    meta_path.write_text(json.dumps(meta, indent=1), encoding='utf-8')
    return None


def fetch_all_freesound(folder: Path) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    print(f'Freesound, {len(FREESOUND_IDS)} recordings -> {folder}')
    refused: list[str] = []
    for sound_id in FREESOUND_IDS:
        if (folder / f'{sound_id}.ogg').exists() and (folder / f'{sound_id}.json').exists():
            print(f'  {sound_id}: already there')
            continue
        reason = fetch_freesound(sound_id, folder)
        if reason is None:
            print(f'  {sound_id}: fetched')
        else:
            refused.append(reason)
    if refused:
        listing = '\n'.join(f'  {reason}' for reason in refused)
        raise SystemExit(f'{len(refused)} Freesound recording(s) refused; nothing was written for them:\n{listing}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('directory', type=Path, help='where the checkouts and the freesound folder go')
    root = parser.parse_args().directory.resolve()
    root.mkdir(parents=True, exist_ok=True)

    check_freesound_references()

    for library in LIBRARIES:
        fetch(library, root)
        print('  sparse folders:')
        for folder in library.folders:
            print(f'    {folder}')

    fetch_all_freesound(root / 'freesound')

    print(f'done: {root}')


if __name__ == '__main__':
    main()
