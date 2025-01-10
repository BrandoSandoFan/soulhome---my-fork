/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.properties;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public class PropTypes
{
    public static class Items
    {
        public static final Supplier<Item.Properties> ONE = () -> new Item.Properties().stacksTo(1);

        public static final Supplier<Item.Properties> SIXTEEN = () -> new Item.Properties().stacksTo(16);

        public static final Supplier<Item.Properties> SIXTY_FOUR = () -> new Item.Properties().stacksTo(64);
    }
}
