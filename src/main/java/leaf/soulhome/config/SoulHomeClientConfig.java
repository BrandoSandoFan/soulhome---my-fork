/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.config;

import leaf.soulhome.structures.core.AmbienceSettings;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The mod's second config file, and the only client-side one - {@code config/soulhome-client.toml}.
 *
 * <p>Every other knob in this mod is server-side by design, read through {@link SoulHomeConfig}'s
 * immutable snapshot, because every other knob decides something about the game: what a room is
 * worth, how often a scan runs, how big a box is. Those are the server's business, and a player
 * being able to set them for themselves would be a cheat.
 *
 * <p>The Ambience epic (#163) is the first thing in the mod that decides nothing at all. Whether a
 * soul's sky is coloured is a question about one person's eyes, and a server has no business
 * answering it - so these live here, per install, and nothing on the server reads them. That is why
 * the mod now has two config files, and it is the only reason it does: a knob that changes an
 * outcome belongs in the other one.
 *
 * <p>Suppression (#188) added a second section for the same reason: screen warping makes some people
 * motion sick, and whether it may is that person's call, not the server's. The server has its own
 * switch too, and the stricter of the two wins - a server that forbids the warp forbids it for
 * everyone, and a player who turns it off never sees it whatever the server allows.
 *
 * <p>Read straight rather than through a snapshot. The server's snapshot exists so that a config
 * reload cannot land halfway through a scan and change the rules under it; nothing here is being
 * computed against, and a value that changes between two frames is a value that changed between two
 * frames.
 */
public final class SoulHomeClientConfig
{
    public static final Client CLIENT;
    public static final ModConfigSpec SPEC;

    static
    {
        final Pair<Client, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        SPEC = pair.getRight();
    }

    private SoulHomeClientConfig()
    {
    }

    /**
     * What {@code ambience.config_version} is written as today. Bumped when a default changes in a
     * way an existing file has to be told about - see {@link #migrate}.
     */
    public static final int CONFIG_VERSION = 2;

    public static void register(ModContainer container)
    {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, "soulhome-client.toml");

        if (container.getEventBus() != null)
        {
            container.getEventBus().addListener(SoulHomeClientConfig::onLoad);
        }
    }

    private static void onLoad(ModConfigEvent.Loading event)
    {
        if (event.getConfig().getSpec() == SPEC)
        {
            migrate();
        }
    }

    /**
     * Carries an existing file over a change of default.
     *
     * <p>NeoForge writes every default into the file the first time it is created, so a default
     * changed later reaches nobody who has already launched the game once - which, for #163's volume
     * and intensity fix, is every player who ever heard the problem. A value still sitting exactly on
     * the old default is taken to be the default rather than a choice, and moved; any other value is
     * a player's own and is left alone. Runs once per file: the version is written back with it.
     */
    private static void migrate()
    {
        if (CLIENT.configVersion.get() >= CONFIG_VERSION)
        {
            return;
        }

        if (CLIENT.intensity.get() == AmbienceSettings.LEGACY_DEFAULT_INTENSITY)
        {
            CLIENT.intensity.set(AmbienceSettings.DEFAULT_INTENSITY);
        }

        if (CLIENT.soundVolume.get() == AmbienceSettings.LEGACY_DEFAULT_SOUND_VOLUME)
        {
            CLIENT.soundVolume.set(AmbienceSettings.DEFAULT_SOUND_VOLUME);
        }

        CLIENT.configVersion.set(CONFIG_VERSION);
        SPEC.save();
    }

    /**
     * The knobs as the rest of the mod reads them.
     *
     * <p>Built on each read rather than cached: this is six values off an already-parsed config,
     * read a few times a second by the fog hook, and a cache that could go stale is a worse trade
     * than the arithmetic it saves. It also means a player editing the file in-game sees the change
     * as soon as Forge reloads it, which for a cosmetic switch is the whole point.
     */
    public static AmbienceSettings ambience()
    {
        if (!SPEC.isLoaded())
        {
            // asked for before the file has been read - a tick that lands during loading, or a
            // dedicated server where this config is never read at all. Nothing rather than the
            // defaults: a player who has this switched off should not see a frame of it while
            // their own setting is still on its way in.
            return AmbienceSettings.OFF;
        }

        return new AmbienceSettings(
                CLIENT.ambienceEnabled.get(),
                CLIENT.rankVisuals.get(),
                CLIENT.characterColour.get(),
                CLIENT.ambientSound.get(),
                CLIENT.intensity.get(),
                CLIENT.soundVolume.get(),
                CLIENT.soulMusic.get());
    }

    /** Whether this player allows suppression's screen warp (#188). False until the file is read, as with {@link #ambience}. */
    public static boolean suppressionDistortion()
    {
        return SPEC.isLoaded() && CLIENT.suppressionDistortion.get();
    }

    /** Whether this player wants suppression's drone (#188). */
    public static boolean suppressionAudio()
    {
        return SPEC.isLoaded() && CLIENT.suppressionAudio.get();
    }

    public static final class Client
    {
        public final ModConfigSpec.BooleanValue ambienceEnabled;
        public final ModConfigSpec.BooleanValue rankVisuals;
        public final ModConfigSpec.BooleanValue characterColour;
        public final ModConfigSpec.BooleanValue ambientSound;
        public final ModConfigSpec.DoubleValue intensity;
        public final ModConfigSpec.DoubleValue soundVolume;
        public final ModConfigSpec.BooleanValue soulMusic;
        public final ModConfigSpec.IntValue configVersion;

        public final ModConfigSpec.BooleanValue suppressionDistortion;
        public final ModConfigSpec.BooleanValue suppressionAudio;

        Client(ModConfigSpec.Builder builder)
        {
            builder.comment(
                            "How a soul dimension looks and sounds. Cosmetic, all of it: nothing under this",
                            "heading changes what a room is worth, what a buff does, or what anything costs.",
                            "A player who turns every one of these off loses nothing but the look.")
                    .push("ambience");

            this.ambienceEnabled = builder
                    .comment("Whether a soul answers its rank and its rooms at all. Off is the flat sky the mod shipped with.")
                    .define("enabled", true);

            this.rankVisuals = builder
                    .comment(
                            "Whether the sky opens and the firmament drifts further off as a soul ascends.",
                            "Never darkens anything and never brings fog inside the box you may build in.")
                    .define("rank_visuals", true);

            this.characterColour = builder
                    .comment(
                            "Whether the colour of the place answers what is built in it - warm for hearths and",
                            "forges, cold for ice and water, and something else again where a soul holds both.",
                            "Your soul is never sorted into a kind; nothing is ever named to you.")
                    .define("character_colour", true);

            this.ambientSound = builder
                    .comment(
                            "A quiet ambient bed, and occasional distant sounds minutes apart over it, both in",
                            "the Ambient/Environment sound category.",
                            "The bed is two long drifting loops with no seam and nothing in them to hum along to;",
                            "how far your soul has climbed mixes it from a small dry room toward a large airy one.",
                            "A one-shot comes from the direction of the room that earned it, and a soul that has",
                            "climbed throws them further out with a short tail behind them - larger, not louder.",
                            "Nothing here ever starts on top of the ascension hum, an ability or a soul key, and",
                            "the bed steps aside for all three.")
                    .define("ambient_sound", true);

            this.intensity = builder
                    .comment(
                            "How strongly all of the above is expressed, 0 to 1. Zero switches off every part of",
                            "it through one setting - which is the point of having it, for anyone who finds this",
                            "kind of thing uncomfortable and would rather not hunt for four switches.")
                    .defineInRange("intensity", AmbienceSettings.DEFAULT_INTENSITY, 0d, 1d);

            this.soundVolume = builder
                    .comment(
                            "Volume of the ambient bed and one-shots, 0 to 1, on top of your own",
                            "Ambient/Environment slider. The default sits under the game, and audibly above nothing.",
                            "This is the real mix control, not a stand-in for a dedicated options-screen slider:",
                            "SoundSource has no extensible-enum support on NeoForge either (#211), so there is no",
                            "vanilla category of this mod's own to put a slider under. Turning the soul down",
                            "without also turning down cave sounds and weather means this knob.")
                    .defineInRange("sound_volume", AmbienceSettings.DEFAULT_SOUND_VOLUME, 0d, 1d);

            this.soulMusic = builder
                    .comment(
                            "Whether a soul plays music of its own in place of Minecraft's while you are inside it.",
                            "It is composed as it plays, from what is built there: the rooms choose its mode and",
                            "its instruments, and how far the soul has climbed decides how much space is in it.",
                            "Under the game's own Music slider. Off gives you Minecraft's music back.")
                    .define("soul_music", true);

            this.configVersion = builder
                    .comment("Bookkeeping for carrying this file over a change of default. Leave it alone.")
                    .defineInRange("config_version", 1, 0, Integer.MAX_VALUE);

            builder.pop();

            builder.comment(
                            "How another player's ascension looks and sounds to you (#188). What their rank is",
                            "and how well you can read it are carried by the ring aura whatever you set here;",
                            "these only choose which of the other two channels also carry it.")
                    .push("suppression");

            this.suppressionDistortion = builder
                    .comment(
                            "Whether the air around a suppressed player warps the screen. Turn this off if screen",
                            "warping makes you uncomfortable - you lose nothing you would need to read them.")
                    .define("distortion", true);

            this.suppressionAudio = builder
                    .comment("Whether a suppressed player carries a low drone, with a beat per rank once you can read them.")
                    .define("audio", true);

            builder.pop();
        }
    }
}
