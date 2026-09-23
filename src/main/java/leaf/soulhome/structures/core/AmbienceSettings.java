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
 * @param music       whether the soul's own music replaces Minecraft's while inside one (#163)
 */
public record AmbienceSettings(
        boolean enabled,
        boolean rankVisuals,
        boolean character,
        boolean sound,
        double intensity,
        double soundVolume,
        boolean music)
{
    /**
     * Most of the way up. It was 0.6, which with every other ceiling in the epic multiplied under it
     * left a soul full of rooms looking and sounding like an empty one (#163's first real playtest).
     * The accessibility half of #167 is that this reaches zero through one control, not that it
     * starts near it.
     */
    public static final double DEFAULT_INTENSITY = 0.85d;

    /**
     * Under the game, not under hearing. It was 0.35 on top of assets already mastered 10 dB down,
     * and the ambience reached the ear quieter than Minecraft's own music. The assets now carry the
     * "quiet" (see {@code tools/ambience}); this knob only sets where the soul sits against the game.
     */
    public static final double DEFAULT_SOUND_VOLUME = 0.8d;

    /** What the two defaults above used to be - {@code SoulHomeClientConfig} migrates a file still holding them. */
    public static final double LEGACY_DEFAULT_INTENSITY = 0.6d;

    public static final double LEGACY_DEFAULT_SOUND_VOLUME = 0.35d;

    public static final AmbienceSettings DEFAULT =
            new AmbienceSettings(true, true, true, true, DEFAULT_INTENSITY, DEFAULT_SOUND_VOLUME, true);

    /** Every part off - what {@code ambience.enabled = false} gives, and what a dedicated server has. */
    public static final AmbienceSettings OFF =
            new AmbienceSettings(false, false, false, false, 0d, 0d, false);

    public AmbienceSettings
    {
        intensity = clamp(intensity);
        soundVolume = clamp(soundVolume);
    }

    /** The settings as they were before the soul had music of its own - music on, as it defaults. */
    public AmbienceSettings(
            boolean enabled, boolean rankVisuals, boolean character, boolean sound, double intensity, double soundVolume)
    {
        this(enabled, rankVisuals, character, sound, intensity, soundVolume, true);
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

    /**
     * Whether the soul's own music plays, and so whether Minecraft's is held off while inside one.
     * Not scaled by {@link #soundVolume}: music has a slider of its own in the game's options, and
     * a second one here would be two controls for one thing. Off at zero intensity like everything
     * else, because that is the one switch #167 promises turns all of it off.
     */
    public boolean musicActive()
    {
        return active() && this.music;
    }

    private static double clamp(double value)
    {
        return Double.isNaN(value) ? 0d : Math.max(0d, Math.min(1d, value));
    }
}
