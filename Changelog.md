SoulHome - For 1.18.1 Minecraft

B1

The initial build for 1.18.1 Minecraft, with basic patchouli, advancements, and soulkey item to access the user's own skyblock-esque dimension.

---

SoulHome - For 1.20.1 Minecraft

Soul rooms

What you build inside your soul now changes what you can do outside it. Build a room, and if it
reads as a farm, an armoury, a library, an enchanting room, an alchemy lab, a bedchamber, a mine,
a track, a training yard or a hearth, you carry that with you: more saturation from food, more
damage with swords, faster experience, stronger enchantments, longer potions, faster healing,
quicker mining, more movement speed, an extra jump in mid-air, softer landings, and a sword
that sets what it hits on fire.

There are no blueprints. Nothing is a multiblock to copy block for block - your soul is looked
over now and then, and each enclosed room, and each dense patch of open ground, is judged on what
it is actually made of. Two libraries that look nothing alike can both be libraries. Variety
counts for more than volume: a hundred bookshelves in a box is not a library.

- Archetypes are datapack JSON (`data/<namespace>/soulhome_archetypes/`), so packmakers and players
  can add their own rooms and buffs without touching Java.
- `/soulhome analyse` reports every region found: what it was classified as, its score and tier,
  how far it is from the next one, which blocks counted, which are missing, and what it nearly
  was instead. `/soulhome analyse here` does the same for the room you are standing in.
- `/soulhome buffs` lists what you are carrying and which room gave it to you.
- The new Soul Lens draws the edges of every region the scan found, coloured by whether it
  counted - which is worth seeing, because a wall you thought was there might not be. Used outside
  a soul it lists your buffs instead.
- Advancements for your first classified room and for each of the ten shipped archetypes.
- The guide book has a Soul Rooms category, written from the archetype definitions themselves so
  its pages cannot drift from what the game actually does.
- Everything is tunable in the server config: a master switch, per-archetype multipliers,
  per-buff-type ceilings, diminishing returns on repeated rooms, scan limits and scan timing.
- Buff magnitude now ramps smoothly with a room's score instead of jumping at each tier boundary,
  so improving an already-qualifying room is felt immediately rather than only once it crosses the
  next threshold. A bare pile of an archetype's defining blocks now earns a small buff instead of
  nothing - but this comes at the cost of the flat tier buff a minimal build used to get in full:
  a room that only just scraped tier 1 is worth noticeably less than before, and a well-arranged
  room is worth more of its archetype's ceiling than it used to be.
- Archetypes can now score how a room is arranged, not just what it contains: whether one thing
  sits near, above, beside or across clear space from another; whether seating rings a fire or ice
  fills the loop a track encloses; whether a rail circuit actually closes rather than just piling
  up rail; and whether a run of blocks forms a platform, an enclosure, a line or a single deliberate
  cluster rather than scattered leftovers. This is vocabulary for datapacks and future archetypes
  to write with, and every shipped room now writes with it - a bed against a wall, chairs ringing a
  hearth, a chair reading across a gap from a lectern, a closed rail loop, a fenced training course,
  and the rest, each score a little higher for being arranged the way the room's name suggests.
  Nothing loses a tier for skipping this: it is worth doing, never worth requiring, and a pile of
  the right blocks still counts for something on its own.
- `/soulhome analyse` explains arrangement the same way it already explains missing blocks: which
  arrangement was credited, which alternative was picked when more than one would have counted, what
  a well-arranged room is still missing, and why a room too large to fully scan or a room that has
  already hit its arrangement credit for the tier is not scoring any higher.

Buffs are earned in the soul and spent in the world. They survive death, dimension changes and
relogs, and a visitor to someone else's soul keeps their own.

Soul room fixes

Rooms that classified correctly and then did nothing.

- Buffs no longer stop working the moment you walk into your soulhome. The capability holding
  them was thrown away for good on the first dimension change of a session - Forge invalidates a
  removed player's capabilities and revives them again on arrival, but a discarded LazyOptional
  cannot be revived, and this one was created once and never replaced. So entering your soul,
  which is a dimension change and the whole point of the mod, permanently severed the buffs from
  the player for the rest of that session: nothing could read them and nothing could write them.
  Meanwhile the Soul Lens and `/soulhome buffs` read the soulhome's own saved results and went on
  reporting the room as working perfectly, which is why this looked like a buff that did nothing
  rather than a buff that was not there. The holder is remade on demand now.
- `/soulhome buffs` re-applies your buffs before reporting them, so what it prints is what you are
  carrying rather than what you ought to be - and so this class of fault has a one-command
  recovery instead of being invisible.
- Buffs no longer vanish when you leave your soulhome. A soul dimension keeps none of its own
  chunks loaded, so the rescan on the way out usually arrived to find an unloaded - and therefore
  apparently empty - dimension, and recorded that: every room you had just built, erased about a
  second after you walked out of it. Leaving now reads the level on the spot, while it is still
  there to read, and a scan that cannot see a soulhome leaves its last known rooms alone instead
  of clearing them. The same fault emptied every soulhome on server start, so buffs also survive a
  restart now.
- `soulhome:double_jump` works outside a development workspace. It reads whether the jump key is
  held from a field whose name differs between source and an installed jar; the reflective lookup
  was written against the source name, so in a real install it failed at startup, logged one
  warning and never granted a jump again. It goes through a mixin accessor now, which the build
  remaps for us.
- `soulhome:mining_speed` applies on the client as well as the server, as it always intended to.
  Break speed is predicted client-side, and the client was reading a copy of your buffs that is
  only ever filled in on the server - so it predicted vanilla speed and the server's confirmation
  was the only half that ever changed.
- A soulhome larger than the configured scan limit no longer fails every scan after copying itself
  into memory first. The limit is checked before the copy, and its owner keeps the buffs from the
  last scan that did fit.
- Your buffs are re-applied after every scan of your soulhome rather than only after one that
  changed something, so what `/soulhome buffs` says you have earned and what you are actually
  carrying cannot drift apart.
- `soulhome:fire_aspect` no longer requires a sword in hand. A hearth is about carrying fire with
  you, not about a specific blade, so any direct melee hit now lights the target - a sword, an
  axe, or a bare fist. `soulhome:sword_damage`, the armoury's buff, is unchanged and still needs
  one: "you hit harder with a sword" is a weapon-conditional promise in a way "your hearth sets
  things alight" was never meant to be.
- Every room page in the guide no longer opens with "Format error:". The book runs in Patchouli's
  i18n mode, which passes every generated line through vanilla's `String.format` whether or not it
  is actually a translation key - and a percentage buff ("+45%") is not a valid format string on
  its own, so eight of the ten room entries hit this on their very first page. Percent signs in
  generated book text are escaped now, so every page renders as written.
- `soulhome:lighting` recognises the light sources it was missing - the tag was a twelve-entry
  hand-written list, and redstone lamps, the one nearly every wired build uses, were not on it, so
  a room lit entirely with them scored as unlit. Jack o'lanterns, froglights, glow lichen, redstone
  torches, amethyst clusters and buds, sea pickles, beacons, conduits, respawn anchors, cave vines
  and the invisible light block all count now too. This is a buff: a room lit with any of these
  scores higher than it did.
- The Soul Lens no longer labels every empty region "Alchemy Lab". An unclassified region has
  every archetype score exactly zero, and the overlay was showing whichever archetype id happened
  to sort first alphabetically as if it had been awarded - a new soul with a few empty pockets read
  as full of alchemy labs. An unclassified region now reads "Not anything yet" instead, and a
  classified room can no longer be crowded out of the label list by empty pockets sitting ahead of
  it.
- The Soul Lens now tells a near miss apart from an award. A region that lost out on the
  ambiguity margin between two close candidates used to have the closer one's name printed as if
  it had been won; it now reads "Not anything yet (nearly <archetype>)" instead, so the top
  candidate is still worth seeing without being mistaken for a classification.
- `soulhome:potion_duration` (the alchemy lab's buff) now extends a splash potion thrown at
  yourself and a lingering cloud you are standing in, not only a potion you drink. It still never
  extends a potion aimed at something else, so it stays your buff and not a debuff-stretching tool
  in PvP or against mobs.
- The alchemy lab no longer extends a potion's harmful effects - it cuts them short instead.
  Turtle Master's Resistance grew with the tier as intended, but so did its Slowness, and a
  poison or weakness potion lasted longer for building a brewing room, which was a nerf disguised
  as a buff. A harmful effect from your own potion now runs shorter by the same fraction a
  beneficial one runs longer; a neutral effect (Glowing is the main one) is left exactly as
  brewed. This is a nerf to anyone currently drinking Turtle Master with an alchemy lab for the
  Resistance and getting away with the Slowness too.
- Arrangement counts for a lot more of a room's score now. Structural credit - the bonus for how a
  room is arranged, not just what it holds - was capped at half the room's block-signal total, with
  no explanation anywhere a player could find it, and that cap was a large part of why a
  well-arranged room still struggled to clear tier 2. It can now add as much again as the room's
  contents alone earned, not just half again - a buff to every room that bothers to arrange itself
  well, and configurable via `structural_share_cap` for anyone who wants the old ratio back.
- The hearth rewards a fireplace now, not just a nether-block bonfire. Past the fire itself, almost
  every point on the archetype came from lava, magma and netherrack, and there was no signal at all
  for seating, furnishings, storage or cooking - the things that make a room feel like somewhere to
  sit rather than a lava pit. Lava and netherrack are worth less now, lighting is worth
  substantially more, and seating, furnishings, storage, a smoker and a cauldron all count toward
  the hearth for the first time. A furnished fireplace room now out-scores a netherrack box of
  comparable size, which used to score roughly twice as high for no arrangement at all. This is a
  nerf to nether-block-only hearths and a buff to furnished ones.
- The guide now explains what "any lighting", "any reagents" and every other category on a room
  page actually means. A new glossary page in the Soul Rooms category lists exactly what each of
  the mod's own categories holds, generated straight from the tag files so it can never drift from
  what the classifier does, and every mention of one of these categories on a room page now links
  straight to it.
- Tier 2 and tier 3 were badly mistuned for every shipped archetype - tier 2 needed a build close
  to the best that archetype could ever score, and tier 3 could not be reached at all, by any
  build, no matter how well arranged. Since buff magnitude ramps against the top of an archetype's
  own tier ladder, an unreachable tier 3 was quietly capping every buff in the mod well under its
  advertised maximum too - the armoury, for instance, could never pay out more than about 47% of
  its stated sword damage ceiling. Every archetype's tier 2 and tier 3 thresholds are retuned
  against what that archetype can actually score, so a well-built room can clear tier 2 without
  being maxed out, tier 3 is reachable by a genuinely excellent one, and every buff can now pay
  out in full.
- Two builds standing next to each other are two rooms again. Open-air structures - a farm, a
  racetrack, a training yard - were grouped by measuring straight-line distance between the blocks
  they are made of, which took no notice of anything in between: solid rock was not a boundary, so
  a farm and a track a few blocks apart came back as one region, holding both archetypes' blocks
  and therefore scoring as neither, and building a wall between them - the obvious thing to reach
  for - changed nothing at all. An open-air region now reaches out to the next block through space
  it could actually cross, so walls, floors and other structures separate builds the way they look
  like they should. Its reach is a little shorter too (`cluster_radius`, now 3): a path through a
  field still leaves one farm, while a few blocks of clear ground between two builds now means two
  builds. And it takes in the ground under it by following its own shape rather than its bounding
  box, so a long or L-shaped field no longer swallows whatever happens to be standing in the
  rectangle it does not occupy.
- A fence or a low wall around a build no longer cuts it off from the rest of itself. Only a block
  filling its whole cell counts as the edge of a build now. A fence, a wall, a pane, a slab, a
  stair or a chest is something you put *inside* a build - the track archetype scores fencing as
  part of a track - so a fenced circuit walled off from its own trackside was the mod disagreeing
  with itself. A wall of full blocks still separates two builds, and that is still the way to say
  "these are two different places".
- A region is a solid thing now, not a ring with a hole in it. The infield of a rail loop, the
  courtyard inside a ring of crops, the stone a raised bed is built around: none of it could be
  reached from a signal block, so none of it was ever counted as part of the build it plainly
  belongs to. Worse, the clearance a room's arrangement is scored against is only recorded for
  what a region took in, so a solid infield read back as clear open space and any arrangement
  asking for room to move got the answer exactly backwards. Whatever a build closes around is now
  part of it - and anything already claimed by a room or another build is left alone, so a shrine
  in the middle of your racetrack is still its own thing.
- A solid mass of the blocks an archetype looks for is taken in whole rather than skinned. Hay
  bales, ice and farmland are all full blocks; a build now steps into one regardless, so a
  haystack counts as a haystack instead of as a hollow shell of its own outside faces.
- A building no longer sprouts a second region on top of itself. A room's walls were only the layer
  of blocks touching its air, which left a roof laid over the ceiling, the outer half of a thick
  wall, and even the corners of a plain box belonging to nothing - and loose blocks are exactly
  what an open-air region forms around, so a barn with a hay roof came back as a barn plus a
  mysterious second box sitting on it. The blocks packed against a room's walls now belong to that
  room's building (`shell_depth`). They are claimed, not scored: what a room is worth is still what
  lines it.
- A farm planted in the crook of an L-shaped house is found again. Buildings used to claim their
  own footprint by excluding the whole bounding box of each room, which for anything that is not a
  plain rectangle covered a great deal of ground the building does not stand on. Anything built in
  that ground - the classic case being a garden in the corner of your own house - was silently
  never reported at all.
- Sealed gaps too small to stand in are no longer offered as rooms. The void inside a double-thick
  wall, the space behind a stair, the shaft up a hollow pillar: each is a sealed pocket, each was a
  region, and none of them can ever classify as anything, so a build of any complexity left the
  Soul Lens full of boxes drawn around nothing. A pocket now has to be at least `min_room_volume`
  cells - eight by default, two blocks each way - to count as a room.
- The Soul Lens has a proper icon instead of the flat placeholder it launched with. It now reads
  as part of the same set as the SoulKey and the guide book - a shaded metal rim and handle, and a
  soul-fire glow visible through the glass with a small purple rune, rather than a handful of flat
  colours with no shading or outline.
- The Soul Lens opens a screen now instead of filling your chat with a wall of scores. Used inside
  your soul, it lists every region it found down one side - the same colours as the outlines it
  still draws in the world - and explains the one you pick: its tier and how close it is to the
  next, what counted and what to add next, how it is arranged, and what it grants. Used outside
  your soul, it opens the same kind of screen for your buffs and where each one came from. Chat is
  back to two short lines - "looking through your soul...", "nothing here to outline yet" - and
  `/soulhome analyse` still reports in full to whoever asks it directly.
- A ladder connecting two floors no longer splits a build into two sealed rooms of its own accord.
  A ladder's real hitbox is a thin sliver against the wall it is mounted on, neither empty nor a
  full cube, so it fell into the same bucket as a fence or a slab and was treated as a wall - and
  unlike a fence, a single ladder is often the only thing standing in a one-wide shaft between
  floors, so whether the two floors read as one connected build or two disconnected rooms came
  down to whether the ladder's footprint happened to cover the whole gap. Ladders (and vines) are
  now always a way through, the same as a torch or a carpet: the room's air flows through them
  regardless of where they are placed.

Other mods

Three new rooms written for mods this one does not depend on, and one door closed.

- Waystones - and every other teleport in the game - can no longer carry anyone into or out of a
  soul. A warp plate built inside a soulhome was a public door into somebody's private dimension,
  and a return scroll used in one was a free trip home that skipped the exit position saved on the
  way in and the rescan on the way out, so the rooms you had just finished building went uncounted
  and the way back pointed wherever you had last used the key. Crossing a soul's boundary by any
  means but a soul key is now refused with a line saying so. Nothing about Waystones in particular
  is singled out: this is written against the event every cross-dimension teleport goes through,
  so portals and other mods' warps are covered by the same rule. `dimension.restrict_travel` in
  the server config turns it off for a pack that wants its own way in.
- **Arcane Sanctum**, for Iron's Spells 'n Spellbooks. An inscription table with shelves gathered
  round it, pedestals set about, somewhere to read and something to read by. Grants up to +60
  maximum mana, in the same units the mod's own robes are measured in.
- **Ritual Chamber**, also for Iron's Spells. A scroll forge standing on soul sand, pedestals
  ringing it, the arcane anvil and the cauldrons to hand. Grants up to +30% spell power - the
  general kind, so a room in your soul never decides which school of magic you are.
- **Workshop**, for Create. Six or more machine parts, laid out as something that runs, with a
  bench, storage against the line and an anvil to hand. Grants up to +1.5 blocks of reach, for
  placing and for hitting alike.
- Iron's Spells' and Create's blocks now count toward the rooms that were already here. Arcane
  debris and pedestals read as arcane, the alchemist's and blood cauldrons as vessels, the arcane
  anvil as smithing, armour piles as armament, firefly jars and rose quartz lamps as lighting,
  Create's seats as seating, its vaults and toolboxes as storage, and its casings, girders and
  framed glass as structural. This is a buff to any room already built with them: they used to
  count for nothing at all.
- None of this needs the mods installed, and none of it breaks without them. Every entry added to
  a tag is optional, so a tag never fails to load; every buff finds its attribute by name at
  runtime rather than by importing a class that may not exist; and the three rooms simply cannot
  be reached, because each gates on a block only that mod can place. A room that grants a buff
  with nothing to act on still classifies and still reports what it would give, and the log says
  once, at startup, which buffs are waiting on a mod.
- The guide book's category glossary no longer overruns its page. A tag with more entries than
  fits now runs onto as many pages as it needs, up to four, and says how many were left out -
  before, a long list was drawn as far as the page went and then simply stopped, with nothing to
  say anything was missing.
- The Ritual Chamber and the arcane tag named two blocks Iron's Spells 'n Spellbooks does not
  actually have - `blood_cauldron` and `arcane_debris`. Neither ever crashed anything (an id from
  an absent block simply never matches), but neither could ever count for anything either. The
  Ritual Chamber's vessel signal now looks for the alchemist's cauldron alone, and the arcane tag
  no longer lists a block that was never going to show up.

Bookshelves

A shelf full of books that the game never counted.

- Bookshelves now actually count in the Library, the Enchanting Room, the Arcane Sanctum, the
  Ritual Chamber and the Armoury. Every one of them was written against a tag called
  `minecraft:bookshelves`, which sounds exactly like something vanilla would ship and does not
  exist - the real tag, `minecraft:enchantment_power_provider`, is both narrower (no chiseled
  bookshelf) and not a name anyone would guess. An archetype gated or scored on a tag with no
  members simply never sees it: the Library's 16-bookshelf requirement could not be met by any
  number of bookshelves, and the other four rooms' bookshelf signal or detractor sat permanently
  at zero, however many were actually standing in the room. This mod now ships its own
  `soulhome:bookshelves` tag, holding both the plain and the chiseled bookshelf, and every
  archetype and the guide book point at that instead. A buff to four rooms and a fix to a fifth
  that could never have been built at all.

Soul Lens screen

- The Soul Lens screen no longer runs text off either edge of the window. A long archetype name,
  a block description, an arrangement clause - anything read straight off the scan - used to be
  drawn as one line regardless of how wide it was, so it either spilled past the right edge of the
  screen or, with enough regions, enough signals, or enough buffs, ran off the bottom where
  nothing could be seen or clicked. The detail panel now wraps every line to the space it has and
  scrolls with the mouse wheel instead, with a small thumb on the right when there is more below
  than fits. The buffs screen you get outside a soul got the same fix, for the same reason.
