# SoulHome

A Minecraft **1.20.1 / Forge 47.3.0** mod (Java 17). A player gets a private skyblock-esque
dimension - their "soulhome" - reached with a SoulKey. What they build in there is scanned,
recognised as rooms, and turned into buffs they carry in the overworld.

This file is for agents working on the repo. It exists so you do not have to rediscover the build
workaround, the architecture, or the invariants that are easy to break silently.

---

## Building and testing

`./gradlew build` compiles main + test and runs the JUnit suite. That is what CI does
(`.github/workflows/build.yml`). Note the repo stores `gradlew` **without** the executable bit, so
CI does `chmod +x ./gradlew` first; locally use `sh gradlew ...` if you hit "Permission denied".

### Gradle needs network access, and often does not have it

ForgeGradle resolves from `maven.minecraftforge.net` and decompiles Minecraft on a cold cache. In a
sandbox with no route to that host, **`./gradlew` cannot run at all** - it fails at plugin
resolution before compiling a single file.

**Do not conclude the tests cannot be run.** The parts of this codebase that carry the interesting
logic are deliberately Minecraft-free and compile with plain `javac`:

```sh
SP=/tmp/soulhome-offline && mkdir -p $SP
curl -sSL -o $SP/junit.jar https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar
curl -sSL -o $SP/gson.jar  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar

javac -nowarn -d $SP/out -cp "$SP/junit.jar:$SP/gson.jar" \
  $(find src/main/java/leaf/soulhome/structures/core -name '*.java') \
  src/main/java/leaf/soulhome/structures/BuiltinFormClauses.java \
  $(find src/test/java/leaf/soulhome/structures/core -name '*.java')

java -jar $SP/junit.jar execute -cp "$SP/out:$SP/gson.jar:src/main/resources:src/test/resources" \
  --select-package leaf.soulhome.structures.core --details=summary
```

That runs the great majority of the suite (region detection, classification, form clauses, buff
maths). What it does **not** cover, and what CI is therefore the first real compile of:

| Not covered offline | Why |
| --- | --- |
| `config/SoulHomeConfig` | ForgeConfigSpec |
| `structures/SnapshotBlockVolume`, `ArchetypeManager`, `StructureScanService` | `ServerLevel`, datapack reload |
| `feedback/RegionHighlight`, all `network/*` | Mojang `Codec` (DataFixerUpper) |
| `datagen/**` and its tests | `DataGenerator`, `Codec` |
| everything under `buffs`, `items`, `client`, `mixin` | Minecraft |

For a file you cannot compile, `javac` it anyway against the offline classpath and check that
**every** error is a missing Forge/Minecraft symbol rather than a syntax error. That catches most
mistakes.

There is no formatter or linter in the build.

---

## Architecture

The whole feature is one pipeline. Follow it in this order when you need to understand a change:

```
ServerLevel
  └─ SnapshotBlockVolume.capture      server thread; copies a box of blocks into arrays
      └─ RegionScanner.scan           worker thread; carves the copy into SoulRegions
          └─ ArchetypeClassifier      worker thread; scores each region against every archetype
              └─ AwardedRoom / BuffCalculator   what the rooms are worth
                  └─ SoulBuffs / PlayerSoulBuffs   capability on the player
                      └─ buffs/effects/*          what a magnitude actually does in the world
```

`StructureScanService` owns the whole flow and the threading. `ScanDebouncer` decides when a scan
is due. `feedback/` and `network/` carry the result to the client for `/soulhome analyse` and the
Soul Lens overlay.

### `structures/core` is Minecraft-free, on purpose

Every class under `leaf.soulhome.structures.core` is pure Java. No `BlockState`, no `Level`, no
Forge. That is what makes region detection and scoring testable without booting the game, and it is
worth protecting - **do not import Minecraft into that package.**

The bridge is three small interfaces/records:

- `BlockVolume` - `bounds()`, `passabilityAt(x,y,z)`, `signatureAt(x,y,z)`. Implemented by
  `SnapshotBlockVolume` (game) and `GridVolume` (tests).
- `BlockSignature` - a block id plus its tags. Implemented by `StateSignature` (game) and
  `TestBlocks.TestBlock` (tests).
- `Passability` - see below.

### Package map

| Package | What lives there |
| --- | --- |
| `structures/core` | region detection, archetype definitions, scoring, form clauses, buff maths. Minecraft-free. |
| `structures` | the game-facing half: snapshot, datapack loading, scan scheduling, saved data, codecs |
| `config` | one `ForgeConfigSpec`; every knob is server-side and read through an immutable `Snapshot` |
| `buffs`, `buffs/effects` | the capability holding a player's magnitudes, and one class per buff type |
| `feedback` | `SoulReport` (chat text for `/soulhome analyse`) and `RegionHighlight` (lens boxes) |
| `network` | sync messages: buffs, regions, archetypes, dimension list |
| `client` | Soul Lens rendering, client-side buff hooks |
| `commands` | `/soulhome analyse`, `/soulhome buffs`, `/soulhome ascent` |
| `compat` | other mods' attributes, resolved by name so none of them is a hard dependency |
| `datagen` | the Patchouli guide book, lang, recipes, advancements. Output is committed under `src/main/generated`. |
| `mixin` | a handful of accessors; see `soulhome.mixins.json` |
| `handlers` | Forge event listeners: `CommonEvents` (the travel guard below), `StructureEvents` (scan triggers), `SoulBoundsEnforcement` (the box, below) |

---

## A soul is entered with a key, and nothing else

`CommonEvents#onTravelToDimension` cancels any travel into or out of a soul dimension that this mod
did not start. Waystones is what prompted it - a warp plate inside someone's soul is a public door
into a private dimension, and a scroll out of one skips both the exit position saved on the way in
and the rescan on the way out - but it is written against Forge's `EntityTravelToDimensionEvent`, so
one rule covers every teleport in the game.

Our own moves are exempt because `TeleportHelper#teleportEntity` wraps them in
`SoulTravel#asSoulTravel`. **Anything new that moves a player in or out of a soulhome has to go
through `TeleportHelper`**, or it will be cancelled by our own guard. `dimension.restrict_travel`
turns the rule off for a pack that wants its own way in.

---

## The Ascent epic: a soulhome is a box, not an unbounded void

`SoulBounds` (`structures/core`, Minecraft-free) is a floor, a ceiling and a square verge, sized by
ascension rank via `SoulBounds.forRank`. The floor is exactly as load-bearing as the ceiling: in a
void dimension a player denied a second storey just builds one downward instead, so both ends are
enforced. `DEFAULT_FLOOR_Y` is **70**, matching `DimensionHelper.FLOOR_LEVEL` - the two are not
linked in code (this package cannot import Minecraft), so keep them in sync by hand if either
changes.

`handlers/SoulBoundsEnforcement` refuses placement outside the box - block placement, bucket use and
piston extension are each checked at the destination rather than the source, since that is the
cheapest check that is still correct. Falling blocks, dispensers and TNT-cannon movement are **not**
covered yet; that is a known gap, not an oversight. `ascent.enforce_bounds` (default on) turns the
whole thing off, byte-for-byte reproducing pre-epic behaviour for a pack that wants no limit.

Rank is not tracked yet - every soulhome currently reads as rank 0 - so a build that predates this
epic is still granted its old, larger footprint forever (`SoulHomeBuffData`'s legacy-grant NBT,
captured once on first post-update scan). `/soulhome ascent` reports the current box; `SyncSoulBoundsMessage`
carries it to the client for the Soul Lens and the firmament/verge world rendering
(`SoulBoundsRenderer`).

---

## Region detection: the part that gets changed most

`RegionScanner` turns a `BlockVolume` into a list of `SoulRegion`. Read its class javadoc before
touching it - it explains each decision and why the obvious alternative is worse. In summary:

1. **`markOutside`** floods in from every face of the scan box. Anything the sky can reach is
   "outside", so a room with a hole in its roof is correctly not a room.
2. **`findEnclosedRegions`** turns every remaining sealed pocket into an `ENCLOSED` region, unless
   it is below `minRoomVolume` (a crevice inside a wall is not a room) or above `maxRoomVolume`
   (a cathedral is not a room; it falls through to the open-air pass instead).
3. **`claimBuildingFabric`** claims the solid blocks packed against each room's shell, to
   `shellDepth` layers. Claimed, **not scored** - it decides who owns a block, not what a room is
   worth. Without it a barn's roof, the outer half of a thick wall and the corners of a plain box
   belong to nothing and go on to seed a phantom open-air region on top of the building.
4. **`findOpenRegions`** clusters the leftover signal-bearing blocks into `OPEN` regions - a farm, a
   racetrack. Run in phases (grow all, then slack, then holes, then build) so a build standing
   inside another's ring is its own structure rather than something the ring swallows.

### Rules that are load-bearing - break these and the mod misbehaves quietly

- **Doors are walls.** Doors, trapdoors and fence gates stop the fill whether open or shut.
  Otherwise buffs blink out every time a player walks through their own front door.
- **Ladders and vines are passable**, checked via `BlockTags.CLIMBABLE` before the generic
  collision-shape fallback. A ladder's real collision box is non-empty but short of a full cube, so
  without the explicit check it fell through to `PARTIAL` - same bucket as a fence - and a one-wide
  shaft between two floors would seal into separate rooms depending on incidental placement.
- **`Passability` answers two different questions.** `stopsFill()` - does air get through, which is
  what seals a room; `isFullBlock()` - does it fill its cell, which is the *only* thing that
  separates one open-air build from the next. A fence, a wall, a pane, a slab, a stair or a chest
  is `PARTIAL`: it seals a room, and it does not divide two builds. The track archetype scores
  fencing as part of a track, so a fenced circuit cut off from its own trackside would be the mod
  disagreeing with itself.
- **Cluster reach is geodesic, not straight-line.** A cluster spends a step per cell of clear space
  and refills on arriving at the next signal block. Straight-line distance means solid matter is not
  a boundary, and a player who builds a wall between two builds watches it do nothing.
- **Signal blocks are always crossed**, however solid. Hay bales, ice and farmland are full blocks;
  a spread that stopped at one would skin a haystack instead of taking it in.
- **A region is solid, never a ring.** `fillInteriorHoles` takes in whatever a region closes around,
  judged layer by layer (a rail circuit has open sky over its infield, so in 3D nothing about it is
  enclosed - yet the infield is plainly inside the track). This matters beyond block counts: the
  clearance index that `across ... require_clear` reads is only written for cells the region took
  in, so an unfilled infield reads back as clear open space.
- **Determinism.** The same build must yield the same regions in the same order, because
  `SoulRegion.identityHash` is used to skip rescans. Anything that makes output depend on traversal
  order defeats the caching. Sweeps are in x, y, z order (which is also index order).
- **A scan that cannot see is not a scan that found nothing.** A soul dimension keeps no chunks
  loaded, so an unloaded dimension reads as bare. Only `Capture.Outcome.EMPTY` - loaded and
  genuinely empty - may clear a soulhome's saved rooms. Every other failure leaves them alone. This
  has caused a "all my buffs vanished" bug more than once.

---

## Archetypes are data, not code

`data/<namespace>/soulhome_archetypes/<name>.json`, loaded by `ArchetypeManager`, parsed by
`ArchetypeCodecs`/`FormCodecs`. Nineteen ship with the mod under
`src/main/resources/data/soulhome/soulhome_archetypes/`. A malformed archetype is logged and
skipped; it never fails the reload.

Shape of one:

- `region_types` (`enclosed` / `open`) and `min_volume` - what kind of space it can be at all
- `requirements` - hard gates ("at least 9 crops"); failing one scores 0 but still reports why
- `signals` - `match` (block id or tag), `weight`, `role`, `cap`. Counts go through `sqrt` before
  weighting, and distinct `role`s multiply the score, so variety beats volume by construction.
- `detractors` - negative evidence (an anvil argues a room is not a library)
- `structures` - *forms*: how blocks are arranged, not just what they are. Clause vocabulary is
  registered in `BuiltinFormClauses` (`loop`, `platform`, `enclosure`, `line`, `cluster`, `within`,
  `at_range`, `above`, `beneath`, `beside`, `across`, `along`, `surrounds`, `inside`). A datapack
  can register its own onto its own `FormClauseRegistry`.
- `tiers` and `buffs` - score thresholds and what they pay out

Which blocks the scanner even bothers clustering around is derived from the loaded archetypes by
`ArchetypeSignals`, so a datapack that adds an archetype gets its blocks detected with no Java
change. Tags live in `data/soulhome/tags/blocks/`.

### Rooms written for mods this one does not depend on

Three of the shipped nineteen - `arcane_sanctum`, `ritual_chamber` (Iron's Spells 'n Spellbooks)
and `workshop` (Create) - name blocks that most installs do not have. Nothing about that is a
special case in Java, and it must not become one:

- **An archetype naming a missing block is fine.** `BlockMatcher` never touches a registry, so an
  id from an absent mod simply never matches and the room can never be awarded.
- **A tag entry naming a missing block is not.** A vanilla block tag with an unknown id fails to
  load *and takes the whole tag with it*, so every cross-mod entry is written
  `{"id": ..., "required": false}`. `TagDocs` and the tag tests read both forms; the glossary lists
  the optional ones apart, since "you may not have this" is a different promise from "this counts".
- **`soulhome:machinery` is the seam for tech mods.** Create fills it today; another mod is a
  datapack away from the workshop, with no Java change.
- **A buff written against another mod's attribute goes through `compat/ModAttributes`**, which
  resolves by `ResourceLocation` and returns an empty `Optional` when the mod is absent. Never
  import the other mod's classes: a class reference in the constant pool is a `NoClassDefFoundError`
  the first time anything touches the effect. `AttributeBuffEffect` handles the rest - an effect
  with no attributes to write to does nothing, and says so once in the log at startup.

---

## Config

One file: `config/SoulHomeConfig`, all server-side, read through an immutable `Snapshot` so a reload
cannot land halfway through a scan. Values that fail a settings record's validation fall back to the
defaults with a log line rather than refusing to start - keep that property when adding a knob.

`ScanSettings`, `ScoringSettings` and `BuffSettings` are records in `structures/core` and are the
single source of truth for defaults; the config spec should reference their `DEFAULT_*` constants
rather than repeating a number. `SoulBounds` (see the Ascent section above) follows the same rule
for the `ascent` knobs even though it is shaped as a per-rank factory rather than a flat settings
record.

---

## Tests

`src/test/java`, JUnit 5. Nearly all of it is under `structures/core`.

- **`GridVolume`** builds a world out of ASCII art - layers bottom to top, rows are Z, columns are
  X, automatically padded with one cell of air on every side. Write a case as the shape it
  describes, not as coordinate arithmetic.
- **`TestBlocks`** is the palette. Tags mirror the real tag files and passability mirrors vanilla,
  **including** the `PARTIAL` / `BLOCKING` split. Getting that wrong makes the tests agree with each
  other and disagree with the game.
- `ArchetypeJsonReader` loads the real shipped archetype JSON, so scoring tests are against what
  actually ships rather than a fixture.

When you change region detection, reproduce the bug as a failing test in `RegionScannerTest` first.
Its layouts are the clearest documentation of what the scanner is supposed to do.

---

## Conventions

- **Braces on their own line** (Allman), 4 spaces, no tabs. Match the file you are in.
- Every file opens with `/* File created ~ D - M - YYYY */`.
- **Comments say why, not what.** The codebase's voice explains the decision and the alternative
  that was rejected - "doors count as boundary whether open or shut, because otherwise a player's
  buffs blink out every time they walk through their own front door". Match it; a comment that
  restates the code is worse than none.
- **`Changelog.md`** gets an entry for anything a player would notice, in the same voice: what was
  wrong, what it looked like from the player's side, what it is now, and whether it is a buff or a
  nerf.
- **Generated files are committed.** `src/main/generated/**` is datagen output and is on the
  resource path. If you change a datagen source (e.g. `PatchouliMultiblocks`), update the
  corresponding JSON under `src/main/generated` to match - nothing in CI checks that they agree, so
  a drift ships silently.
- The guide book runs in Patchouli's i18n mode, which pushes every string through `String.format`.
  A lone `%` renders the page as "Format error:". `PatchouliFormatSafetyTest` guards this.
- Git: develop on the branch you were given; do not open a pull request unless asked.
- The mainline branch is **`1.20.1`**, not `main` or `master`.
