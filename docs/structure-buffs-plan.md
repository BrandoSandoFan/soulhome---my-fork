# Soulhome Structure Buffs — Implementation Plan

Status: **planning**. Each numbered section below is drafted as a standalone GitHub issue,
in dependency order. Issue numbers referenced in the text (`#2`, `#3`, …) assume they are
filed in the order given here, starting from the epic as `#1`.

> Issues are currently disabled on this fork, so this document is the interim home for the
> chain. Enable **Settings → General → Features → Issues** and these can be filed verbatim.

---

## #1 — [Epic] Soulhome structure buffs: build rooms in your soul to gain powers

**Labels:** `enhancement`

### Background

This is the feature the original author planned but never shipped. It is already promised to
players in two places in the guide book:

`src/main/java/leaf/soulhome/datagen/patchouli/categories/PatchouliBasics.java:41`

> "For example, building a farm in a specific multiblock structure might mean that you get more
> saturation out of each thing that you eat. Or maybe you'd build an armoury in order to gain a
> buff to toughness. At this point in time though, there's only the SoulKey"

`src/main/java/leaf/soulhome/datagen/patchouli/categories/PatchouliBasics.java:75`

> "Look out for future updates that will let you build multiblock structures to give yourself buffs."

There is also empty scaffolding waiting for it: the `multiblocks` Patchouli category exists with a
single placeholder entry (`PatchouliMultiblocks.java`), described as *"Structures you can build
within your soul that will grant you power."*, gated behind a `soulhome:main/blank` advancement.

### The core design decision

The author's note says *"a specific multiblock structure"*. **We are deliberately not doing that.**

A rigid multiblock (fixed block-for-block schematic, à la Patchouli's multiblock API) is the easy
implementation, but it turns the soulhome — a space meant to be a personal expression of your soul —
into a copy-paste checklist. Every player's library would be identical, and the mod's whole premise
argues against that.

Instead the feature is built on a **fuzzy, data-driven classifier**: you build whatever *you* think
a library looks like, and the mod looks at the room and decides how library-ish it is. Two players'
libraries can look nothing alike and both work.

This trades implementation simplicity for a much harder problem, and most of the risk in this epic
lives there:

- **Detection** — what even counts as "a structure"? (#2)
- **Description** — how is an archetype described, flexibly and by datapack? (#3)
- **Scoring** — how do we rank a room against archetypes without letting players trivially cheese it? (#4)
- **Legibility** — a fuzzy system that silently fails is *worse* than a rigid one, because the player
  has no schematic to check against. Players must be able to see what the classifier saw and why. (#12)

That last point is not polish. It is what makes a fuzzy classifier usable at all, and it should not
be deferred to the end of the project.

### Launch archetypes

From the author's own examples plus the obvious extensions:

| Archetype | Buff | Issue |
|---|---|---|
| Farm | more saturation from food | #8 |
| Armoury | more damage with swords | #9 |
| Library | faster XP gain | #10 |
| Enchanting room | stronger enchantments | #11 |

### Design principles

1. **Creativity over compliance.** Match on block *tags* wherever possible, so any wood type, any
   crop, any candle counts. Reward variety, not volume.
2. **Everything is a datapack.** Archetypes ship as JSON so packmakers and players can add their own
   without touching Java.
3. **Sublinear scoring.** 10,000 bookshelves in a box must never beat a thoughtfully built study.
4. **Buffs are earned in the soul, spent in the world.** They are computed from the soulhome but
   apply everywhere, which drives the persistence/sync design in #6.
5. **No per-tick scanning.** Soulhomes are small and per-player, but classification is still a chunk
   sweep. Debounce and cache.

### Issue chain

**Phase 1 — Foundations** (these three *are* the feature; everything else is plumbing)
- #2 Region detection: find enclosed rooms *and* open-air clusters
- #3 Archetype definition format + datapack loader
- #4 Fuzzy classifier: score regions against archetypes

**Phase 2 — Plumbing**
- #5 Scan scheduling, dirty-tracking and `SavedData` persistence
- #6 Buff registry + player attachment (persist across death/dimension, sync to client)

**Phase 3 — Effects**
- #7 Buff effect application framework
- #8 Farm → saturation
- #9 Armoury → sword damage
- #10 Library → XP gain
- #11 Enchanting room → enchantment power

**Phase 4 — Making it usable**
- #12 Feedback UX: `/soul analyse` + Soul Lens item *(pull earlier if Phase 1 lands rough)*
- #13 Balance pass, diminishing returns, config
- #14 Patchouli documentation + advancements

### Bugs found while surveying the codebase

Unrelated to this feature, filed separately: #15, #16, #17, #18. **#15 and #16 are worth fixing
before this epic starts** — #15 affects the island every soulhome is built on, and #16 is in code
this feature will lean on.

---

# Phase 1 — Foundations

## #2 — Region detection: find enclosed rooms and open-air clusters

**Labels:** `enhancement` · **Blocks:** #4, #5

### Problem

Before we can classify a structure we have to decide what a "structure" *is*. The player builds
freely in an otherwise empty void dimension; we need to carve that into candidate regions.

### Two region types (both are needed)

**1. Enclosed volumes** — the classic "room". Flood-fill through air/passable blocks, bounded by
solid blocks, doors, glass, trapdoors, etc.

- Seed from air blocks within the populated bounding box of the soulhome.
- If the fill escapes to open sky or the void, or exceeds `maxRoomVolume` (suggest 4096), the pocket
  is *outdoors* and is discarded as a room.
- Record both the **boundary** blocks (walls/floor/ceiling — this is where wall-mounted signals like
  bookshelves and armour stands live) and the **contents** (non-air blocks inside the pocket).
  Both feed the classifier; a library is as much its walls as its furniture.

**2. Open-air clusters** — **do not skip this.** A farm is the author's own first example, and farms
are typically *outdoors*: a field of wheat under an open sky is not an enclosed volume and would be
invisible to room detection. Cluster "signal-bearing" blocks that are not inside any detected room
using a simple density-based clustering pass (DBSCAN-ish: seed on a signal block, absorb signal
blocks within radius `r`, suggest `r = 4`), then take the cluster's bounding box as the region.

Archetypes declare which region types they accept (see #3), so an armoury can require enclosure
while a farm accepts either.

### Deliverables

- `SoulRegion` record: region type, bounding box, boundary block multiset, content block multiset,
  volume, an identity hash for change detection.
- `RegionScanner` producing `List<SoulRegion>` for a `ServerLevel`.
- Bounding box discovery that avoids sweeping the whole 256-height dimension — soulhomes are a
  single small island around `DimensionHelper.FLOOR_LEVEL` (70), so derive a scan volume from
  non-empty chunk sections rather than a fixed box.
- Unit tests over a synthetic block accessor: a sealed room, a room with an open door, a room with a
  hole in the roof, nested rooms, an open-air crop field, and an empty dimension.

### Notes / risks

- Decide up front whether an open door "breaks" a room. Recommendation: doors, trapdoors, gates and
  glass count as *boundary*, not leaks, regardless of open/closed state — otherwise players get
  buffs that flicker as they walk in and out.
- Nested/adjacent rooms: a corridor between two rooms will fill as one region if the doors count as
  passable. This is the main reason to treat doors as boundary.
- Cap total regions per soulhome (suggest 64) to bound worst-case cost.

---

## #3 — Archetype definition format and datapack loader

**Labels:** `enhancement` · **Blocks:** #4

### Problem

Archetypes must be describable in data, not Java, so packmakers can extend the system, and so
balance changes don't require a recompile.

### Proposed format

`data/<namespace>/soulhome_archetypes/<id>.json`

```jsonc
{
  "display_name": "block.soulhome.archetype.library",
  "region_types": ["enclosed"],
  "min_volume": 27,

  // Hard gates. If any fails, score is 0 regardless of everything else.
  "requirements": [
    { "match": { "tag": "minecraft:bookshelves" }, "min_count": 16 }
  ],

  // Weighted positive signals. "role" drives the diversity bonus in #4.
  "signals": [
    { "match": { "tag": "minecraft:bookshelves" }, "weight": 3.0, "role": "core",    "cap": 64 },
    { "match": { "block": "minecraft:lectern" },   "weight": 5.0, "role": "core",    "cap": 4  },
    { "match": { "tag": "soulhome:seating" },      "weight": 1.5, "role": "comfort", "cap": 8  },
    { "match": { "tag": "minecraft:candles" },     "weight": 0.5, "role": "light",   "cap": 16 }
  ],

  // Negative signals used to disambiguate near-miss archetypes.
  "detractors": [
    { "match": { "block": "minecraft:anvil" }, "weight": -2.0 }
  ],

  "tiers": [
    { "min_score": 20,  "tier": 1 },
    { "min_score": 50,  "tier": 2 },
    { "min_score": 100, "tier": 3 }
  ],

  "buffs": [
    { "type": "soulhome:xp_gain", "per_tier": 0.10, "max": 0.30 }
  ]
}
```

### Deliverables

- Codec-based deserialisation (the codebase already uses `RecordCodecBuilder` — see
  `SoulChunkGenerator` and `SyncDimensionListMessage`, follow that style).
- A `SimpleJsonResourceReloadListener` registered on `AddReloadListenerEvent`.
- `BlockPredicate`-style matcher supporting `block`, `tag`, and a list of either.
- Sync archetype definitions to clients on login/reload — #12's UI needs display names and the
  signal list to tell players what to build. The existing `Network` channel can carry this.
- Ship `soulhome:seating`, `soulhome:storage`, `soulhome:weapon_display` and similar helper block
  tags so archetypes can be written expressively.

### Notes

- Prefer tags over blocks in the shipped defaults. `minecraft:bookshelves` rather than
  `minecraft:bookshelf` means chiselled bookshelves and modded variants work for free — this is the
  single biggest lever on "does this feel creative or does it feel like a checklist".
- Validate on load and log loudly on a malformed archetype rather than failing the datapack.

---

## #4 — Fuzzy classifier: score regions against archetypes

**Labels:** `enhancement` · **Blocked by:** #2, #3 · **Blocks:** #5, #7

### Problem

Given a region and the archetype definitions, decide what the region *is* and how good an example of
it it is. This is the heart of the feature and the place it will most easily go wrong.

### Scoring model

For each archetype, over the region's combined boundary + content block multiset:

```
raw     = Σ  weight_i × f(min(count_i, cap_i))
```

**`f` must be sublinear.** `f(n) = sqrt(n)` or `f(n) = log2(1 + n)`. With linear scoring, the
optimal library is a solid cube of bookshelves, which is exactly the outcome this design exists to
avoid. The per-signal `cap` is a second, harder line of defence.

Then apply two modifiers that make the classifier reward *building a room* over *stacking a block*:

- **Diversity bonus** — scale by the number of distinct `role` groups that have at least one match.
  Suggest `× (1 + 0.15 × (distinctRoles − 1))`. A room with books, seating, lighting and a lectern
  beats a room with only books and the same raw count.
- **Density term** — penalise signal-sparse cathedrals. Suggest a soft penalty when
  `signalCount / volume` falls below a floor, rather than a hard cut.

Finally `score = (raw + Σ detractors) × diversity × density`, hard-gated to 0 if any `requirements`
entry fails.

### Assignment

- Compute scores for every (region × archetype) pair.
- A region gets **one** archetype: the argmax, provided it clears its tier-1 threshold **and** beats
  the runner-up by a margin (suggest 15%). Otherwise the region is `AMBIGUOUS` — and that state must
  be surfaced to the player by #12, not silently dropped. "I built a thing and nothing happened" is
  the failure mode that kills this feature.
- Where several regions classify as the same archetype, count only the best-scoring one at full
  value; see #13 for diminishing returns on the rest.

### Deliverables

- `ArchetypeClassifier` with `List<SoulRegion> → List<ClassificationResult>`, where
  `ClassificationResult` carries the winning archetype, score, tier, runner-up, and the per-signal
  breakdown (#12 renders this).
- Tests: a canonical library scores tier 1+; a bookshelf cube does *not* out-score it; a
  library-with-an-anvil resolves sensibly; an empty room scores 0.

### Notes

Keep the breakdown data on the result object from day one. Reconstructing "why did I get this score"
after the fact is painful, and #12 is the difference between a feature players enjoy and one they
find opaque.

---

# Phase 2 — Plumbing

## #5 — Scan scheduling, dirty-tracking and persistence

**Labels:** `enhancement` · **Blocked by:** #2, #4 · **Blocks:** #6

### Problem

Classification is a chunk sweep. It must never run on a tick loop, but buffs must feel responsive.

### Approach

Trigger a rescan on:

- Player leaving their soulhome (`DimensionHelper.FlipDimension` outbound) — the natural moment to
  say "here is what you built".
- Block place/break inside a soul dimension → mark dirty, debounce (suggest 5s of quiet, or a cap of
  once per 30s) and rescan asynchronously.
- Level load, for soulhomes that were modified in a previous session.

Persist results in a per-level `SavedData` (`ServerLevel#getDataStorage()`), keyed by soul dimension,
holding the last classification results and the region identity hashes so an unchanged soulhome can
skip rescanning entirely.

### Deliverables

- `SoulHomeBuffData extends SavedData` with NBT round-trip.
- Debounced dirty-tracking hooked to `BlockEvent.EntityPlaceEvent` / `BlockEvent.BreakEvent`,
  filtered to soul dimensions via `DimensionHelper.isDimensionOfType`.
- Scan off the main thread; apply results back on the server thread.

### Notes

- Soul dimensions are created lazily and there is one per player (`DimensionRegistry`), so the
  number of live soulhomes on a busy server is unbounded. Only scan dimensions that are loaded and
  dirty.
- Watch interaction with #17 — the outbound teleport path is where the rescan hook naturally goes,
  and that path currently has a null-dereference bug.

---

## #6 — Buff registry and player attachment

**Labels:** `enhancement` · **Blocked by:** #5 · **Blocks:** #7

### Problem

Buffs are computed in the soulhome but applied in the overworld, across dimension changes, deaths
and relogs. They need a home on the player, not on the level.

### Deliverables

- `SoulBuff` type registry keyed by `ResourceLocation` (`soulhome:xp_gain`, `soulhome:sword_damage`,
  …), each with a magnitude and stacking rule.
- A `Capability` attached to `Player` via `AttachCapabilitiesEvent<Entity>`, implementing
  `ICapabilitySerializable` so buffs survive save/load.
- Copy across death and dimension change via `PlayerEvent.Clone` — **including the `wasDeath`
  branch**, which is the standard place capabilities silently get dropped.
- Recompute-and-push on login, on respawn, and whenever #5 produces new results.
- Client sync packet on the existing `Network` channel so #12 can render active buffs without a
  round-trip.

### Notes

The mod currently uses `PlayerHelper.getPersistentTag` (persistent NBT) for the last-dimension data,
which is simpler than capabilities and already survives death. It is a legitimate alternative if
capabilities feel heavyweight — but it has no client sync story, which #12 needs. Recommend
capabilities; note the tradeoff in the PR.

---

# Phase 3 — Effects

## #7 — Buff effect application framework

**Labels:** `enhancement` · **Blocked by:** #6 · **Blocks:** #8, #9, #10, #11

### Problem

Each buff hooks a different part of the game. Give them a common shape so #8–#11 are small.

### Deliverables

- A `SoulBuffEffect` interface with a registration point, so a buff type declares its own hook
  rather than accumulating a god-class of event handlers.
- Shared helper: "what is this player's magnitude for buff type X, or 0".
- A single `CommonEvents`-style subscriber per hook family, dispatching to registered effects.
- Guard rails: buffs never apply to a `FakePlayer`; magnitude always clamped to the configured max
  from #13.

---

## #8 — Farm archetype → saturation buff

**Labels:** `enhancement` · **Blocked by:** #7

The author's own headline example: *"building a farm … might mean that you get more saturation out
of each thing that you eat."*

- **Archetype:** accepts `open` *and* `enclosed` regions (see #2 — most farms are outdoors).
  Signals: `minecraft:crops` tag, farmland, water source, composter, hay bales, bee hives,
  `soulhome:storage` for the barn.
- **Hook options, in preference order:**
  1. `LivingEntityUseItemEvent.Finish` + top up `FoodData` saturation directly. Simplest, works for
     any food item including modded.
  2. Forge's entity-aware `ItemStack#getFoodProperties(LivingEntity)` — cleaner in principle but only
     reflects the buff if we intercept the call site.
  3. Mixin `FoodData#eat`. Last resort; the mod already has an unused mixin package if needed.
- Verify exact signatures against Forge 47.3.0 / 1.20.1 before committing to an approach.
- Cap saturation at the vanilla ceiling (never exceed current food level) so the buff doesn't create
  a permanently-full hunger bar.

---

## #9 — Armoury archetype → sword damage buff

**Labels:** `enhancement` · **Blocked by:** #7

The author's second example (they said toughness; sword damage per this feature request — consider
shipping both as separate tiers of the same archetype).

- **Archetype:** `enclosed` only. Signals: armour stands, `minecraft:swords`/`minecraft:anvil`,
  item frames, grindstone, smithing table, banners, `soulhome:weapon_display`.
- **Hook:** `LivingHurtEvent` (or `LivingDamageEvent`) — check the direct damage source is the
  player and the mainhand item is in the `minecraft:swords` tag, then scale `event.setAmount`.
- **Do not** use a permanent `ATTACK_DAMAGE` attribute modifier: it would buff axes, tridents and
  fists too, and "increases your damage with swords" is specifically weapon-conditional.
- Decide whether it applies to sweep damage and to thrown/indirect damage; document the choice.

---

## #10 — Library archetype → XP gain buff

**Labels:** `enhancement` · **Blocked by:** #7

- **Archetype:** `enclosed`. Signals as sketched in #3.
- **Hook:** `PlayerXpEvent.XpChange`, scaling the amount.
- **Watch for double-counting:** `PlayerXpEvent.PickupXp` and `XpChange` both fire on orb pickup.
  Pick one — `XpChange` also covers furnace/trade/breeding XP, which is the more consistent
  behaviour — and add a test or manual check that a single orb is not multiplied twice.
- Consider whether it should affect XP *dropped* by mobs (no — it is a gain-rate buff on the player).

---

## #11 — Enchanting room archetype → enchantment power buff

**Labels:** `enhancement` · **Blocked by:** #7

*"functionally increases the enchant ability of everything you enchant"*.

- **Archetype:** `enclosed`. Signals: enchanting table, bookshelves, lapis storage, candles,
  end rods, obsidian. Note the deliberate overlap with the library — the detractor/margin logic in
  #4 is what keeps these two apart, and this pair is the best test case for it.
- **Hook:** Forge's `EnchantmentLevelSetEvent`, raising the effective level per slot. Verify the
  exact class and firing site on 1.20.1 before building on it.
- **Scope decision:** table only, or anvils and grindstones too? Recommend table-only for v1 — the
  vanilla 15-bookshelf cap is the natural thing this buff pushes past, and it keeps the balance
  conversation contained.
- Beware pushing effective level far past vanilla's range; clamp, and check what the enchantment
  cost display does at extreme values.

---

# Phase 4 — Making it usable

## #12 — Feedback UX: `/soul analyse` and the Soul Lens

**Labels:** `enhancement` · **Blocked by:** #4 · *Consider pulling forward into Phase 1.*

### Why this matters more than its position suggests

With a rigid multiblock, a player who gets no buff can compare their build to the schematic in the
book. With a fuzzy classifier there is nothing to compare against, so an unexplained "no buff" is a
dead end and the feature reads as broken. **This issue is what makes the fuzzy approach viable.**
It is listed in Phase 4 for dependency reasons, but should be built as soon as #4 produces scores —
it is also by far the best debugging tool for tuning the classifier itself.

### Deliverables

- `/soul analyse` (extend the existing `SoulCommand` / `SoulHomeCommand`) printing, per detected
  region: classification or `AMBIGUOUS`, score, tier, distance to the next tier, and the top
  contributing and missing signals.
- A **Soul Lens** item: hold it and look at a region to get the same information as an overlay, plus
  a highlight of the detected region bounds. Far more discoverable than a command, and it makes the
  invisible region-detection step visible.
- Explicit "why didn't this classify" output: which `requirements` failed, and which archetype it
  *nearly* was.
- Surface active buffs and their source rooms somewhere persistent.

---

## #13 — Balance pass, diminishing returns, and config

**Labels:** `enhancement` · **Blocked by:** #8, #9, #10, #11

- Diminishing returns on repeated rooms of one archetype (suggest each additional room at 50% of the
  previous, or a hard cap of 3).
- Global cap on total buff magnitude across all archetypes.
- Forge config: master enable, per-archetype magnitude multipliers, scan interval and debounce, max
  regions, max room volume.
- Server-authoritative — all magnitudes computed server-side; the client copy is display only.
- Multiplayer: confirm a player standing in *someone else's* soulhome gets their own buffs, not the
  host's. `BoundSoulkey` lets players visit other souls, so this case is real.

---

## #14 — Patchouli documentation and advancements

**Labels:** `enhancement` · **Blocked by:** #8, #9, #10, #11, #12

- Replace the placeholder `blankEntry` in `PatchouliMultiblocks.java` with a real entry per
  archetype, generated from the loaded archetype definitions where possible so docs cannot drift
  from data.
- Update the two `PatchouliBasics.java` strings (lines 41 and 75) that currently say the feature does
  not exist yet.
- Emphasise in the text that these are *not* fixed schematics — players need to be told the system is
  fuzzy, or they will assume they must copy the book exactly and the creativity goal is lost.
- Replace the `soulhome:main/blank` advancement with a real trigger on first classified room, and add
  one per archetype.
- Update `Changelog.md`.

---

# Bugs found while surveying the codebase

These are pre-existing and unrelated to the feature, found while reading the code.

## #15 — Negative modulo means ~1/3 of soulhomes fall back to the ugly legacy platform

**Labels:** `bug`

`src/main/java/leaf/soulhome/registry/DimensionRegistry.java:159`

```java
int islandStyle = rand.nextInt() % 3;
```

`Random.nextInt()` returns the full int range, so `% 3` yields `-2, -1, 0, 1, 2`. For negative
results the lookup becomes `soulhome:soul_island-1` / `soul_island-2`, which do not exist. The
`templateOptional.isPresent()` check then fails and generation falls through to the legacy branch —
a flat 32×32 dirt-and-grass platform — while logging `"Dimension generated via legacy method!!"`.

Roughly a third of players get the fallback platform instead of one of the three hand-built islands,
deterministically for their UUID, so an affected player never sees a real island.

**Fix:** `int islandStyle = rand.nextInt(3);` (or `Math.floorMod(rand.nextInt(), 3)`).

Worth fixing before the buffs epic: this is the terrain every soulhome gets built on.

---

## #16 — Entity filter removes at most one entity, so enemies still follow you into your soul

**Labels:** `bug`

`src/main/java/leaf/soulhome/utils/EntityHelper.java:41-59`

```java
for (Entity ent : entitiesFound)
{
    final boolean removeSelf = ent == entity && !includeSelf;
    if (removeSelf || !ALLOWED_TO_TELEPORT.test(ent))
    {
        entitiesFound.remove(ent);
        break;          // <-- exits after the first removal
    }
}
```

The `break` stops the loop after removing a single entity. Any second or subsequent disallowed
entity is left in the list and gets teleported. With two zombies next to you when you use the
SoulKey, one is filtered and the other rides along.

This directly undermines commit `ff0c810` *"No more taking enemies into soulhomes."* The `break` is
presumably there to dodge the `ConcurrentModificationException` that removing during a for-each
would otherwise throw, but it trades a crash for silently wrong behaviour.

**Fix:**

```java
entitiesFound.removeIf(ent -> (ent == entity && !includeSelf) || !ALLOWED_TO_TELEPORT.test(ent));
```

Worth fixing before the buffs epic — this is code the feature will build on.

---

## #17 — `server.getLevel()` returns null rather than throwing, so the removed-dimension guard never fires

**Labels:** `bug`

`src/main/java/leaf/soulhome/utils/DimensionHelper.java:84-96`

```java
try
{
    destination = server.getLevel(destinationKey);
}
catch (Exception e) // sometimes people remove mods. Protect against unknown by sending them to overworld spawn.
{
    destination = server.overworld();
    ...
}
```

`MinecraftServer#getLevel` is `@Nullable` and returns `null` for an unknown dimension key — it does
not throw. The catch block is therefore dead, `destination` stays `null`, and the fallback to
overworld spawn never runs.

`TeleportHelper.teleportEntity` then dereferences it at `TeleportHelper.java:35`
(`destinationDimension.dimension()`), throwing an NPE. A player who entered their soulhome from a
dimension belonging to a since-removed mod cannot get out — which is exactly the scenario the
comment was written to handle.

**Fix:** null-check the result instead of catching.

```java
destination = server.getLevel(destinationKey);
if (destination == null)
{
    destination = server.overworld();
    final BlockPos sharedSpawnPos = destination.getSharedSpawnPos();
    x = sharedSpawnPos.getX();
    y = sharedSpawnPos.getY();
    z = sharedSpawnPos.getZ();
}
```

**Related, same method:** at `DimensionHelper.java:130` the group-teleport offset computes
`newPosByDestination` including a `y` component, but line 136 passes the un-offset `y` instead of
`newPosByDestination.y`. The computed Y is dead. Flattening everyone onto one Y may well be
intentional for arrival, but as written it is indistinguishable from a typo — worth either using the
value or dropping it from the calculation with a comment.

---

## #18 — Divide-by-zero produces NaN particle coordinates on the first tick of SoulKey use

**Labels:** `bug`

`src/main/java/leaf/soulhome/items/SoulKeyItem.java:87-112`

On the first use tick `count == USE_TICKS_REQUIRED`, so `percentage` is 0 and
`particlesToCreate` is 0. Then:

```java
float bits = 360f / particlesToCreate;   // -> Infinity
...
float ang = (bits * i);                  // Infinity * 0 -> NaN
```

The loop still runs once at `i == 0`, spawning a particle at NaN X/Z. Cosmetic and client-only —
low severity — but it is an easy guard.

**Fix:** early-return when `particlesToCreate <= 0`.

**While in this file:** `onUseTick` is annotated `@OnlyIn(Dist.CLIENT)` while overriding a method
vanilla calls on both sides. The body is already guarded by `livingEntity.level().isClientSide`, so
the annotation is redundant, and `@OnlyIn` on an override of a common-side method is a known hazard
worth removing.
