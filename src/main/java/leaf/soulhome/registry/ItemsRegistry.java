/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 * Special thank you to SizableShrimp from the Forge Project discord!
 * Java isn't my first programming language, so I didn't know you could collect and set up items like this!
 * Makes setting up items for metals a breeze~
 */

package leaf.soulhome.registry;


import leaf.soulhome.SoulHome;
import leaf.soulhome.items.GuideItem;
import leaf.soulhome.items.BoundSoulkey;
import leaf.soulhome.items.SoulKeyItem;
import leaf.soulhome.items.SoulLensItem;
import leaf.soulhome.items.SublimeEssenceItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;


public class ItemsRegistry
{
    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SoulHome.MODID);


    public static final RegistryObject<Item> SOUL_KEY = ITEMS.register("soulkey", () -> createItem(new SoulKeyItem()));
    public static final RegistryObject<Item> PERSONAL_SOUL_KEY = ITEMS.register("personal_soulkey", () -> createItem(new BoundSoulkey()));
    public static final RegistryObject<net.minecraft.world.item.Item> GUIDE = ITEMS.register("guide", () -> createItem(new GuideItem()));

    /** Shows what the structure classifier can see. See {@link SoulLensItem}. */
    public static final RegistryObject<Item> SOUL_LENS = ITEMS.register("soul_lens", () -> createItem(new SoulLensItem()));

    /**
     * Sublime Essence I through V (#82), indexed by rank - {@code SUBLIME_ESSENCE.get(0)} is
     * Essence I. Registered as a list rather than five named fields so the recipes for the
     * crafting ladder and the nine-into-one consolidation can both be written as a loop.
     */
    public static final List<RegistryObject<Item>> SUBLIME_ESSENCE = List.of(
            ITEMS.register("sublime_essence_1", () -> createItem(new SublimeEssenceItem(1))),
            ITEMS.register("sublime_essence_2", () -> createItem(new SublimeEssenceItem(2))),
            ITEMS.register("sublime_essence_3", () -> createItem(new SublimeEssenceItem(3))),
            ITEMS.register("sublime_essence_4", () -> createItem(new SublimeEssenceItem(4))),
            ITEMS.register("sublime_essence_5", () -> createItem(new SublimeEssenceItem(5))));


    private static <T extends net.minecraft.world.item.Item> T createItem(T item)
    {
        return item;
    }

}
