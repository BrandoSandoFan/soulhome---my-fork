/* File created ~ 9 - 9 - 2026 */

package leaf.soulhome.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What every message in this package is: a vanilla custom payload that knows how to apply itself.
 *
 * <p>Under Forge these were plain {@code Consumer<NetworkEvent.Context>} objects and the channel
 * held the id-to-class table. NeoForge's payload system gives each message its own
 * {@link CustomPacketPayload.Type} instead, so the id lives on the message rather than in a
 * hand-numbered list in {@link Network} that a reordering could silently shift. The
 * apply-yourself half is unchanged: {@link #accept} still runs on the receiving side with the
 * context it needs to hop onto the main thread.
 */
public interface SoulPayload extends CustomPacketPayload
{
    void accept(IPayloadContext context);
}
