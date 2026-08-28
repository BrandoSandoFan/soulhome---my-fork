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
