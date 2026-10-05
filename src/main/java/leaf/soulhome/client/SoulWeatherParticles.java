/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * What the air of a soul carries (#163): embers where hearths are, snow where the cold is, spores
 * where things grow - the soul's character, where a player stands, rather than only in its sky.
 *
 * <p>The first playtest's complaint was that a soul full of rooms showed nothing of them. Colour
 * alone cannot fix that, because colour is one number for the whole place and a player stops seeing
 * it after a minute. Particles are local and they move: an ember drifting past a player at their
 * workbench says "hearth" without a line of text, and a soul of hearths and ice has both, in the
 * proportions it was built in, with steam where the two meet.
 *
 * <p>How many of each is {@code SoulAmbience.weatherRates}, eased through {@link ClientAmbience};
 * which particle a voice uses is here, since a particle type is a client-side thing. Every one of
 * them is a vanilla type chosen because it already drifts slowly and is already small, so nothing
 * here can read as an event or hide a block.
 */
public final class SoulWeatherParticles
{
    /** How far from the player a particle may appear, horizontally. */
    private static final double REACH = 14d;

    /** Bounded per voice per tick, so a tick that owes many cannot spike. */
    private static final int MAX_PER_TICK = 3;

    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(
            new Vector3f(1f, 0.62f, 0.2f), new Vector3f(0.75f, 0.16f, 0.05f), 0.7f);

    private static final DustParticleOptions FILINGS = new DustParticleOptions(new Vector3f(0.62f, 0.7f, 0.82f), 0.55f);

    private SoulWeatherParticles()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused())
        {
            return;
        }

        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience
                .forDimension(minecraft.level.dimension().location().toString());

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            return;
        }

        final RandomSource random = minecraft.level.random;

        for (SoulVoice voice : SoulVoice.values())
        {
            final float rate = ClientAmbience.weather(voice);

            if (rate <= 0f)
            {
                continue;
            }

            final int owed = Math.min(MAX_PER_TICK, (int) rate + (random.nextFloat() < rate % 1f ? 1 : 0));

            for (int i = 0; i < owed; i++)
            {
                spawn(minecraft.level, minecraft.player, soul, voice, random);
            }
        }
    }

    private static void spawn(
            ClientLevel level, Player player, SyncSoulAmbienceMessage soul, SoulVoice voice, RandomSource random)
    {
        final double half = soul.getVergeHalfExtentOrLegacy();
        final double x = clamp(player.getX() + (random.nextDouble() * 2d - 1d) * REACH, half);
        final double z = clamp(player.getZ() + (random.nextDouble() * 2d - 1d) * REACH, half);
        final double y = Math.min(soul.getCeilingY(), player.getY() - 2d + random.nextDouble() * 10d);

        // never inside a block: a spore in a wall is a spore nobody sees, and an ember in one is a
        // glitch somebody does
        if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir())
        {
            return;
        }

        final double driftX = (random.nextDouble() - 0.5d) * 0.02d;
        final double driftZ = (random.nextDouble() - 0.5d) * 0.02d;

        switch (voice)
        {
            // rising and cooling as they go - the one particle here that moves up on purpose
            case WARM -> level.addParticle(EMBER, x, y, z, driftX, 0.03d, driftZ);
            case COLD -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, driftX, -0.02d, driftZ);
            // steam: the two poles at once rising as vapour, rather than embers and snow taking turns
            case STEAM -> level.addParticle(ParticleTypes.CLOUD, x, y, z, driftX * 0.5d, 0.015d, driftZ * 0.5d);
            // enchanting glyphs drawn in toward a point in the air, as they are to a bookshelf
            case ARCANE -> level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                    (random.nextDouble() - 0.5d) * 2d, random.nextDouble() * 1.5d, (random.nextDouble() - 0.5d) * 2d);
            case WROUGHT -> level.addParticle(random.nextInt(6) == 0 ? ParticleTypes.ELECTRIC_SPARK : FILINGS,
                    x, y, z, driftX, 0d, driftZ);
            case QUICKENED -> level.addParticle(ParticleTypes.GLOW, x, y, z, driftX, 0.01d, driftZ);
            case VERDANT -> level.addParticle(sporeOr(ParticleTypes.CHERRY_LEAVES, random), x, y, z, driftX, 0d, driftZ);
            case HOLLOW -> level.addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    x, y, z, driftX, -0.01d, driftZ);
            case OVERGROWN -> level.addParticle(sporeOr(ParticleTypes.MYCELIUM, random), x, y, z, driftX, 0d, driftZ);
            case BASE ->
            {
                // the place itself drifts as the firmament motes, not here
            }
        }
    }

    private static ParticleOptions sporeOr(ParticleOptions other, RandomSource random)
    {
        return random.nextInt(3) == 0 ? other : ParticleTypes.SPORE_BLOSSOM_AIR;
    }

    private static double clamp(double value, double half)
    {
        return Math.max(-half, Math.min(half, value));
    }
}
