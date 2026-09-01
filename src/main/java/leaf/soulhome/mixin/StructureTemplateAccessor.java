/* File created ~ 1 - 9 - 2026 */

package leaf.soulhome.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * {@code StructureTemplate} keeps its block data behind {@code palettes} with no public getter -
 * everything else on the class either places blocks straight into a world or filters them down to
 * a single {@code Block} type. Finding the island templates' own highest solid block (see
 * {@code DimensionRegistry#createSoulDimension}) needs every block, so this reaches the field the
 * same way {@code DefrostedRegistry} reaches a vanilla registry's private state.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor
{
    @Accessor("palettes")
    List<StructureTemplate.Palette> getPalettes();
}
