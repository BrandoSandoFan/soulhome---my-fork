/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The knobs on the Ambience epic (#163/#167), and the only settings record in this package that a
 * <b>client</b> owns rather than a server.
 *
 * <p>That is the departure worth spelling out. Every other knob in this mod is server-side and read
 * through {@code SoulHomeConfig}'s immutable snapshot, because every other knob decides something
 * about the game. Ambience decides only how the place looks to one person, and a server has no
 * business deciding whether one player sees fog - so these come off {@code SoulHomeClientConfig}
 * instead, out of {@code config/soulhome-client.toml}, per player and per install.
 *
 * <p>{@link #intensity} reaches zero through the same control as everything else, on purpose: a
 * player who finds any of this uncomfortable should have one setting to find rather than four
 * (#167). At zero, nothing here is drawn, tinted, moved or played, and the mod's fog and sky are
 * exactly Minecraft's.
 *
 * @param enabled     the master switch for the whole epic
 * @param rankVisuals whether sky and fog answer ascension rank (#164)
 * @param character   whether they answer what is built in the soul (#165)
 * @param sound       whether the ambient one-shots play at all (#166)
 * @param intensity   0 to 1, scaling every part of it together
 * @param soundVolume 0 to 1, on top of the player's own Ambient/Environment slider
 */
public record AmbienceSettings(
        boolean enabled,
        boolean rankVisuals,
        boolean character,
        boolean sound,
        double intensity,
        double soundVolume)
{
    public static final double DEFAULT_INTENSITY = 0.6d;

    /** Deliberately well under a block being placed: ambience that competes with the game is noise. */
    public static final double DEFAULT_SOUND_VOLUME = 0.35d;

    public static final AmbienceSettings DEFAULT =
            new AmbienceSettings(true, true, true, true, DEFAULT_INTENSITY, DEFAULT_SOUND_VOLUME);

    /** Every part off - what {@code ambience.enabled = false} gives, and what a dedicated server has. */
    public static final AmbienceSettings OFF =
            new AmbienceSettings(false, false, false, false, 0d, 0d);

    public AmbienceSettings
    {
        intensity = clamp(intensity);
        soundVolume = clamp(soundVolume);
    }

    /** Whether anything at all should be drawn or played - the one test every surface starts with. */
    public boolean active()
    {
        return this.enabled && this.intensity > 0d;
    }

    public boolean rankVisualsActive()
    {
        return active() && this.rankVisuals;
    }

    public boolean characterActive()
    {
        return active() && this.character;
    }

    public boolean soundActive()
    {
        return active() && this.sound && this.soundVolume > 0d;
    }

    private static double clamp(double value)
    {
        return Double.isNaN(value) ? 0d : Math.max(0d, Math.min(1d, value));
    }
}
