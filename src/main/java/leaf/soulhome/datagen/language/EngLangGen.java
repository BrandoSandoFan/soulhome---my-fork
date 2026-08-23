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
        add(Constants.StringKeys.REGION_STRUCTURE, "Arranged: %s (%s)");
        add(Constants.StringKeys.REGION_STRUCTURE_MISSING, "Could also arrange: %s - %s");

        add(Constants.StringKeys.BUFFS_HEADER, "What your soul is giving you:");
        add(Constants.StringKeys.BUFFS_NONE, "Your soul is giving you nothing yet. Build a room in it.");
        add(Constants.StringKeys.BUFFS_ENTRY, "%s %s");
        add(Constants.StringKeys.BUFFS_SOURCE, "from your %s (%s room(s), best tier %s)");
        add(Constants.StringKeys.BUFFS_CAPPED, "(held at the cap)");

        add(Constants.StringKeys.LENS_HIGHLIGHTED, "Use inside your soul to outline what was found");
        add(Constants.StringKeys.LENS_NOTHING_TO_SHOW, "Nothing here to outline yet.");

        //Buff types. Named rather than shown as ids, since a player reads these and a log does not
        add("buff.soulhome.saturation", "Saturation from food");
        add("buff.soulhome.sword_damage", "Sword damage");
        add("buff.soulhome.xp_gain", "Experience gain");
        add("buff.soulhome.enchantment_power", "Enchanting levels");
        add("buff.soulhome.potion_duration", "Potion duration");
        add("buff.soulhome.healing", "Healing");
        add("buff.soulhome.mining_speed", "Mining speed");
        add("buff.soulhome.speed", "Movement speed");
        add("buff.soulhome.double_jump", "Extra jumps");
        add("buff.soulhome.fall_protection", "Fall damage reduction");
        add("buff.soulhome.fire_aspect", "Fire on hit");

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

        //Guide book
        add("soulhome.landing", "They say the soul is infinite. They didn't say how empty it was. Fortunately, we can fill it.");

        add("category.basics", "Basics");
        add("category.multiblock", "Multiblocks");

        add("entry.welcome", "Welcome");
        add("entry.soul", "Soul");
        add("entry.soul_key", "SoulKey");
        add("entry.guide", "Guide");


        //KeyBindings
        add(Constants.StringKeys.KEYS_CATEGORY, "SoulHome");
        add(Constants.StringKeys.KEY_SOUL_CHARGE, "Charge Key To Transport");


        //Advancements

        add("advancements.soulhome.main.title", "SoulHome");
        add("advancements.soulhome.main.description", "Welcome to SoulHome. The way to your inner soul.");

        add("advancements.soulhome.obtained_soul_key.title", "Obtained SoulKey");
        add("advancements.soulhome.obtained_soul_key.description", "By using this key, you can transport yourself and nearby entities to your soul.");

        add("advancements.soulhome.entered_soul_dimension.title", "Enlightened");
        add("advancements.soulhome.entered_soul_dimension.description", "Hey wait, why is it so empty in here?");

        add("advancements.soulhome.obtained_guide.title", "Well Read");
        add("advancements.soulhome.obtained_guide.description", "");

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

        //misc

        add(SoulHome.SOULHOME_LOC.toString(), "SoulHome");
        add("biome.soulhome.soulhome", "SoulHome");

    }

}
