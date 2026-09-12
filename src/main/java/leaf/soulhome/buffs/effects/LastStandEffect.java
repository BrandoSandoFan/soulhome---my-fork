/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ossuary: the room's own promise - having stood vigil over the dead, dying is a little less
 * sudden - now kept as armour rather than as a heal.
 *
 * <p>Scaled by how much of the player's own health is already gone, not by the room's magnitude
 * alone: the same press is nearly nothing at full health and its strongest at the edge of death,
 * which is the room's whole point - a crypt does not steady someone who was never at risk. A
 * player who presses this unhurt is told so and spends no charge, the same courtesy every other
 * ability that can do nothing gives.
 *
 * <p>A transient {@code Attributes.ARMOR} modifier rather than the vanilla Resistance status
 * effect, so it reads on the armour bar a player already watches rather than adding a second place
 * to look - and so it stacks with whatever armour the player is already wearing instead of
 * replacing a fraction of incoming damage outright. Removed by {@link #onServerTick} once its own
 * span runs out, tracked by expiry rather than by a potion duration because an attribute modifier
 * has no clock of its own.
 */
public class LastStandEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.LAST_STAND;

    /**
     * Derived from the type id, the same way {@link AttributeBuffEffect} derives its own - 1.21
     * keys modifiers by {@link ResourceLocation} rather than by uuid, and the id already is one.
     */
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.parse(TYPE);

    /** 4 armour points at tier 1, at the edge of death - scaled down from there by how hurt the player is not. */
    private static final double BASE_ARMOR = 3d;
    private static final double ARMOR_PER_MAGNITUDE = 0.6d;

    /** 8 seconds at tier 1. */
    private static final int BASE_DURATION_TICKS = 120;
    private static final int DURATION_PER_MAGNITUDE = 20;

    /** 100 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 2000;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 130;

    /** Player id to where and when their own ward runs out. */
    private static final Map<UUID, Expiry> ACTIVE = new ConcurrentHashMap<>();

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "armour points at the edge of death, and how long the ward holds";
    }

    /** The one passive hook this active needs, to take the armour back off again on time. */
    @Override
    public void register()
    {
        NeoForge.EVENT_BUS.register(this);
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
        final double missingHealth = 1d - player.getHealth() / player.getMaxHealth();
        final double desperation = Math.max(0d, Math.min(1d, missingHealth));
        final double bonus = (BASE_ARMOR + magnitude * ARMOR_PER_MAGNITUDE) * desperation;

        if (bonus <= 0d)
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_LAST_STAND_UNHURT), true);
            return false;
        }

        final AttributeInstance armor = player.getAttribute(Attributes.ARMOR);

        if (armor == null)
        {
            return false;
        }

        if (armor.getModifier(MODIFIER_ID) != null)
        {
            armor.removeModifier(MODIFIER_ID);
        }

        // transient, so it is never written to the player's save file - the ward is recomputed
        // from a fresh press, not persisted state
        armor.addTransientModifier(new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));

        final ServerLevel level = player.serverLevel();
        final int duration = BASE_DURATION_TICKS + (int) Math.round(magnitude * DURATION_PER_MAGNITUDE);
        ACTIVE.put(player.getUUID(), new Expiry(level, level.getGameTime() + duration));

        level.playSound(
                null, player.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f, 0.7f);

        return true;
    }

    /** Takes the ward back off once its own span runs out - it does not fade, it ends. */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event)
    {
        if (ACTIVE.isEmpty())
        {
            return;
        }

        ACTIVE.entrySet().removeIf(entry ->
        {
            final Expiry expiry = entry.getValue();

            if (expiry.level().getGameTime() < expiry.expiry())
            {
                return false;
            }

            final ServerPlayer player = expiry.level().getServer().getPlayerList().getPlayer(entry.getKey());

            if (player != null)
            {
                final AttributeInstance armor = player.getAttribute(Attributes.ARMOR);

                if (armor != null)
                {
                    armor.removeModifier(MODIFIER_ID);
                }
            }

            return true;
        });
    }

    private record Expiry(ServerLevel level, long expiry)
    {
    }
}
