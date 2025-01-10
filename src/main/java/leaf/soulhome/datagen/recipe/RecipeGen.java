/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.recipe;

import leaf.soulhome.SoulHome;
import leaf.soulhome.registry.ItemsRegistry;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.data.*;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class RecipeGen extends RecipeProvider implements IConditionBuilder
{

    public RecipeGen(PackOutput output)
    {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer)
    {
        ShapedRecipeBuilder
                .shaped(RecipeCategory.TRANSPORTATION,ItemsRegistry.SOUL_KEY.get()) //output
                .define('I', Items.IRON_INGOT)
                .define('E', Items.ENDER_PEARL)
                .pattern("I  ") //top row
                .pattern("II ") //middle row
                .pattern("  E") //bottom row
                .unlockedBy("has_material", has(Items.ENDER_PEARL))
                .save(consumer);

        ShapelessRecipeBuilder
                .shapeless(RecipeCategory.MISC,ItemsRegistry.GUIDE.get())
                .requires(Items.BOOK)
                .requires(ItemsRegistry.SOUL_KEY.get())
                .unlockedBy("has_soul_key", has(ItemsRegistry.SOUL_KEY.get()))
                .save(consumer);

        ShapedRecipeBuilder
                .shaped(RecipeCategory.TRANSPORTATION, ItemsRegistry.PERSONAL_SOUL_KEY.get()) //output
                .define('I', Items.IRON_INGOT)
                .define('E', Items.ENDER_EYE)
                .pattern("I  ") //top row
                .pattern("II ") //middle row
                .pattern("  E") //bottom row
                .unlockedBy("has_material", has(Items.ENDER_EYE))
                .save(consumer);
    }
}
