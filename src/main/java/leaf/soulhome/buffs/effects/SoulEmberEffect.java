/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Shrine: a fraction of what death would take from you stays behind as orbs at the spot you fell,
 * instead of being lost outright.
 *
 * <p>Adds to {@link LivingExperienceDropEvent#getDroppedExperience()} rather than reducing what
 * the world takes some other way - the event already carries exactly "the experience this death
 * would drop" and "the experience this death does drop" as two separate numbers, so raising the
 * second by a fraction of the first is the whole implementation. What happens to the orbs
 * afterwards - picked up by the player who died, by someone else, or by nobody at all - is the
 * same as any other dropped experience; a shrine buys back some of what was going to be lost, not
 * a guarantee of getting it back.
 */
public class SoulEmberEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SOUL_EMBER;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "experience kept on death that would otherwise be lost, as a fraction of it";
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final int original = event.getOriginalExperience();

        if (original <= 0)
        {
            return;
        }

        final int kept = (int) Math.round(original * Math.min(1d, magnitudeFor(player)));

        if (kept > 0)
        {
            event.setDroppedExperience(event.getDroppedExperience() + kept);
        }
    }
}
