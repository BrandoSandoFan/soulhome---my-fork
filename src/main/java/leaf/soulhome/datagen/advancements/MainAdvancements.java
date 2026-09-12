/*
 * File created ~ 13 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.advancements;

import leaf.soulhome.SoulHome;
import leaf.soulhome.advancements.ClassifiedRoomTrigger;
import leaf.soulhome.registry.ItemsRegistry;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.*;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MainAdvancements implements AdvancementProvider.AdvancementGenerator
{
    public MainAdvancements()
    {
    }

    @Override
    public void generate(
            HolderLookup.Provider registries,
            Consumer<AdvancementHolder> advancementConsumer,
            ExistingFileHelper existingFileHelper)
    {
        final String tabName = "main";

        final String titleFormat = "advancements.soulhome.%s.title";
        final String descriptionFormat = "advancements.soulhome.%s.description";
        final String achievementPathFormat = "soulhome:%s/%s";

        AdvancementHolder root = Advancement.Builder.advancement()
                .display(ItemsRegistry.SOUL_KEY.get(),
                        Component.translatable(String.format(titleFormat, tabName)),
                        Component.translatable(String.format(descriptionFormat, tabName)),
                        ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png"),
                        AdvancementType.TASK,
                        false,//showToast
                        false,//announceChat
                        false)//hidden
                // was hasItems(ItemPredicate.ANY) - a match-anything item predicate, which 1.20.5
                // removed along with the rest of the ANY constants. The root is meant to unlock the
                // moment the tab is loaded, and minecraft:tick says that outright.
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, "root"));


        final String obtainedSoulKey = "obtained_soul_key";
        AdvancementHolder advancement1 = Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, obtainedSoulKey)),
                        Component.translatable(String.format(descriptionFormat, obtainedSoulKey)),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion(
                        "has_item",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemsRegistry.SOUL_KEY.get()))
                .rewards(new AdvancementRewards(50, List.of(), List.of(ResourceLocation.parse("soulhome:guide")), Optional.empty()))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, obtainedSoulKey));


        final String obtainedGuide = "obtained_guide";
        AdvancementHolder advancement2 = Advancement.Builder.advancement()
                .parent(advancement1)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, obtainedGuide)),
                        Component.translatable(String.format(descriptionFormat, obtainedGuide)),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion(
                        "has_item",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemsRegistry.GUIDE.get()))
                .rewards(new AdvancementRewards(5, List.of(), List.of(), Optional.empty()))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, obtainedGuide));


        final String enteredSoulDimension = "entered_soul_dimension";
        AdvancementHolder advancement3 = Advancement.Builder.advancement()
                .parent(advancement1)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, enteredSoulDimension)),
                        Component.translatable(String.format(descriptionFormat, enteredSoulDimension)),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion(
                        "entered_soul",
                        PlayerTrigger.TriggerInstance.located(
                                LocationPredicate.Builder.location().setBiomes(soulBiomes())))
                .rewards(new AdvancementRewards(5, List.of(), List.of(), Optional.empty()))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, enteredSoulDimension));

        // The soulhome structure buffs. 'blank' used to sit here as a placeholder for exactly this
        // feature, gated behind an impossible trigger so nothing could ever unlock the book entry
        // it guarded. It is now the real thing.
        final String firstRoom = "first_room";
        AdvancementHolder roomAdvancement = Advancement.Builder.advancement()
                .parent(advancement3)
                .display(
                        ItemsRegistry.SOUL_LENS.get(),
                        Component.translatable(String.format(titleFormat, firstRoom)),
                        Component.translatable(String.format(descriptionFormat, firstRoom)),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("classified_room", ClassifiedRoomTrigger.Instance.any())
                .rewards(new AdvancementRewards(10, List.of(), List.of(), Optional.empty()))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, firstRoom));

        // Two rooms at once: the moment bonds (#140) can first mean anything, and what the guide
        // book's page on them is gated behind - a player with one room has nothing to bond.
        final String twoRooms = "two_rooms";
        Advancement.Builder.advancement()
                .parent(roomAdvancement)
                .display(
                        Items.OAK_DOOR,
                        Component.translatable(String.format(titleFormat, twoRooms)),
                        Component.translatable(String.format(descriptionFormat, twoRooms)),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("classified_rooms", ClassifiedRoomTrigger.Instance.atLeastRooms(2))
                .rewards(new AdvancementRewards(10, List.of(), List.of(), Optional.empty()))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, twoRooms));

        // One per shipped archetype. Named after the archetype id so that the advancement, the
        // book entry and the datapack file all agree without anything mapping between them.
        archetypeAdvancement(advancementConsumer, roomAdvancement, "farm", Items.WHEAT);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "armoury", Items.IRON_SWORD);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "library", Items.BOOKSHELF);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "enchanting_room", Items.ENCHANTING_TABLE);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "alchemy_lab", Items.BREWING_STAND);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "bedchamber", Items.RED_BED);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "mine", Items.RAIL);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "track", Items.OAK_FENCE);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "training_yard", Items.SLIME_BALL);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "hearth", Items.BLAZE_POWDER);

        // The three rooms built out of another mod's blocks. Their advancements exist whether or
        // not that mod does - an advancement nobody in this install can earn is invisible until
        // someone can, which is a great deal simpler than generating a different advancement tree
        // per mod list - and each is given a vanilla icon, since the icon has to draw in a game
        // that may not have the blocks the room is made of.
        archetypeAdvancement(advancementConsumer, roomAdvancement, "arcane_sanctum", Items.LAPIS_LAZULI);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "ritual_chamber", Items.SOUL_LANTERN);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "workshop", Items.PISTON);

        archetypeAdvancement(advancementConsumer, roomAdvancement, "cold_storage", Items.PACKED_ICE);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "shrine", Items.LODESTONE);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "greenhouse", Items.POPPY);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "treasury", Items.DIAMOND_BLOCK);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "trophy_room", Items.WITHER_SKELETON_SKULL);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "aquarium", Items.TROPICAL_FISH_BUCKET);

        // The eight rooms that grant an active ability (#88-#92, #94-#96). Nothing about them is a
        // special case here - a room is a room, whether what it pays out is carried or pressed.
        archetypeAdvancement(advancementConsumer, roomAdvancement, "watchtower", Items.SPYGLASS);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "bulwark", Items.IRON_BARS);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "rift_chamber", Items.AMETHYST_SHARD);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "mead_hall", Items.BARREL);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "stable", Items.SADDLE);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "storm_spire", Items.LIGHTNING_ROD);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "powder_magazine", Items.TNT);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "infected_grotto", Items.SCULK_CATALYST);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "purifying_font", Items.CAULDRON);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "gale_roost", Items.BAMBOO);

        // Three more: apiary and observatory grant a passive, ossuary an active - see Changelog.md
        archetypeAdvancement(advancementConsumer, roomAdvancement, "apiary", Items.HONEYCOMB);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "observatory", Items.DAYLIGHT_DETECTOR);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "ossuary", Items.BONE_BLOCK);
    }

    /**
     * "You built a library." Awarded the first time a room is classified as this archetype, at any
     * tier - the first one is the moment worth marking, and asking for tier 3 up front would hide
     * the advancement behind the balance pass.
     */
    /**
     * The soulhome biome, named by tag rather than by id - see {@link TagOnlyHolderSet} for why it
     * has to be a tag, and why the set is one of those rather than a real holder set.
     */
    private static HolderSet<Biome> soulBiomes()
    {
        return new TagOnlyHolderSet<>(
                TagKey.create(Registries.BIOME, ResourceLocationHelper.prefix("is_soulhome")));
    }

    private static void archetypeAdvancement(
            Consumer<AdvancementHolder> advancementConsumer,
            AdvancementHolder parent,
            String archetype,
            ItemLike icon)
    {
        Advancement.Builder.advancement()
                .parent(parent)
                .display(
                        icon,
                        Component.translatable("advancements.soulhome." + archetype + ".title"),
                        Component.translatable("advancements.soulhome." + archetype + ".description"),
                        (ResourceLocation) null,
                        AdvancementType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("classified_room", ClassifiedRoomTrigger.Instance.of(SoulHome.MODID + ":" + archetype))
                .rewards(new AdvancementRewards(10, List.of(), List.of(), Optional.empty()))
                .save(advancementConsumer, "soulhome:main/" + archetype);
    }
}