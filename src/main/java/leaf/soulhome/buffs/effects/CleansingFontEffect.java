/*
 * File created ~ 6 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Purifying Font: strips harmful potion effects from you, and from whoever else is standing at the
 * basin.
 *
 * <p>Scaling is <b>radius only</b>. There is no damage here to cap and no cooldown worth spending
 * on a bigger number - what a higher tier buys is a wider basin, not a stronger cleanse, since a
 * cleanse either removes an effect or it does not.
 *
 * <p><b>Only the harmful half of a player's effects is touched.</b> {@link MobEffectCategory#HARMFUL}
 * is read straight off each {@link MobEffectInstance}'s own effect rather than an exception list
 * this mod would have to keep in step with every future potion - the same category vanilla itself
 * already uses to colour an effect's HUD icon.
 *
 * <p>Refuses and refunds the charge when there was nothing to cleanse, the same as every other
 * active that can be pressed into doing nothing - a font that charges a player for standing in
 * clean water would be the one thing about this room that reads as broken.
 */
public class CleansingFontEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.CLEANSING_FONT;

    /** 5 blocks at tier 1, per Rally's own scale for a radius that shares something with nearby players. */
    private static final double BASE_RADIUS = 4d;
    private static final double RADIUS_PER_MAGNITUDE = 1d;

    /** 60 seconds at tier 1 - shorter than a combat active, since nothing here can hurt anyone. */
    private static final int BASE_RECHARGE_TICKS = 1200;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 150;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Purifying Font's cleanse reaches";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        return 1;
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
        final double radius = BASE_RADIUS + magnitude * RADIUS_PER_MAGNITUDE;

        int cleansed = cleanse(player);

        for (Player nearby : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius)))
        {
            if (nearby == player || nearby.isSpectator())
            {
                continue;
            }

            cleansed += cleanse(nearby);
        }

        if (cleansed == 0)
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_CLEANSING_FONT_NOTHING), true);
            return false;
        }

        level.playSound(
                null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.2f);

        player.displayClientMessage(
                Component.translatable(Constants.StringKeys.ABILITY_CLEANSING_FONT_CLEANSED, cleansed), true);

        return true;
    }

    /**
     * Strips every harmful effect from one entity, returning how many were removed - copied out
     * first, since removing while iterating the entity's own live list is asking for a
     * {@code ConcurrentModificationException}.
     */
    private int cleanse(Player target)
    {
        List<MobEffectInstance> harmful = new ArrayList<>();

        for (MobEffectInstance instance : target.getActiveEffects())
        {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL)
            {
                harmful.add(instance);
            }
        }

        for (MobEffectInstance instance : harmful)
        {
            target.removeEffect(instance.getEffect());
        }

        return harmful.size();
    }
}
