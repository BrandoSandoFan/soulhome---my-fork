/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attaches {@link PlayerSoulBuffs} to a player and saves it with them.
 *
 * <p>A capability rather than persistent NBT. Persistent NBT - which this mod already uses for the
 * last-dimension data - would be simpler and survives death for free, but it has no client sync
 * story, and the feedback work needs the client to be able to render active buffs without a
 * round-trip for every tooltip.
 */
public class SoulBuffsProvider implements ICapabilitySerializable<CompoundTag>
{
    public static final Capability<PlayerSoulBuffs> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>()
            {
            });

    private final PlayerSoulBuffs buffs = new PlayerSoulBuffs();
    private final LazyOptional<PlayerSoulBuffs> holder = LazyOptional.of(() -> this.buffs);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side)
    {
        return CAPABILITY.orEmpty(capability, this.holder);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        return this.buffs.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        this.buffs.deserializeNBT(tag);
    }

    public void invalidate()
    {
        this.holder.invalidate();
    }
}
