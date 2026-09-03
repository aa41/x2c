package dev.x2c.compiler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/** Dependency-free class-file verifier for contracts that cannot survive a resource-free JAR. */
public final class ClassContractVerifier {
    private static final int CLASS_MAGIC = 0xCAFEBABE;

    public void verify(String entryName, byte[] bytes) {
        verify(entryName, bytes, false);
    }

    /** Normal integration permits only getIdentifier; all other strict resource checks remain. */
    public void verify(String entryName, byte[] bytes, boolean allowDynamicIdentifier) {
        try {
            ConstantPool pool = readConstantPool(bytes);
            verifyClassReferences(entryName, pool);
            verifyMethodReferences(entryName, pool, allowDynamicIdentifier);
        } catch (IOException | RuntimeException error) {
            if (error instanceof ResourceCompilationException) {
                throw (ResourceCompilationException) error;
            }
            throw failure(entryName, "Cannot inspect class file", error);
        }
    }

    private static void verifyClassReferences(String entryName, ConstantPool pool) {
        for (int index = 1; index < pool.tags.length; index++) {
            if (pool.tags[index] != 7) {
                continue;
            }
            String className = pool.className(index);
            if (isForbiddenRClass(className)) {
                throw failure(entryName, "Android resource class reference is forbidden: "
                        + className.replace('/', '.'));
            }
            if (className.equals("android/content/res/TypedArray")) {
                throw failure(entryName,
                        "TypedArray/styleable access is forbidden in strict resource-free JAR mode");
            }
        }
    }

    private static void verifyMethodReferences(
            String entryName, ConstantPool pool, boolean allowDynamicIdentifier) {
        for (int index = 1; index < pool.tags.length; index++) {
            int tag = pool.tags[index];
            if (tag != 10 && tag != 11) {
                continue;
            }
            int[] reference = pool.pair(index);
            String owner = pool.className(reference[0]);
            int[] nameAndType = pool.pair(reference[1]);
            String method = pool.utf8(nameAndType[0]);
            if (owner.equals("android/content/res/Resources")
                    && ((!allowDynamicIdentifier && method.equals("getIdentifier"))
                    || method.equals("obtainAttributes"))) {
                throw failure(entryName, "Dynamic/styleable resource API is forbidden: "
                        + owner.replace('/', '.') + "." + method);
            }
            if ((owner.equals("android/content/Context")
                    || owner.equals("android/content/res/Resources$Theme"))
                    && method.equals("obtainStyledAttributes")) {
                throw failure(entryName, "Theme/styleable API is forbidden: "
                        + owner.replace('/', '.') + "." + method);
            }
        }
    }

    private static boolean isForbiddenRClass(String className) {
        if (className.equals("android/R") || className.startsWith("android/R$")) {
            return false;
        }
        int separator = className.lastIndexOf('/');
        String simpleName = separator < 0 ? className : className.substring(separator + 1);
        return simpleName.equals("R") || simpleName.startsWith("R$");
    }

    private static ConstantPool readConstantPool(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != CLASS_MAGIC) {
                throw new IOException("Invalid class magic");
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            int[] tags = new int[count];
            Object[] values = new Object[count];
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                tags[index] = tag;
                switch (tag) {
                    case 1:
                        values[index] = input.readUTF();
                        break;
                    case 3:
                    case 4:
                        skipFully(input, 4);
                        break;
                    case 5:
                    case 6:
                        skipFully(input, 8);
                        index++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        values[index] = input.readUnsignedShort();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        values[index] = new int[] {
                                input.readUnsignedShort(), input.readUnsignedShort()};
                        break;
                    case 15:
                        input.readUnsignedByte();
                        input.readUnsignedShort();
                        break;
                    default:
                        throw new IOException("Unsupported constant-pool tag " + tag);
                }
            }
            return new ConstantPool(tags, values);
        } catch (EOFException error) {
            throw new IOException("Truncated class file", error);
        }
    }

    private static void skipFully(DataInputStream input, int byteCount) throws IOException {
        for (int index = 0; index < byteCount; index++) {
            input.readUnsignedByte();
        }
    }

    private static ResourceCompilationException failure(String entryName, String message) {
        return new ResourceCompilationException(entryName + ": " + message);
    }

    private static ResourceCompilationException failure(String entryName, String message, Throwable cause) {
        return new ResourceCompilationException(entryName + ": " + message, cause);
    }

    private static final class ConstantPool {
        private final int[] tags;
        private final Object[] values;

        private ConstantPool(int[] tags, Object[] values) {
            this.tags = tags;
            this.values = values;
        }

        private String utf8(int index) {
            if (index <= 0 || index >= tags.length || tags[index] != 1) {
                throw new IllegalArgumentException("Expected UTF-8 constant at " + index);
            }
            return (String) values[index];
        }

        private int[] pair(int index) {
            if (index <= 0 || index >= tags.length || !(values[index] instanceof int[])) {
                throw new IllegalArgumentException("Expected constant-pool pair at " + index);
            }
            return (int[]) values[index];
        }

        private String className(int index) {
            if (index <= 0 || index >= tags.length || tags[index] != 7) {
                throw new IllegalArgumentException("Expected class constant at " + index);
            }
            return utf8((Integer) values[index]);
        }
    }
}
