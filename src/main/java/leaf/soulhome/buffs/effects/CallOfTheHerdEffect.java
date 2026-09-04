/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SnapshotBlockVolume;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Stable: your mount, wherever you left it (#92).
 *
 * <p>Not a number to be inflated - a <i>convenience</i> that gets less grudging as it improves.
 * There is no version of "my horse arrives faster" that becomes unwieldy, which is why the
 * magnitude goes into the cooldown and into what arrives with the mount rather than into a radius
 * or a damage figure.
 *
 * <p><b>The remembered mount lives on the player's persistent NBT, not on the capability.</b> It is
 * one UUID with no interaction with buffs, magnitudes or scanning, and the capability is
 * synchronised to the client on every change - which this has no reason to be, since the client
 * never needs to know which horse it is.
 *
 * <p>Constraints, per #92: the mount must be one this player has ridden and tamed; it must be in
 * this same level; inside a soulhome it must land inside the verge (#79). A dead or missing mount
 * says so and costs no charge. One mount, not a herd - the name is flavour.
 */
public class CallOfTheHerdEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.CALL_OF_THE_HERD;

    /** Where the last-ridden mount's id is kept on the player. */
    private static final String MOUNT_KEY = "soulhome:last_mount";

    /** Five minutes at tier 1, per #92. */
    private static final int BASE_RECHARGE_TICKS = 6000;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 1200;

    /** From tier 2, the mount arrives healed; from tier 3, hastened as well. */
    private static final double HEALS_FROM_MAGNITUDE = 2d;
    private static final double HASTENS_FROM_MAGNITUDE = 3d;

    private static final int HASTE_DURATION_TICKS = 200;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how often the herd answers, and what arrives with it";
    }

    /**
     * The one active that does need a passive hook: something has to notice which mount was last
     * ridden, and that is a Forge event rather than a keypress.
     *
     * <p>Subscribes directly rather than delegating to {@code SoulBuffEffect}'s default. That
     * default is not reachable from here - {@code SoulBuffEffect} is a superinterface of
     * {@link SoulActiveEffect} rather than of this class, and Java only allows
     * {@code Interface.super.method()} for an interface the class implements itself.
     */
    @Override
    public void register()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event)
    {
        if (!event.isMounting() || event.getLevel().isClientSide)
        {
            return;
        }

        if (!(event.getEntityMounting() instanceof ServerPlayer player))
        {
            return;
        }

        final Entity mount = event.getEntityBeingMounted();

        // remembered for every player, whether or not they own the room today: a player who builds
        // the stable after taming a horse should not have to re-ride it for the ability to work
        if (isCallable(mount, player))
        {
            player.getPersistentData().putUUID(MOUNT_KEY, mount.getUUID());
        }
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
        final CompoundTag data = player.getPersistentData();

        if (!data.hasUUID(MOUNT_KEY))
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_HERD_NO_MOUNT), true);
            return false;
        }

        final ServerLevel level = player.serverLevel();
        final UUID id = data.getUUID(MOUNT_KEY);
        final Entity mount = level.getEntity(id);

        if (mount == null || !mount.isAlive())
        {
            // either dead, or standing in a level this one is not. Both read the same from here,
            // and neither costs the player a charge.
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_HERD_WRONG_DIMENSION), true);
            return false;
        }

        if (!isCallable(mount, player))
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_HERD_NO_MOUNT), true);
            return false;
        }

        final Vec3 landing = player.position();

        if (!withinVerge(level, landing))
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_HERD_WRONG_DIMENSION), true);
            return false;
        }

        mount.teleportTo(landing.x, landing.y, landing.z);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.resetFallDistance();

        if (magnitude >= HEALS_FROM_MAGNITUDE && mount instanceof LivingEntity living)
        {
            living.setHealth(living.getMaxHealth());
        }

        if (magnitude >= HASTENS_FROM_MAGNITUDE && mount instanceof LivingEntity living)
        {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, HASTE_DURATION_TICKS, 0, false, true, true));
        }

        level.playSound(null, player.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.6f, 1.0f);

        player.displayClientMessage(
                Component.translatable(Constants.StringKeys.ABILITY_HERD_SUMMONED, mount.getDisplayName()), true);

        return true;
    }

    /**
     * A mount this player may call: alive, and either a tamed animal they own or a horse they have
     * tamed. Anything else - a boat, a minecart, someone else's horse - is remembered by nobody and
     * summoned by nobody.
     */
    private boolean isCallable(Entity mount, Player player)
    {
        if (mount instanceof TamableAnimal tamable)
        {
            return tamable.isTame() && player.getUUID().equals(tamable.getOwnerUUID());
        }

        if (mount instanceof AbstractHorse horse)
        {
            return horse.isTamed() && player.getUUID().equals(horse.getOwnerUUID());
        }

        return false;
    }

    private boolean withinVerge(ServerLevel level, Vec3 position)
    {
        if (!SoulHomeConfig.enforceBounds() || DimensionHelper.soulOwner(level).isEmpty())
        {
            return true;
        }

        final RegionBounds box = SnapshotBlockVolume.declaredBox(level);

        return box.contains(
                (int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z));
    }
}
