/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Just enough of the NBT format to read a structure template, with no Minecraft on the classpath.
 *
 * <p>The shipped starter islands are {@code StructureTemplate} NBT, and the regression corpus
 * (#139) has to read them from the Minecraft-free test suite - the whole point of the corpus is
 * that it runs under the offline {@code javac} + JUnit path, where the faults it exists to catch
 * would have been caught on day one rather than after a full ForgeGradle setup. NBT is a small,
 * stable binary format; reading it here costs less than a fixture format of our own would, and it
 * means the corpus reads the very file the game places, so there is nothing to regenerate when a
 * template changes.
 *
 * <p>Tags come back as plain Java: a compound is a {@code Map<String, Object>}, a list a
 * {@code List<Object>}, numbers their boxed primitives, arrays their primitive arrays.
 */
final class NbtReader
{
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private NbtReader()
    {
    }

    /** The root compound of a gzip-compressed NBT file, as the game writes structure templates. */
    static Map<String, Object> readCompressed(InputStream stream) throws IOException
    {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(stream)))
        {
            final int type = input.readUnsignedByte();

            if (type != TAG_COMPOUND)
            {
                throw new IOException("Expected a compound at the root, found tag type " + type);
            }

            input.readUTF(); // the root's name, conventionally empty

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) readPayload(input, TAG_COMPOUND);
            return root;
        }
    }

    private static Object readPayload(DataInputStream input, int type) throws IOException
    {
        switch (type)
        {
            case TAG_BYTE:
                return input.readByte();
            case TAG_SHORT:
                return input.readShort();
            case TAG_INT:
                return input.readInt();
            case TAG_LONG:
                return input.readLong();
            case TAG_FLOAT:
                return input.readFloat();
            case TAG_DOUBLE:
                return input.readDouble();
            case TAG_BYTE_ARRAY:
            {
                byte[] values = new byte[input.readInt()];
                input.readFully(values);
                return values;
            }
            case TAG_STRING:
                return input.readUTF();
            case TAG_LIST:
            {
                final int elementType = input.readUnsignedByte();
                final int length = input.readInt();
                List<Object> values = new ArrayList<>(Math.max(0, length));

                for (int i = 0; i < length; i++)
                {
                    values.add(readPayload(input, elementType));
                }

                return values;
            }
            case TAG_COMPOUND:
            {
                Map<String, Object> values = new LinkedHashMap<>();

                while (true)
                {
                    final int elementType = input.readUnsignedByte();

                    if (elementType == TAG_END)
                    {
                        return values;
                    }

                    final String name = input.readUTF();
                    values.put(name, readPayload(input, elementType));
                }
            }
            case TAG_INT_ARRAY:
            {
                int[] values = new int[input.readInt()];

                for (int i = 0; i < values.length; i++)
                {
                    values[i] = input.readInt();
                }

                return values;
            }
            case TAG_LONG_ARRAY:
            {
                long[] values = new long[input.readInt()];

                for (int i = 0; i < values.length; i++)
                {
                    values[i] = input.readLong();
                }

                return values;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }
}
