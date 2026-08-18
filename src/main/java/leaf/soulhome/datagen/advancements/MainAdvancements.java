/*
 * File created ~ 13 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.advancements;

import leaf.soulhome.SoulHome;
import leaf.soulhome.advancements.ClassifiedRoomTrigger;
import leaf.soulhome.registry.ItemsRegistry;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.critereon.*;
import net.minecraft.commands.CommandFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.function.Consumer;

public class MainAdvancements implements Consumer<Consumer<Advancement>>
{
    public MainAdvancements()
    {
    }

    public void accept(Consumer<Advancement> advancementConsumer)
    {
        final String tabName = "main";

        final String titleFormat = "advancements.soulhome.%s.title";
        final String descriptionFormat = "advancements.soulhome.%s.description";
        final String achievementPathFormat = "soulhome:%s/%s";

        Advancement root = Advancement.Builder.advancement()
                .display(ItemsRegistry.SOUL_KEY.get(),
                        Component.translatable(String.format(titleFormat, tabName)),
                        Component.translatable(String.format(descriptionFormat, tabName)),
                        new ResourceLocation("textures/gui/advancements/backgrounds/stone.png"),
                        FrameType.TASK,
                        false,//showToast
                        false,//announceChat
                        false)//hidden
                .addCriterion("tick", InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.ANY))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, "root"));


        final String obtainedSoulKey = "obtained_soul_key";
        Advancement advancement1 = Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, obtainedSoulKey)),
                        Component.translatable(String.format(descriptionFormat, obtainedSoulKey)),
                        (ResourceLocation)null,
                        FrameType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion(
                        "has_item",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemsRegistry.SOUL_KEY.get()))
                .rewards(new AdvancementRewards(50, new ResourceLocation[]{new ResourceLocation("soulhome:guide")}, new ResourceLocation[0], CommandFunction.CacheableFunction.NONE))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, obtainedSoulKey));



        final String obtainedGuide = "obtained_guide";
        Advancement advancement2 = Advancement.Builder.advancement()
                .parent(advancement1)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, obtainedGuide)),
                        Component.translatable(String.format(descriptionFormat, obtainedGuide)),
                        (ResourceLocation)null,
                        FrameType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion(
                        "has_item",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemsRegistry.GUIDE.get()))
                .rewards(new AdvancementRewards(5, new ResourceLocation[0], new ResourceLocation[0], CommandFunction.CacheableFunction.NONE))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, obtainedGuide));


        final String enteredSoulDimension = "entered_soul_dimension";
        Advancement advancement3 = Advancement.Builder.advancement()
                .parent(advancement1)
                .display(
                        ItemsRegistry.GUIDE.get(),
                        Component.translatable(String.format(titleFormat, enteredSoulDimension)),
                        Component.translatable(String.format(descriptionFormat, enteredSoulDimension)),
                        (ResourceLocation)null,
                        FrameType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("entered_soul", PlayerTrigger.TriggerInstance.located(LocationPredicate.inBiome(ResourceKey.create(Registries.BIOME, SoulHome.SOULHOME_LOC))))
                .rewards(new AdvancementRewards(5, new ResourceLocation[0], new ResourceLocation[0], CommandFunction.CacheableFunction.NONE))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, enteredSoulDimension));

        // The soulhome structure buffs. 'blank' used to sit here as a placeholder for exactly this
        // feature, gated behind an impossible trigger so nothing could ever unlock the book entry
        // it guarded. It is now the real thing.
        final String firstRoom = "first_room";
        Advancement roomAdvancement = Advancement.Builder.advancement()
                .parent(advancement3)
                .display(
                        ItemsRegistry.SOUL_LENS.get(),
                        Component.translatable(String.format(titleFormat, firstRoom)),
                        Component.translatable(String.format(descriptionFormat, firstRoom)),
                        (ResourceLocation)null,
                        FrameType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("classified_room", ClassifiedRoomTrigger.Instance.any())
                .rewards(new AdvancementRewards(10, new ResourceLocation[0], new ResourceLocation[0], CommandFunction.CacheableFunction.NONE))
                .save(advancementConsumer, String.format(achievementPathFormat, tabName, firstRoom));

        // One per shipped archetype. Named after the archetype id so that the advancement, the
        // book entry and the datapack file all agree without anything mapping between them.
        archetypeAdvancement(advancementConsumer, roomAdvancement, "farm", Items.WHEAT);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "armoury", Items.IRON_SWORD);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "library", Items.BOOKSHELF);
        archetypeAdvancement(advancementConsumer, roomAdvancement, "enchanting_room", Items.ENCHANTING_TABLE);
    }

    /**
     * "You built a library." Awarded the first time a room is classified as this archetype, at any
     * tier - the first one is the moment worth marking, and asking for tier 3 up front would hide
     * the advancement behind the balance pass.
     */
    private static void archetypeAdvancement(
            Consumer<Advancement> advancementConsumer,
            Advancement parent,
            String archetype,
            ItemLike icon)
    {
        Advancement.Builder.advancement()
                .parent(parent)
                .display(
                        icon,
                        Component.translatable("advancements.soulhome." + archetype + ".title"),
                        Component.translatable("advancements.soulhome." + archetype + ".description"),
                        (ResourceLocation)null,
                        FrameType.TASK,
                        true, //showToast
                        true, //announce
                        false)//hidden
                .addCriterion("classified_room", ClassifiedRoomTrigger.Instance.of(SoulHome.MODID + ":" + archetype))
                .rewards(new AdvancementRewards(10, new ResourceLocation[0], new ResourceLocation[0], CommandFunction.CacheableFunction.NONE))
                .save(advancementConsumer, "soulhome:main/" + archetype);
    }
}