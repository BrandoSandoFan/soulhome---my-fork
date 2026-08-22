/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parameter values a datapack supplied for one clause, already read according to its
 * {@link FormClauseType#params()} and with defaults filled in - the neutral bag a
 * {@link FormClauseType#create} turns into a typed {@link FormClause}.
 *
 * <p>Deliberately untyped storage: the point of this class is to be the one thing both the
 * production codec and the Gson test reader build, from their own JSON access, without either
 * needing to know the shape of any particular clause beyond its {@link ClauseParamSpec} list.
 */
public final class ClauseParams
{
    private final Map<String, Object> values;

    private ClauseParams(Map<String, Object> values)
    {
        this.values = values;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String getString(String name)
    {
        return (String) value(name);
    }

    public double getDouble(String name)
    {
        return ((Number) value(name)).doubleValue();
    }

    public int getInt(String name)
    {
        return ((Number) value(name)).intValue();
    }

    /** Same underlying storage as {@link #getString} - kept distinct so a clause reads its intent. */
    public String getElement(String name)
    {
        return (String) value(name);
    }

    private Object value(String name)
    {
        if (!this.values.containsKey(name))
        {
            throw new IllegalStateException("No clause parameter named '" + name
                    + "' - every ClauseParamSpec a FormClauseType declares should have been filled in"
                    + " by the parser before create() is ever called");
        }

        return this.values.get(name);
    }

    public static final class Builder
    {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String name, Object value)
        {
            this.values.put(name, value);
            return this;
        }

        public ClauseParams build()
        {
            return new ClauseParams(Map.copyOf(this.values));
        }
    }
}
