/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.commands.SoulCommand;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncArchetypesMessage;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
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