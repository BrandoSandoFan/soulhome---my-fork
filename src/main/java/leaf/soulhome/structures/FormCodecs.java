/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import leaf.soulhome.structures.core.AllClause;
import leaf.soulhome.structures.core.AnyClause;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.ClauseParamSpec;
import leaf.soulhome.structures.core.ClauseParams;
import leaf.soulhome.structures.core.Form;
import leaf.soulhome.structures.core.FormClause;
import leaf.soulhome.structures.core.FormClauseRegistry;
import leaf.soulhome.structures.core.FormClauseType;
import leaf.soulhome.structures.core.UnknownClause;
import leaf.soulhome.structures.core.WeightedClause;
import leaf.soulhome.utils.LogHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Decodes and encodes one structural {@link Form}: named elements plus a clause tree of
 * {@code shape}, {@code relation}, {@code all} and {@code any} nodes. See the structural form
 * grammar epic (#27).
 *
 * <p>Hand-written rather than built from {@code RecordCodecBuilder}, for two reasons neither
 * combinator handles: the clause tree is recursive, and its leaves are an open, datapack-facing
 * vocabulary ({@link FormClauseRegistry}) dispatched on <i>which</i> of {@code "shape"}/
 * {@code "relation"}/{@code "all"}/{@code "any"} a clause object carries, rather than on a single
 * discriminator field the way {@code Codec.dispatch} expects.
 *
 * <p>An unknown or malformed clause is not a hard parse failure: it is logged and dropped, so one
 * datapack naming a mod's clause that is not installed - or getting a parameter wrong - does not
 * take a whole form, or a whole archetype, down with it. Inside an {@code any} a dropped clause
 * simply stops being one of the alternatives; inside an {@code all} it drops out of the weighted
 * mean entirely, rather than counting as a zero. A form left with no evaluable clauses is dropped
 * whole. This mirrors {@link ArchetypeJsonReader}'s parsing exactly (same {@link ClauseParamSpec}
 * per registered {@link FormClauseType}, same leniency) even though the two are necessarily
 * separate implementations - one against {@link DynamicOps}, so this codec also works over NBT for
 * {@code SyncArchetypesMessage}; one against Gson, since DataFixerUpper is only on the game's
 * classpath and the unit tests deliberately are not.
 */
final class FormCodecs
{
    /**
     * @param registry which {@code shape}/{@code relation} clause types this codec resolves
     *                 against - {@link ArchetypeCodecs#FORM} binds this to
     *                 {@link FormClauseRegistry#BUILTIN}, exactly as the production loader needs;
     *                 a test wanting its own fake clause types builds its own registry instead, so
     *                 one test's fakes can never leak into another's (or into {@code BUILTIN}).
     */
    static Codec<Form> forRegistry(FormClauseRegistry registry)
    {
        return new Codec<>()
        {
            @Override
            public <T> DataResult<Pair<Form, T>> decode(DynamicOps<T> ops, T input)
            {
                Optional<MapLike<T>> map = ops.getMap(input).result();

                if (map.isEmpty())
                {
                    return DataResult.error(() -> "A structural form must be an object");
                }

                MapLike<T> fields = map.get();
                Optional<String> name = readString(ops, fields, "name");

                if (name.isEmpty())
                {
                    return DataResult.error(() -> "A structural form is missing its 'name'");
                }

                final String formName = name.get();
                double weight = readDouble(ops, fields, "weight", 1.0);
                String role = readString(ops, fields, "role").orElse(null);
                Map<String, BlockMatcher> elements = readElements(ops, fields);

                Set<String> referenced = new LinkedHashSet<>();
                FormClause root = decodeClauseBody(ops, fields, registry, referenced);

                if (root == null || root instanceof UnknownClause)
                {
                    LogHelper.warn("Structural form '" + formName + "' has no evaluable clauses; dropped");
                    return DataResult.error(() -> "form '" + formName + "' has no evaluable clauses");
                }

                Form form = new Form(formName, weight, role, elements, root, referenced);
                return DataResult.success(Pair.of(form, input));
            }

            @Override
            public <T> DataResult<T> encode(Form form, DynamicOps<T> ops, T prefix)
            {
                Map<T, T> fields = new LinkedHashMap<>();
                fields.put(ops.createString("name"), ops.createString(form.name()));
                fields.put(ops.createString("weight"), ops.createDouble(form.weight()));
                fields.put(ops.createString("role"), ops.createString(form.role()));

                Map<T, T> elements = new LinkedHashMap<>();

                for (Map.Entry<String, BlockMatcher> entry : form.elements().entrySet())
                {
                    DataResult<T> encodedMatcher = ArchetypeCodecs.BLOCK_MATCHER.encodeStart(ops, entry.getValue());
                    encodedMatcher.result().ifPresent(value -> elements.put(ops.createString(entry.getKey()), value));
                }

                fields.put(ops.createString("elements"), ops.createMap(elements));
                encodeClauseInto(fields, form.root(), ops, registry);

                return DataResult.success(ops.createMap(fields));
            }
        };
    }

    private FormCodecs()
    {
    }

    // region decode

    /**
     * Whichever of {@code shape}/{@code relation}/{@code all}/{@code any} the object carries.
     * {@code null} means none of those keys are present at all - a structurally empty clause
     * object. A recognised-but-unresolvable clause comes back as {@link UnknownClause} instead, so
     * a caller can tell "nothing here" apart from "something here that did not work out" - the
     * distinction that keeps a genuinely malformed entry from being silently reinterpreted as a
     * different clause kind.
     */
    private static <T> FormClause decodeClauseBody(
            DynamicOps<T> ops, MapLike<T> fields, FormClauseRegistry registry, Set<String> referencedOut)
    {
        T shapeValue = fields.get("shape");

        if (shapeValue != null)
        {
            return decodeLeaf(ops, fields, shapeValue, FormClauseType.Kind.SHAPE, registry, referencedOut);
        }

        T relationValue = fields.get("relation");

        if (relationValue != null)
        {
            return decodeLeaf(ops, fields, relationValue, FormClauseType.Kind.RELATION, registry, referencedOut);
        }

        T allValue = fields.get("all");

        if (allValue != null)
        {
            return decodeComposite(ops, allValue, registry, referencedOut, true);
        }

        T anyValue = fields.get("any");

        if (anyValue != null)
        {
            return decodeComposite(ops, anyValue, registry, referencedOut, false);
        }

        return null;
    }

    private static <T> FormClause decodeComposite(
            DynamicOps<T> ops, T listValue, FormClauseRegistry registry, Set<String> referencedOut, boolean all)
    {
        Optional<Stream<T>> stream = ops.getStream(listValue).result();

        if (stream.isEmpty())
        {
            LogHelper.warn("Soulhome structural '" + (all ? "all" : "any") + "' must be a list; skipped");
            return null;
        }

        List<WeightedClause> children = new ArrayList<>();

        for (T entryValue : stream.get().toList())
        {
            Optional<MapLike<T>> entryFields = ops.getMap(entryValue).result();

            if (entryFields.isEmpty())
            {
                LogHelper.warn("Soulhome structural clause entry is not an object; skipped");
                continue;
            }

            double weight = readDouble(ops, entryFields.get(), "weight", 1.0);
            FormClause clause = decodeClauseBody(ops, entryFields.get(), registry, referencedOut);

            if (clause == null)
            {
                LogHelper.warn("Soulhome structural clause has none of 'shape', 'relation', 'all' or 'any'; skipped");
                continue;
            }

            if (clause instanceof UnknownClause unknown)
            {
                LogHelper.warn("Soulhome structural clause '" + unknown.rawId() + "' is unknown or invalid; skipped");
                continue;
            }

            children.add(new WeightedClause(weight, clause));
        }

        if (children.isEmpty())
        {
            return null;
        }

        return all ? new AllClause(children) : new AnyClause(children);
    }

    private static <T> FormClause decodeLeaf(
            DynamicOps<T> ops, MapLike<T> fields, T idValue, FormClauseType.Kind kind,
            FormClauseRegistry registry, Set<String> referencedOut)
    {
        Optional<String> idResult = Codec.STRING.parse(ops, idValue).result();

        if (idResult.isEmpty())
        {
            LogHelper.warn("Soulhome structural clause's '" + (kind == FormClauseType.Kind.SHAPE ? "shape" : "relation")
                    + "' must be a string; skipped");
            return new UnknownClause("?");
        }

        String id = idResult.get();
        Optional<FormClauseType> type = registry.get(kind, id);

        if (type.isEmpty())
        {
            LogHelper.warn("Soulhome structural clause '" + id + "' is not a known clause type; skipped");
            return new UnknownClause(id);
        }

        try
        {
            ClauseParams.Builder builder = ClauseParams.builder();

            for (ClauseParamSpec spec : type.get().params())
            {
                T rawValue = fields.get(spec.name());

                if (rawValue == null)
                {
                    if (spec.required())
                    {
                        LogHelper.warn("Soulhome structural clause '" + id + "' is missing required parameter '"
                                + spec.name() + "'; skipped");
                        return new UnknownClause(id);
                    }

                    builder.put(spec.name(), spec.defaultValue());
                    continue;
                }

                Object value = readTyped(ops, rawValue, spec);
                builder.put(spec.name(), value);

                if (spec.type() == ClauseParamSpec.Type.ELEMENT)
                {
                    referencedOut.add((String) value);
                }
            }

            return type.get().create(builder.build());
        }
        catch (RuntimeException exception)
        {
            LogHelper.warn("Soulhome structural clause '" + id + "' failed to parse (" + exception.getMessage()
                    + "); skipped");
            return new UnknownClause(id);
        }
    }

    private static <T> Object readTyped(DynamicOps<T> ops, T rawValue, ClauseParamSpec spec)
    {
        Codec<?> codec = switch (spec.type())
        {
            case DOUBLE -> Codec.DOUBLE;
            case INT -> Codec.INT;
            case STRING, ELEMENT -> Codec.STRING;
        };

        return codec.parse(ops, rawValue).result()
                .orElseThrow(() -> new IllegalArgumentException("'" + spec.name() + "' has the wrong type"));
    }

    private static <T> Map<String, BlockMatcher> readElements(DynamicOps<T> ops, MapLike<T> fields)
    {
        T rawElements = fields.get("elements");

        if (rawElements == null)
        {
            return Map.of();
        }

        Optional<MapLike<T>> elementsMap = ops.getMap(rawElements).result();

        if (elementsMap.isEmpty())
        {
            return Map.of();
        }

        Map<String, BlockMatcher> elements = new LinkedHashMap<>();

        for (Pair<T, T> entry : elementsMap.get().entries().toList())
        {
            Optional<String> key = Codec.STRING.parse(ops, entry.getFirst()).result();
            Optional<BlockMatcher> matcher = ArchetypeCodecs.BLOCK_MATCHER.parse(ops, entry.getSecond()).result();

            if (key.isPresent() && matcher.isPresent())
            {
                elements.put(key.get(), matcher.get());
            }
        }

        return elements;
    }

    private static <T> Optional<String> readString(DynamicOps<T> ops, MapLike<T> fields, String key)
    {
        T rawValue = fields.get(key);
        return rawValue == null ? Optional.empty() : Codec.STRING.parse(ops, rawValue).result();
    }

    private static <T> double readDouble(DynamicOps<T> ops, MapLike<T> fields, String key, double defaultValue)
    {
        T rawValue = fields.get(key);
        return rawValue == null ? defaultValue : Codec.DOUBLE.parse(ops, rawValue).result().orElse(defaultValue);
    }

    // endregion

    // region encode

    private static <T> void encodeClauseInto(Map<T, T> fields, FormClause clause, DynamicOps<T> ops, FormClauseRegistry registry)
    {
        if (clause instanceof AllClause all)
        {
            fields.put(ops.createString("all"), encodeWeightedList(all.children(), ops, registry));
            return;
        }

        if (clause instanceof AnyClause any)
        {
            fields.put(ops.createString("any"), encodeWeightedList(any.children(), ops, registry));
            return;
        }

        encodeLeafInto(fields, clause, ops, registry);
    }

    private static <T> T encodeWeightedList(List<WeightedClause> children, DynamicOps<T> ops, FormClauseRegistry registry)
    {
        List<T> encoded = new ArrayList<>(children.size());

        for (WeightedClause child : children)
        {
            Map<T, T> entry = new LinkedHashMap<>();
            entry.put(ops.createString("weight"), ops.createDouble(child.weight()));
            encodeClauseInto(entry, child.clause(), ops, registry);
            encoded.add(ops.createMap(entry));
        }

        return ops.createList(encoded.stream());
    }

    private static <T> void encodeLeafInto(Map<T, T> fields, FormClause clause, DynamicOps<T> ops, FormClauseRegistry registry)
    {
        Optional<FormClauseType> type = registry.findById(clause.typeId());

        if (type.isEmpty())
        {
            // an UnknownClause, or a clause type that has since been unregistered - either way
            // there is nothing left to encode it back into
            return;
        }

        String field = type.get().kind() == FormClauseType.Kind.SHAPE ? "shape" : "relation";
        fields.put(ops.createString(field), ops.createString(type.get().id()));

        Map<String, Object> values = type.get().encode(clause);

        for (ClauseParamSpec spec : type.get().params())
        {
            Object value = values.get(spec.name());

            if (value == null)
            {
                continue;
            }

            T encoded = switch (spec.type())
            {
                case DOUBLE -> ops.createDouble(((Number) value).doubleValue());
                case INT -> ops.createInt(((Number) value).intValue());
                case STRING, ELEMENT -> ops.createString((String) value);
            };

            fields.put(ops.createString(spec.name()), encoded);
        }
    }

    // endregion
}
