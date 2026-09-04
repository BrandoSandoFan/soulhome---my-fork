/*
 * File created ~ 13 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class StringHelper
{
    public static String fixCapitalisation(String text)
    {
        String original = text.trim().replace("    ", "").replace("_", " ").replace("/", ".");
        //an empty token has no first letter to raise. Blank input reaches here from a block id an
        //archetype names but no mod supplies, by way of BlockNames, and used to throw out of a
        //report rather than print the plain id it is meant to fall back to.
        String output = Arrays.stream(original.split("\\s+")).map(t -> t.isEmpty() ? t : t.substring(0, 1).toUpperCase() + t.substring(1)).collect(Collectors.joining(" "));
        return output;
    }

    //Basically the opposite, make string suitable for path
    public static String fixPath(String text)
    {
        String output = text.trim().toLowerCase(Locale.ROOT).replace("    ", "").replace(" ", "_").replace("/", ".");
        return output;
    }
}
