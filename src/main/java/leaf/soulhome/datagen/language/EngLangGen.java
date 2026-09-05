/*
 * File created ~ 13 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.language;

import leaf.soulhome.SoulHome;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.items.BoundSoulkey;
import leaf.soulhome.utils.ResourceLocationHelper;
import leaf.soulhome.utils.StringHelper;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

public class EngLangGen extends LanguageProvider
{
    private final PackOutput packOutput;

    public EngLangGen(PackOutput packOutput)
    {
        super(packOutput, SoulHome.MODID, "en_us");
        this.packOutput = packOutput;
    }

    @Override
    protected void addTranslations()
    {
        //Items and Blocks
        for (Item item : ForgeRegistries.ITEMS.getValues())
        {
            final ResourceLocation registryName = ResourceLocationHelper.get(item);
            if (registryName.getNamespace().contentEquals(SoulHome.MODID))
            {
                final String path = registryName.getPath();
                String localisedString = StringHelper.fixCapitalisation(path);
                final String tooltipStringKey = String.format(Constants.StringKeys.SOULHOME_ITEM_TOOLTIP, path);
                String tooltipString = "";

                //string overrides
                switch (localisedString)
                {
                    case "Guide":
                        //localisedString = "exampleOverride";
                        tooltipString = "If patchouli is installed, this is your guide to the mod";
                        break;
                    case "Soulkey":
                        tooltipString = "The key to accessing your own soul. Use for the full duration and it will take you there. As well as anything else nearby.";
                        break;
                    case "Soul Lens":
                        tooltipString = "Shows what your soul is made of, and what it is worth";
                        break;
                    case "Sublime Essence 1":
                        localisedString = "Sublime Essence I";
                        tooltipString = "Spent on the ascension ritual that raises your soulhome's rank.";
                        break;
                    case "Sublime Essence 2":
                        localisedString = "Sublime Essence II";
                        tooltipString = "Spent on the ascension ritual that raises your soulhome's rank.";
                        break;
                    case "Sublime Essence 3":
                        localisedString = "Sublime Essence III";
                        tooltipString = "Spent on the ascension ritual that raises your soulhome's rank.";
                        break;
                    case "Sublime Essence 4":
                        localisedString = "Sublime Essence IV";
                        tooltipString = "Spent on the ascension ritual that raises your soulhome's rank.";
                        break;
                    case "Sublime Essence 5":
                        localisedString = "Sublime Essence V";
                        tooltipString = "Spent on the ascension ritual that raises your soulhome's rank.";
                        break;
                    case "Soul Anchor":
                        tooltipString = "Right-click to hear what your soulhome's next ascension still needs.";
                        break;
                }

                if (item instanceof BoundSoulkey)
                {
                    add(item.getDescriptionId(), "Bound Soulkey");
                }
                else
                {
                    add(item.getDescriptionId(), localisedString);
                }
                add(tooltipStringKey, tooltipString);


            }
        }

        //Entities
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES)
        {
            final ResourceLocation registryName = ResourceLocationHelper.get(type);
            if (registryName.getNamespace().equals(SoulHome.MODID))
            {
                add(type.getDescriptionId(), StringHelper.fixCapitalisation(registryName.getPath()));
            }
        }

        //ItemGroups/Tabs
        add("tabs." + SoulHome.MODID + ".items", "SoulHome");

        //Damage Sources

        //Containers

        //effects

        //Sound Schemes

        //Configs

        //Commands


        //Tooltips
        add(Constants.StringKeys.SHIFT_ITEM_TOOLTIP, "\u00A77Hold \u00A78[\u00A7eShift\u00A78]");
        add(Constants.StringKeys.SHIFT_CONTROL_ITEM_TOOLTIP, "\u00A77Hold \u00A78[\u00A7eShift\u00A78] \u00A77and \u00A78[\u00A7eControl\u00A78]");
        add(Constants.StringKeys.CONTROL_ITEM_TOOLTIP, "\u00A77Hold \u00A78[\u00A7eControl\u00A78]");

        add(Constants.StringKeys.PATCHOULI_NOT_INSTALLED, "Patchouli is not installed");

        //Structure analysis
        //A fuzzy classifier that cannot say what it saw is indistinguishable from a broken one,
        //so these strings are load-bearing rather than decoration. Two rules they follow: never
        //say a room "failed" without saying what would fix it, and never name a number without
        //its unit.
        add(Constants.StringKeys.ANALYSE_HEADER, "Your soul holds %s region(s), of which %s counts for something.");
        add(Constants.StringKeys.ANALYSE_NOTHING_FOUND, "Nothing in your soul reads as a room yet. Enclose a space, or gather enough of one kind of thing in one place.");
        add(Constants.StringKeys.ANALYSE_NO_ARCHETYPES, "No archetypes are loaded, so nothing here can ever count. Check the datapack.");
        add(Constants.StringKeys.ANALYSE_SCANNING, "Looking through your soul...");
        add(Constants.StringKeys.ANALYSE_DISABLED, "Soulhome structure buffs are switched off in the server config.");
        add(Constants.StringKeys.ANALYSE_NO_SOULHOME, "You have never opened your soul, so there is nothing in it to look at.");
        add(Constants.StringKeys.ANALYSE_NOT_HERE, "Stand in the soul you want to look at first.");
        add(Constants.StringKeys.ANALYSE_NO_REGION_HERE, "You are not standing in anything that reads as a region.");

        add(Constants.StringKeys.REGION_CLASSIFIED, "%s, tier %s (score %s)");
        add(Constants.StringKeys.REGION_AMBIGUOUS, "Halfway between %s and %s");
        add(Constants.StringKeys.REGION_UNCLASSIFIED, "Not anything yet");
        add(Constants.StringKeys.REGION_SHAPE, "%s region %s, volume %s");
        add(Constants.StringKeys.REGION_NEXT_TIER, "%s more points to tier %s");
        add(Constants.StringKeys.REGION_AMBIGUOUS_DETAIL, "%s scored %s, %s scored %s");
        add(Constants.StringKeys.REGION_AMBIGUOUS_ADVICE, "Too close to call. Lean one way: add more of what only one of them wants.");
        add(Constants.StringKeys.REGION_CLOSEST, "Closest was %s, at %s");
        add(Constants.StringKeys.REGION_REQUIREMENT_FAILED, "needs %2$s of %1$s, found %3$s");
        add(Constants.StringKeys.REGION_SIGNAL, "%s x%s (%s)");
        add(Constants.StringKeys.REGION_SIGNAL_CAPPED, "only %s counted");
        add(Constants.StringKeys.REGION_MISSING, "Has none of: %s");

        add(Constants.StringKeys.REGION_STRUCTURE_HEADER, "Arrangement:");
        add(Constants.StringKeys.REGION_STRUCTURE, "%s (%s)");
        add(Constants.StringKeys.REGION_STRUCTURE_ZERO, "%s - not yet");
        add(Constants.StringKeys.REGION_STRUCTURE_CLAUSE_HIT, "+ %s");
        add(Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS, "- %s");
        add(Constants.StringKeys.REGION_STRUCTURE_CAPPED, "(held at the cap)");
        add(Constants.StringKeys.REGION_STRUCTURE_TRUNCATED, "This region is too large to analyse its arrangement.");
        add(Constants.StringKeys.REGION_STRUCTURE_SKIPPED, "%s (needs a mod that is not installed)");

        add(Constants.StringKeys.BUFFS_HEADER, "What your soul is giving you:");
        add(Constants.StringKeys.BUFFS_NONE, "Your soul is giving you nothing yet. Build a room in it.");
        add(Constants.StringKeys.BUFFS_ENTRY, "%s %s");
        add(Constants.StringKeys.BUFFS_SOURCE, "from your %s (%s room(s), best tier %s)");
        add(Constants.StringKeys.BUFFS_CAPPED, "(held at the cap)");
        add(Constants.StringKeys.BUFFS_RANK_BONUS, "of which %s is from your soul's rank");
        add(Constants.StringKeys.BUFFS_SOFT_CEILING_CONVERTED, "%s past its useful ceiling became %s");
        add(Constants.StringKeys.BUFFS_SOFT_CEILING_DROPPED, "%s past its useful ceiling, dropped");

        add(Constants.StringKeys.TRAVEL_BLOCKED, "Only a soul key crosses into a soul, and only a soul key leads back out.");

        add(Constants.StringKeys.ASCENT_DENIED, "This is beyond your soul's reach right now.");
        add(Constants.StringKeys.ASCENT_HEADER, "Your soul's reach:");
        add(Constants.StringKeys.ASCENT_RANK, "Rank: %s");
        add(Constants.StringKeys.ASCENT_BOX, "Floor y=%s, ceiling y=%s, walls %s blocks out from the centre");
        add(Constants.StringKeys.ASCENT_BUILD_LAYERS, "%s build layers");
        add(Constants.StringKeys.ASCENT_LEGACY, "Your soul predates this limit and also reaches %s, from what was already built.");
        add(Constants.StringKeys.ASCENT_NOT_YET, "Find your Soul Anchor to see what your next ascension needs.");
        add(Constants.StringKeys.ASCENT_MAXED, "Your soul has reached the highest rank this server allows.");
        add(Constants.StringKeys.ASCENT_DISABLED, "Soulhome bounds are switched off in the server config.");
        add(Constants.StringKeys.ASCENT_NO_SOULHOME, "You have never opened your soul, so there is no box to report on.");

        add(Constants.StringKeys.ASCENT_SET_SUCCESS, "Your soul is now rank %s.");
        add(Constants.StringKeys.ASCENT_SET_OUT_OF_RANGE, "This server's max_rank is %s - you cannot set a rank above it.");

        // The Soul Anchor and the ascension ritual (#83)
        add(Constants.StringKeys.ANCHOR_NOT_HERE, "The Soul Anchor only answers inside a soulhome.");
        add(Constants.StringKeys.ANCHOR_ALREADY_EXISTS, "This soulhome already has a Soul Anchor. Break it first if you want to move it.");
        add(Constants.StringKeys.ANCHOR_HEADER, "The Soul Anchor stirs:");
        add(Constants.StringKeys.ANCHOR_RANK, "Rank: %s");
        add(Constants.StringKeys.ANCHOR_MAXED, "This soul has reached the highest rank this server allows.");
        add(Constants.StringKeys.ANCHOR_READY, "Everything is in place. Stand on the pillar's cap and hold.");
        add(Constants.StringKeys.ANCHOR_RESIDUE_CONVERTED, "Converted your soul's residue into %s Essence I.");

        add(Constants.StringKeys.ANCHOR_PILLAR_OK, "Pillar: stands unbroken to the firmament.");
        add(Constants.StringKeys.ANCHOR_PILLAR_NO_BASE, "Pillar: no 3x3 base of full blocks stands near this anchor.");
        add(Constants.StringKeys.ANCHOR_PILLAR_GAP, "Pillar: stops %s block(s) short of the firmament.");
        add(Constants.StringKeys.ANCHOR_WILLPOWER_OK, "Willpower: %s / %s - your soul's own substance is enough.");
        add(Constants.StringKeys.ANCHOR_WILLPOWER_MISSING, "Willpower: %s / %s - build more before you climb.");
        add(Constants.StringKeys.ANCHOR_ESSENCE_OK, "Essence: %s / %s Sublime Essence %s - enough to spend on the climb.");
        add(Constants.StringKeys.ANCHOR_ESSENCE_MISSING, "Essence: %s / %s Sublime Essence %s - find or craft more first.");

        add(Constants.StringKeys.ANCHOR_RITUAL_IN_PROGRESS, "Another ascension is already underway in this soulhome.");
        add(Constants.StringKeys.ANCHOR_RITUAL_STARTED, "The sky presses down. Hold your ground.");
        add(Constants.StringKeys.ANCHOR_RITUAL_ABORTED_MOVED, "You left the pillar's cap. The ritual fails, and your essence is returned.");
        add(Constants.StringKeys.ANCHOR_RITUAL_ABORTED_PILLAR, "The pillar gave way beneath the ritual. Your essence is returned.");
        add(Constants.StringKeys.ANCHOR_RITUAL_SUCCESS, "Your soul ascends to rank %s.");

        add(Constants.StringKeys.LENS_HIGHLIGHTED, "Use inside your soul to outline what was found");
        add(Constants.StringKeys.LENS_NOTHING_TO_SHOW, "Nothing here to outline yet.");

        add(Constants.StringKeys.LENS_SCREEN_TITLE, "Soul Lens");
        add(Constants.StringKeys.LENS_SCREEN_UNCLASSIFIED, "Not anything yet");
        add(Constants.StringKeys.LENS_SCREEN_AMBIGUOUS, "Halfway between two rooms");
        add(Constants.StringKeys.LENS_SCREEN_TIER, "Tier %s");
        add(Constants.StringKeys.LENS_SCREEN_SCORE, "Score: %s");
        add(Constants.StringKeys.LENS_SCREEN_NEXT_TIER, "%s more points to tier %s");
        add(Constants.StringKeys.LENS_SCREEN_MAXED, "Nothing more to reach");
        add(Constants.StringKeys.LENS_SCREEN_AMBIGUOUS_DETAIL, "Too close to call against %s (%s). Add more of what only one of them wants.");
        add(Constants.StringKeys.LENS_SCREEN_COUNTS, "Your soul holds %s region(s), of which %s counts for something.");
        add(Constants.StringKeys.LENS_SCREEN_SIGNALS_HEADER, "What counted");
        add(Constants.StringKeys.LENS_SCREEN_MISSING_HEADER, "What to add next");
        add(Constants.StringKeys.LENS_SCREEN_ARRANGEMENT_HEADER, "Arrangement");
        add(Constants.StringKeys.LENS_SCREEN_GRANTS_HEADER, "Grants");
        add(Constants.StringKeys.LENS_SCREEN_MORE, "...and %s more");
        add(Constants.StringKeys.LENS_SCREEN_EMPTY_DETAIL, "No archetypes are loaded, so nothing here can ever count.");
        add(Constants.StringKeys.LENS_SCREEN_BUFFS_TITLE, "Your Soul's Buffs");
        add(Constants.StringKeys.LENS_SCREEN_BUFFS_FROM, "%s (%s room(s), best tier %s)");
        add(Constants.StringKeys.LENS_SCREEN_BUFFS_RANK_BONUS, "of which %s is from your soul's rank");
        add(Constants.StringKeys.LENS_SCREEN_CLOSE, "Close");

        add(Constants.StringKeys.LENS_SCREEN_BOX_HEADER, "Your soul's reach");
        add(Constants.StringKeys.LENS_SCREEN_BOX_LAYERS, "%s build layers (floor y=%s to ceiling y=%s)");
        add(Constants.StringKeys.LENS_SCREEN_BOX_VERGE, "walls %s blocks out from the centre");
        add(Constants.StringKeys.LENS_SCREEN_BOX_RANK, "Rank %s");
        add(Constants.StringKeys.LENS_SCREEN_BOX_LEGACY, "Your soul predates this limit and also reaches %s");

        //Buff types. Named rather than shown as ids, since a player reads these and a log does not.
        //The table is shared with the guide book, so the name a room page promises and the name
        //the "/soulhome buffs" command prints cannot drift apart
        for (Map.Entry<String, String> buff : BuffDisplayNames.all().entrySet())
        {
            add("buff." + buff.getKey().replace(':', '.'), buff.getValue());
        }

        //Archetypes. These are the 'display_name' keys the shipped archetype JSON names
        add("archetype.soulhome.farm", "Farm");
        add("archetype.soulhome.armoury", "Armoury");
        add("archetype.soulhome.library", "Library");
        add("archetype.soulhome.enchanting_room", "Enchanting Room");
        add("archetype.soulhome.alchemy_lab", "Alchemy Lab");
        add("archetype.soulhome.bedchamber", "Bedchamber");
        add("archetype.soulhome.mine", "Mine");
        add("archetype.soulhome.track", "Track");
        add("archetype.soulhome.training_yard", "Training Yard");
        add("archetype.soulhome.hearth", "Hearth");
        add("archetype.soulhome.arcane_sanctum", "Arcane Sanctum");
        add("archetype.soulhome.ritual_chamber", "Ritual Chamber");
        add("archetype.soulhome.workshop", "Workshop");
        add("archetype.soulhome.cold_storage", "Cold Storage");
        add("archetype.soulhome.shrine", "Shrine");
        add("archetype.soulhome.greenhouse", "Greenhouse");
        add("archetype.soulhome.treasury", "Treasury");
        add("archetype.soulhome.trophy_room", "Trophy Room");
        add("archetype.soulhome.aquarium", "Aquarium");
        add("archetype.soulhome.watchtower", "Watchtower");
        add("archetype.soulhome.bulwark", "Bulwark");
        add("archetype.soulhome.rift_chamber", "Rift Chamber");
        add("archetype.soulhome.mead_hall", "Mead Hall");
        add("archetype.soulhome.stable", "Stable");
        add("archetype.soulhome.storm_spire", "Storm Spire");
        add("archetype.soulhome.powder_magazine", "Powder Magazine");
        add("archetype.soulhome.infected_grotto", "Infected Grotto");

        //The abilities those rooms grant. A room and the thing it grants are not the same noun,
        //so these are their own keys rather than reusing the archetype names
        add(Constants.StringKeys.ABILITY_NAME_SURVEYORS_EYE, "Surveyor's Eye");
        add(Constants.StringKeys.ABILITY_NAME_AEGIS, "Aegis");
        add(Constants.StringKeys.ABILITY_NAME_SOUL_STEP, "Soul Step");
        add(Constants.StringKeys.ABILITY_NAME_RALLY, "Rally");
        add(Constants.StringKeys.ABILITY_NAME_CALL_OF_THE_HERD, "Call of the Herd");
        add(Constants.StringKeys.ABILITY_NAME_THUNDERCLAP, "Thunderclap");
        add(Constants.StringKeys.ABILITY_NAME_BARRAGE, "Barrage");
        add(Constants.StringKeys.ABILITY_NAME_RUPTURE, "Rupture");

        //Guide book
        add("soulhome.landing", "They say the soul is infinite. They didn't say how empty it was. Fortunately, we can fill it.");

        //KeyBindings
        add(Constants.StringKeys.KEYS_CATEGORY, "SoulHome");
        add(Constants.StringKeys.KEY_SOUL_CHARGE, "Charge Key To Transport");
        add(Constants.StringKeys.KEY_ABILITY_USE, "Use Soul Ability");
        add(Constants.StringKeys.KEY_ABILITY_CYCLE, "Next Soul Ability");

        //What an ability says when it fires, and when it refuses. A refusal always names its
        //reason - "nothing happened" is the single most common shape of an ability bug report
        add(Constants.StringKeys.ABILITY_SELECTED, "Soul ability: %s");
        add(Constants.StringKeys.ABILITY_HUD_CHARGES, "%s / %s");
        add(Constants.StringKeys.ABILITY_SOUL_STEP_NO_ROOM, "Nowhere safe to step to.");
        add(Constants.StringKeys.ABILITY_SOUL_STEP_DISABLED, "Soul Step is switched off on this server.");
        add(Constants.StringKeys.ABILITY_HERD_NO_MOUNT, "You have not ridden a mount of your own yet.");
        add(Constants.StringKeys.ABILITY_HERD_WRONG_DIMENSION, "Your mount is too far away to hear you.");
        add(Constants.StringKeys.ABILITY_HERD_SUMMONED, "%s answers.");
        add(Constants.StringKeys.ABILITY_SURVEYORS_EYE_NOTHING, "Nothing worth seeing within reach.");
        add(Constants.StringKeys.ABILITY_RALLY_ALONE, "You steady yourself.");
        add(Constants.StringKeys.ABILITY_RALLY_SHARED, "You rally %s others.");
        add(Constants.StringKeys.ABILITY_THUNDERCLAP_NOTHING, "Nothing hostile within reach.");
        add(Constants.StringKeys.ABILITY_RECHARGING, "Still recharging - %s seconds to go.");
        add(Constants.StringKeys.ABILITY_NO_DAMAGE, "It struck, and nothing took damage from it.");


        //Advancements

        add("advancements.soulhome.main.title", "SoulHome");
        add("advancements.soulhome.main.description", "Welcome to SoulHome. The way to your inner soul.");

        add("advancements.soulhome.obtained_soul_key.title", "Obtained SoulKey");
        add("advancements.soulhome.obtained_soul_key.description", "By using this key, you can transport yourself and nearby entities to your soul.");

        add("advancements.soulhome.entered_soul_dimension.title", "Enlightened");
        add("advancements.soulhome.entered_soul_dimension.description", "Hey wait, why is it so empty in here?");

        add("advancements.soulhome.obtained_guide.title", "Well Read");
        add("advancements.soulhome.obtained_guide.description", "Get hold of the guide, and read up on what a soul is for.");

        add("advancements.soulhome.first_room.title", "Furnished");
        add("advancements.soulhome.first_room.description", "Build something in your soul that the world recognises.");

        add("advancements.soulhome.farm.title", "Soul Food");
        add("advancements.soulhome.farm.description", "Grow enough in your soul that it counts as a farm.");

        add("advancements.soulhome.armoury.title", "Well Armed");
        add("advancements.soulhome.armoury.description", "Fit out a room in your soul as an armoury.");

        add("advancements.soulhome.library.title", "Inner Study");
        add("advancements.soulhome.library.description", "Fill a room in your soul with books, and somewhere to read them.");

        add("advancements.soulhome.enchanting_room.title", "Arcane Interior");
        add("advancements.soulhome.enchanting_room.description", "Build a room in your soul worthy of an enchanting table.");

        add("advancements.soulhome.alchemy_lab.title", "Bottled Up");
        add("advancements.soulhome.alchemy_lab.description", "Set a room in your soul up for brewing.");

        add("advancements.soulhome.bedchamber.title", "Sound Asleep");
        add("advancements.soulhome.bedchamber.description", "Make a room in your soul you could actually rest in.");

        add("advancements.soulhome.mine.title", "Deep Down");
        add("advancements.soulhome.mine.description", "Dig a working mine out of your own soul.");

        add("advancements.soulhome.track.title", "Built For Speed");
        add("advancements.soulhome.track.description", "Lay out a track in your soul worth racing on.");

        add("advancements.soulhome.training_yard.title", "Spring Loaded");
        add("advancements.soulhome.training_yard.description", "Build a training yard in your soul that teaches you to jump twice.");

        add("advancements.soulhome.hearth.title", "Playing With Fire");
        add("advancements.soulhome.hearth.description", "Build a hearth in your soul hot enough to temper a blade.");

        add("advancements.soulhome.arcane_sanctum.title", "Deep Reserves");
        add("advancements.soulhome.arcane_sanctum.description", "Make a place in your soul to study spells, and hold more magic for it.");
        add("advancements.soulhome.ritual_chamber.title", "Circle Complete");
        add("advancements.soulhome.ritual_chamber.description", "Set a room in your soul aside for the work spells are made in.");
        add("advancements.soulhome.workshop.title", "Everything To Hand");
        add("advancements.soulhome.workshop.description", "Fill a room in your soul with working machinery.");

        add("advancements.soulhome.cold_storage.title", "On Ice");
        add("advancements.soulhome.cold_storage.description", "Keep a room in your soul cold enough that fire and lava barely touch you.");

        add("advancements.soulhome.shrine.title", "Ember Kept");
        add("advancements.soulhome.shrine.description", "Raise a shrine in your soul that holds a little of what death would take.");

        add("advancements.soulhome.greenhouse.title", "Green Thumb");
        add("advancements.soulhome.greenhouse.description", "Grow a greenhouse in your soul lush enough to keep you fed longer.");

        add("advancements.soulhome.treasury.title", "Ill-Gotten");
        add("advancements.soulhome.treasury.description", "Stock a treasury in your soul rich enough to draw a little extra from the ground.");

        add("advancements.soulhome.trophy_room.title", "Mounted");
        add("advancements.soulhome.trophy_room.description", "Hang enough of what you've beaten on the wall of your soul to stand your ground.");

        add("advancements.soulhome.aquarium.title", "Deep End");
        add("advancements.soulhome.aquarium.description", "Flood a room in your soul until it is an aquarium, and swim like you belong there.");

        //The eight rooms that grant something you press (#88-#92, #94-#96)
        add("advancements.soulhome.watchtower.title", "Long Sight");
        add("advancements.soulhome.watchtower.description", "Build high enough, and glassed in enough, to see what the stone is hiding.");

        add("advancements.soulhome.bulwark.title", "Held Line");
        add("advancements.soulhome.bulwark.description", "Wall a room in your soul to hold rather than to make, and take a hit you should not have survived.");

        add("advancements.soulhome.rift_chamber.title", "Short Step");
        add("advancements.soulhome.rift_chamber.description", "Set amethyst in a frame of obsidian, and learn to be somewhere else.");

        add("advancements.soulhome.mead_hall.title", "Long Table");
        add("advancements.soulhome.mead_hall.description", "Lay a hall out for company, and find you are worth more with people around you.");

        add("advancements.soulhome.stable.title", "Answered Call");
        add("advancements.soulhome.stable.description", "Keep stalls in your soul, and never walk back to where you left your horse again.");

        add("advancements.soulhome.storm_spire.title", "Reaching For It");
        add("advancements.soulhome.storm_spire.description", "Raise a copper mast under open sky, with the rod at its tip and not at its foot.");

        add("advancements.soulhome.powder_magazine.title", "Racked And Ready");
        add("advancements.soulhome.powder_magazine.description", "Store your charges together rather than scattered, and have something to throw.");

        add("advancements.soulhome.infected_grotto.title", "Something Living In It");
        add("advancements.soulhome.infected_grotto.description", "Let sculk take a hollow in your soul, and keep it dark enough to spread.");

        //misc

        add(SoulHome.SOULHOME_LOC.toString(), "SoulHome");
        add("biome.soulhome.soulhome", "SoulHome");

    }

}
