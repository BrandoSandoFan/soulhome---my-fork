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

        //a magnifying glass: amethyst for the lens, nuggets for the rim, a stick for the handle
        ShapedRecipeBuilder
                .shaped(RecipeCategory.TOOLS, ItemsRegistry.SOUL_LENS.get()) //output
                .define('N', Items.IRON_NUGGET)
                .define('A', Items.AMETHYST_SHARD)
                .define('S', Items.STICK)
                .pattern(" N ") //top row
                .pattern("NAN") //middle row
                .pattern("S  ") //bottom row
                .unlockedBy("has_material", has(Items.AMETHYST_SHARD))
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

        buildEssenceRecipes(consumer);
    }

    /**
     * Sublime Essence's three taps (#82). The overworld ladder is one craft per rank, each keyed to
     * a vanilla material genuinely harder to get than the one before it - exact counts are a
     * balance-pass decision, the ladder itself is not. Consolidation (nine of one rank into one of
     * the next) is deliberately worse value than the direct craft at every rank: it exists only so a
     * player who cannot reach one tap - no ancient city on their seed, a skyblock pack with no ocean
     * monument - is never hard-blocked, not so anyone would prefer it.
     */
    private void buildEssenceRecipes(Consumer<FinishedRecipe> consumer)
    {
        final Item[] keyedMaterials = {
                Items.AMETHYST_SHARD, Items.ECHO_SHARD, Items.HEART_OF_THE_SEA, Items.NETHERITE_SCRAP, Items.NETHER_STAR
        };
        final int[] counts = {4, 3, 2, 1, 1};

        for (int rank = 1; rank <= 5; rank++)
        {
            final Item essence = ItemsRegistry.SUBLIME_ESSENCE.get(rank - 1).get();
            final Item material = keyedMaterials[rank - 1];

            ShapelessRecipeBuilder
                    .shapeless(RecipeCategory.MISC, essence)
                    .requires(material, counts[rank - 1])
                    .unlockedBy("has_material", has(material))
                    .save(consumer, ResourceLocationHelper.prefix("sublime_essence_" + rank + "_from_crafting"));

            if (rank < 5)
            {
                final Item nextEssence = ItemsRegistry.SUBLIME_ESSENCE.get(rank).get();

                ShapelessRecipeBuilder
                        .shapeless(RecipeCategory.MISC, nextEssence)
                        .requires(essence, 9)
                        .unlockedBy("has_essence", has(essence))
                        .save(consumer, ResourceLocationHelper.prefix("sublime_essence_" + (rank + 1) + "_from_consolidation"));
            }
        }
    }
}
