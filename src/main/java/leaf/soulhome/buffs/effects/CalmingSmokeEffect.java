/*
 * File created ~ 10 - 9 - 2026
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apiary: a beekeeper's smoke settles more than the hive.
 *
 * <p>Every mob that is only hostile once provoked - a zombified piglin, an enderman looked at too
 * long, a bee, a wolf, a piglin - implements {@link NeutralMob} whether or not it has bothered you
 * yet. This calms whichever of them are nearby, whether they were already angry or not, for a
 * span rather than for one instant: {@link NeutralMob#stopBeingAngry()} alone would clear an
 * existing grudge but do nothing about the piglin that gets spooked by the player's own next
 * footstep.
 *
 * <p><b>A ward, not a cure.</b> {@link #CALMED} tracks which mobs are under it and until when,
 * reapplied every server tick by {@link #onServerTick} so that whatever tries to anger a warded
 * mob in the meantime - the player's own proximity, another mob's alert, anything vanilla's own AI
 * would otherwise act on - is quietly undone before it can be felt. {@link #onLivingHurt} is the
 * one thing that is allowed to win: a warded mob that takes a hit is provoked regardless of who or
 * what struck it, and is dropped from the ward on the spot, per the room's own promise that this
 * lasts "for some time, or until you provoke them again" rather than for the full span regardless.
 */
public class CalmingSmokeEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.CALMING_SMOKE;

    /** 8 blocks at tier 1. */
    private static final double BASE_RADIUS = 6d;
    private static final double RADIUS_PER_MAGNITUDE = 2d;

    /** 20 seconds at tier 1. */
    private static final int BASE_DURATION_TICKS = 300;
    private static final int DURATION_PER_MAGNITUDE = 60;

    /** 60 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 1200;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 100;

    /** Mob id to the level it was warded in and the game time the ward expires. */
    private static final Map<UUID, Calmed> CALMED = new ConcurrentHashMap<>();

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Calming Smoke reaches, and how long it lasts before something has to provoke it again";
    }

    /** The two passive hooks this active needs - see the class javadoc. */
    @Override
    public void register()
    {
        MinecraftForge.EVENT_BUS.register(this);
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
        final int duration = BASE_DURATION_TICKS + (int) Math.round(magnitude * DURATION_PER_MAGNITUDE);
        final long expiry = level.getGameTime() + duration;

        List<Mob> neutrals = level.getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob instanceof NeutralMob && mob.isAlive());

        if (neutrals.isEmpty())
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_CALMING_SMOKE_NOTHING), true);
            return false;
        }

        for (Mob mob : neutrals)
        {
            ((NeutralMob) mob).stopBeingAngry();
            CALMED.put(mob.getUUID(), new Calmed(level, expiry));
        }

        level.playSound(
                null, player.blockPosition(), SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 0.6f, 1.0f);

        return true;
    }

    /** Reapplies the ward every tick, so nothing gets even one tick of anger inside it. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || CALMED.isEmpty())
        {
            return;
        }

        CALMED.entrySet().removeIf(entry ->
        {
            final Calmed calmed = entry.getValue();

            if (calmed.level().getGameTime() >= calmed.expiry())
            {
                return true;
            }

            final Entity entity = calmed.level().getEntity(entry.getKey());

            if (!(entity instanceof Mob mob) || !mob.isAlive() || !(mob instanceof NeutralMob neutral))
            {
                return true;
            }

            neutral.stopBeingAngry();
            return false;
        });
    }

    /** Provoked regardless of who or what struck it - the ward is not armour, only calm. */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        CALMED.remove(event.getEntity().getUUID());
    }

    private record Calmed(ServerLevel level, long expiry)
    {
    }
}
