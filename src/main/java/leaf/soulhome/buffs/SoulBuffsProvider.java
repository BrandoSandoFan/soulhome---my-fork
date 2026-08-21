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
 *
 * <h2>The holder has to be replaceable</h2>
 *
 * {@link LazyOptional#invalidate()} is permanent. It is a one-way switch with no counterpart:
 * {@code reviveCaps()} restores the <i>provider</i>, and can do nothing about a holder the
 * provider already threw away.
 *
 * <p>That matters here because a player is removed and revived on every dimension change - Forge
 * calls {@code invalidateCaps()} on the way out and {@code reviveCaps()} on the way back in - and
 * the first half of that reaches {@link #invalidate} through the listener registered alongside
 * this provider. A holder created once in a field and never replaced is therefore dead from the
 * first time its player travels anywhere, and a soulhome is somewhere a player travels to on
 * purpose: every buff they had earned would stop being readable the moment they walked in to
 * build the room that granted it, while the soulhome's own saved results - which is what
 * {@code /soulhome buffs} and the Soul Lens report - went on saying everything was fine.
 *
 * <p>So the holder is remade on demand. Callers still get their invalidation notice, which is what
 * it is for, and the capability comes back the moment anything asks for it again. Nothing can ask
 * while the player is genuinely removed: {@code CapabilityProvider#getCapability} returns empty
 * without consulting this class until the player has been revived.
 */
public class SoulBuffsProvider implements ICapabilitySerializable<CompoundTag>
{
    public static final Capability<PlayerSoulBuffs> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>()
            {
            });

    private final PlayerSoulBuffs buffs = new PlayerSoulBuffs();

    /**
     * Not final, and deliberately. A race here costs a second wrapper around the same
     * {@link #buffs}, which resolves to the same object and so cannot disagree with itself.
     */
    private LazyOptional<PlayerSoulBuffs> holder = LazyOptional.of(() -> this.buffs);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side)
    {
        if (!this.holder.isPresent())
        {
            this.holder = LazyOptional.of(() -> this.buffs);
        }

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
