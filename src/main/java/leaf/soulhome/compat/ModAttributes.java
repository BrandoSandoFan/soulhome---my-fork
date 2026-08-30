/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Another mod's attribute, by name, without ever mentioning its classes.
 *
 * <p>This is the whole of the trick behind buffs like {@code soulhome:max_mana}: a mana buff has
 * to add to Iron's Spells' {@code irons_spellbooks:max_mana} attribute, but this mod does not
 * depend on Iron's Spells and must load, classify rooms and apply every other buff perfectly well
 * on a server that has never heard of it. Referring to {@code AttributeRegistry.MAX_MANA} would
 * put that class in the constant pool, and the first time anything touched the effect the JVM
 * would throw {@code NoClassDefFoundError}. A registry lookup by {@link ResourceLocation} cannot:
 * the worst case is an empty {@link Optional} and an effect that quietly does nothing.
 *
 * <p>The same route works for attributes that are always present - Forge's own reach attributes
 * go through here too - so there is one way of doing this rather than two.
 *
 * <h2>When to call</h2>
 *
 * Not before attribute registration. Results are cached, including misses, because a lookup that
 * failed once will fail every tick afterwards and there is no point paying for it each time; the
 * flip side is that a call made too early would cache "not installed" for the life of the game.
 * Everything here is called from a tick or from common setup, both of which are long after
 * registries are filled.
 */
public final class ModAttributes
{
    private static final Map<String, Optional<Attribute>> CACHE = new ConcurrentHashMap<>();

    private ModAttributes()
    {
    }

    /**
     * @param id a namespaced attribute id, e.g. {@code irons_spellbooks:max_mana}
     * @return empty if the mod that registers it is not installed, or never registered it
     */
    public static Optional<Attribute> find(String id)
    {
        return CACHE.computeIfAbsent(id, key ->
        {
            final ResourceLocation location = ResourceLocation.tryParse(key);

            return location == null
                   ? Optional.empty()
                   : Optional.ofNullable(ForgeRegistries.ATTRIBUTES.getValue(location));
        });
    }
}
