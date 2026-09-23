/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.mixin;

import leaf.soulhome.client.SoulMusicPlayer;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds Minecraft's own music still while a soul plays its own (#163).
 *
 * <p>A mixin because Forge 47.3.0 has no event for it: {@code SelectMusicEvent} arrived in later
 * builds, and a biome's {@code music} entry would only cover survival - creative mode ignores the
 * biome and plays the creative tracks whatever it says. {@code MusicManager#tick} is the one place
 * every vanilla track is chosen and started, so holding it still is the whole of the job, and it
 * touches nothing else: jukeboxes, note blocks and every other sound in the game go through
 * {@code SoundManager} directly.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin
{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void soulhome$yieldToTheSoul(CallbackInfo callback)
    {
        if (!SoulMusicPlayer.replacesVanilla())
        {
            return;
        }

        if (SoulMusicPlayer.claimVanillaStop())
        {
            ((MusicManager) (Object) this).stopPlaying();
        }

        callback.cancel();
    }
}
