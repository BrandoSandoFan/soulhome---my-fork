/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSurveyedBlocksMessage;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * Watchtower: ore and hostiles, outlined through terrain for a few seconds (#88).
 *
 * <p>The cleanest case in the epic for amplifying an active. Doubling the radius of "I can see
 * where the diamonds are" is always worth having and never trivialises anything, because you still
 * have to go and mine it - unlike a percentage, which stops being interesting long before it stops
 * growing.
 *
 * <p><b>Hostiles use vanilla Glowing and ore uses our own outline</b>, which looks like two
 * mechanisms for one ability and is one fewer than the alternative. Glowing already draws an entity
 * through walls, expires by itself, and is applied server-side; writing a second entity outliner to
 * sit beside it would be code that exists only to avoid using the one that already works. Blocks
 * have no such effect, so those we draw ourselves - see {@code SurveyorsEyeRenderer}.
 */
public class SurveyorsEyeEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.SURVEYORS_EYE;

    /** Two charges at tier 1, and one more per tier after it - #88's own starting number. */
    private static final int BASE_CHARGES = 2;

    /** 90 seconds at tier 1, shortened as the room improves. */
    private static final int BASE_RECHARGE_TICKS = 1800;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 300;

    private static final int BASE_RADIUS = 8;
    private static final int RADIUS_PER_MAGNITUDE = 6;

    private static final int BASE_DURATION_TICKS = 100;
    private static final int DURATION_PER_MAGNITUDE = 60;

    /**
     * A cap on how much ore one activation will report, so a rank V survey standing in a badlands
     * cave cannot hand a client a packet with fifty thousand positions in it. Generous enough that
     * no honest use reaches it.
     */
    private static final int MAX_REPORTED = 512;

    /**
     * Forge's own ore tag rather than {@link BlockTags#GOLD_ORES} and friends one at a time -
     * modded ores land in it too, which is the same reason the archetypes prefer tags to block ids.
     */
    private static final TagKey<net.minecraft.world.level.block.Block> ORES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores"));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Surveyor's Eye reaches and how long it lingers";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        return BASE_CHARGES + (int) Math.floor(Math.max(0d, magnitude - 1d));
    }

    @Override
    public int rechargeTicksFor(double magnitude)
    {
        return BASE_RECHARGE_TICKS - (int) Math.round(magnitude * RECHARGE_SAVED_PER_MAGNITUDE);
    }

    @Override
    public boolean activate(ServerPlayer player, double magnitude)
    {
        final ServerLevel level = player.serverLevel();
        final int radius = BASE_RADIUS + (int) Math.round(magnitude * RADIUS_PER_MAGNITUDE);
        final int duration = BASE_DURATION_TICKS + (int) Math.round(magnitude * DURATION_PER_MAGNITUDE);

        final List<Long> ore = findOre(level, player.blockPosition(), radius);
        final int outlined = glowHostiles(player, level, radius, duration);

        if (ore.isEmpty() && outlined == 0)
        {
            // nothing to show. Refusing rather than spending the charge is the difference between
            // "there is no ore here" and "the ability is broken"
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_SURVEYORS_EYE_NOTHING), true);
            return false;
        }

        Network.sendTo(new SyncSurveyedBlocksMessage(ore, duration), player);

        level.playSound(
                null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.8f);

        return true;
    }

    /**
     * Every ore block in a cube around the player. A cube rather than a sphere on purpose: the
     * corners cost nothing to include, and a player who has learned that the survey reaches "about
     * twenty blocks" is better served by it reaching that in every direction than by the corners
     * quietly being shorter.
     */
    private List<Long> findOre(ServerLevel level, BlockPos centre, int radius)
    {
        List<Long> found = new ArrayList<>();

        final int minY = Math.max(level.getMinBuildHeight(), centre.getY() - radius);
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + radius);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++)
        {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++)
            {
                for (int y = minY; y <= maxY; y++)
                {
                    cursor.set(x, y, z);

                    // an unloaded chunk is not searched rather than being loaded to search it: a
                    // survey should not be a way to force chunk loading around a player
                    if (!level.isLoaded(cursor))
                    {
                        continue;
                    }

                    final BlockState state = level.getBlockState(cursor);

                    if (state.isAir() || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE))
                    {
                        continue;
                    }

                    if (state.is(ORES))
                    {
                        found.add(cursor.asLong());

                        if (found.size() >= MAX_REPORTED)
                        {
                            return found;
                        }
                    }
                }
            }
        }

        return found;
    }

    private int glowHostiles(ServerPlayer player, ServerLevel level, int radius, int duration)
    {
        final AABB box = player.getBoundingBox().inflate(radius);
        int outlined = 0;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, target -> target instanceof Enemy))
        {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, false));
            outlined++;
        }

        return outlined;
    }
}
