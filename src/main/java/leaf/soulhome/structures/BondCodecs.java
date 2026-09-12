/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import leaf.soulhome.structures.core.Bond;
import leaf.soulhome.structures.core.BondRelation;
import leaf.soulhome.structures.core.BondRelationRegistry;
import leaf.soulhome.structures.core.ClauseParamSpec;
import leaf.soulhome.structures.core.ClauseParams;
import leaf.soulhome.utils.LogHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decodes and encodes an archetype's {@code bonds} list - #143 of the Soul Architecture epic.
 *
 * <p>Hand-written for the same reason {@link FormCodecs} is: a bond's parameters depend on which
 * {@link BondRelation} it names, read generically from that relation's {@link ClauseParamSpec}s so
 * a relation a mod adds round-trips with no codec of its own. And lenient for the same reason: a
 * bond naming a relation nothing registered, or getting a parameter wrong, is logged and dropped
 * rather than failing the archetype - one bad bond must not take a whole room down with it.
 * Mirrors {@code ArchetypeJsonReader#readBond} in the Minecraft-free test suite.
 */
final class BondCodecs
{
    private BondCodecs()
    {
    }

    /** The whole list, so one bad entry can be skipped rather than failing the list. */
    static Codec<List<Bond>> listForRegistry(BondRelationRegistry registry)
    {
        return new Codec<>()
        {
            @Override
            public <T> DataResult<Pair<List<Bond>, T>> decode(DynamicOps<T> ops, T input)
            {
                Optional<Stream<T>> stream = ops.getStream(input).result();

                if (stream.isEmpty())
                {
                    return DataResult.error(() -> "'bonds' must be a list");
                }

                List<Bond> bonds = new ArrayList<>();

                for (T entry : stream.get().toList())
                {
                    decodeOne(ops, entry, registry).ifPresent(bonds::add);
                }

                return DataResult.success(Pair.of(List.copyOf(bonds), input));
            }

            @Override
            public <T> DataResult<T> encode(List<Bond> bonds, DynamicOps<T> ops, T prefix)
            {
                List<T> encoded = new ArrayList<>(bonds.size());

                for (Bond bond : bonds)
                {
                    encoded.add(encodeOne(bond, ops, registry));
                }

                return DataResult.success(ops.createList(encoded.stream()));
            }
        };
    }

    private static <T> Optional<Bond> decodeOne(DynamicOps<T> ops, T input, BondRelationRegistry registry)
    {
        Optional<MapLike<T>> map = ops.getMap(input).result();

        if (map.isEmpty())
        {
            LogHelper.warn("Soulhome bond is not an object; skipped");
            return Optional.empty();
        }

        MapLike<T> fields = map.get();
        Optional<String> with = readString(ops, fields, "with");
        Optional<String> relationId = readString(ops, fields, "relation").map(id -> id.toLowerCase(Locale.ROOT));
        Optional<Double> weight = readDouble(ops, fields, "weight");

        if (with.isEmpty() || relationId.isEmpty() || weight.isEmpty())
        {
            LogHelper.warn("Soulhome bond needs 'with', 'relation' and 'weight'; skipped");
            return Optional.empty();
        }

        Optional<BondRelation> relation = registry.get(relationId.get());

        if (relation.isEmpty())
        {
            LogHelper.warn("Soulhome bond with " + with.get() + " names relation '" + relationId.get()
                    + "', which is not registered; skipped");
            return Optional.empty();
        }

        ClauseParams.Builder params = ClauseParams.builder();

        for (ClauseParamSpec spec : relation.get().params())
        {
            T raw = fields.get(spec.name());

            if (raw == null)
            {
                if (spec.required())
                {
                    LogHelper.warn("Soulhome bond with " + with.get() + " by '" + relationId.get()
                            + "' is missing required parameter '" + spec.name() + "'; skipped");
                    return Optional.empty();
                }

                params.put(spec.name(), spec.defaultValue());
                continue;
            }

            Codec<?> codec = switch (spec.type())
            {
                case DOUBLE -> Codec.DOUBLE;
                case INT -> Codec.INT;
                case STRING, ELEMENT -> Codec.STRING;
            };

            Optional<?> value = codec.parse(ops, raw).result();

            if (value.isEmpty())
            {
                LogHelper.warn("Soulhome bond with " + with.get() + " by '" + relationId.get()
                        + "' has the wrong type for '" + spec.name() + "'; skipped");
                return Optional.empty();
            }

            params.put(spec.name(), value.get());
        }

        return Optional.of(new Bond(
                with.get().toLowerCase(Locale.ROOT),
                relationId.get(),
                weight.get(),
                readString(ops, fields, "role").orElse(Bond.DEFAULT_ROLE),
                params.build()));
    }

    private static <T> T encodeOne(Bond bond, DynamicOps<T> ops, BondRelationRegistry registry)
    {
        Map<T, T> fields = new LinkedHashMap<>();
        fields.put(ops.createString("with"), ops.createString(bond.with()));
        fields.put(ops.createString("relation"), ops.createString(bond.relation()));
        fields.put(ops.createString("weight"), ops.createDouble(bond.weight()));
        fields.put(ops.createString("role"), ops.createString(bond.role()));

        registry.get(bond.relation()).ifPresent(relation ->
        {
            for (ClauseParamSpec spec : relation.params())
            {
                T encoded = switch (spec.type())
                {
                    case DOUBLE -> ops.createDouble(bond.params().getDouble(spec.name()));
                    case INT -> ops.createInt(bond.params().getInt(spec.name()));
                    case STRING, ELEMENT -> ops.createString(bond.params().getString(spec.name()));
                };

                fields.put(ops.createString(spec.name()), encoded);
            }
        });

        return ops.createMap(fields);
    }

    private static <T> Optional<String> readString(DynamicOps<T> ops, MapLike<T> fields, String key)
    {
        T raw = fields.get(key);
        return raw == null ? Optional.empty() : Codec.STRING.parse(ops, raw).result();
    }

    private static <T> Optional<Double> readDouble(DynamicOps<T> ops, MapLike<T> fields, String key)
    {
        T raw = fields.get(key);
        return raw == null ? Optional.empty() : Codec.DOUBLE.parse(ops, raw).result();
    }
}
