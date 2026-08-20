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

Six rules hold the line:

1. **Structure is evidence, never a gate.** Structural findings may not appear in `requirements`.
   A room that has the blocks still classifies. A room that also arranges them well scores higher.
2. **Graded, never boolean.** Every structural check returns a confidence in `[0, 1]`, not a
   yes/no. A horseshoe of rails is a 0.6 loop. Four chairs on one side of the fire are a 0.5 ring.
   A check that can only return 0 or 1 is a schematic wearing a costume, and should be rejected in
   review.
3. **Shape *families*, not shapes.** "The rails form a closed circuit" — a rectangle, an oval, a
   kidney and a figure-of-eight all pass. Never "the rails form a circle of radius 5".
4. **Several arrangements are right.** Where a room can sensibly be built more than one way, all
   of them score. The ice on a track can run *under* the rails, *around* them, or *inside* them,
   and a player who picks one is not punished for not picking the others.
5. **Structure amplifies effort, it does not replace it.** Structural credit is capped as a
   proportion of the room's block-signal score, so a perfect ring of nothing is worth nothing.
6. **A pile of blocks counts — weakly.** Understanding what a room is *made of* earns something;
   arranging it earns the rest. No build loses a tier: thresholds only ever come down. Minimal
   builds do lose *magnitude*, which is the intent — see "the reward has to be continuous" below.

Rule 6 has a consequence worth writing down now, because it will come up during balancing: if the
audit shows the top of the range becoming too easy once arrangement counts, the fix is to **lower
the structural share cap or raise the ramp exponent**, not to raise the thresholds. Both of those
move the middle of the curve without moving either end; raising thresholds takes buffs away from
builds that already earned them.

## The shape of the design

A third kind of evidence sits alongside `signals` and `detractors`: **`structures`**, a list of
weighted *forms*. A form declares **named elements** and a small tree of **clauses** relating them.

```json
{
  "name": "circuit",
  "weight": 8.0,
  "role": "circuit",

  "elements": {
    "rails":   { "tag": "minecraft:rails" },
    "surface": { "block": ["minecraft:ice", "minecraft:packed_ice", "minecraft:hay_block"] }
  },

  "all": [
    { "shape": "loop", "of": "rails", "min_cells": 12, "ideal_cells": 40, "weight": 2.0 },
    { "any": [
      { "relation": "above",     "of": "rails",   "to": "surface" },
      { "relation": "surrounds", "of": "surface", "to": "rails", "max_gap": 1 },
      { "relation": "inside",    "of": "surface", "to": "rails", "max_gap": 1 }
    ]}
  ]
}
```

### Why not fixed-arity forms

The first draft of this document proposed a closed vocabulary of fixed-shape forms — `loop{match}`,
`ring_around{core, ring}`, `adjacency{match, near}` — each with its arity baked in. Two cases break
that, and both are ordinary things players build.

**Ice on a track relates to the rails in more than one way, and several are right.** The ice should
itself form a loop; but it also makes sense as the outer ring around the rails, or the infield
inside them, or the surface beneath them. All three are a track:

```
  the rails run on it        it rings them          it fills the infield

  y=1  .......               IIIIIII                .......
       .=====.               I=====I                .=====.
       .=...=.               I=...=I                .=III=.
       .=...=.               I=...=I                .=III=.
       .=====.               I=====I                .=====.
       .......               IIIIIII                .......
  y=0  IIIIIII               (one layer)            (one layer)
```

`loop{match: ice}` cannot say "and it should be under or around the rails"; `adjacency` cannot say
"in a loop".

**A reading spot is a lectern, a gap, and a chair.**

```
BBBBB     bookshelves along the back wall
.....
..L..     the lectern
.....     the gap - this is the part that matters
..C..     a chair, two blocks back, facing across it
```

A radius-1 adjacency check cannot express "two to four blocks away, on an axis, with clear space
between", and the gap is the whole point. A chair jammed against a lectern is furniture; a chair
facing it across a gap is a place someone sits and reads.

So arity has to be open, and clauses have to compose.

### The vocabulary

**Shape leaves**, over one element: `loop`, `platform`, `enclosure`, `line`, `cluster`.

**Relation leaves**, between two: `within`, `at_range`, `above`/`beneath`, `beside`, `across`,
`along`, `surrounds`/`inside`. Later, `facing`.

**Nodes**: `any` takes the best-satisfied alternative; `all` takes a weighted **mean**, never a
product — a product would let one unmet clause zero the form, which is gating through the back door
and breaks the first two rules above.

Nesting is capped at two levels. That is enough for every case here and it keeps clause trees
readable, reviewable, and — the part that actually matters — diagnosable: the feedback work has to
turn a failing clause into a sentence, and it cannot do that through arbitrary nesting.

### `of` is judged, `to` is the reference

Every leaf grades as **the fraction of the `of` element's cells that satisfy it**. This is the
easiest thing in the grammar to get backwards:

- `above{of: rails, to: surface}` — what fraction of the rails run on ice. A broad ice field with a
  small loop on it scores 1.0. Correct.
- `beneath{of: surface, to: rails}` — what fraction of the ice is under rails. The same build scores
  0.33, because most of the ice is under nothing. Also correct, and almost never what was meant.

Coverage grading is also what makes a form touch a lot of the room rather than a token corner of it:
a relation satisfied by 2 of 40 ice blocks scores 0.05, not 1.0.

### Forms relate several things, but not everything

**Two to four elements**, enforced in validation. One element in isolation says much less about a
room than the same element placed in relation to another — the whole reason `hearth` can finally
count seating is that the seating is related to *the fire*. A single-element form is permitted,
because a pure shape statement is sometimes exactly what is meant, but it should be the exception.

More than four is a schematic, and cannot produce a diagnostic more useful than "it is wrong".

### Yes, this is a small language, and here is its fence

An earlier draft of this document said "we are not building a shape scripting language". A
composable clause tree plainly is one, so the honest thing is to bound it rather than deny it:

- elements are a flat map - no computed or derived sets
- clause nesting is capped at two levels
- leaf predicates are a closed vocabulary implemented in Java, extensible only by a mod
- no arithmetic, no variables, no conditionals, no references between clauses
- every leaf returns a graded confidence and a diagnostic; nothing returns a bare boolean

A datapack composes; it does not compute. A datapack naming a clause type that is not installed
skips *that clause* with a warning — inside an `any` it stops being one of the alternatives, inside
an `all` it drops out of the mean — rather than invalidating the archetype. Same tolerance
`BlockMatcher` already extends to blocks from mods that are not present.

### Scoring

```
confidence(leaf)   = the clause's own graded result, in [0,1]
confidence(any)    = max over children
confidence(all)    = Σ (weight_i × confidence_i) / Σ weight_i
contribution(form) = form.weight × confidence(root)
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
`structuralRoleThreshold` (default `0.25`). Otherwise an accidental 0.02-confidence relation buys
a diversity bonus for free.

There is no way to write a hard requirement in this grammar, and that is deliberate.

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
magnitude = max * (entryFraction + (1 - entryFraction) * pow(t, rampExponent))
```

Then lower the tier-1 thresholds so a bare pile of the defining blocks lands just inside — for
`track`, 20 to 8.

#### Three knobs, each shaping a different part of the curve

| knob | suggested | what it controls |
|---|---|---|
| `entryFraction` | 0.10 | the floor at the bar — what "just barely counts" is worth |
| `rampExponent` | 1.5 | how the rest of the payout is spread between the bar and the ceiling |
| tier-1 threshold | per archetype | where the bar sits |

`rampExponent` is the shape knob. At `1.0` the ramp is linear; above 1 it back-loads — quadratic at
`2.0`, cubic at `3.0`, and any fractional value in between. Below 1 it front-loads instead, which is
legal and occasionally what a generous pack wants, but works against "a pile counts weakly" and so
is not the default.

Measured on `track`, with `entry` 8, `top` 100, `entryFraction` 0.10:

| build | score | exp 1.0 | exp 1.5 | exp 2.0 | exp 3.0 |
|---|---|---|---|---|---|
| a pile: 8 rails in a heap | 8.49 | 2.5% | 2.4% | 2.4% | 2.4% |
| a bigger pile: 32 rails in a heap | 16.97 | 4.5% | 3.1% | 2.6% | 2.4% |
| thrown together: 32 rails + 8 torches | 21.14 | 5.5% | 3.6% | 2.8% | 2.5% |
| decent, unarranged | 46.01 | 11.3% | 8.1% | 6.1% | 3.9% |
| well arranged | 70 | 17.0% | 14.3% | 12.2% | 9.0% |
| well arranged | 85 | 20.5% | 18.9% | 17.5% | 15.1% |
| exceptional | 100 | 24.0% | 24.0% | 24.0% | 24.0% |

Two rows carry the argument. Across **the bigger pile**: at exponent 1 a heap of 32 rails is worth
nearly twice a heap of 8, which rewards hoarding — the same failure the `sqrt` curve exists to stop,
one level up. At exponent 2 the two are within 0.2% of each other and the bottom of the range
flattens into "you have the right blocks; now build something".

Down the **exponent-2 column**: a decent unarranged room lands at 6.1%, leaving three quarters of
the ceiling for arrangement to claim. At exponent 1 it has already taken half.

The trade shows at exponent 3, where a decent room gets 3.9% against a pile's 2.4% — at which point
furnishing a room properly barely matters either. Somewhere around 1.5 to 2.0 is the sweet spot, and
the balance pass is where it gets picked against real fixtures.

Note the property that makes this knob safe to tune: raising the exponent lowers the magnitude
everywhere strictly between the two endpoints and moves neither endpoint. The pile is still worth
`entryFraction`, the exceptional room is still worth `max`.

Tiers survive as labels — what the report says, what `scoreToNextTier` counts towards, what the
advancements fire on. What they stop being is the thing the buff is computed from.

This is a nerf for minimal builds that currently scrape tier 1 — at the suggested exponent, a room
at score 21 goes from +8% to +3.6% — and that is the point of rule 6. It is player-visible and
belongs in the changelog rather than being discovered.

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

### Worked examples

Three shipped archetypes, as they should read once the grammar lands. Each names several elements
and relates them; none is a schematic, and in each case a different arrangement of the same idea
scores as well.

**The hearth** — chairs ringing the fire, sat back from it, in a lit room:

```
.C.C.      C  seating
C...C      F  the fire
..F..
C...C      eight chairs, every direction covered,
.C.C.      each an arm's length back from the flames
```

```json
{
  "name": "gathering", "weight": 6.0, "role": "gathering",
  "elements": {
    "fire":    { "block": ["minecraft:campfire", "minecraft:soul_campfire", "minecraft:furnace"] },
    "seating": { "tag": "soulhome:seating" },
    "light":   { "tag": "soulhome:lighting" }
  },
  "all": [
    { "relation": "surrounds", "of": "seating", "to": "fire",
      "min_radius": 1, "max_radius": 4, "sectors": 8, "min_sectors": 3, "weight": 2.0 },
    { "relation": "at_range", "of": "seating", "to": "fire", "min_distance": 2, "max_distance": 4 },
    { "relation": "within", "of": "light", "to": "fire", "max_distance": 6 }
  ]
}
```

`at_range` is doing real work alongside `surrounds`: chairs pressed against the campfire cover all
eight sectors just as well as chairs set back from it, and only one of those is somewhere a person
sits. Angular coverage says *where*; range says *how close*.

**The library** — the reading spot, and the reason `across` exists:

```json
{
  "name": "reading_spot", "weight": 5.0, "role": "study",
  "elements": {
    "lectern": { "block": "minecraft:lectern" },
    "seating": { "tag": "soulhome:seating" },
    "shelves": { "tag": "minecraft:bookshelves" }
  },
  "all": [
    { "relation": "across", "of": "seating", "to": "lectern",
      "min_distance": 2, "max_distance": 4, "require_clear": true },
    { "relation": "within", "of": "lectern", "to": "shelves", "max_distance": 3 }
  ]
}
```

`require_clear` walks the straight line between the two cells and checks every cell on it is
passable. Be generous with axis alignment: a chair one cell off the lectern's axis is still a chair
someone reads in, so grade alignment as a falloff over perpendicular offset rather than requiring
it exactly — otherwise this becomes the fussiest clause in the set and players will hate it without
knowing why.

**The bedchamber** — four elements, three relations, no shape clause at all, because this room is
entirely about how things sit relative to one another:

```
#####      #  wall
#BB.#      B  bed, head against the north wall
#...#      L  light
#..L#      C  chest
#C..#
#####
```

```json
{
  "name": "sleeping_quarters", "weight": 5.0, "role": "rest",
  "elements": {
    "bed":     { "tag": "minecraft:beds" },
    "wall":    { "tag": "soulhome:structural" },
    "light":   { "tag": "soulhome:lighting" },
    "storage": { "tag": "soulhome:storage" }
  },
  "all": [
    { "relation": "beside", "of": "bed", "to": "wall", "weight": 2.0 },
    { "relation": "at_range", "of": "light", "to": "bed", "min_distance": 1, "max_distance": 5 },
    { "relation": "within", "of": "storage", "to": "bed", "max_distance": 6 }
  ]
}
```

### How `loop` grades

Worth spelling out, because it is where "tracks being circles" is answered without ever asking for
a circle. Build a graph over the element's cells (8-neighbour in the horizontal plane with a ±1 step
in Y, which is what rails physically do), then:

- **closure** = fraction of the largest component's cells that survive iteratively pruning every
  degree-1 cell. What is left is exactly the cells on a cycle. A closed ring is 1.0; a ring with a
  siding is a little under; a straight line is 0.
- **extent** = `clamp(cycleCells / ideal_cells, 0, 1)`, floored by `min_cells`, so a four-rail
  square is not a racetrack.

A figure-of-eight scores very well, which is correct and is the sort of thing that tells you the
grading is measuring the right property rather than an accidental proxy for it.

Deferred to a later phase: `facing`, `symmetry`, `spacing`, `clearance`.

### Legibility

A structural miss is *far* more opaque than a missing block. "You need 16 bookshelves and have 3"
is self-solving; "your arrangement is wrong" is a dead end and would make the feature read as
broken — the exact failure mode #12 exists to prevent.

So clauses do not return a number. They return a number **and a diagnostic**, computed while
evaluating rather than reconstructed afterwards, in the same spirit as `ArchetypeScore`. The report
walks the clause tree rather than the form, because "arrangement: 0.4" is not actionable and the
clause that scored zero is:

> 24 rails, but the longest closed circuit is 0 — join the two ends
>
> seating covers 3 of 8 directions around the campfire
>
> the seating rings the fire, but it is pressed right against it

Two rules fall out of the grammar. **Name the winning alternative of an `any`** — a player who ringed
their rails in ice should be told that is what was credited, or they may "fix" the one thing that was
working. And **say what the alternatives were when an `any` scores zero**: "the ice is not under,
around, or inside the rails" tells a player there are three ways forward rather than one, which is
the single most useful thing this grammar makes possible.

The Soul Lens can go further and highlight the cells a *clause* reasoned about: the gap in the loop,
the empty sectors around the fire, the bed that is not against a wall. `RegionHighlight` already
draws region boxes, so the machinery is mostly there.

### Cost

One extra filtered pass over each region to build the index. Form evaluation is linear or near-
linear in the indexed cell count: `loop` is O(n) with pruning, `surrounds` is O(cores × ring
cells), `platform` a flood fill, `enclosure` a perimeter walk. All of it runs in the existing
worker phase off the snapshot, so there are no new threading concerns.

`ScanSettings` gains `maxGeometryCells` (default 8192). Past that the index is truncated and forms
report zero **with an explicit reason surfaced to the player**, rather than silently scoring badly.

Clauses are value records, so a clause shared across several archetypes — seating surrounding a fire
is a plausible thing for two archetypes to want — is evaluated once per region and memoized for the
rest of the scan. Short-circuiting mostly does not help: an `any` cannot stop at the first satisfied
child because it needs the max, and an `all` needs every child for the mean. The one real saving is
skipping a whole form when an element resolves to zero cells, which is common and cheap to detect.

## Issue chain

**Phase 1 — Foundations**

1. Region geometry: keep positions for structurally interesting blocks
2. Structural form grammar: elements, shapes, relations, and how they compose
3. Structural scoring in the classifier
4. Buff magnitude ramps with score, and a pile of blocks just barely counts *(independent of the
   geometry work — can ship on its own)*

**Phase 2 — The vocabulary**

5. Relations: distance, direction, and clear space between
6. `surrounds` / `inside` — chairs around the hearth fire, ice around the rails
7. `soulhome:loop` — the track is a circuit, and the ice belongs to it
8. Shapes: `platform`, `enclosure`, `line` and `cluster`

**Phase 3 — Making it real**

9. Feedback UX: explaining structural hits and misses
10. Apply forms to the shipped archetypes, and the balance pass
11. Patchouli: document the forms from the data

**Phase 4 — Later**

12. Block facing without widening `BlockSignature` → the `facing` relation
13. `symmetry`, `spacing`, `clearance`

The geometry half of Phase 1 is not independently shippable — nothing changes for a player until at
least one form from Phase 2 exists. The reward curve is the exception: it stands on its own and is
worth landing first, because it is what makes every later arrangement improvement actually felt. Phase 3's feedback issue should be pulled forward the moment the first form lands
rather than saved for the end, for the same reason #12 was.

## One drift hazard to close on the way past

`ArchetypeCodecs` (DataFixerUpper, production) and `ArchetypeJsonReader` (Gson, tests) already
parse the same file format twice, by hand, and the test reader's own comment admits it "deliberately
mirrors" the production codec. Per-clause parameter schemas multiply the surface those two can drift across, and there
will be a dozen clause types.

Recommended: declare each clause's parameters **once**, in `core`, as a small declarative spec — name,
type, default — and have both parsers read that spec generically into a neutral parameter bag that
`core` turns into a typed clause. It is more work upfront than writing a `MapCodec` per clause and a
Gson mirror per clause, and it is the version where a new clause cannot be added to one parser and
forgotten in the other.
