# Archetype cost report (#304)

`ArchetypeCostReport` (in `src/test/java/.../structures/core/`) answers a question
`ArchetypeCeilingTest` cannot: tier bands sit at the same *share of each room's own ceiling*, so
difficulty is consistent in **score** but not in what the **blocks cost to gather**. For every shipped
archetype it prints the cheapest block mix it can find that reaches tier 1, 2 and 3, the cost of that mix
from `costs.json`, and the buff's maximum magnitude per unit of tier-3 cost.

Run by hand, never by CI. It is Minecraft-free, so plain `javac` is enough (from the repo root):

```sh
SP=/tmp/soulhome-offline && mkdir -p $SP
# junit.jar and gson.jar as in CLAUDE.md
javac -nowarn -d $SP/cost -cp "$SP/junit.jar:$SP/gson.jar" \
  $(find src/main/java/leaf/soulhome/structures/core -name '*.java') \
  src/main/java/leaf/soulhome/structures/BuiltinFormClauses.java \
  src/main/java/leaf/soulhome/structures/BuiltinBondRelations.java \
  src/test/java/leaf/soulhome/structures/core/ArchetypeJsonReader.java \
  src/test/java/leaf/soulhome/structures/core/ArchetypeCostReport.java
java -cp "$SP/cost:$SP/gson.jar:src/main/resources" leaf.soulhome.structures.core.ArchetypeCostReport
```

## What it does
Signals score `weight x sqrt(count)` up to `cap`, and distinct roles multiply the total, so the marginal
score per block is cheap to compute. Requirements are bought first, then it repeatedly buys the one block
with the best marginal score per unit cost until the tier threshold is met.

## What it ignores (printed at the top of every report, too)
- **Forms and bonds**, which are optional credit. A tier that cannot be reached from signals alone is shown
  as `forms`: those rooms *need* an arrangement, and the cost of that is not in the number.
- The density multiplier, detractors, and the room's volume (air and shell are free).
- Costs that are time, not materials (a charged creeper), and that a buff's value is not linear in its
  magnitude. The output is a starting point for tuning, not a verdict.
- Greedy is not optimal. It is cheap and consistent, which is what a comparison between rooms needs.

A signal that matches several blocks is priced at its cheapest entry. Anything with no entry in
`costs.json` uses `default` and is listed under *unpriced*; give those a number when you see them.

## Reading the last column
`max/100c` is the buff's maximum magnitude per 100 cost units, in that buff's *own* units (a 0.24 speed
bonus and 6.0 mana are not comparable). Compare rooms paying the same kind of buff, or look for rooms
orders of magnitude from their neighbours. The first run flags `trophy_room` (knockback resistance 0.15
for roughly 1,900 units, most of it heads) as the clear outlier, with `library` and `mine` next.
