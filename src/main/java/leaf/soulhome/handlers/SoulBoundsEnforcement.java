/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SnapshotBlockVolume;
import leaf.soulhome.structures.SoulHomeBuffData;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A soulhome is a box (#78/#79): floor, ceiling and four walls, and nothing may be placed outside
 * them. Every event here is a different way a block can end up somewhere a player never directly
 * clicked, and each is refused at the destination rather than by trying to reason about the source
 * - the cheapest check that is still correct, per #79.
 *
 * <p>What this does <b>not</b> cover: falling blocks, dispensers, and TNT-cannon movement. Those
 * paths write to the level directly rather than through an event Forge exposes generically, and
 * closing that gap needs either a mixin into block-placement internals or per-source hooks this
 * pass did not attempt. Worth a follow-up; the common case - a player's own placements, buckets and
 * pistons - is covered.
 *
 * <p>Entities are untouched on purpose. A player may walk, fly and fall outside the box; only a
 * block being placed is refused, so creative flight into the empty void around a soulhome is
 * harmless rather than something to police.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulBoundsEnforcement
{
    /** How rarely a denied player is told, so holding right-click into a wall is not sixty messages a second. */
    private static final long DENIAL_MESSAGE_COOLDOWN_MILLIS = 2_000L;

    private static final Map<UUID, Long> lastDeniedAtMillis = new ConcurrentHashMap<>();

    private SoulBoundsEnforcement()
    {
    }

    @SubscribeEvent
    public static void onEntityMultiPlace(BlockEvent.EntityMultiPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level))
        {
            return;
        }

        final RegionBounds box = SnapshotBlockVolume.declaredBox(level);
        boolean denied = false;

        for (var snapshot : event.getReplacedBlockSnapshots())
        {
            final BlockPos pos = snapshot.getPos();

            if (!box.contains(pos.getX(), pos.getY(), pos.getZ()))
            {
                denied = true;
                break;
            }
        }

        if (denied)
        {
            deny(event, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event)
    {
        // BlockEvent.EntityMultiPlaceEvent extends this class and fires it too - handled in full
        // above, since a multi-place's own getPos() names only one of the positions it touches
        if (event instanceof BlockEvent.EntityMultiPlaceEvent)
        {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level))
        {
            return;
        }

        final BlockPos pos = event.getPos();
        final RegionBounds box = SnapshotBlockVolume.declaredBox(level);

        if (!box.contains(pos.getX(), pos.getY(), pos.getZ()))
        {
            deny(event, event.getEntity());
        }
    }

    /**
     * Bucket placement: {@code BlockEvent.EntityPlaceEvent} does not reliably fire for a fluid
     * emptied from a bucket on 1.20.1, so this is caught one step earlier, at the interaction that
     * would trigger it. Mirrors vanilla's own destination logic closely enough for a guard rather
     * than exactly: the clicked block if it can be replaced, otherwise the block against the
     * clicked face.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level))
        {
            return;
        }

        final ItemStack held = event.getItemStack();

        if (!(held.getItem() instanceof BucketItem) || held.is(Items.BUCKET))
        {
            // not a bucket, or the empty one - which picks fluid up rather than placing it
            return;
        }

        final BlockPos clicked = event.getPos();
        final BlockState clickedState = level.getBlockState(clicked);
        final Direction face = event.getFace();
        final BlockPos destination = clickedState.canBeReplaced() || face == null
                ? clicked
                : clicked.relative(face);

        final RegionBounds box = SnapshotBlockVolume.declaredBox(level);

        if (!box.contains(destination.getX(), destination.getY(), destination.getZ()))
        {
            deny(event, event.getEntity());
        }
    }

    /**
     * Pistons: only the immediately-pushed cell is checked, not the whole chain a piston can push.
     * A piston sitting inside the verge that pushes a short chain across it is caught; a piston
     * deep inside the box pushing a chain long enough to reach past the wall is not. The full check
     * needs the same structure resolution vanilla's own piston code does, which this pass did not
     * attempt - see the class javadoc.
     */
    @SubscribeEvent
    public static void onPistonExtend(PistonEvent.Pre event)
    {
        if (event.getPistonMoveType() != PistonEvent.PistonMoveType.EXTEND)
        {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level))
        {
            return;
        }

        final BlockPos destination = event.getPos().relative(event.getDirection(), 2);
        final RegionBounds box = SnapshotBlockVolume.declaredBox(level);

        if (!box.contains(destination.getX(), destination.getY(), destination.getZ()))
        {
            event.setCanceled(true);
        }
    }

    /** Whether this level is a soulhome currently being bounded at all. */
    private static boolean applies(ServerLevel level)
    {
        if (!SoulHomeConfig.enforceBounds() || DimensionHelper.soulOwner(level).isEmpty())
        {
            return false;
        }

        // a save mid-migration: its legacy box, if it has one, is not known yet, so refusing
        // placement here risks blocking a legacy player's own pre-existing build before the scan
        // that would have protected it has had a chance to run
        return !SoulHomeBuffData.get(level).needsLegacyMigration();
    }

    private static void deny(Event event, Entity entity)
    {
        event.setCanceled(true);

        if (entity instanceof ServerPlayer player)
        {
            notify(player);
        }
    }

    private static void notify(ServerPlayer player)
    {
        final long now = System.currentTimeMillis();
        final Long last = lastDeniedAtMillis.get(player.getUUID());

        if (last != null && now - last < DENIAL_MESSAGE_COOLDOWN_MILLIS)
        {
            return;
        }

        lastDeniedAtMillis.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(Constants.StringKeys.ASCENT_DENIED), true);
    }
}
