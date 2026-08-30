/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.commands.SoulCommand;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncArchetypesMessage;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.SoulTravel;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents
{
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event)
    {
        SoulCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new ArchetypeManager());
    }

    @SubscribeEvent
    public static void syncArchetypes(OnDatapackSyncEvent event)
    {
        //fires on login for one player, and on /reload with a null player for everyone
        final SyncArchetypesMessage message = new SyncArchetypesMessage(ArchetypeManager.archetypes());
        final ServerPlayer player = event.getPlayer();

        if (player != null)
        {
            Network.sendTo(message, player);
        }
        else
        {
            Network.sendPacketToAll(message);
        }
    }


    /**
     * A soul is entered and left with a soul key, and by nothing else.
     *
     * <p>Waystones is the reason this exists - a warp plate inside a soulhome, or a return scroll
     * used in one, walks straight past the key, past the exit position the mod saves on the way
     * in, and past the rescan on the way out that decides what the owner's rooms are currently
     * worth. Written against Forge's own travel event rather than against Waystones, because the
     * problem is not Waystones: every teleport that crosses a dimension boundary goes through
     * here, so one rule covers the warp stones, the scrolls, the portals and whatever the next
     * pack adds.
     *
     * <p>This mod's own moves are exempt - see {@link SoulTravel} for how they are marked - and
     * travel that touches no soul dimension at either end is none of our business.
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event)
    {
        if (!SoulHomeConfig.restrictSoulTravel() || SoulTravel.isSoulTravel())
        {
            return;
        }

        final boolean leavingASoul = SoulTravel.isSoulDimensionKey(event.getEntity().level().dimension());
        final boolean enteringASoul = SoulTravel.isSoulDimensionKey(event.getDimension());

        if (!leavingASoul && !enteringASoul)
        {
            return;
        }

        event.setCanceled(true);

        //a warp that silently does nothing reads as a broken mod, so say which rule was hit
        if (event.getEntity() instanceof ServerPlayer player)
        {
            player.displayClientMessage(
                    TextHelper.createTranslatedText(Constants.StringKeys.TRAVEL_BLOCKED), true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        final LivingEntity entityLiving = event.getEntity();
        final boolean inSoulDimension = DimensionHelper.isInSoulDimension(entityLiving);

        if (!inSoulDimension)
        {
            return;
        }

        //no fall damage in soul homes for any entity
        if (event.getSource() == entityLiving.damageSources().fall())
        {
            entityLiving.fallDistance = 0;
            event.setCanceled(true);
            return;
        }


        if (entityLiving instanceof Player)
        {
            event.setCanceled(true);
            entityLiving.fallDistance = 0;

            if (event.getSource() == entityLiving.damageSources().fellOutOfWorld())
            {
                DimensionHelper.FlipDimension((Player) entityLiving, entityLiving.getServer(), null, entityLiving.getUUID());
            }

        }
    }
}