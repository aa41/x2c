package dev.x2c.plugin.compiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Builds the deterministic, resource-free class closure transformed and shipped in one plugin. */
final class PluginDependencyClosure {
    private PluginDependencyClosure() {}

    static Result read(
            File primaryJar,
            Collection<File> dependencyArtifacts,
            Collection<File> compileOnlyArtifacts) throws IOException {
        if (primaryJar == null || !primaryJar.isFile()) {
            throw new IllegalArgumentException("Primary plugin JAR does not exist: " + primaryJar);
        }
        Result result = new Result();
        readJarFile(primaryJar, "plugin module", false, result, null);

        List<File> sorted = new ArrayList<File>();
        if (dependencyArtifacts != null) sorted.addAll(dependencyArtifacts);
        Collections.sort(sorted, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return stablePath(left).compareTo(stablePath(right));
            }
        });
        String primaryPath = stablePath(primaryJar);
        for (File artifact : sorted) {
            if (primaryPath.equals(stablePath(artifact))) continue;
            readDependency(artifact, result);
        }

        List<CompileOnlyBaseClosure.SelectedArtifact> selected =
                CompileOnlyBaseClosure.select(compileOnlyArtifacts, result.classes);
        for (CompileOnlyBaseClosure.SelectedArtifact artifact : selected) {
            TreeMap<String, byte[]> artifactClasses = new TreeMap<String, byte[]>();
            for (Map.Entry<String, byte[]> entry : artifact.classes.entrySet()) {
                putClass(entry.getKey(), entry.getValue(), artifact.displayName, true,
                        result, artifactClasses);
            }
            result.dependencies.add(new PackagedDependency(
                    artifact.displayName,
                    artifact.type,
                    artifactClasses.size(),
                    digest(artifactClasses)));
        }
        Collections.sort(result.dependencies);
        result.dependencyClosureSha256 = digest(result.dependencyClasses);
        return result;
    }

    private static void readDependency(File artifact, Result result) throws IOException {
        if (artifact == null || !artifact.exists()) {
            throw new IllegalStateException("Resolved plugin dependency does not exist: " + artifact);
        }
        TreeMap<String, byte[]> artifactClasses = new TreeMap<String, byte[]>();
        String type;
        if (artifact.isDirectory()) {
            type = "directory";
            readDirectory(artifact, result, artifactClasses);
        } else if (artifact.getName().endsWith(".aar")) {
            type = "aar";
            readAar(artifact, result, artifactClasses);
        } else if (artifact.getName().endsWith(".jar") || isZip(artifact)) {
            type = "jar";
            readJarFile(artifact, artifact.getName(), true, result, artifactClasses);
        } else {
            throw new IllegalStateException(
                    "Unsupported plugin runtime dependency artifact (expected JAR/AAR/classes "
                            + "directory): " + artifact.getName());
        }
        result.dependencies.add(new PackagedDependency(
                displayName(artifact), type, artifactClasses.size(), digest(artifactClasses)));
    }

    private static void readDirectory(
            File directory, Result result, Map<String, byte[]> artifactClasses)
            throws IOException {
        final Path root = directory.toPath();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> {
                        String entry = root.relativize(path).toString()
                                .replace(File.separatorChar, '/');
                        try {
                            putClass(entry, Files.readAllBytes(path), directory.getName(), true,
                                    result, artifactClasses);
                        } catch (IOException error) {
                            throw new DependencyReadException(error);
                        }
                    });
        } catch (DependencyReadException error) {
            throw error.cause;
        }
    }

    private static void readAar(
            File artifact, Result result, Map<String, byte[]> artifactClasses)
            throws IOException {
        boolean foundClasses = false;
        try (JarFile aar = new JarFile(artifact)) {
            List<JarEntry> entries = Collections.list(aar.entries());
            Collections.sort(entries, new Comparator<JarEntry>() {
                @Override public int compare(JarEntry left, JarEntry right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (JarEntry entry : entries) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (isAndroidPayload(name)) {
                    throw new IllegalStateException(
                            "First-stage dependency closure only accepts resource-free AARs; "
                                    + artifact.getName() + " contains " + name);
                }
                if ("classes.jar".equals(name)
                        || (name.startsWith("libs/") && name.endsWith(".jar"))) {
                    foundClasses = true;
                    try (InputStream input = new BufferedInputStream(aar.getInputStream(entry))) {
                        readNestedJar(input, artifact.getName() + "!/" + name,
                                result, artifactClasses);
                    }
                }
            }
        }
        if (!foundClasses) {
            throw new IllegalStateException(
                    "Plugin dependency AAR has no classes.jar: " + artifact.getName());
        }
    }

    private static boolean isAndroidPayload(String name) {
        return name.startsWith("res/")
                || name.startsWith("assets/")
                || name.startsWith("jni/")
                || name.startsWith("prefab/");
    }

    private static void readNestedJar(
            InputStream input,
            String origin,
            Result result,
            Map<String, byte[]> artifactClasses) throws IOException {
        try (JarInputStream jar = new JarInputStream(input)) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                if (!entry.isDirectory() && isClassEntry(entry.getName())) {
                    putClass(entry.getName(), read(jar), origin, true, result, artifactClasses);
                }
            }
        }
    }

    private static void readJarFile(
            File artifact,
            String origin,
            boolean dependency,
            Result result,
            Map<String, byte[]> artifactClasses) throws IOException {
        try (JarFile jar = new JarFile(artifact)) {
            List<JarEntry> entries = Collections.list(jar.entries());
            Collections.sort(entries, new Comparator<JarEntry>() {
                @Override public int compare(JarEntry left, JarEntry right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (JarEntry entry : entries) {
                if (entry.isDirectory() || !isClassEntry(entry.getName())) continue;
                try (InputStream input = new BufferedInputStream(jar.getInputStream(entry))) {
                    putClass(entry.getName(), read(input), origin, dependency,
                            result, artifactClasses);
                }
            }
        }
    }

    private static boolean isClassEntry(String name) {
        return name.endsWith(".class")
                && !name.startsWith("META-INF/versions/")
                && !"module-info.class".equals(name);
    }

    private static void putClass(
            String entry,
            byte[] bytes,
            String origin,
            boolean dependency,
            Result result,
            Map<String, byte[]> artifactClasses) {
        ClassReader reader;
        try {
            reader = new ClassReader(bytes);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Invalid class " + entry + " from " + origin, error);
        }
        String expectedEntry = reader.getClassName() + ".class";
        if (!expectedEntry.equals(entry)) {
            throw new IllegalStateException("Class entry/name mismatch in " + origin + ": "
                    + entry + " contains " + expectedEntry);
        }
        if (dependency) validateDependencyClass(entry, bytes, origin);
        String previousOrigin = result.origins.putIfAbsent(entry, origin);
        if (previousOrigin != null) {
            throw new IllegalStateException("Duplicate plugin-private class "
                    + entry.substring(0, entry.length() - 6).replace('/', '.')
                    + " from " + previousOrigin + " and " + origin);
        }
        result.classes.put(entry, bytes);
        if (artifactClasses != null) {
            artifactClasses.put(entry, bytes);
            result.dependencyClasses.put(entry, bytes);
        }
    }

    private static void validateDependencyClass(
            final String entry, byte[] bytes, final String origin) {
        final String className = entry.substring(0, entry.length() - 6);
        if (isHostOwned(className)) {
            throw new IllegalStateException(
                    "Host-owned X2C API/runtime/base class must not be packaged in a plugin "
                            + "dependency; use compileOnly: " + className.replace('/', '.'));
        }
        if (isPlatformClass(className)) {
            throw new IllegalStateException("Plugin dependency attempts to package a platform "
                    + "class: " + className.replace('/', '.') + " from " + origin);
        }
        if (isDeferredAndroidX(className)) {
            throw new IllegalStateException(
                    "First-stage dependency closure does not support AndroidX/AppCompat/Material "
                            + "classes: " + className.replace('/', '.') + " from " + origin);
        }
        if (isRClass(className)) {
            throw new IllegalStateException("Android resource class is forbidden in the "
                    + "resource-free plugin closure: " + className.replace('/', '.'));
        }

        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                rejectType(superName, entry);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) rejectType(interfaceName, entry);
                }
            }

            @Override public FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                rejectDescriptor(descriptor, entry);
                return null;
            }

            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if ((access & Opcodes.ACC_NATIVE) != 0) {
                    throw new IllegalStateException("Native method is forbidden in the "
                            + "class-only plugin closure: " + className.replace('/', '.')
                            + '.' + name + descriptor);
                }
                rejectMethodDescriptor(descriptor, entry);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        rejectType(type, entry);
                    }

                    @Override public void visitFieldInsn(
                            int opcode, String owner, String name, String descriptor) {
                        rejectType(owner, entry);
                        rejectDescriptor(descriptor, entry);
                    }

                    @Override public void visitMethodInsn(
                            int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                        rejectType(owner, entry);
                        rejectMethodDescriptor(descriptor, entry);
                        if ("android/content/res/Resources".equals(owner)
                                && ("getIdentifier".equals(name)
                                || "obtainAttributes".equals(name))) {
                            throw resourceFailure(entry, owner + '.' + name);
                        }
                        if (("android/content/Context".equals(owner)
                                || "android/content/res/Resources$Theme".equals(owner))
                                && "obtainStyledAttributes".equals(name)) {
                            throw resourceFailure(entry, owner + '.' + name);
                        }
                    }

                    @Override public void visitLdcInsn(Object value) {
                        if (value instanceof Type) rejectType(((Type) value).getInternalName(), entry);
                    }
                };
            }
        }, 0);
    }

    private static void rejectMethodDescriptor(String descriptor, String entry) {
        Type type = Type.getMethodType(descriptor);
        rejectAsmType(type.getReturnType(), entry);
        for (Type argument : type.getArgumentTypes()) rejectAsmType(argument, entry);
    }

    private static void rejectDescriptor(String descriptor, String entry) {
        rejectAsmType(Type.getType(descriptor), entry);
    }

    private static void rejectAsmType(Type type, String entry) {
        while (type.getSort() == Type.ARRAY) type = type.getElementType();
        if (type.getSort() == Type.OBJECT) rejectType(type.getInternalName(), entry);
    }

    private static void rejectType(String type, String entry) {
        if (type == null) return;
        while (type.startsWith("[")) {
            Type array = Type.getType(type);
            type = array.getElementType().getInternalName();
        }
        if (isRClass(type)) {
            throw resourceFailure(entry, type.replace('/', '.'));
        }
        if ("android/content/res/TypedArray".equals(type)) {
            throw resourceFailure(entry, "android.content.res.TypedArray");
        }
        if (isDeferredAndroidX(type)) {
            throw new IllegalStateException(
                    "First-stage dependency closure does not support AndroidX/AppCompat/Material "
                            + "reference " + type.replace('/', '.') + " in "
                            + entry.substring(0, entry.length() - 6).replace('/', '.'));
        }
    }

    private static IllegalStateException resourceFailure(String entry, String reference) {
        return new IllegalStateException("Resource-backed dependency API is forbidden in the "
                + "class-only plugin closure: "
                + entry.substring(0, entry.length() - 6).replace('/', '.')
                + " -> " + reference);
    }

    private static boolean isHostOwned(String name) {
        return name.startsWith("dev/x2c/runtime/")
                || name.startsWith("dev/x2c/plugin/api/")
                || name.startsWith("dev/x2c/plugin/base/")
                || name.startsWith("dev/x2c/plugin/runtime/")
                || name.startsWith("dev/x2c/plugin/loader/");
    }

    private static boolean isPlatformClass(String name) {
        return name.startsWith("java/")
                || name.startsWith("javax/")
                || name.startsWith("android/")
                || name.startsWith("dalvik/")
                || name.startsWith("org/xml/")
                || name.startsWith("org/w3c/");
    }

    private static boolean isDeferredAndroidX(String name) {
        return name.startsWith("androidx/")
                || name.startsWith("com/google/android/material/");
    }

    private static boolean isRClass(String name) {
        if (name == null || name.startsWith("android/R")) return false;
        int separator = name.lastIndexOf('/');
        String simple = separator < 0 ? name : name.substring(separator + 1);
        return "R".equals(simple) || simple.startsWith("R$");
    }

    private static String digest(Map<String, byte[]> classes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue());
            }
            StringBuilder output = new StringBuilder(64);
            for (byte value : digest.digest()) {
                output.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean isZip(File file) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            int first = input.read();
            int second = input.read();
            return first == 'P' && second == 'K';
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String stablePath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static String displayName(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        for (int index = 1; index < path.getNameCount(); index++) {
            if ("build".equals(path.getName(index).toString())) {
                return path.getName(index - 1) + "/" + file.getName();
            }
        }
        return file.getName();
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    static final class Result {
        final Map<String, byte[]> classes = new TreeMap<String, byte[]>();
        final Map<String, byte[]> dependencyClasses = new TreeMap<String, byte[]>();
        final Map<String, String> origins = new LinkedHashMap<String, String>();
        final List<PackagedDependency> dependencies = new ArrayList<PackagedDependency>();
        String dependencyClosureSha256;
    }

    private static final class DependencyReadException extends RuntimeException {
        final IOException cause;

        DependencyReadException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
