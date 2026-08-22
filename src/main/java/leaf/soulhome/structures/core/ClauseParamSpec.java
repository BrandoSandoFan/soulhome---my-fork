/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * One parameter a {@link FormClauseType} reads out of its clause's JSON object - name, type,
 * and whether a default applies when the datapack leaves it out.
 *
 * <p>Declared once per clause type and read generically by both the production codec (DataFixerUpper,
 * against JSON or NBT) and the Gson test reader, so a clause's parameter shape cannot drift between
 * the two the way a hand-mirrored field list could. See the structural form grammar epic (#27).
 *
 * @param name         the JSON key
 * @param type         how the raw value should be read
 * @param required     whether the datapack must supply this parameter
 * @param defaultValue used when {@code required} is false and the datapack omits it - must not be
 *                      {@code null}, since the parameter bag this feeds cannot hold a null value
 */
public record ClauseParamSpec(String name, Type type, boolean required, Object defaultValue)
{
    /** How a parameter's raw value should be read and, for {@link #ELEMENT}, what it means. */
    public enum Type
    {
        STRING,
        DOUBLE,
        INT,

        /**
         * A string naming one of the form's declared elements, e.g. {@code "of": "rails"}. Read no
         * differently from {@link #STRING} at parse time - a clause referring to an element that
         * turns out not to be declared is a validation error, not a parse failure, so a form
         * author sees "which element" rather than a silently dropped clause. See
         * {@link Form#validationErrors()}.
         */
        ELEMENT
    }

    public static ClauseParamSpec required(String name, Type type)
    {
        return new ClauseParamSpec(name, type, true, null);
    }

    public static ClauseParamSpec optional(String name, Type type, Object defaultValue)
    {
        if (defaultValue == null)
        {
            throw new IllegalArgumentException(
                    "An optional clause parameter needs a non-null default - '" + name + "' has none");
        }

        return new ClauseParamSpec(name, type, false, defaultValue);
    }
}
