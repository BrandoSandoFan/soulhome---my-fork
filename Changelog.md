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
quicker mining, more movement speed, an extra jump or two in mid-air, softer landings, and a sword
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

Buffs are earned in the soul and spent in the world. They survive death, dimension changes and
relogs, and a visitor to someone else's soul keeps their own.
