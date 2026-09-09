/* File created ~ 9 - 9 - 2026 */

package leaf.soulhome.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Whose soulhome a {@link BoundSoulkey} opens, and under what name to show it.
 *
 * <p>Two loose NBT keys on the stack until 1.20.5, which is now impossible: item NBT is gone and
 * components are typed. A record with a codec is a better fit than the {@code custom_data}
 * component would have been - the pair is structured, so the game can validate it, show it in
 * {@code /data}, and sync it without this mod writing a byte of wire format by hand.
 *
 * <p>The name is carried rather than looked up from the uuid because a key can be held on a server
 * the bound player has never joined, or by someone browsing a creative inventory offline - there is
 * nothing to resolve the uuid against, and a key whose tooltip reads as a bare uuid is a key nobody
 * can tell apart from the next one.
 */
public record SoulBinding(UUID soul, String name)
{
    public static final Codec<SoulBinding> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    UUIDUtil.CODEC.fieldOf("soul").forGetter(SoulBinding::soul),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(SoulBinding::name))
            .apply(instance, SoulBinding::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulBinding> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SoulBinding::soul,
                    ByteBufCodecs.STRING_UTF8, SoulBinding::name,
                    SoulBinding::new);

    public SoulBinding
    {
        name = name == null ? "" : name;
    }
}
