package dev.x2c.plugin.compiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/** Selects marked Activity bases from compile-only artifacts without embedding the whole project. */
final class CompileOnlyBaseClosure {
    private static final String MARKER = "Ldev/x2c/plugin/api/X2cPluginBase;";
    private static final String FRAMEWORK_ACTIVITY = "android/app/Activity";

    private CompileOnlyBaseClosure() {}

    static List<SelectedArtifact> select(
            Collection<File> artifacts, Map<String, byte[]> packagedClasses) throws IOException {
        if (artifacts == null || artifacts.isEmpty()) return Collections.emptyList();

        List<File> sorted = new ArrayList<File>(artifacts);
        Collections.sort(sorted, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return stablePath(left).compareTo(stablePath(right));
            }
        });
        Map<String, Candidate> candidates = new TreeMap<String, Candidate>();
        Set<String> seenArtifacts = new HashSet<String>();
        for (File file : sorted) {
            if (file == null || !seenArtifacts.add(stablePath(file))) continue;
            Artifact artifact = readArtifact(file);
            for (Map.Entry<String, byte[]> entry : artifact.classes.entrySet()) {
                if (packagedClasses.containsKey(entry.getKey())) continue;
                Candidate candidate = new Candidate(entry.getKey(), entry.getValue(), artifact);
                Candidate previous = candidates.putIfAbsent(entry.getKey(), candidate);
                if (previous != null && !Arrays.equals(previous.bytes, candidate.bytes)) {
                    throw new IllegalStateException(
                            "Duplicate compileOnly candidate class " + candidate.className()
                                    + " from " + previous.artifact.displayName + " and "
                                    + artifact.displayName);
                }
            }
        }

        Set<Candidate> selected = new HashSet<Candidate>();
        for (String entry : new TreeSet<String>(packagedClasses.keySet())) {
            selectMarkedAncestorChain(entry, packagedClasses, candidates, selected);
        }
        if (selected.isEmpty()) return Collections.emptyList();

        Queue<Candidate> queue = new ArrayDeque<Candidate>(selected);
        while (!queue.isEmpty()) {
            Candidate owner = queue.remove();
            for (String reference : owner.info.references) {
                Candidate dependency = candidates.get(reference + ".class");
                // A marked project is a selection boundary. Other compileOnly artifacts remain
                // host-provided unless their own marked Activity hierarchy is selected.
                if (dependency != null && dependency.artifact == owner.artifact
                        && selected.add(dependency)) {
                    queue.add(dependency);
                }
            }
        }

        Map<Artifact, TreeMap<String, byte[]>> byArtifact =
                new LinkedHashMap<Artifact, TreeMap<String, byte[]>>();
        List<Candidate> ordered = new ArrayList<Candidate>(selected);
        Collections.sort(ordered, new Comparator<Candidate>() {
            @Override public int compare(Candidate left, Candidate right) {
                int byArtifact = left.artifact.displayName.compareTo(right.artifact.displayName);
                return byArtifact != 0 ? byArtifact : left.entry.compareTo(right.entry);
            }
        });
        for (Candidate candidate : ordered) {
            TreeMap<String, byte[]> classes = byArtifact.get(candidate.artifact);
            if (classes == null) {
                classes = new TreeMap<String, byte[]>();
                byArtifact.put(candidate.artifact, classes);
            }
            classes.put(candidate.entry, candidate.bytes);
        }

        List<SelectedArtifact> result = new ArrayList<SelectedArtifact>();
        for (Map.Entry<Artifact, TreeMap<String, byte[]>> entry : byArtifact.entrySet()) {
            Artifact artifact = entry.getKey();
            result.add(new SelectedArtifact(
                    artifact.displayName, "compileOnly-" + artifact.type, entry.getValue()));
        }
        return result;
    }

    private static void selectMarkedAncestorChain(
            String startEntry,
            Map<String, byte[]> packaged,
            Map<String, Candidate> candidates,
            Set<Candidate> selected) {
        String currentEntry = startEntry;
        Set<String> visited = new HashSet<String>();
        List<Candidate> chain = new ArrayList<Candidate>();
        boolean marked = false;
        while (currentEntry != null && visited.add(currentEntry)) {
            byte[] packagedBytes = packaged.get(currentEntry);
            Candidate candidate = candidates.get(currentEntry);
            String superName;
            if (packagedBytes != null) {
                superName = new ClassReader(packagedBytes).getSuperName();
            } else if (candidate != null) {
                chain.add(candidate);
                marked |= candidate.info.markedBase;
                superName = candidate.info.superName;
            } else {
                break;
            }
            currentEntry = superName == null ? null : superName + ".class";
        }
        if (!marked) return;
        for (Candidate candidate : chain) {
            if (candidate.info.markedBase
                    && !extendsFrameworkActivity(candidate, packaged, candidates)) {
                throw new IllegalStateException(
                        "@X2cPluginBase currently supports only Activity inheritance roots: "
                                + candidate.className());
            }
            selected.add(candidate);
        }
    }

    private static boolean extendsFrameworkActivity(
            Candidate start,
            Map<String, byte[]> packaged,
            Map<String, Candidate> candidates) {
        String current = start.info.superName;
        Set<String> visited = new HashSet<String>();
        while (current != null && visited.add(current)) {
            if (FRAMEWORK_ACTIVITY.equals(current)) return true;
            byte[] packagedBytes = packaged.get(current + ".class");
            if (packagedBytes != null) {
                current = new ClassReader(packagedBytes).getSuperName();
                continue;
            }
            Candidate candidate = candidates.get(current + ".class");
            if (candidate == null) return false;
            current = candidate.info.superName;
        }
        return false;
    }

    private static Artifact readArtifact(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalStateException("Resolved compileOnly artifact does not exist: " + file);
        }
        Artifact artifact = new Artifact(displayName(file));
        if (file.isDirectory()) {
            artifact.type = "directory";
            readDirectory(file, artifact.classes);
        } else if (file.getName().endsWith(".aar")) {
            artifact.type = "aar";
            readAar(file, artifact.classes);
        } else if (file.getName().endsWith(".jar") || isZip(file)) {
            artifact.type = "jar";
            readJar(file, artifact.classes);
        } else {
            throw new IllegalStateException(
                    "Unsupported compileOnly class artifact: " + file.getName());
        }
        return artifact;
    }

    private static void readDirectory(File directory, final Map<String, byte[]> classes)
            throws IOException {
        final Path root = directory.toPath();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            putRaw(root.relativize(path).toString().replace(File.separatorChar, '/'),
                                    Files.readAllBytes(path), classes, directory.getName());
                        } catch (IOException error) {
                            throw new CandidateReadException(error);
                        }
                    });
        } catch (CandidateReadException error) {
            throw error.cause;
        }
    }

    private static void readJar(File file, Map<String, byte[]> classes) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            List<JarEntry> entries = Collections.list(jar.entries());
            Collections.sort(entries, new Comparator<JarEntry>() {
                @Override public int compare(JarEntry left, JarEntry right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (JarEntry entry : entries) {
                if (entry.isDirectory() || !isClassEntry(entry.getName())) continue;
                try (InputStream input = new BufferedInputStream(jar.getInputStream(entry))) {
                    putRaw(entry.getName(), read(input), classes, file.getName());
                }
            }
        }
    }

    private static void readAar(File file, Map<String, byte[]> classes) throws IOException {
        try (JarFile aar = new JarFile(file)) {
            List<JarEntry> entries = Collections.list(aar.entries());
            Collections.sort(entries, new Comparator<JarEntry>() {
                @Override public int compare(JarEntry left, JarEntry right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (JarEntry entry : entries) {
                String name = entry.getName();
                if (entry.isDirectory() || !("classes.jar".equals(name)
                        || (name.startsWith("libs/") && name.endsWith(".jar")))) {
                    continue;
                }
                try (JarInputStream jar = new JarInputStream(
                        new BufferedInputStream(aar.getInputStream(entry)))) {
                    JarEntry nested;
                    while ((nested = jar.getNextJarEntry()) != null) {
                        if (!nested.isDirectory() && isClassEntry(nested.getName())) {
                            putRaw(nested.getName(), read(jar), classes,
                                    file.getName() + "!/" + name);
                        }
                    }
                }
            }
        }
    }

    private static void putRaw(
            String entry, byte[] bytes, Map<String, byte[]> classes, String origin) {
        ClassReader reader;
        try {
            reader = new ClassReader(bytes);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Invalid compileOnly class " + entry, error);
        }
        String expected = reader.getClassName() + ".class";
        if (!expected.equals(entry)) {
            throw new IllegalStateException("Class entry/name mismatch in " + origin + ": "
                    + entry + " contains " + expected);
        }
        byte[] previous = classes.putIfAbsent(entry, bytes);
        if (previous != null && !Arrays.equals(previous, bytes)) {
            throw new IllegalStateException(
                    "Duplicate class inside compileOnly artifact " + origin + ": " + entry);
        }
    }

    private static ClassInfo inspect(byte[] bytes) {
        final ClassInfo info = new ClassInfo();
        new ClassReader(bytes).accept(new DependencyVisitor(info), 0);
        return info;
    }

    private static final class DependencyVisitor extends ClassVisitor {
        private final ClassInfo info;

        DependencyVisitor(ClassInfo info) {
            super(Opcodes.ASM9);
            this.info = info;
        }

        @Override public void visit(
                int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            info.superName = superName;
            addInternal(info.references, superName);
            if (interfaces != null) {
                for (String value : interfaces) addInternal(info.references, value);
            }
            addSignature(info.references, signature, false);
        }

        @Override public void visitNestHost(String nestHost) {
            addInternal(info.references, nestHost);
        }

        @Override public void visitOuterClass(String owner, String name, String descriptor) {
            addInternal(info.references, owner);
            addDescriptor(info.references, descriptor);
        }

        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (MARKER.equals(descriptor)) info.markedBase = true;
            addDescriptor(info.references, descriptor);
            return annotationVisitor(info.references);
        }

        @Override public AnnotationVisitor visitTypeAnnotation(
                int typeRef, TypePath typePath, String descriptor, boolean visible) {
            addDescriptor(info.references, descriptor);
            return annotationVisitor(info.references);
        }

        @Override public void visitNestMember(String nestMember) {
            addInternal(info.references, nestMember);
        }

        @Override public void visitPermittedSubclass(String permittedSubclass) {
            addInternal(info.references, permittedSubclass);
        }

        @Override public void visitInnerClass(
                String name, String outerName, String innerName, int access) {
            addInternal(info.references, name);
            addInternal(info.references, outerName);
        }

        @Override public RecordComponentVisitor visitRecordComponent(
                String name, String descriptor, String signature) {
            addDescriptor(info.references, descriptor);
            addSignature(info.references, signature, true);
            return new RecordComponentVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(
                        String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }
            };
        }

        @Override public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            addDescriptor(info.references, descriptor);
            addSignature(info.references, signature, true);
            addConstant(info.references, value);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(
                        String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }

                @Override public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }
            };
        }

        @Override public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature,
                String[] exceptions) {
            addDescriptor(info.references, descriptor);
            addSignature(info.references, signature, false);
            if (exceptions != null) {
                for (String value : exceptions) addInternal(info.references, value);
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotationDefault() {
                    return annotationVisitor(info.references);
                }

                @Override public AnnotationVisitor visitAnnotation(
                        String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }

                @Override public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }

                @Override public AnnotationVisitor visitParameterAnnotation(
                        int parameter, String descriptor, boolean visible) {
                    addDescriptor(info.references, descriptor);
                    return annotationVisitor(info.references);
                }

                @Override public void visitFrame(
                        int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                    addFrame(info.references, local);
                    addFrame(info.references, stack);
                }

                @Override public void visitTypeInsn(int opcode, String type) {
                    addInternal(info.references, type);
                }

                @Override public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                    addInternal(info.references, owner);
                    addDescriptor(info.references, descriptor);
                }

                @Override public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor,
                        boolean isInterface) {
                    addInternal(info.references, owner);
                    addDescriptor(info.references, descriptor);
                }

                @Override public void visitInvokeDynamicInsn(
                        String name, String descriptor, Handle bootstrapMethodHandle,
                        Object... bootstrapMethodArguments) {
                    addDescriptor(info.references, descriptor);
                    addConstant(info.references, bootstrapMethodHandle);
                    if (bootstrapMethodArguments != null) {
                        for (Object value : bootstrapMethodArguments) {
                            addConstant(info.references, value);
                        }
                    }
                }

                @Override public void visitLdcInsn(Object value) {
                    addConstant(info.references, value);
                }

                @Override public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                    addDescriptor(info.references, descriptor);
                }

                @Override public void visitTryCatchBlock(
                        org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                        org.objectweb.asm.Label handler, String type) {
                    addInternal(info.references, type);
                }

                @Override public void visitLocalVariable(
                        String name, String descriptor, String signature,
                        org.objectweb.asm.Label start, org.objectweb.asm.Label end, int index) {
                    addDescriptor(info.references, descriptor);
                    addSignature(info.references, signature, true);
                }
            };
        }
    }

    private static AnnotationVisitor annotationVisitor(final Set<String> references) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                addConstant(references, value);
            }

            @Override public void visitEnum(String name, String descriptor, String value) {
                addDescriptor(references, descriptor);
            }

            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                addDescriptor(references, descriptor);
                return this;
            }

            @Override public AnnotationVisitor visitArray(String name) {
                return this;
            }
        };
    }

    private static void addFrame(Set<String> references, Object[] values) {
        if (values == null) return;
        for (Object value : values) {
            if (value instanceof String) addInternal(references, (String) value);
        }
    }

    private static void addConstant(Set<String> references, Object value) {
        if (value instanceof Type) {
            addType(references, (Type) value);
        } else if (value instanceof Handle) {
            Handle handle = (Handle) value;
            addInternal(references, handle.getOwner());
            addDescriptor(references, handle.getDesc());
        } else if (value instanceof ConstantDynamic) {
            ConstantDynamic dynamic = (ConstantDynamic) value;
            addDescriptor(references, dynamic.getDescriptor());
            addConstant(references, dynamic.getBootstrapMethod());
            for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                addConstant(references, dynamic.getBootstrapMethodArgument(index));
            }
        }
    }

    private static void addDescriptor(Set<String> references, String descriptor) {
        if (descriptor == null) return;
        Type type = descriptor.startsWith("(")
                ? Type.getMethodType(descriptor) : Type.getType(descriptor);
        if (type.getSort() == Type.METHOD) {
            addType(references, type.getReturnType());
            for (Type argument : type.getArgumentTypes()) addType(references, argument);
        } else {
            addType(references, type);
        }
    }

    private static void addType(Set<String> references, Type type) {
        while (type.getSort() == Type.ARRAY) type = type.getElementType();
        if (type.getSort() == Type.OBJECT) addInternal(references, type.getInternalName());
        if (type.getSort() == Type.METHOD) {
            addType(references, type.getReturnType());
            for (Type argument : type.getArgumentTypes()) addType(references, argument);
        }
    }

    private static void addSignature(
            final Set<String> references, String signature, boolean typeOnly) {
        if (signature == null) return;
        SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
            @Override public void visitClassType(String name) {
                addInternal(references, name);
            }
        };
        SignatureReader reader = new SignatureReader(signature);
        if (typeOnly) reader.acceptType(visitor); else reader.accept(visitor);
    }

    private static void addInternal(Set<String> references, String name) {
        if (name == null) return;
        if (name.startsWith("[")) {
            addType(references, Type.getType(name));
        } else {
            references.add(name);
        }
    }

    private static boolean isClassEntry(String name) {
        return name.endsWith(".class")
                && !name.startsWith("META-INF/versions/")
                && !"module-info.class".equals(name);
    }

    private static boolean isZip(File file) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return input.read() == 'P' && input.read() == 'K';
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

    static final class SelectedArtifact {
        final String displayName;
        final String type;
        final Map<String, byte[]> classes;

        SelectedArtifact(String displayName, String type, Map<String, byte[]> classes) {
            this.displayName = displayName;
            this.type = type;
            this.classes = classes;
        }
    }

    private static final class Artifact {
        final String displayName;
        final Map<String, byte[]> classes = new TreeMap<String, byte[]>();
        String type;

        Artifact(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class Candidate {
        final String entry;
        final byte[] bytes;
        final Artifact artifact;
        final ClassInfo info;

        Candidate(String entry, byte[] bytes, Artifact artifact) {
            this.entry = entry;
            this.bytes = bytes;
            this.artifact = artifact;
            this.info = inspect(bytes);
        }

        String className() {
            return entry.substring(0, entry.length() - 6).replace('/', '.');
        }
    }

    private static final class ClassInfo {
        String superName;
        boolean markedBase;
        final Set<String> references = new TreeSet<String>();
    }

    private static final class CandidateReadException extends RuntimeException {
        final IOException cause;

        CandidateReadException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
