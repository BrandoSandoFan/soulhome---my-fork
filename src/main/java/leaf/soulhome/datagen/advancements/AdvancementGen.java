/*
 * File created ~ 13 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.advancements;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the mod's advancements.
 *
 * <p>This used to be a hand-written {@code DataProvider} that serialised each advancement itself
 * and resolved its own output path. Neither is ours to do any more: an advancement is built through
 * a codec rather than a {@code serializeToJson} method, and 1.21 moved the output folder from
 * {@code data/<ns>/advancements} to the singular {@code data/<ns>/advancement}. Delegating to
 * NeoForge's provider means both follow the game rather than a copy of it here.
 */
public class AdvancementGen extends AdvancementProvider
{
    public AdvancementGen(
            PackOutput packOutput,
            CompletableFuture<HolderLookup.Provider> registries,
            ExistingFileHelper existingFileHelper)
    {
        super(packOutput, registries, existingFileHelper, List.of(new MainAdvancements()));
    }
}
