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

Hearth and Track

- A brand-new hearth - one furnace or campfire, nothing else - now reliably sets its target on
  fire. The buff ramps in gently from an archetype's own first tier, and a bare-minimum hearth was
  landing well under half a second of burn: rounded to whole seconds, that could come out to
  nothing at all, so a hit from a starting hearth sometimes lit nothing. Fire Aspect now always
  grants at least one second once the buff is active, whatever the exact ramp gives underneath it.
- The Track archetype no longer wants minecart rails with ice glued underneath them - a shape that
  read as a minecart line, not something a player runs on. It is now built around two separate
  fence lines with a gap of one to three blocks between them: the lane a player actually runs
  through. A single fence loop still scores as a bonus if the lane curves into a real circuit, and
  rails and ice/hay are now optional flavour on top rather than the point of the room. Fence gates
  count as fence everywhere this applies, so a gate in the line does not split it in two.
- Track was also far too easy to push to its top tier - a single loop of rail with some fence and
  ice around it comfortably cleared what used to be the top score. The signal weights and tier
  thresholds are now tuned so a minimal lane just clears the first tier, and the top tier wants a
  long, lit, properly-shaped track, not a lap of cheap blocks.

Six new rooms

Six more shapes a soul can take, each with its own block palette and its own arrangement to build
toward.

- **Cold Storage**. Ice and snow, with your barrels and chests kept in a row rather than a heap.
  Grants up to 45% less damage from fire and lava - taken straight off the hit, not the vanilla
  status effect, so there is nothing to cure and nothing new on the buffs bar.
- **Shrine**. A lodestone with candles ringing it. Grants up to 40% of the experience death would
  otherwise take from you, left behind as orbs where you fell instead of lost outright.
- **Greenhouse**. Flowers and leaves kept behind glass, not out in the open like a farm's crops.
  Grants up to 35% slower hunger.
- **Treasury**. A cluster of gold, diamond, emerald or netherite blocks - no cage, no guard, just a
  hoard kept together rather than shelved and catalogued. Grants up to a 15% chance of an extra
  drop breaking any block, anywhere, not only inside the room.
- **Trophy Room**. Mob heads mounted on fence posts like pikes. A zombie's or a skeleton's is worth
  little on its own; a wither skeleton's or an ender dragon's a great deal more, and a wall of
  several different kinds beats a wall of one kind repeated. Grants up to +10% knockback
  resistance.
- **Aquarium**. Every part of the room that is not a wall or a solid decoration has to be water - a
  tank with a dry corner in it does not count. Grants up to 35% faster swimming.

Guide book polish

- The Arcane Sanctum, Ritual Chamber and Workshop pages no longer show up in the guide book on an
  install that does not have Iron's Spells 'n Spellbooks or Create. Every room page was gated
  behind the same "you have entered your soul" advancement, including the three written for
  another mod's blocks, so a vanilla player could read all about a room they could never build.
  Those three are now gated behind classifying one instead, which an install without the mod can
  never do - so the page only appears once it can actually be built, on any install, not just the
  three shipped this way.
- The basics chapter no longer reads like a developer's diary. The welcome page dropped a
  reference to another mod the book had no business mentioning and a "more exciting stuff coming"
  line that had gone stale; the "entering your soul" pages lost a couple of asides that broke the
  book's own voice. A widening circle of travel particles is now spelled correctly, and a doubled
  paragraph break in the Bound Soulkey page is gone.
- The Bound Soulkey's own page was titled "Personal Soul Key" - a name the item itself has never
  had. It now reads "Bound Soulkey" everywhere the book names it, matching the item.

A checkerboard was a perfect floor

- `soulhome:platform` - the clause that is supposed to tell a laid floor from a heap of the same
  blocks - counted diagonal neighbours as touching, so a checkerboard with no two blocks actually
  side by side, or a spiral climbing three storeys as a single block at a time, scored a flawless
  1.000: the same as a solid slab. A player scattering farmland in a lattice got full credit for a
  field they never laid. The fill is now four-connected instead of eight; a real floor, a
  water-trenched farm and a terraced farm all still read as one surface, since a genuine slope or
  trench runs along an axis rather than a diagonal.

A long fall into your own soul

- Entering a fresh soulhome used to drop you 6 to 19 blocks through empty air before you landed,
  depending on which of the three starting islands your soul picked - harmless, since soulhomes
  take no fall damage, but every new soul's first moment was an uncontrolled drop rather than
  arriving on solid ground. The island was being placed by the height of its whole bounding box,
  which includes the air and trees above its actual terrain; it is now placed by the height of the
  ground under the entry point instead, so every island style lands you exactly where you are
  meant to arrive.

The Ascent, phase one: a soulhome is a box

The first stage of a larger piece of work. A soulhome used to be 256 blocks of free vertical void
with nothing marking where you may build - the sensible thing to do with that much free space was
to fly up and stack a floor per room, and nothing about the space itself was ever a choice. A
soulhome is now bounded: a floor, a ceiling and four walls, visible as a faint shimmer near the
walls and the ceiling that fades the further away you build from them. Nothing can be built outside
the box - a placed block, a bucket of fluid, or a piston pushing something across the wall are all
refused, with one message if you keep trying rather than a flood of them.

- Every soulhome that already existed keeps every block it has: a legacy soulhome's own built
  footprint, captured once on its first scan after this update, stays placeable and scannable on
  top of whatever the box currently grants, for as long as the box stays smaller than what was
  already there. Nothing is deleted, and no room stops counting.
- `/soulhome ascent` reports the box you currently have to build inside of: the floor and ceiling
  heights, how far the walls reach, how many build layers that leaves, and - for a legacy soul -
  how much further your own existing build still reaches beyond it.
- The Soul Lens now opens on that same summary, so you know how much room you have left without
  leaving the screen you already check your rooms from.
- The climb that raises this box - ranks, essence, the pillar you build to earn one - is not part
  of this stage. Every soulhome is rank 0 for now; the box above is what you have to work with
  until the next stage lands.
- Everything here can be turned off in the server config (`ascent.enforce_bounds`), which returns a
  soulhome to exactly how it worked before this update.

The Ascent, phase two: a rank that actually holds

Phase one gave every soulhome a box, but rank itself was a constant zero wired through every part
of it - nothing could raise it, and the Soul Lens's own rank line was a hardcoded "0" rather than
anything read from a soulhome. Rank is now real, saved data: it lives with the rest of a soulhome's
state, survives a server restart, and reaches the client the same way the box itself already did.

- `/soulhome ascent` and the Soul Lens now report your soulhome's actual rank, in Roman numerals
  from I upward, instead of always showing 0.
- `/soulhome ascent set <rank>` lets an operator jump a soulhome straight to a rank, for testing -
  useful now, and necessary once the real climb (essence, willpower, a pillar to stand on) lands,
  since nobody should have to ascend for real five times over just to look at rank V.
- Two new server config knobs under `ascent`: `max_rank` (default 5) shortens or lengthens the
  ladder, and `starting_rank` (default 0) hands freshly-created soulhomes a head start.
- Nothing about how a rank is earned exists yet - there is no essence, no pillar, no ritual. This
  is the plumbing every later stage of the climb reads from and writes to.

Sublime Essence: the currency the climb spends

Phase two gave rank somewhere to live. This gives the Ascent something to earn and something to
spend - the first half of the actual climb, still short of the ritual that spends it.

- A new item family, Sublime Essence I through V. One item per rank, not one per rank per
  placeable block: the essence is spent on the ascension itself, never on the blocks you build
  with, so nothing about what you may build above a given rank narrows.
- Your soulhome now earns soul residue on its own, for free, just by holding rooms the game
  recognises - the same total score `/soulhome analyse` already adds up. A soulhome with nothing
  built in it earns nothing; a well-built one earns steadily, whether or not you are watching it
  happen, and whether or not you are even logged in. There is no daily cap and no online-time
  requirement - only a curve that flattens hard as your soulhome's score climbs, so a soulhome
  with many good rooms earns a few times what a modest one does, not many times over.
- Residue survives a server restart along with the rest of your soulhome's saved state, and a
  soulhome that predates this update starts earning from the moment it updates rather than being
  billed for however long it already existed.
- Essence can also be crafted directly from vanilla materials that get harder to find at each
  rank - amethyst shard, echo shard, heart of the sea, netherite scrap, nether star - for anyone
  who would rather not wait on residue, or whose soulhome cannot score highly yet.
- Nine of one rank's essence can be consolidated into one of the next, so a player stuck on one
  path - no ocean nearby for a heart of the sea, say - is never fully blocked. It is deliberately
  the worst-value way to get there: nobody should prefer it over the direct craft.
- Two new server config knobs under `ascent.essence`: a multiplier on how fast residue accrues,
  and how much residue converts into Essence I. A third, `residue_tap_enabled`, turns residue off
  entirely for a pack that would rather ascension be paid for than grown.
- What actually spends the essence - the Soul Anchor, the pillar you build to earn a rank, and the
  ritual itself - is not part of this update. This is the wallet; the till comes next.

The Soul Anchor and the Pillar of Ascent

The till. Rank has lived in a soulhome's save file since phase two, and essence has had somewhere
to come from since the last update, but nothing could actually spend either of them - every
soulhome sat at rank 0 whatever it had built or banked. It no longer does.

- A new block, the Soul Anchor, craftable from obsidian, an amethyst block and a corner of
  Essence I. Place one in your soulhome - only one is ever needed, and only one is ever allowed -
  and right-click it to hear exactly what your next ascension still needs: whether a pillar
  stands, how much willpower your soulhome still has to find, and how much essence you are
  carrying. The same click also draws down any soul residue you have banked into whole units of
  Essence I, so you never have to go looking for a separate way to cash it in.
- To ascend, build a pillar: a solid 3x3 base of full blocks somewhere within a few blocks of your
  anchor, running unbroken all the way up to your soulhome's current firmament. What it is made of
  and what shape it takes above that base is entirely up to you - a plain post and an ornamented
  buttressed tower both count, the same way two libraries that look nothing alike both count.
  Stand on top of it, and as long as your soulhome's total room score already clears the rank's
  threshold and you are carrying enough Sublime Essence of the right rank, the ritual begins on
  its own: no button, no menu, just the sky pressing down. Hold your ground under Slowness and
  Mining Fatigue for the ritual's duration and your rank rises, your box grows, and the essence is
  spent. Step off the pillar, or let it break under you, and the ritual fails with every scrap of
  essence handed straight back - an ascension should never cost you anything for a push you never
  chose to take.
- Only one ascension can run in a soulhome at a time, so two players standing on two different
  pillars can never both spend the same soulhome's one payment of progress.
- Breaking the Soul Anchor loses nothing. Rank and residue were never stored on the block - they
  never have been - so a stray pickaxe swing costs you a block to replace, not a single rank of
  progress.
- Four new server config knobs under `ascent.ritual`: how much essence one ascension costs, how
  long the ritual takes to hold, and the willpower threshold for the first rank and for every rank
  after it. A fifth, `pillar_search_radius`, sets how far from the anchor the pillar's base may
  sit.

Eight new rooms, and buffs you press

Every room in the mod so far pays out something you carry: a percentage, a duration, a count of
extra jumps. That works until it does not - four of those buffs stop being worth having long
before they stop growing, and a rank that multiplies them has nowhere good to put the extra. So
there is now a second kind of buff. Charges, reach and duration stay meaningful as they grow, and
a key that does something is felt in a way that damage going from +27% to +31% is not.

- Eight new rooms, each granting one ability. A **Watchtower** of glass and ladders grants
  Surveyor's Eye, which outlines ore and hostiles through solid stone. A **Bulwark** of bars and
  heavy stone grants Aegis, a bank of absorption for the hit you were not going to survive. A
  **Rift Chamber** of amethyst framed in obsidian grants Soul Step, a short blink through walls. A
  **Mead Hall** with a long table grants Rally. A **Stable** grants Call of the Herd, which brings
  your mount to you. A **Storm Spire** - a copper mast under open sky, rod at the tip - grants
  Thunderclap. A **Powder Magazine** grants Barrage. An **Infected Grotto**, sculk spreading over
  a carved hollow, grants Rupture.
- Two new keys, unbound conflicts aside: one to use your selected ability, one to cycle between
  them. A small display above the hotbar shows what is selected, how many charges are banked and
  how far along the next one is. Build no room that grants an ability and you will never see it -
  the mod adds nothing to your screen until you have earned it.
- The Watchtower is deliberately the room rank 0 cannot really build. A watchtower wants height,
  and rank 0 grants six build layers, so wanting one is a reason to climb. That is the point of
  it, not an oversight.
- Charges do not survive death, and cooldowns reset with them. They do survive logging out: come
  back halfway through a recharge and you are still halfway through it.
- Rally always does something for you alone - Strength and Resistance land on you whether or not
  anyone is nearby - and does more with a group around you. A room that did nothing single-player
  is a room most players would never build.
- Nothing an ability does can wreck a world. Thunderclap's lightning lights no fires and turns no
  pigs into piglins. Barrage breaks no blocks, primes no TNT and consumes nothing - the crates in
  your magazine are scenery and stay scenery. Soul Step cannot cross dimensions, and inside a
  soulhome it cannot land you outside your own box.
- Server owners get a master switch for the whole system, a cooldown multiplier, a floor no
  cooldown can fall below however high your rank climbs, a charge ceiling, and a per-ability
  off switch by id. That last one is there because there is no way for this mod to ask a claim
  protection mod whether a blink is allowed near spawn - so rather than pretend otherwise, a
  server uneasy about Soul Step can simply switch that one ability off and keep the rest.
- Two new ways a room can be judged on its shape. A Storm Spire scores for its rod actually
  crowning the mast rather than sitting at its foot, and an Infected Grotto scores for being
  carved rather than squared off. The second is the first thing in the mod that rewards
  irregularity, and it is deliberately a small bonus for going out of your way rather than a
  tax for building the way the block grid nudges everyone to build: a perfectly square grotto
  still counts as a grotto, and still earns its tier on what is in it.

Abilities that actually land, and pages that say what they mean

Four fixes to what the last release shipped. Three of them are the same failure in different
places: the mod knew something and did not say it.

- **Every ability was capped at a fraction of what its room promised.** An ability's magnitude is
  a count - bolts called, blocks blinked, absorption banked - and each of the eight was inheriting
  the ceiling meant for buffs measured as percentages. A Bulwark's page offered up to twelve points
  of absorption and Aegis handed out one, half a heart, at every tier and every rank. Thunderclap
  could never call a second bolt, Rally could never reach Strength II, and Call of the Herd's
  healing and hastening of your mount were unreachable at any score. Each of the eight now has its
  own ceiling, set to what its room already claimed, so a tier 3 room is worth three times a tier 1
  one instead of exactly the same. This is a large buff to every ability room, and an existing
  server's config file does not need editing for it: a buff type the file has never heard of now
  takes the mod's own ceiling rather than falling through to the percentage default.
- **Thunderclap and Rupture dealt magic damage, which another mod could quietly refuse.** The bolt
  fell, the mob flinched, and its health did not move. Thunderclap now deals lightning damage and
  Rupture deals the warden's own, both credited to you - so what they kill counts as your kill and
  what survives turns on you rather than on nobody. All three offensive abilities also land past
  the half-second of invulnerability a mob gets from being hit: pressing one in the middle of your
  own swing used to be swallowed whole, silently, with the charge spent anyway.
- **A press that does nothing now says so.** An ability still recharging answered with complete
  silence, which is indistinguishable from an ability that does not work. It now says how long is
  left. If a strike lands and nothing takes damage from it, that is said too, and the charge is
  handed back rather than spent on nothing.
- **The book explains its own arrangement pages.** A room page would tell you that "mast runs in a
  line" without anywhere saying what a mast was - the word was a key out of the archetype file,
  never a thing you could go and build. Every arrangement page now lists what each part it names is
  made of, with the same links to the category glossary the rest of the page uses, and the
  clauses that had numbers hidden behind them now state them: how long a line has to run, how close
  to the top the rod has to sit.
- **Ids stopped leaking into prose.** `/soulhome analyse` and the Soul Lens named blocks by
  registry id - "needs 1 of minecraft:lightning_rod" - and now use the name the block has in your
  hands, translated by your own client. The category glossary no longer opens each page with the
  raw tag id, room pages name a buff the way `/soulhome buffs` names it rather than as a flattened
  id, and blocks whose name is not their id read correctly at last: TNT, a Hay Bale, a Block of
  Copper.
