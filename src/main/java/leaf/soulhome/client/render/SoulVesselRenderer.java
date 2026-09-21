/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.client.render;

import leaf.soulhome.entity.SoulVesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Draws the Soul Vessel (#182) as its owner's own skin and armour, sitting where they left off.
 *
 * <p>The owner is online by definition whenever a vessel exists - they are either in their soul or
 * standing in the middle of a Soulgaze - so {@link Minecraft#getConnection()} always has a tab-list
 * entry for them, and {@link DefaultPlayerSkin} only ever covers the single frame between the
 * vessel appearing on this client and that entry arriving.
 *
 * <p>Always the wide (non-slim) arm model - resolving a vessel's owner to their actual chosen skin
 * model would need a second baked model swapped in per-entity the way vanilla's own player renderer
 * does for the client player, which is more machinery than a cosmetic arm width justifies here.
 */
public class SoulVesselRenderer extends LivingEntityRenderer<SoulVesselEntity, PlayerModel<SoulVesselEntity>>
{
    public SoulVesselRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SoulVesselModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SoulVesselEntity vessel)
    {
        return vessel.getOwnerId().map(this::skinFor).orElseGet(DefaultPlayerSkin::getDefaultSkin);
    }

    private ResourceLocation skinFor(UUID ownerId)
    {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        final PlayerInfo info = connection == null ? null : connection.getPlayerInfo(ownerId);

        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(ownerId);
    }
}
