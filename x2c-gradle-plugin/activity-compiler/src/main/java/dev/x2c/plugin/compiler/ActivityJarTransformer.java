package dev.x2c.plugin.compiler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Converts annotated Android components and emits one direct-constructor component factory. */
public final class ActivityJarTransformer {
    /** Host/plugin bytecode protocol emitted by this compiler. */
    public static final int CURRENT_RUNTIME_ABI = 1;

    public static final String REGISTRY_CLASS_NAME =
            "dev.x2c.generated.plugin.ComponentRegistry";
    private static final String REGISTRY_INTERNAL_NAME =
            REGISTRY_CLASS_NAME.replace('.', '/');

    public ActivityTransformResult transform(File inputJar, File outputJar, String pluginId)
            throws IOException {
        return transform(inputJar, Collections.<File>emptyList(),
                Collections.<File>emptyList(), outputJar, pluginId);
    }

    /**
     * Transforms the plugin module together with its plugin-private runtime dependency closure.
     * JARs, resource-free AARs and class directories are accepted as dependency artifacts.
     */
    public ActivityTransformResult transform(
            File inputJar,
            Collection<File> dependencyArtifacts,
            File outputJar,
            String pluginId) throws IOException {
        return transform(inputJar, dependencyArtifacts, Collections.<File>emptyList(),
                outputJar, pluginId);
    }

    /**
     * Transforms runtime dependencies plus the selected {@code @X2cPluginBase} closure from the
     * compile-only classpath. Unselected compile-only classes remain host-provided and are never
     * copied into the payload.
     */
    public ActivityTransformResult transform(
            File inputJar,
            Collection<File> dependencyArtifacts,
            Collection<File> compileOnlyArtifacts,
            File outputJar,
            String pluginId) throws IOException {
        if (pluginId == null || !pluginId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "pluginId must contain only letters, digits, '.', '_' or '-'");
        }
        PluginDependencyClosure.Result closure =
                PluginDependencyClosure.read(
                        inputJar, dependencyArtifacts, compileOnlyArtifacts);
        Map<String, byte[]> classes = closure.classes;
        validateNoHostOwnedClasses(classes.keySet());
        Map<String, ClassModel> models = new LinkedHashMap<String, ClassModel>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            models.put(entry.getKey(), inspect(entry.getValue()));
        }

        List<TransformedActivity> activities = new ArrayList<TransformedActivity>();
        List<TransformedComponent> services = new ArrayList<TransformedComponent>();
        List<TransformedComponent> receivers = new ArrayList<TransformedComponent>();
        List<TransformedComponent> providers = new ArrayList<TransformedComponent>();
        Map<String, String> replacementSupers = new HashMap<String, String>();
        Map<ComponentKind, Set<String>> hierarchies = new HashMap<ComponentKind, Set<String>>();
        Map<ComponentKind, Set<String>> ancestors = new HashMap<ComponentKind, Set<String>>();
        for (ComponentKind kind : ComponentKind.values()) {
            hierarchies.put(kind, new HashSet<String>());
            ancestors.put(kind, new HashSet<String>());
        }
        Set<String> providerAuthorities = new HashSet<String>();

        for (ClassModel model : models.values()) {
            if (model.kind == null) continue;
            validateEntry(model);
            ClassModel root = resolveHierarchy(
                    model, model.kind, models, hierarchies.get(model.kind), ancestors.get(model.kind));
            if (root != null) {
                String previous = replacementSupers.put(root.name, model.kind.pluginBase);
                if (previous != null && !previous.equals(model.kind.pluginBase)) {
                    throw new IllegalStateException(
                            "Component hierarchy has conflicting roots: "
                                    + root.name.replace('/', '.'));
                }
            }
            switch (model.kind) {
                case ACTIVITY:
                    activities.add(new TransformedActivity(model.name, model.launchMode));
                    break;
                case SERVICE:
                    services.add(new TransformedComponent(model.name, null));
                    break;
                case RECEIVER:
                    receivers.add(new TransformedComponent(model.name, null));
                    break;
                case PROVIDER:
                    validateAuthority(model, providerAuthorities);
                    providers.add(new TransformedComponent(model.name, model.authority));
                    break;
                default:
                    throw new AssertionError(model.kind);
            }
        }

        if (activities.isEmpty() && services.isEmpty() && receivers.isEmpty() && providers.isEmpty()) {
            throw new IllegalStateException(
                    "No @X2cPlugin* Android components found in " + inputJar.getAbsolutePath());
        }
        validateComponentCoverage(models, ancestors);
        Collections.sort(activities);
        Collections.sort(services);
        Collections.sort(receivers);
        Collections.sort(providers);
        validateContainerCapacity(activities);
        if (services.size() > 8) {
            throw new IllegalStateException(
                    "Plugin declares more than 8 Services, exceeding the host container pool");
        }

        Map<String, byte[]> output = new TreeMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            ClassModel model = models.get(entry.getKey());
            output.put(entry.getKey(), ActivityBytecodeRewriter.rewrite(
                    entry.getValue(),
                    replacementSupers.get(model.name),
                    hierarchies.get(ComponentKind.ACTIVITY),
                    hierarchies.get(ComponentKind.SERVICE),
                    hierarchies.get(ComponentKind.RECEIVER),
                    hierarchies.get(ComponentKind.PROVIDER)));
        }
        if (output.containsKey(REGISTRY_INTERNAL_NAME + ".class")) {
            throw new IllegalStateException(
                    "Input JAR already contains reserved generated registry " + REGISTRY_CLASS_NAME);
        }
        output.put(REGISTRY_INTERNAL_NAME + ".class", ActivityRegistryGenerator.generate(
                pluginId, activities, services, receivers, providers));
        writeJar(outputJar, output);
        return new ActivityTransformResult(
                pluginId, REGISTRY_CLASS_NAME, activities, services, receivers, providers,
                closure.dependencies, closure.dependencyClosureSha256);
    }

    private static ClassModel inspect(byte[] bytes) {
        final ClassModel model = new ClassModel();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                model.access = access;
                model.name = name;
                model.superName = superName;
            }

            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                ComponentKind kind = ComponentKind.forAnnotation(descriptor);
                if (kind == null) return null;
                if (model.kind != null) {
                    throw new IllegalStateException(
                            "Android component has multiple X2C annotations: "
                                    + model.name.replace('/', '.'));
                }
                model.kind = kind;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visit(String name, Object value) {
                        if ("authority".equals(name)) model.authority = String.valueOf(value);
                    }

                    @Override public void visitEnum(String name, String descriptor, String value) {
                        if ("launchMode".equals(name)) model.launchMode = value;
                    }
                };
            }

            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if ("<init>".equals(name) && "()V".equals(descriptor)
                        && (access & Opcodes.ACC_PUBLIC) != 0) {
                    model.publicNoArgConstructor = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return model;
    }

    private static void validateEntry(ClassModel model) {
        if ((model.access & Opcodes.ACC_PUBLIC) == 0
                || (model.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) {
            throw new IllegalStateException("Plugin component must be public and concrete: "
                    + model.name.replace('/', '.'));
        }
        if (!model.publicNoArgConstructor) {
            throw new IllegalStateException("Plugin component requires a public no-arg constructor: "
                    + model.name.replace('/', '.'));
        }
    }

    private static void validateAuthority(ClassModel model, Set<String> authorities) {
        if (model.authority == null
                || !model.authority.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalStateException(
                    "@X2cPluginProvider requires a literal non-empty authority: "
                            + model.name.replace('/', '.'));
        }
        if (!authorities.add(model.authority)) {
            throw new IllegalStateException(
                    "Duplicate plugin ContentProvider authority: " + model.authority);
        }
    }

    private static ClassModel resolveHierarchy(
            ClassModel entry,
            ComponentKind kind,
            Map<String, ClassModel> models,
            Set<String> hierarchy,
            Set<String> ancestors) {
        ClassModel current = entry;
        Set<String> visited = new HashSet<String>();
        boolean first = true;
        while (current != null && visited.add(current.name)) {
            hierarchy.add(current.name);
            if (!first) ancestors.add(current.name);
            if (kind.frameworkBase.equals(current.superName)) return current;
            if (kind.pluginBase.equals(current.superName)
                    || kind.sharedBase.equals(current.superName)) {
                // The parent is an intentionally host-owned delegate root. It must not be copied
                // into the payload and must retain one Class identity across plugin ClassLoaders.
                return null;
            }
            ClassModel parent = models.get(current.superName + ".class");
            if (parent == null) {
                throw new IllegalStateException(
                        "Plugin " + kind.label + " base class must be packaged in the plugin "
                                + "payload closure (including a marked compileOnly base closure) "
                                + "so its Android root can be transformed, or extend the supported "
                                + "host-owned " + kind.sharedBase.replace('/', '.') + " root: "
                                + entry.name.replace('/', '.') + " -> "
                                + current.superName.replace('/', '.'));
            }
            current = parent;
            first = false;
        }
        throw new IllegalStateException("@X2cPlugin" + kind.label + " does not extend "
                + kind.frameworkBase.replace('/', '.') + ": " + entry.name.replace('/', '.'));
    }

    private static void validateComponentCoverage(
            Map<String, ClassModel> models, Map<ComponentKind, Set<String>> ancestors) {
        for (ClassModel model : models.values()) {
            if ((model.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION
                    | Opcodes.ACC_ABSTRACT)) != 0 || model.kind != null) {
                continue;
            }
            for (ComponentKind kind : ComponentKind.values()) {
                // Concrete BroadcastReceiver subclasses are also valid callback/dynamic receiver
                // objects. Only explicitly annotated Receivers are plugin components.
                if (kind == ComponentKind.RECEIVER) continue;
                if (ancestors.get(kind).contains(model.name)) break;
                if (isSubclass(model, kind, models, new HashSet<String>())) {
                    throw new IllegalStateException("Every concrete " + kind.label
                            + " in a plugin JAR must use @X2cPlugin" + kind.label + ": "
                            + model.name.replace('/', '.'));
                }
            }
        }
    }

    private static boolean isSubclass(
            ClassModel model,
            ComponentKind kind,
            Map<String, ClassModel> models,
            Set<String> visited) {
        if (kind.frameworkBase.equals(model.superName)
                || kind.pluginBase.equals(model.superName)
                || kind.sharedBase.equals(model.superName)) {
            return true;
        }
        if (model.superName == null || !visited.add(model.name)) return false;
        ClassModel parent = models.get(model.superName + ".class");
        return parent != null && isSubclass(parent, kind, models, visited);
    }

    private static void validateContainerCapacity(List<TransformedActivity> activities) {
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        for (TransformedActivity activity : activities) {
            Integer previous = counts.get(activity.launchMode);
            int count = previous == null ? 1 : previous.intValue() + 1;
            if (count > 8) {
                throw new IllegalStateException(
                        "Plugin declares more than 8 " + activity.launchMode
                                + " Activities, exceeding the host container pool");
            }
            counts.put(activity.launchMode, Integer.valueOf(count));
        }
    }

    private static void validateNoHostOwnedClasses(Set<String> classEntries) {
        for (String entry : classEntries) {
            if (entry.startsWith("dev/x2c/runtime/")
                    || entry.startsWith("dev/x2c/plugin/base/")
                    || entry.startsWith("dev/x2c/plugin/runtime/")
                    || entry.startsWith("dev/x2c/plugin/api/")
                    || entry.startsWith("dev/x2c/plugin/loader/")) {
                throw new IllegalStateException(
                        "Host-owned X2C API/runtime/base class must not be packaged in a plugin "
                                + "payload; use compileOnly: " + entry.replace('/', '.'));
            }
        }
    }

    private static void writeJar(File outputJar, Map<String, byte[]> classes) throws IOException {
        Files.createDirectories(outputJar.toPath().getParent());
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputJar)))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0L);
                jar.putNextEntry(jarEntry);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
    }

    private enum ComponentKind {
        ACTIVITY("Activity", "android/app/Activity", "dev/x2c/plugin/runtime/PluginActivity",
                "dev/x2c/plugin/base/BasePluginActivity",
                "Ldev/x2c/plugin/api/X2cPluginActivity;"),
        SERVICE("Service", "android/app/Service", "dev/x2c/plugin/runtime/PluginService",
                "dev/x2c/plugin/base/BasePluginService",
                "Ldev/x2c/plugin/api/X2cPluginService;"),
        RECEIVER("Receiver", "android/content/BroadcastReceiver",
                "dev/x2c/plugin/runtime/PluginReceiver",
                "dev/x2c/plugin/base/BasePluginReceiver",
                "Ldev/x2c/plugin/api/X2cPluginReceiver;"),
        PROVIDER("Provider", "android/content/ContentProvider",
                "dev/x2c/plugin/runtime/PluginContentProvider",
                "dev/x2c/plugin/base/BasePluginProvider",
                "Ldev/x2c/plugin/api/X2cPluginProvider;");

        final String label;
        final String frameworkBase;
        final String pluginBase;
        final String sharedBase;
        final String annotation;

        ComponentKind(
                String label,
                String frameworkBase,
                String pluginBase,
                String sharedBase,
                String annotation) {
            this.label = label;
            this.frameworkBase = frameworkBase;
            this.pluginBase = pluginBase;
            this.sharedBase = sharedBase;
            this.annotation = annotation;
        }

        static ComponentKind forAnnotation(String descriptor) {
            for (ComponentKind kind : values()) {
                if (kind.annotation.equals(descriptor)) return kind;
            }
            return null;
        }
    }

    private static final class ClassModel {
        int access;
        String name;
        String superName;
        String launchMode = "STANDARD";
        String authority;
        ComponentKind kind;
        boolean publicNoArgConstructor;
    }
}
