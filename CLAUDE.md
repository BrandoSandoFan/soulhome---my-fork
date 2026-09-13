# SoulHome

A Minecraft **1.21.1 / NeoForge 21.1.250** mod (Java 21). A player gets a private skyblock-esque
dimension - their "soulhome" - reached with a SoulKey. What they build in there is scanned,
recognised as rooms, and turned into buffs they carry in the overworld.

This file is for agents working on the repo. It exists so you do not have to rediscover the build
workaround, the architecture, or the invariants that are easy to break silently.

An agent working here is usually running in an environment with free permissions - broad tool
access with little or no per-action approval - rather than a tightly sandboxed one. That changes
nothing about the engineering judgment this file asks for: verify the offline build and tests the
way this file describes, do not claim something works without having run it, and still treat
destructive or hard-to-reverse actions (force-pushing, discarding uncommitted work, and the like)
with the same caution as ever.

---

## Building and testing

`./gradlew build` compiles main + test and runs the JUnit suite. That is what CI does
(`.github/workflows/build.yml`). Note the repo stores `gradlew` **without** the executable bit, so
CI does `chmod +x ./gradlew` first; locally use `sh gradlew ...` if you hit "Permission denied".

### Gradle needs network access, and often does not have it

ModDevGradle resolves from `maven.neoforged.net` and decompiles Minecraft on a cold cache. In a
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
  src/main/java/leaf/soulhome/structures/BuiltinBondRelations.java \
  $(find src/test/java/leaf/soulhome/structures/core -name '*.java')

java -jar $SP/junit.jar execute -cp "$SP/out:$SP/gson.jar:src/main/resources:src/test/resources" \
  --select-package leaf.soulhome.structures.core --details=summary
```

That runs the great majority of the suite (region detection, classification, form clauses, buff
maths). What it does **not** cover, and what CI is therefore the first real compile of:

| Not covered offline | Why |
| --- | --- |
| `config/SoulHomeConfig` | `ModConfigSpec` |
| `structures/SnapshotBlockVolume`, `ArchetypeManager`, `StructureScanService` | `ServerLevel`, datapack reload |
| `feedback/RegionHighlight`, all `network/*` | Mojang `Codec` (DataFixerUpper) |
| `datagen/**` and its tests | `DataGenerator`, `Codec` |
| everything under `buffs`, `items`, `client`, `mixin` | Minecraft |

For a file you cannot compile, `javac` it anyway against the offline classpath and check that
**every** error is a missing NeoForge/Minecraft symbol rather than a syntax error. That catches most
mistakes.

### When you do have network access, run the game

`./gradlew runData` regenerates `src/main/generated` and is the only check that the datapack the mod
ships still parses; `./gradlew runServer` boots a dedicated server, which is where NeoForge's
registration rules bite (see the next section). The run tasks are wired to the real stdin, so
`printf 'soulhome analyse\nstop\n' | ./gradlew runServer --console=plain` drives the console. The
client boots headless under `xvfb-run -a sh gradlew runClient` with `LIBGL_ALWAYS_SOFTWARE=1`; the
narrator and OpenAL failures in that log are the container having no speech library and no sound
card, not the mod.

### NeoForge rejects three things Forge quietly tolerated

All three compile cleanly and fail at runtime, so the server boot above is what catches them:

- **`EVENT_BUS.register(obj)` on an object with no `@SubscribeEvent` methods of its own throws.**
  Most `SoulBuffEffect`s have none - every active runs when a player presses a key, and
  `FortuneEffect`'s hook is a loot modifier - so `SoulBuffEffect#register` checks
  `getDeclaredMethods` before handing anything to the bus.
- **It also throws if a *supertype* declares one.** `AttributeBuffEffect` is the one base class with
  a shared hook, so it adds its tick listener with `addListener` instead of annotating it. Do not
  put `@SubscribeEvent` on a method of a class that is extended.
- **`EntityDataSerializers.registerSerializer` throws for a mod.** A raw registration hands out ids
  by call order, which desyncs a client and server that loaded mods in different orders;
  `DataSerializersRegistry` uses `NeoForgeRegistries.ENTITY_DATA_SERIALIZERS` instead.

There is no formatter or linter in the build.

---

## Architecture

The whole feature is one pipeline. Follow it in this order when you need to understand a change:

```
ServerLevel
  └─ SnapshotBlockVolume.capture      server thread; copies a box of blocks into arrays
      └─ RegionScanner.scanWithAdjacency   worker thread; carves the copy into SoulRegions, and
          │                                  computes how they relate (RegionAdjacency)
          └─ ArchetypeClassifier      worker thread; scores each region against every archetype,
              │                       then each awarded room's bonds against the other awards
              └─ AwardedRoom / BuffCalculator   what the rooms are worth
                  └─ SoulBuffs / PlayerSoulBuffs   capability on the player
                      └─ buffs/effects/*          what a magnitude actually does in the world
```

`StructureScanService` owns the whole flow and the threading. `ScanDebouncer` decides when a scan
is due. `feedback/` and `network/` carry the result to the client for `/soulhome analyse` and the
Soul Lens overlay.

### `structures/core` is Minecraft-free, on purpose

Every class under `leaf.soulhome.structures.core` is pure Java. No `BlockState`, no `Level`, no
NeoForge. That is what makes region detection and scoring testable without booting the game, and it is
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
| `structures` | the game-facing half: snapshot, datapack loading, scan scheduling, saved data, codecs (`ArchetypeCodecs`, `FormCodecs`, `BondCodecs`) |
| `config` | one `ModConfigSpec`; every knob is server-side and read through an immutable `Snapshot` |
| `buffs`, `buffs/effects` | the capability holding a player's magnitudes, and one class per buff type |
| `feedback` | `SoulReport` (chat text for `/soulhome analyse`) and `RegionHighlight` (lens boxes) |
| `network` | sync messages: buffs, regions, archetypes, dimension list |
| `client` | Soul Lens rendering, client-side buff hooks |
| `commands` | `/soulhome analyse`, `/soulhome buffs` |
| `compat` | other mods' attributes, resolved by name so none of them is a hard dependency |
| `datagen` | the Patchouli guide book, lang, recipes, advancements. Output is committed under `src/main/generated`. |
| `mixin` | a handful of accessors; see `soulhome.mixins.json` |

---

## A soul is entered with a key, and nothing else

`CommonEvents#onTravelToDimension` cancels any travel into or out of a soul dimension that this mod
did not start. Waystones is what prompted it - a warp plate inside someone's soul is a public door
into a private dimension, and a scroll out of one skips both the exit position saved on the way in
and the rescan on the way out - but it is written against NeoForge's `EntityTravelToDimensionEvent`, so
one rule covers every teleport in the game.

Our own moves are exempt because `TeleportHelper#teleportEntity` wraps them in
`SoulTravel#asSoulTravel`. **Anything new that moves a player in or out of a soulhome has to go
through `TeleportHelper`**, or it will be cancelled by our own guard. `dimension.restrict_travel`
turns the rule off for a pack that wants its own way in.

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
- **Only an open archetype's palette seeds an open-air cluster, and only the parts of it that are
  not terrain.** `ArchetypeSignals.openClusterFilterFor` is what the scanner clusters around;
  `filterFor` is what the classifier counts, and the two are different questions (#134). A signal
  that is also simply what the ground is made of - a spire's masonry, a farm's water, an apiary's
  wildflowers - is marked `"seed": false` in its archetype and is counted only where a region takes
  it in. Let terrain seed and a fresh soul is one island-sized region before a block is placed,
  and two builds chain together through the ground between them.
- **A shared shell cell is worth one block in total**, split evenly among the rooms touching it,
  except that a cell a room's air stands on is that room's floor and is credited only to it
  (#136, #137). Decided on who touches the cell and from which face, never on scan order. This is
  why `BlockCounts` carries fractional credit: the classifier scores `credit()` exactly and
  `count()` is the whole-block view, rounded down, for reports and requirements.
- **Fabric is full blocks.** `claimBuildingFabric` claims only `isFullBlock()` blocks packed
  against a shell; a garden's farmland on a flat roof is on the building, not part of it (#138).

---

## Archetypes are data, not code

`data/<namespace>/soulhome_archetypes/<name>.json`, loaded by `ArchetypeManager`, parsed by
`ArchetypeCodecs`/`FormCodecs`. The shipped archetypes live under
`src/main/resources/data/soulhome/soulhome_archetypes/` - a count in prose here would only go stale
again, and has three times already. A malformed archetype is logged and skipped; it never fails the
reload.

Shape of one:

- `region_types` (`enclosed` / `open`) and `min_volume` - what kind of space it can be at all
- `requirements` - hard gates ("at least 9 crops"); failing one scores 0 but still reports why
- `signals` - `match` (block id or tag), `weight`, `role`, `cap`. Counts go through `sqrt` before
  weighting, and distinct `role`s multiply the score, so variety beats volume by construction.
- `detractors` - negative evidence (an anvil argues a room is not a library)
- `structures` - *forms*: how blocks are arranged, not just what they are. Clause vocabulary is
  registered in `BuiltinFormClauses` (`loop`, `platform`, `enclosure`, `line`, `cluster`, `within`,
  `at_range`, `above`, `beneath`, `beside`, `across`, `along`, `surrounds`, `inside`, `lane`,
  `apex`, `soulhome:irregularity`, `verticality`, `facing`). A datapack can register its own onto
  its own `FormClauseRegistry`.
- `tiers` and `buffs` - score thresholds and what they pay out
- `aspects` - optional, and what a room of this kind can be *for* rather than what it is. See below.

Which blocks the scanner even bothers clustering around is derived from the loaded archetypes by
`ArchetypeSignals`, so a datapack that adds an archetype gets its blocks detected with no Java
change. Tags live in `data/soulhome/tags/blocks/`.

### Bonds: how a room sits relative to other rooms

`bonds` on an archetype (#140) is the third kind of evidence beside `signals` and `structures`:
`with` names another archetype, `relation` one of a closed vocabulary registered in
`BondRelationRegistry` (`adjoins`, `near`, `connects`, `above`/`beneath`, `encloses`/`within`, via
`BuiltinBondRelations`), `weight` is positive for a bond and negative for a discord, `role` feeds
the diversity multiplier, and relation-specific parameters follow the same `ClauseParamSpec` shape
clauses use. Rules that hold, and that the tests pin:

- **Declared once, credited to both rooms.** `BondBook` resolves every declaration for both sides,
  mirrors a directional relation (`mine beneath workshop` reads `workshop above mine` from the
  workshop), and keeps one of two declarations that describe the same bond - the one on the
  archetype whose id sorts first - logging the other at load.
- **Bonds are scored against the awards, and never re-run.** `ArchetypeClassifier.classify(List,
  RegionAdjacency)` classifies every region on its own first, then grades each awarded room's bonds
  against the other awards. A bond adjusts what a room is worth, never what it is. Bond credit is
  capped at `bondShareCap` of the room's own signal and arrangement total; discords are not capped,
  and can cost a room every tier but its first.
- **Every relation reads only `RegionAdjacency`**, computed once per scan by `RegionScanner`:
  shared shell cells, a walkable path length, a geodesic separation, and hole-filled footprints. The
  floods behind path and separation run out to `ArchetypeSignals.adjacencyReachFor`, which is zero
  when no loaded bond is distance-based - so a pack without bonds pays nothing for them.
- **Connectivity crosses doors; region detection does not.** Both are right, for different
  questions, and the javadocs on `RegionAdjacency` and `RegionScanner` each point at the other so
  neither is "fixed" to match.
- **Order independence.** Every relationship is symmetric, and each region's `identityHash` folds
  in a commutative digest of its relationships computed from every region's own hash in a second
  pass. `RegionAdjacencyTest` and `BondScoringTest` mirror layouts to prove it.
- **`ArchetypeCeilingTest` judges the tier bands on the solo ceiling** and separately bounds how
  much headroom bonds may add (`ArchetypeCeiling.withBonds`), because thresholds only ever come
  down. Give every positive bond on a room the same role, or the diversity bump alone trips it.
- **The book documents bonds from the data** (`PatchouliMultiblocks.bondPages`), and its explainer
  gates on the `two_rooms` advancement, fired when one scan awards two rooms.

### Aspects: what a room is for, as opposed to what it is

`aspects` on an archetype (#171) is the fourth kind of thing an archetype declares, and the only one
that is not evidence. An archetype says what a room *is*; an aspect says what it is *for* - a library
of shelves and stores is an `archive` and keeps its XP gain, one of lecterns laid out in rows under
light is a `scriptorium` and grants enchanting levels. `library`, `hearth` and `mine` ship with two
each; nothing else has any, and the set is meant to stay small.

Each aspect carries `id`, `display_name`, `leans` (weighted block evidence), optionally its own
`structures`, and optionally its own `buffs`. Exactly one is marked `"default": true` and pays the
archetype's own top-level `buffs`. The rules that hold, and that the tests pin:

- **The aspect selects the buff. It never scales it.** This is the one that will be broken by
  accident, because it runs against how everything else here works. The magnitude is computed exactly
  as it always was - signals, arrangement, tier, ramp, falloff, rank, ceilings, all untouched - and
  the aspect decides only which `BuffSpec` that magnitude is paid into. A room whose aspects stand at
  0.9 and 0.7 pays exactly what a room whose stand at 0.999 and 0.99 pays. Mixed evidence diluting is
  right for deciding what a room is and would be badly wrong for deciding what it is for: a library
  with a fine archive *and* a fine scriptorium is a better library, not a worse one. If
  `ArchetypeCeilingTest` ever moves because of an aspect, this rule has been broken.
- **`leanSupport` appears in no expression that produces a score.** `AspectSelector` runs after the
  score is settled, and `AspectSelection` carries no number a magnitude could be derived from.
- **Every block an aspect reads is a block the room already scores.** Checked at load by
  `ArchetypeDefinition#validationErrors`, conservatively and without touching a registry: each id or
  tag a lean (or an aspect form's element) names must be named by one of the archetype's own
  `signals`. Otherwise a player would be choosing between a better room and the buff they wanted.
  Widening a room's signals to satisfy this is the intended fix, and is a buff to describe as one.
- **An aspect may ask for an arrangement its room does not.** A library is expected to hold a lectern;
  only a scriptorium is expected to hold them in ranked rows. So an aspect's `structures` are graded
  exactly as the archetype's are, out of `RegionGeometry`, and credited **only** to the aspect - never
  to the room's arrangement total. `ArchetypeSignals` reads `ArchetypeDefinition#allForms` so their
  elements are still indexed; `ArchetypeCeiling` deliberately does not, since they score nothing.
- **The default is the anchor, and nothing is remembered.** A challenger takes a room only by leading
  the default by `aspectMargin`; ties and near-ties go to the default. That is what makes "a player
  who updates and changes nothing notices nothing" a property of the mechanism rather than a hope
  about tuning, and it is why there is no hysteresis and no saved previous aspect - #176 requires
  selection be derived every scan and never trusted from a save.
- **Off means off, everywhere.** `aspects.enabled` (default on) is rule 1 of the epic, and "there is a
  switch" and "the switch restores the old behaviour" are different claims. With it off no aspect is
  selected at all, so `ArchetypeScore#aspect` and `AwardedRoom#aspectId` are null and every surface
  that could mention one is silent without checking the config - there is nothing to check. Nothing
  is written to the save, `identityHash` is untouched, and an archetype declaring aspects still loads
  and pays its own buffs. `AspectsDisabledTest` pins each of those.
- **The player is told.** `/soulhome analyse`, the lens and `/soulhome buffs` name the aspect taken,
  the runner-up and the margin, what would tip it in blocks, and - explicitly - that the split cost
  the room nothing. That last line is not decoration: two competing numbers mean a dilution
  everywhere else in this mod, and an unexplained aspect reads as a nerf.

### Rooms written for mods this one does not depend on

Two archetypes, `arcane_sanctum` and `ritual_chamber` (Iron's Spells 'n Spellbooks), name another
mod's blocks directly - and one more, `mine`, leans on the cross-loader convention tags `c:ores` and
`c:storage_blocks`, which is safe (NeoForge defines them) but is still the only third-namespace
dependency in the set. Nothing about any of that is a special case in Java, and it
must not become one:

- **An archetype naming a missing block is fine.** `BlockMatcher` never touches a registry, so an
  id from an absent mod simply never matches and the room can never be awarded. `arcane_sanctum` and
  `ritual_chamber` do exactly this.
- **A tag entry naming a missing block is not.** A vanilla block tag with an unknown id fails to
  load *and takes the whole tag with it*, so every cross-mod entry is written
  `{"id": ..., "required": false}`. `TagDocs` and the tag tests read both forms; the glossary lists
  the optional ones apart, since "you may not have this" is a different promise from "this counts".
- **`soulhome:machinery` is the seam for tech mods**, and the worked example of the tag pattern
  above: `workshop` names no Create block directly at all, only `soulhome:machinery` and a handful
  of other `soulhome:` tags, with Create appearing solely inside `machinery.json`'s optional
  entries. Create fills the seam today; another mod is a datapack away from the workshop, with no
  Java change.
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
rather than repeating a number.

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
- **`SoulIslandCorpusTest`** is the regression corpus (#139): it scans the three shipped starter
  islands - read straight from `data/soulhome/structure/soul_island{0,1,2}.nbt` by
  `SoulIslandVolume`, through a Minecraft-free `NbtReader` - and asserts what a brand-new soul
  reports: no classified rooms, no region covering more than a small share of the island, two
  builds a modest distance apart read as two regions, a sealed room classifies as what it was built
  as, and identical hashes on a second scan. It runs offline. The one thing a template cannot say is
  how each block behaves in a scan; that lives in `src/test/resources/soul_islands/palette.json`
  (passability as `SnapshotBlockVolume` would derive it, tags as the tag files give them), and a
  template block missing from it fails the corpus by name. A changed template needs no
  regeneration step - only a palette entry for any new block it introduces.

When you change region detection, reproduce the bug as a failing test in `RegionScannerTest` first.
Its layouts are the clearest documentation of what the scanner is supposed to do. Then run the
corpus: every fault in #133 was one the corpus would have caught on the day the islands shipped.

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
  resource path. If you change a datagen source (e.g. `PatchouliMultiblocks` or `EngLangGen`),
  update the corresponding JSON under `src/main/generated` to match. CI runs `./gradlew runData`
  and fails on any diff outside `src/main/generated/.cache`, so a drift is caught rather than
  shipped - but it is caught late, after a full ModDevGradle setup, so it is worth getting right
  the first time.
- The guide book runs in Patchouli's i18n mode, which pushes every string through `String.format`.
  A lone `%` renders the page as "Format error:". `PatchouliFormatSafetyTest` guards this.
- Git: develop on the branch you were given; do not open a pull request unless asked.
- **Mainlines are named after the game version**, not `main` or `master`. **`1.21.1`** is the one
  this file describes and the one to branch from; **`1.20.1`** is the previous line, still there for
  backports, and a change made there does not automatically belong here - the loader is different.
  Check which one you are on before assuming a symbol exists.
- **Datapack folder names are singular in 1.21**: `advancement`, `loot_table`, `recipe`,
  `tags/block`, `structure`. A file under the 1.20.1 plural name is silently not loaded.
