/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.compat;

import leaf.soulhome.utils.LogHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Create's {@code copycat_panel}, {@code copycat_step}, {@code copycat_bars}, {@code copycat_base}
 * - and whatever Create adds next - all share one block entity class,
 * {@code CopycatBlockEntity}, and wear another block's appearance through its
 * {@code getMaterial()} method, which returns {@code AllBlocks.COPYCAT_BASE}'s own state for a
 * copycat wearing nothing. That default state is not special-cased here: it is not named by any
 * archetype or tag either, so a bare copycat naturally scores as nothing, exactly as it should.
 *
 * <p>Reached by reflection, never by importing Create's classes - see {@link BlockDisguises}'
 * javadoc for why. The class and method are resolved once, at class-init time, and reused for
 * every copycat block in every scan; if Create is not installed, or ever renames the method,
 * resolution simply fails once and every lookup afterwards is the cheap null check on
 * {@link #GET_MATERIAL}.
 */
final class CreateCopycatDisguise implements BlockDisguise
{
    private static final String BLOCK_ENTITY_CLASS =
            "com.simibubi.create.content.decoration.copycat.CopycatBlockEntity";

    private static final Class<?> BLOCK_ENTITY_TYPE = resolveType();
    private static final Method GET_MATERIAL = resolveGetMaterial();

    private static final AtomicBoolean WARNED_ONCE = new AtomicBoolean(false);

    private static Class<?> resolveType()
    {
        try
        {
            return Class.forName(BLOCK_ENTITY_CLASS);
        }
        catch (ClassNotFoundException e)
        {
            // Create is not installed. Every lookup below is then a no-op, the same outcome
            // ModAttributes gives a buff whose target mod is absent.
            return null;
        }
    }

    private static Method resolveGetMaterial()
    {
        if (BLOCK_ENTITY_TYPE == null)
        {
            return null;
        }

        try
        {
            return BLOCK_ENTITY_TYPE.getMethod("getMaterial");
        }
        catch (NoSuchMethodException e)
        {
            // Create is installed but no longer shaped the way this mod expects. Logged once at
            // class-init rather than once per copycat block a scan happens to touch.
            LogHelper.warn("Create is installed, but CopycatBlockEntity#getMaterial() could not be"
                    + " found. Copycat blocks will score as themselves rather than the material"
                    + " they are wearing.");
            return null;
        }
    }

    @Override
    public Optional<BlockState> materialOf(BlockEntity blockEntity)
    {
        if (GET_MATERIAL == null || !BLOCK_ENTITY_TYPE.isInstance(blockEntity))
        {
            return Optional.empty();
        }

        try
        {
            return Optional.ofNullable((BlockState) GET_MATERIAL.invoke(blockEntity));
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            if (WARNED_ONCE.compareAndSet(false, true))
            {
                LogHelper.warn("CopycatBlockEntity#getMaterial() failed while scanning a soulhome ("
                        + e + "); affected copycats will score as themselves instead.");
            }

            return Optional.empty();
        }
    }
}
