# SoulHome

A Minecraft **1.20.1 / Forge 47.3.0** mod (Java 17). A player gets a private skyblock-esque
dimension - their "soulhome" - reached with a SoulKey. What they build in there is scanned,
recognised as rooms, and turned into buffs they carry in the overworld.

This file is for agents working on the repo. It exists so you do not have to rediscover the build
workaround, the architecture, or the invariants that are easy to break silently.

An agent working here is usually running in an environment with free permissions - broad tool
access with little or no per-action approval, and outbound network access to the Maven
repositories - rather than a tightly sandboxed one. **That means you can, and should, run the full
Gradle build yourself, Minecraft and all** (see below); it is not something to leave for CI. None
of this changes the engineering judgment this file asks for: do not claim something works without
having run it, and still treat destructive or hard-to-reverse actions (force-pushing, discarding
uncommitted work, and the like) with the same caution as ever.

---

## Building and testing

`./gradlew build` compiles main + test and runs the JUnit suite. That is what CI does
(`.github/workflows/build.yml`). Note the repo stores `gradlew` **without** the executable bit, so
CI does `chmod +x ./gradlew` first; locally use `sh gradlew ...` if you hit "Permission denied".

### Run the full build locally - it is expected

You will normally have network access to `maven.minecraftforge.net`, Maven Central and the other
repositories `build.gradle` names, and permission to run long commands. So the default is the real
thing: **`sh gradlew build` before every push**, Minecraft classes included. It compiles every
file - the Forge-facing half too - and runs the whole suite. The first run on a cold cache downloads
and decompiles Minecraft and takes several minutes; that is the cost of the check, not a reason to
skip it. Run it in the background if it helps, but run it. The toolchain is pinned to Java 17; if
Gradle reports "No matching toolchains found", the container simply lacks one - install it
(`apt-get install -y openjdk-17-jdk-headless`) rather than treating the build as unrunnable.
The same applies to `sh gradlew runData` when you touch datagen, and to `runServer` when a change
can only be seen in the game (a config file appearing, a world loading) - a dedicated server starts
headless, once `run/eula.txt` says `eula=true`.

Do not substitute the offline path below out of habit, and do not report a change to a
Forge-facing class as verified because it `javac`s with only missing-symbol errors. That is a
fallback for when Gradle genuinely cannot reach its repositories, not an equivalent.

### Fallback: when Gradle cannot reach the network

ForgeGradle resolves from `maven.minecraftforge.net` and decompiles Minecraft on a cold cache. In a
sandbox with no route to that host, **`./gradlew` cannot run at all** - it fails at plugin
resolution before compiling a single file. Confirm that is actually what happened (the error names
the host) before falling back.

Even then, **do not conclude the tests cannot be run.** The parts of this codebase that carry the
interesting logic are deliberately Minecraft-free and compile with plain `javac`:

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
maths). What it does **not** cover, and what CI is the first real compile of when you had to take
this path (say so when you report the change):

| Not covered offline | Why |
| --- | --- |
| `config/SoulHomeConfig`, `config/SoulHomeClientConfig` | ForgeConfigSpec |
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
| `structures/core` | region detection, archetype definitions, scoring, form clauses, buff maths. Minecraft-free. `structures/core/music` is the soul's composer and synth. |
| `structures` | the game-facing half: snapshot, datapack loading, scan scheduling, saved data, codecs (`ArchetypeCodecs`, `FormCodecs`, `BondCodecs`) |
| `config` | two `ForgeConfigSpec`s: the server's, read through an immutable `Snapshot`, and the client's, which holds only the cosmetic ambience knobs |
| `buffs`, `buffs/effects` | the capability holding a player's magnitudes, and one class per buff type |
| `feedback` | `SoulReport` (chat text for `/soulhome analyse`) and `RegionHighlight` (lens boxes) |
| `network` | sync messages: buffs, regions, archetypes, dimension list, a soul's ambience |
| `client` | Soul Lens rendering, client-side buff hooks, the soul's ambience (sky, fog, motes, sound) |
| `commands` | `/soulhome analyse`, `/soulhome buffs` |
| `compat` | other mods' attributes, resolved by name so none of them is a hard dependency |
| `datagen` | the Patchouli guide book, lang, recipes, advancements. Output is committed under `src/main/generated`. |
| `mixin` | a handful of accessors; see `soulhome.mixins.json` |

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

### The body you leave, and the souls you can see into (#181)

Every way into a soul - a cushion, a key, a gaze - leaves a `SoulVesselEntity` where the player
stood, spawned only through `VesselLifecycleService`. The rules, and what breaks if one is missed:

- **One health pool, and it is the player's.** The vessel never loses health. `hurt()` forwards the
  hit to its owner as `soulhome:soul_severed` (datapack JSON under `data/soulhome/damage_type/`,
  looked up through `SoulSevered.KEY`), scaled by the vessel's fragility and gated by
  `vessel.damage_transfer`. `CommonEvents#onLivingHurt` has exactly **one hole** in its blanket
  cancel for that type. Close it and damage transfer silently does nothing while every test passes.
- **`/kill` and the void are removals, not hits.** `LivingEntity#kill` routes through `hurt()` with
  `Float.MAX_VALUE`; the vessel overrides `kill()` and `onBelowWorld()` so an operator never kills a
  meditating player by accident. A removal the owner did not choose ejects them alive.
- **Spectators are invulnerable, and a gazer's body is not.** `GazeService#hurtWhileGazing` lifts
  `abilities.invulnerable` for one `hurt()` call. Do not "simplify" this by tagging soul_severed
  `bypasses_invulnerability` - that tag also stops a totem of undying from working.
- **The spill runs in death order.** A gazer is handed their game mode back in `LivingDeathEvent`
  (spectators drop nothing), drops and experience move at `LOWEST` priority, and the body is
  removed only after the drops are spawned. Vanilla writes the death position at the *end* of
  `ServerPlayer#die`, so the body's position is applied in `PlayerEvent.Clone`, not the death event.
- **Nobody is stranded as a spectator.** A gaze session is saved in the gazer's persistent NBT the
  moment it starts, and `GazeService#onLogin` restores and closes any it finds. A gazer's dimension
  change is flagged (`isGazeTravel`) so `StructureEvents` never rescans on their account.
- **Suppression is gated on the server.** `SuppressionService` sends a precomputed
  `SuppressionSettings.Signature`, never raw ranks, and only to an observer with an awarded room.
  Their rank is the amount, yours the legibility - `SuppressionSettingsTest` asserts no two rank
  pairs render alike. Every channel reads `ClientSuppression#visible`, which is line-of-sight only.
- **A gaze always leaves a trace.** `GazeSettings#obviousnessFor` falls toward a floor validated
  strictly above zero; `GazeSettingsTest` pins it at any magnitude.

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

### Ambience: what the place looks and sounds like

The soul dimension answers two things and nothing else (#163): how far it has climbed, and what is
built in it. All of it is cosmetic - `SoulAmbienceService` writes nothing, schedules nothing and
consumes a classification that already existed. The rules, and what breaks if one is missed:

- **A soul is never assigned a kind.** `SoulCharacter` sums each room's declared pulls, weighted by
  what that room scored, and hands back leanings rather than a verdict. There is no threshold, no
  label, and nothing in `feedback`, the lens, the book or a command names a trait, an axis or a
  blend. The mod judges fuzzily everywhere else; an ambience announcing "your soul is a fire soul"
  would be the first thing in it to sort a player into a box.
- **Both poles at once is a third thing, not the average.** This is the one that will be
  reimplemented wrongly by someone doing the obvious thing. Warm and cold built in equal measure is
  *steam* - see `SoulAxis` and `SoulAmbience.axisColour`, where `tension` carries the blend toward
  the contested reading. Averaging them would tell a player who has built a great deal of two
  opposite things that they have built nothing in particular.
- **Fog is placed off the far corner of the box**, derived from the soulhome's own verge (or its
  legacy reach, where that is further), so no rank, blend or intensity can put haze between a player
  and something they were allowed to place. `SoulAmbienceTest` sweeps every rank and intensity for
  this. The lightmap is never touched: the light you build by is the same at rank 0 and rank V.
- **Everything is a target, and the client eases toward it.** `ClientAmbience` moves by a fixed
  share per tick, which is both the "interpolate over seconds" of #165 and the whole of #167's
  no-flashing rule - nothing can move faster than the easing allows, whatever arrives on the wire.
- **Which traits a room pulls toward is data; what a trait looks like is not.** An archetype's
  `character` block names ids from `SoulTrait`; the colours and sounds live in
  `SoulAmbience.Palette`. A colour in a datapack is a datapack that can make a soul unreadably dark,
  and #167 rules that out at the level of the mechanism. An unknown trait id is dropped with a load
  warning, never an error - a pack written against a later version should lose its colouring, not
  its rooms.
- **The ambience belongs to the place, not the looker.** `SyncSoulAmbienceMessage` is the only sync
  in the mod sent to a dimension rather than to an owner, so a visitor sees the soul they are
  standing in. Their buffs, rank and attunement stay their own.
- **Read from every classified room, not the attuned ones.** Attunement is about what a player
  carries out; a library they are not carrying today is still standing there. A loadout change
  repainting the sky would read as a bug.
- **Off means off, and it is the player's switch.** `AmbienceSettings.active()` is false at
  `intensity` 0 as well as with the master switch off, and `SoulAmbience.of` then returns `NONE`,
  which every surface treats as "change nothing" rather than "set it to the same value".
- **A one-shot comes from the room that earned it** (#215). `SoulAmbience.oneShotOrigin` picks among
  the soul's classified rooms, weighted by the `character` pull their archetype declares for that
  voice's traits, and a room with no pull toward a voice is never a candidate for it. The direction
  is kept and the distance is not: a hearth forty blocks off is heard *from that direction* at the
  edge of hearing, and a hearth three blocks off is heard three blocks off. A voice with no room
  behind it, and `BASE` always, falls back to the random compass angle - the blend is still right,
  only the direction is unknown. The boxes ride along on `SyncSoulAmbienceMessage`, trimmed
  server-side to rooms whose archetype declares a character at all.
- **Larger, never louder** (#216). `SoulAmbience.oneShotProfile` moves the distance band outward
  with rank and builds a tail (Minecraft has no reverb, so it is repeats of the same event, quieter
  and lower and further round the compass). The first sound is scaled by `leadVolume` so the whole
  tail sums to exactly one unechoed one-shot, and `SoulAmbienceTest` pins that at every rank. A
  bigger soul that is also a noisier one is the failure mode this rule exists to prevent.
- **Nothing the ambience does may cover the game** (#212), and what counts as "the game" is decided
  at this mod's own call sites rather than sniffed off the sound engine. `SoulSounds#playFeedback`
  plays the sound and sends the hold; a sound nobody routed through it holds nothing, which is what
  makes footsteps and block-placing correct by construction. Matching on the sound event instead
  would duck for somebody else's beacon or anvil, and matching on `SoundSource.PLAYERS` inside a
  soul would duck for every block placed - in a dimension whose whole purpose is placing blocks.
  A tail in flight is silenced by a hold rather than allowed to finish.

- **Quiet is relative to the game, and it lives in the assets and the player's knobs** (#163). The
  first playtest found the whole ambience quieter than Minecraft's music, because every layer had
  added its own "if in doubt, quieter" ceiling on top of assets already mastered far down. The
  assets are mastered by `tools/ambience` (one-shots -20 dBFS RMS, limited to -3 peak; bed -24;
  character beds -28), and the Java ceilings only keep the layers in order relative to each other.
  Do not add another attenuation stage; turn a mastering target or a default instead.
- **The soul plays its own music, and vanilla's is held off by a mixin** (#163). `SoulSynth` and
  `SoulComposer` (under `structures/core/music`, Minecraft-free) compose and render it live;
  `SoulMusicPlayer` hands it to the sound engine through Forge's `SoundInstance#getStream`, and
  `MusicManagerMixin` cancels `MusicManager#tick` while a soul's music is on - Forge 47.3.0 has no
  `SelectMusicEvent`. A piece is composed from the brief when it starts and never changes under a
  player; every note is in the piece's mode and no diminished chord is ever held, which
  `SoulComposerTest` pins, and `SoulSynthTest` holds every voice between -28 and -16 dBFS at every
  rank.
- **Vanilla has eight streaming channels, total.** The bed's rank layers, its character layers and
  the music all stream. `SoulAmbienceBed.MAX_CHARACTER_LAYERS` keeps only the loudest few character
  layers alive; a stream that cannot get a channel is silently dropped, so anything new that streams
  has to fit in that budget.
- **The sky's horizon is the fog colour, read back** (`RenderSystem.getShaderFogColor`), never
  recomputed, so terrain at the edge of sight fades into the sky without a seam. `SoulSky` decides
  everything above it; its zenith has a floor of its own and the lightmap is still never touched.

### Attunement: which rooms a soul is actually carrying

A soulhome grants only the rooms bound into its attunement slots (#151). Everything lives in
`structures/core/AttunementBook` (Minecraft-free, so identity matching is testable), with
`AttunementSettings` for the numbers, `RoomBinding` for the state and `structures/AttunementService`
for the server half. The rules, and what breaks if one is missed:

- **What is attuned is the room, and a room is a footprint.** Not the archetype (a player with two
  libraries could not choose), not the `SoulRegion` (its `identityHash` is a digest of contents, so
  one bookshelf would silently unbind an attuned library), not the position. Every classified room
  gets a serial the first time it is seen, and later scans carry it forward by footprint overlap of
  at least `AttunementBook.MIN_OVERLAP_SHARE` of the smaller box. Archetype agreement is a *tiebreak*
  only - a library converted to a workshop is still that room.
- **A binding outlives its room.** Demolish an attuned room and it keeps its slot until the player
  releases it; rebuild it on the same spot and the ghost is re-matched. Freeing the slot would mean
  demolishing something silently re-arms something else, and rebuilding it silently takes that back.
- **A free slot fills itself, once per room.** `AttunementBook.autoFill` binds only ids handed out
  for the first time this scan. That is what makes "a player under their limit notices nothing"
  a property rather than a hope - without it, this epic landing on an existing save zeroes every buff
  in it until its owner walks to their anchor. It never reaches for a room the player *released*:
  that decision is the only thing the whole mechanic exists to ask for.
- **Two pools, decided by what the room pays.** `RoomPool.ACTIVE` for any room granting a
  `SoulBuffTypes.ACTIVE` buff under the aspect it took, `PASSIVE` otherwise. One pool would quietly
  mean every slot goes to an ability.
- **Attunement never touches the total.** `SoulHomeBuffData#totalScore` reads *every* classified
  room, because residue (#82) and the ascension willpower check (#83) do. If attunement ever reduced
  it, attuning nothing while grinding for rank would be the correct play, and that is a worse game.
  `StructureScanService#carriedRooms` is the only filter, and `totalScore` deliberately does not go
  through it.
- **The falloff ranks the attuned subset.** Filtering happens before `BuffCalculator`, so attuning
  your second-best library grants what your best one would have. Anything else punishes a player for
  a choice the system invited.
- **Off means off.** With `attunement.enabled` false nothing is given an identity, so nothing can be
  bound, nothing is written to the save (`AttunementBook.anonymise`), and every report reads
  `AttunementReport.EMPTY` and draws nothing without checking the config. Existing bindings are left
  on disk rather than erased, so turning it back on restores the loadout. `AttunementDisabledTest`
  and `AttunementReportTest` pin that.
- **The player is told, once.** `/soulhome buffs` names every room not being carried and what it
  would grant, the lens says the same out in the world, `/soulhome analyse` marks each room, and the
  first time a soul exceeds its slots it gets one message and a saved flag so it is never repeated.
  Every one of those surfaces says, in as many words, that an unattuned room still counts toward the
  climb - that is the assumption a player will otherwise get wrong, and getting it wrong makes them
  play badly.

### Rooms written for mods this one does not depend on

Two archetypes, `arcane_sanctum` and `ritual_chamber` (Iron's Spells 'n Spellbooks), name another
mod's blocks directly - and one more, `mine`, leans on Forge's own `forge:ores` and
`forge:storage_blocks` tags, which is safe (Forge is guaranteed) but is still the only
third-namespace dependency in the set. Nothing about any of that is a special case in Java, and it
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

Two files, and the split is the whole of the rule: **a knob that changes an outcome is the server's;
a knob that changes only what one person sees is that person's.**

`config/SoulHomeConfig` is the server one, and is where everything belongs unless it is purely
cosmetic. All server-side, read through an immutable `Snapshot` so a reload cannot land halfway
through a scan. Values that fail a settings record's validation fall back to the defaults with a log
line rather than refusing to start - keep that property when adding a knob.

A server config is **per world**: the live file is `<world>/serverconfig/soulhome-server.toml` and
does not exist until that world has loaded once. It is never in the instance's `config/` folder, and
"the config isn't generating" has been reported for exactly that reason. `SoulHomeConfig.register`
therefore also keeps `defaultconfigs/soulhome-server.toml` in the instance folder, which Forge copies
into each new world; it adds new keys to that template but never overwrites a value someone set.
`1.21.1` does not have this problem - NeoForge keeps server configs in `config/` - so the template
exists only on this line.

`ScanSettings`, `ScoringSettings` and `BuffSettings` are records in `structures/core` and are the
single source of truth for defaults; the config spec should reference their `DEFAULT_*` constants
rather than repeating a number.

`config/SoulHomeClientConfig` (`soulhome-client.toml`) is the client one, added by the Ambience epic
(#163/#167). It holds two sections: whether and how strongly a soul answers its rank and its rooms,
and whether suppression (#188) may warp this player's screen or hum at them. It exists because a
server has no business deciding whether one player sees fog, or gets motion sick. Read
straight rather than through a snapshot - nothing is being computed against it, so a value that
changes between two frames is just a value that changed between two frames - and its defaults live
on `AmbienceSettings` in `structures/core` like every other settings record. Nothing on the server
reads it, and nothing that decides an outcome may be put in it.

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
  islands - read straight from `data/soulhome/structures/soul_island{0,1,2}.nbt` by
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
  shipped - but it is caught late, after a full ForgeGradle setup, so it is worth getting right
  the first time.
- The guide book runs in Patchouli's i18n mode, which pushes every string through `String.format`.
  A lone `%` renders the page as "Format error:". `PatchouliFormatSafetyTest` guards this.
- Git: develop on the branch you were given; do not open a pull request unless asked.
- **Branch from `1.20.1`.** That is the mainline to work off of, not `main` or `master`. A second
  mainline, `1.21.1`, carries the Forge 1.21.1 port. Any feature you implement has to reach both -
  land it on your `1.20.1` branch as usual, then port the same change to `1.21.1`. The two are not
  interchangeable: the loader differs, so a port is a second pass against `1.21.1`'s own code, not a
  cherry-pick of the `1.20.1` commit. A fix that only makes sense on one line (a bug that does not
  exist on the other) is the one exception - say so in the commit rather than porting it anyway.
