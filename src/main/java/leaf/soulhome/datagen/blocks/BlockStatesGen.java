/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.datagen.blocks;

import leaf.soulhome.SoulHome;
import leaf.soulhome.registry.BlocksRegistry;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** The Soul Anchor (#83) and the Meditation Cushion (#183). */
public class BlockStatesGen extends BlockStateProvider
{
    public BlockStatesGen(PackOutput output, ExistingFileHelper existingFileHelper)
    {
        super(output, SoulHome.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels()
    {
        simpleBlockWithItem(BlocksRegistry.SOUL_ANCHOR.get(), cubeAll(BlocksRegistry.SOUL_ANCHOR.get()));

        simpleBlockWithItem(BlocksRegistry.MEDITATION_CUSHION.get(), meditationCushionModel());
    }

    /**
     * A low element matching {@code MeditationCushionBlock}'s own 5/16-tall collision shape
     * (#238) - the complaint was that the cushion rendered as a full block despite already
     * having a cut-down hitbox, so the model has to carve the same box the collision does rather
     * than lean on {@code cubeAll} and a hitbox nobody can see.
     *
     * <p>The side faces sample only the bottom 5 pixels of {@code meditation_cushion_side.png}
     * (uvs {@code 0,11,16,16}), the same convention vanilla's own slab models use to reuse a
     * full-height texture for a half-height box - here on a texture drawn for exactly that strip.
     */
    private BlockModelBuilder meditationCushionModel()
    {
        var top = modLoc("block/meditation_cushion_top");
        var side = modLoc("block/meditation_cushion_side");

        BlockModelBuilder model = models().getBuilder("meditation_cushion")
                .texture("particle", top)
                .texture("top", top)
                .texture("side", side);

        model.element()
                .from(0, 0, 0)
                .to(16, 5, 16)
                .face(Direction.UP).texture("#top").end()
                .face(Direction.DOWN).texture("#side").cullface(Direction.DOWN).end()
                .face(Direction.NORTH).texture("#side").uvs(0, 11, 16, 16).end()
                .face(Direction.SOUTH).texture("#side").uvs(0, 11, 16, 16).end()
                .face(Direction.EAST).texture("#side").uvs(0, 11, 16, 16).end()
                .face(Direction.WEST).texture("#side").uvs(0, 11, 16, 16).end()
                .end();

        return model;
    }
}
