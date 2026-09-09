/* File created ~ 9 - 9 - 2026 */

package leaf.soulhome.buffs;

import leaf.soulhome.SoulHome;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Attaches {@link PlayerSoulBuffs} to a player and saves it with them.
 *
 * <p>A data attachment rather than persistent NBT. Persistent NBT - which this mod already uses
 * for the last-dimension data - would be simpler, but it has no client sync story, and the
 * feedback work needs the client to be able to render active buffs without a round-trip for
 * every tooltip.
 *
 * <h2>Why this is no longer a provider</h2>
 *
 * <p>Under Forge this was an {@code ICapabilitySerializable} whose {@code LazyOptional} holder had
 * to be remade on demand, because invalidation was a permanent one-way switch and a player is
 * invalidated and revived on every dimension change - so a holder created once in a field was dead
 * from the first time its player walked into their own soulhome. An attachment has no holder to
 * throw away: the value lives on the entity's attachment map, follows it across dimensions, and is
 * created lazily by {@code getData} the first time anything asks. The whole class of bug is gone
 * rather than worked around.
 *
 * <p>{@link AttachmentType.Builder#copyOnDeath()} is what carries the buffs - and the ascension
 * rank they were clamped against (#85) - through a respawn. NeoForge already copies serializable
 * entity attachments when a player returns from the End, so this only has to opt into the death
 * case; both halves used to be one hand-written {@code PlayerEvent.Clone} handler that had to
 * revive the old player's capabilities to read them.
 */
public final class SoulBuffsAttachment
{
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SoulHome.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSoulBuffs>> BUFFS =
            ATTACHMENT_TYPES.register(
                    "buffs",
                    () -> AttachmentType.serializable(PlayerSoulBuffs::new).copyOnDeath().build());

    private SoulBuffsAttachment()
    {
    }
}
