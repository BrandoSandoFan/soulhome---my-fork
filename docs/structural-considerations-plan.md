# Structural considerations

*Planning document for the follow-up epic to the structure-buffs feature. The issue chain on
GitHub carries the same content, split into deliverable pieces.*

## The problem

The classifier is positionless. `SoulRegion` hands the scorer a `BlockCounts` — a multiset — and
everything downstream reasons about *how many* of a thing a room holds, never *where* any of it is.

That was the right first cut. It is also the reason the shipped archetypes read the way they do:

- **`track.json`** requires 8 rails and rewards up to 32. Eight rails in a heap on the floor score
  exactly as well as eight rails laid in a circuit. Nothing in the mod knows what a track *is*.
- **`hearth.json`** rewards a furnace or a campfire, some lava, some netherrack. Seating is not
  even a signal — because seating scattered at random around a room is not evidence of a hearth,
  and the classifier has no way to tell scattered from gathered.
- **`bedchamber`**, **`library`**, **`training_yard`** all have the same shape of hole. A bed in the
  middle of the floor, a lectern facing a wall, scaffolding nowhere near the slime block.

The blunt version: the mod currently scores a room's *shopping list*, not its *architecture*. A
player who arranges a room thoughtfully gets nothing for it over a player who dumps the same blocks
in a pile.

## What must not break

The whole premise of this feature (epic #1) is that the soulhome is a personal expression, so the
mod judges rooms fuzzily instead of matching a schematic. Every player's library can look different
and all of them work. Adding geometry is the single most likely way to lose that, because the
obvious implementation of "the track should be a circle" is to check for a circle — and then there
is one correct track and we have quietly shipped the multiblock system the epic exists to avoid.

Five rules hold the line:

1. **Structure is evidence, never a gate.** Structural findings may not appear in `requirements`.
   A room that has the blocks still classifies. A room that also arranges them well scores higher.
2. **Graded, never boolean.** Every structural check returns a confidence in `[0, 1]`, not a
   yes/no. A horseshoe of rails is a 0.6 loop. Four chairs on one side of the fire are a 0.5 ring.
   A check that can only return 0 or 1 is a schematic wearing a costume, and should be rejected in
   review.
3. **Shape *families*, not shapes.** "The rails form a closed circuit" — a rectangle, an oval, a
   kidney and a figure-of-eight all pass. Never "the rails form a circle of radius 5".
4. **Structure amplifies effort, it does not replace it.** Structural credit is capped as a
   proportion of the room's block-signal score, so a perfect ring of nothing is worth nothing.
5. **A pile of blocks counts — weakly.** Understanding what a room is *made of* earns something;
   arranging it earns the rest. No build loses a tier: thresholds only ever come down. Minimal
   builds do lose *magnitude*, which is the intent — see "the reward has to be continuous" below.

Rule 5 has a consequence worth writing down now, because it will come up during balancing: if the
audit shows the top of the range becoming too easy once arrangement counts, the fix is to **lower
the structural share cap**, not to raise the thresholds. Raising thresholds takes buffs away from
builds that already earned them.

## The shape of the design

A third kind of evidence sits alongside `signals` and `detractors`: **`structures`**, a list of
weighted *forms*. A form is a named, parameterised, positional predicate with a graded output.

```json
"structures": [
  {
    "form": "soulhome:loop",
    "weight": 8.0,
    "role": "circuit",
    "match": { "tag": "minecraft:rails" },
    "min_cells": 12,
    "ideal_cells": 40
  },
  {
    "form": "soulhome:ring_around",
    "weight": 6.0,
    "role": "gathering",
    "core": { "block": ["minecraft:campfire", "minecraft:soul_campfire", "minecraft:furnace"] },
    "ring": { "tag": "soulhome:seating" },
    "min_radius": 1,
    "max_radius": 4,
    "sectors": 8,
    "min_sectors": 3
  }
]
```

The vocabulary of form *types* is closed and lives in Java; what a datapack composes out of them is
open. This is the same relationship buff types already have: `soulhome:speed` is implemented in
Java, and any archetype may ask for it with any magnitude. We are not building a shape scripting
language, and a datapack naming a form that is not installed should skip that entry with a warning
rather than invalidating the archetype — the same tolerance `BlockMatcher` already extends to
blocks from mods that are not present.

### Scoring

For each form entry:

```
confidence = clamp(form.evaluate(geometry), 0, 1)
contribution = weight × confidence
```

No `sqrt` curve and no cap, unlike signals — and pleasingly, neither is needed. The curve exists to
defeat count-stuffing, and you cannot build *more* of a loop. Confidence is already bounded.

Then, folding into the existing model:

```
structuralRaw = Σ contribution
capped        = min(structuralRaw, structuralShareCap × signalRaw)
raw           = signalRaw + detractorTotal + capped
score         = max(0, raw × diversity × density)
```

`structuralShareCap` defaults to `0.5` and becomes a config entry. Being *proportional to
`signalRaw`* is the point: arrangement multiplies a real room and does nothing for an empty one.

A form's `role` feeds the existing diversity multiplier, but only once its confidence clears
`structuralRoleThreshold` (default `0.25`). Otherwise an accidental 0.02-confidence adjacency buys
a diversity bonus for free.

The alternative — a multiplicative `raw × (1 + Σ weight × confidence)` term, matching how diversity
and density work — was considered and set aside. It composes more neatly but it makes structural
credit invisible in the per-signal point breakdown players are shown, and legibility outranks
elegance here.

### The reward has to be continuous, or none of this is felt

Two measurements against the shipped `track.json`, both of which change what this design has to do.

**The buff is a three-step staircase.** `BuffCalculator` calls `BuffSpec.magnitudeAt(tier)`, which
is `min(max, perTier x tier)` — an integer tier in, a magnitude out. The score never touches it. So
a track scoring 21.14 and a track scoring 46.01 both give exactly +8% speed; everything from 20 to
49.99 is flat.

That is fatal here rather than merely untidy. With a staircase, moving a chair into the ring around
the fire does *nothing* until the room happens to cross a threshold — so the feature reads as broken
at exactly the moment a player is doing the thing it was built to reward.

**A pile counts for nothing.** Eight rails in a heap score 8.49 against a tier-1 bar of 20, and even
32 rails in a heap only reach 16.97 — a single signal role earns no diversity multiplier, so a heap
cannot cross at any count. It only qualifies once a second *kind* of thing is added.

Both are fixed by the same change, and they are coupled: lowering the entry bar is only safe once
the reward ramps rather than jumps, or a pile qualifies and immediately collects the full first-tier
buff. Ramp the magnitude across the archetype's own tier ladder:

```
entry     = tiers[0].minScore
top       = tiers[last].minScore
t         = clamp((score - entry) / (top - entry), 0, 1)
magnitude = max * (entryFraction + (1 - entryFraction) * t)
```

`entryFraction` (suggest 0.10) is what stops qualification from meaning nothing. Then lower the
tier-1 thresholds so a bare pile of the defining blocks lands just inside — for `track`, 20 to 8:

| build | score | now | proposed |
|---|---|---|---|
| 8 rails in a heap | 8.49 | 0% | +2.5% |
| 32 rails in a heap | 16.97 | 0% | +4.5% |
| 32 rails + 8 torches | 21.14 | +8% | +5.5% |
| a real track | 46.01 | +8% | +11.3% |
| a well-arranged track | 80 | +16% | +19.3% |
| an exceptional track | 100 | +24% | +24% |

Tiers survive as labels — what the report says, what `scoreToNextTier` counts towards, what the
advancements fire on. What they stop being is the thing the buff is computed from.

This is a nerf for minimal builds that currently scrape tier 1, and that is the point of rule 5. It
is player-visible and belongs in the changelog rather than being discovered.

No new plumbing: `AwardedRoom` already persists `score` alongside `tier`, and the repeated-room
falloff, per-archetype ceilings, config multipliers and global per-type caps all operate on
magnitudes and are unaffected.

### Geometry

`RegionScanner` currently discards positions. It gains a `RegionGeometry`: a positional index of
the cells in the region, carried on `SoulRegion` alongside the block counts.

Only *structurally interesting* blocks are indexed — the union of every matcher named by any
`structures` entry of any loaded archetype, derived exactly the way `ArchetypeSignals.filterFor`
already derives the open-air cluster filter. A room full of stone indexes nothing; a room with
rails and chairs indexes the rails and the chairs. That bounds the memory to what the forms will
actually ask about, and keeps datapack-added forms working without a Java change.

Two things fall out of this that are easy to miss:

- **`SoulRegion.identityHash` must fold in the geometry.** Today, sliding a chair across the room
  changes no count, so the hash is unchanged, so `SoulHomeBuffData` concludes nothing happened and
  the buffs never refresh. With arrangement mattering, that is a live bug the moment the first form
  ships. The persisted `contentHash` changes shape as a result, so every soulhome recomputes once
  on update — harmless, but worth expecting.
- **Facing is deliberately out of scope for v1.** Chairs *pointing at* the fire is the version of
  the hearth everyone actually wants, but `BlockSignature` keys on `Block` and not `BlockState` on
  purpose, so a lit and an unlit candle collapse to one entry. Widening it would multiply distinct
  signatures and change counting semantics everywhere. The right shape is a separate packed facing
  channel on `RegionGeometry`, leaving `BlockSignature` untouched — a later issue, not this one.

### The v1 vocabulary

| Form | Question it answers | Grading |
|---|---|---|
| `soulhome:adjacency` | Is A next to B? | satisfied A-cells / `ideal_pairs` |
| `soulhome:ring_around` | Do the As surround a B? | occupied angular sectors, over `sectors` |
| `soulhome:loop` | Do the As form a closed circuit? | closure × extent |
| `soulhome:platform` | Do the As form a contiguous floor? | largest contiguous area / `ideal_area` |
| `soulhome:enclosure` | Do the As ring the region's edge? | perimeter cells covered / perimeter |

`loop` is worth spelling out, because it is where "tracks being circles" is answered without ever
asking for a circle. Build a graph over the matched cells (8-neighbour in the horizontal plane with
a ±1 step in Y, which is what rails physically do), then:

- **closure** = fraction of the largest component's cells that survive iteratively pruning every
  degree-1 cell. What is left is exactly the cells on a cycle. A closed ring is 1.0; a ring with a
  siding is a little under; a straight line is 0.
- **extent** = `clamp(cycleCells / ideal_cells, 0, 1)`, floored by `min_cells`, so a four-rail
  square is not a racetrack.

A figure-of-eight scores very well, which is correct and is the sort of thing that tells you the
grading is measuring the right property.

Deferred to a later phase: `facing`, `symmetry`, `spacing`, `clearance`.

### Legibility

A structural miss is *far* more opaque than a missing block. "You need 16 bookshelves and have 3"
is self-solving; "your arrangement is wrong" is a dead end and would make the feature read as
broken — the exact failure mode #12 exists to prevent.

So forms do not return a number. They return a number **and a diagnostic**, computed while
evaluating rather than reconstructed afterwards, in the same spirit as `ArchetypeScore`:

> 24 rails, but the longest closed circuit is 0 — join the two ends
>
> seating covers 3 of 8 directions around the campfire

The Soul Lens can go further and highlight the cells involved: the gap in the loop, the empty
sectors around the fire. `RegionHighlight` already draws region boxes, so the machinery is mostly
there.

### Cost

One extra filtered pass over each region to build the index. Form evaluation is linear or near-
linear in the indexed cell count: `loop` is O(n) with pruning, `ring_around` is O(cores × ring
cells), `platform` a flood fill, `enclosure` a perimeter walk. All of it runs in the existing
worker phase off the snapshot, so there are no new threading concerns.

`ScanSettings` gains `maxGeometryCells` (default 8192). Past that the index is truncated and forms
report zero **with an explicit reason surfaced to the player**, rather than silently scoring badly.

Forms are value records, so a form instance shared across several archetypes — `soulhome:seating`
around a fire is a plausible thing for two archetypes to want — is evaluated once per region and
memoized for the rest of the scan.

## Issue chain

**Phase 1 — Foundations**

1. Region geometry: keep positions for structurally interesting blocks
2. Structural form definitions: format, registry, codec, sync
3. Structural scoring in the classifier
4. Buff magnitude ramps with score, and a pile of blocks just barely counts *(independent of the
   geometry work — can ship on its own)*

**Phase 2 — The vocabulary**

5. `soulhome:adjacency`
6. `soulhome:ring_around` — chairs around the hearth fire
7. `soulhome:loop` — the track is a circuit
8. `soulhome:platform` and `soulhome:enclosure`

**Phase 3 — Making it real**

9. Feedback UX: explaining structural hits and misses
10. Apply forms to the shipped archetypes, and the balance pass
11. Patchouli: document the forms from the data

**Phase 4 — Later**

12. Block facing without widening `BlockSignature` → `soulhome:facing`
13. `soulhome:symmetry`, `soulhome:spacing`, `soulhome:clearance`

The geometry half of Phase 1 is not independently shippable — nothing changes for a player until at
least one form from Phase 2 exists. The reward curve is the exception: it stands on its own and is
worth landing first, because it is what makes every later arrangement improvement actually felt. Phase 3's feedback issue should be pulled forward the moment the first form lands
rather than saved for the end, for the same reason #12 was.

## One drift hazard to close on the way past

`ArchetypeCodecs` (DataFixerUpper, production) and `ArchetypeJsonReader` (Gson, tests) already
parse the same file format twice, by hand, and the test reader's own comment admits it "deliberately
mirrors" the production codec. Adding per-form parameter schemas doubles the surface on which those
two can drift.

Recommended: declare each form's parameters **once**, in `core`, as a small declarative spec — name,
type, default — and have both parsers read that spec generically into a neutral parameter bag that
`core` turns into a typed form. It is more work upfront than writing a `MapCodec` per form and a
Gson mirror per form, and it is the version where a new form cannot be added to one parser and
forgotten in the other.
