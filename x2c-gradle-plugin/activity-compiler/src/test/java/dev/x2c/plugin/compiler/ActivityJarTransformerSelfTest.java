package dev.x2c.plugin.compiler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class ActivityJarTransformerSelfTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("x2c-activity-transform-");
        try {
            Path input = root.resolve("input.jar");
            Path first = root.resolve("first.jar");
            Path second = root.resolve("second.jar");
            writeInput(input);
            ActivityTransformResult result = new ActivityJarTransformer().transform(
                    input.toFile(), first.toFile(), "sample.plugin");
            new ActivityJarTransformer().transform(
                    input.toFile(), second.toFile(), "sample.plugin");
            require(result.activities.size() == 4, "Expected four transformed Activities");
            require(result.services.size() == 2, "Expected two transformed Services");
            require(result.receivers.size() == 2, "Expected two transformed Receivers");
            require(result.providers.size() == 2, "Expected two transformed Providers");
            require("sample.plugin".equals(result.pluginId), "Wrong plugin ID");
            require(Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(second)),
                    "Transformed JAR must be deterministic");

            Map<String, byte[]> classes = readJar(first.toFile());
            verifyActivity(classes.get("sample/FirstActivity.class"), "SINGLE_TOP");
            verifyActivity(classes.get("sample/SecondActivity.class"), "STANDARD");
            verifyActivityHierarchy(classes);
            verifySharedBaseHierarchy(classes);
            verifySuperclass(classes.get("sample/ProbeService.class"),
                    "dev/x2c/plugin/runtime/PluginService",
                    "Service superclass was not transformed");
            verifySuperclass(classes.get("sample/ProbeReceiver.class"),
                    "dev/x2c/plugin/runtime/PluginReceiver",
                    "Receiver superclass was not transformed");
            verifySuperclass(classes.get("sample/ProbeProvider.class"),
                    "dev/x2c/plugin/runtime/PluginContentProvider",
                    "Provider superclass was not transformed");
            verifySuperclass(classes.get("sample/SharedProbeService.class"),
                    "dev/x2c/plugin/base/BasePluginService",
                    "Shared Service base must retain its host ClassLoader boundary");
            verifySuperclass(classes.get("sample/SharedProbeReceiver.class"),
                    "dev/x2c/plugin/base/BasePluginReceiver",
                    "Shared Receiver base must retain its host ClassLoader boundary");
            verifySuperclass(classes.get("sample/SharedProbeProvider.class"),
                    "dev/x2c/plugin/base/BasePluginProvider",
                    "Shared Provider base must retain its host ClassLoader boundary");
            verifyHelperCallSite(classes.get("sample/Helper.class"));
            verifyContentResolverCallSite(classes.get("sample/ProviderClient.class"));
            verifyRegistry(classes.get(
                    "dev/x2c/generated/plugin/ComponentRegistry.class"));
            verifyExternalDependencyClosure(root);
            verifyCompileOnlyBaseClosure(root);
            verifyFailClosed(root);
            System.out.println("ActivityJarTransformerSelfTest: passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception ignored) {}
                });
            }
        }
    }

    private static void verifyExternalDependencyClosure(Path root) throws Exception {
        Path plugin = root.resolve("closure-plugin.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(plugin.toFile())))) {
            add(jar, "sample/ExternalDerivedActivity.class", activity(
                    "sample/ExternalDerivedActivity",
                    "business/BusinessBaseActivity", "SINGLE_TASK"));
        }
        Path dependency = root.resolve("business-base.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(dependency.toFile())))) {
            add(jar, "business/BusinessBaseActivity.class", baseActivity(
                    "business/BusinessBaseActivity", "android/app/Activity"));
        }
        Path output = root.resolve("closure-output.jar");
        ActivityTransformResult result = new ActivityJarTransformer().transform(
                plugin.toFile(), Collections.singleton(dependency.toFile()),
                output.toFile(), "sample.closure");
        require(result.dependencies.size() == 1,
                "Dependency closure metadata is missing");
        require(result.dependencyClosureSha256.matches("[0-9a-f]{64}"),
                "Dependency closure digest is missing");
        PackagedDependency metadata = result.dependencies.get(0);
        require("business-base.jar".equals(metadata.artifactName)
                        && "jar".equals(metadata.artifactType)
                        && metadata.classCount == 1
                        && metadata.classesSha256.matches("[0-9a-f]{64}"),
                "Dependency closure metadata is invalid");
        Map<String, byte[]> classes = readJar(output.toFile());
        verifySuperclass(classes.get("sample/ExternalDerivedActivity.class"),
                "business/BusinessBaseActivity",
                "Plugin entry must retain the external business base");
        verifySuperclass(classes.get("business/BusinessBaseActivity.class"),
                "dev/x2c/plugin/runtime/PluginActivity",
                "External business base root was not transformed");

        Path duplicate = root.resolve("duplicate-dependency.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(duplicate.toFile())))) {
            add(jar, "sample/ExternalDerivedActivity.class", activity(
                    "sample/ExternalDerivedActivity", "android/app/Activity", null));
        }
        expectDependencyFailure(plugin, duplicate, root.resolve("duplicate-output.jar"),
                "Duplicate plugin-private class sample.ExternalDerivedActivity");

        Path resourceAar = root.resolve("resource-base.aar");
        try (JarOutputStream aar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(resourceAar.toFile())))) {
            add(aar, "res/layout/base.xml", new byte[] {1});
        }
        expectDependencyFailure(plugin, resourceAar, root.resolve("resource-output.jar"),
                "only accepts resource-free AARs");

        Path androidX = root.resolve("androidx-base.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(androidX.toFile())))) {
            add(jar, "androidx/sample/Unsupported.class", baseActivity(
                    "androidx/sample/Unsupported", "android/app/Activity"));
        }
        expectDependencyFailure(plugin, androidX, root.resolve("androidx-output.jar"),
                "does not support AndroidX/AppCompat/Material");
    }

    private static void verifyCompileOnlyBaseClosure(Path root) throws Exception {
        Path plugin = root.resolve("compile-only-plugin.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(plugin.toFile())))) {
            add(jar, "sample/CompileOnlyDerivedActivity.class", activity(
                    "sample/CompileOnlyDerivedActivity",
                    "business/MarkedBusinessBaseActivity", "SINGLE_TASK"));
        }
        Path compileOnly = root.resolve("compile-only-business.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(compileOnly.toFile())))) {
            add(jar, "business/MarkedBusinessBaseActivity.class",
                    markedBaseActivity(
                            "business/MarkedBusinessBaseActivity",
                            "android/app/Activity",
                            "business/BaseHelper",
                            "business/ReflectiveHelper"));
            add(jar, "business/BaseHelper.class", plainObject("business/BaseHelper"));
            add(jar, "business/ReflectiveHelper.class",
                    plainObject("business/ReflectiveHelper"));
            add(jar, "business/UnrelatedHelper.class",
                    plainObject("business/UnrelatedHelper"));
        }

        Path output = root.resolve("compile-only-output.jar");
        ActivityTransformResult result = new ActivityJarTransformer().transform(
                plugin.toFile(), Collections.<File>emptyList(),
                Collections.singleton(compileOnly.toFile()),
                output.toFile(), "sample.compile-only");
        require(result.dependencies.size() == 1,
                "Selected compileOnly closure metadata is missing");
        PackagedDependency metadata = result.dependencies.get(0);
        require("compile-only-business.jar".equals(metadata.artifactName)
                        && "compileOnly-jar".equals(metadata.artifactType)
                        && metadata.classCount == 3,
                "Selected compileOnly closure metadata is invalid");
        Map<String, byte[]> classes = readJar(output.toFile());
        verifySuperclass(classes.get("business/MarkedBusinessBaseActivity.class"),
                "dev/x2c/plugin/runtime/PluginActivity",
                "Marked compileOnly Activity root was not transformed");
        require(classes.containsKey("business/BaseHelper.class"),
                "Statically referenced compileOnly helper was not selected");
        require(classes.containsKey("business/ReflectiveHelper.class"),
                "Explicit @X2cPluginBase.include helper was not selected");
        require(!classes.containsKey("business/UnrelatedHelper.class"),
                "Unrelated compileOnly project class leaked into the payload");

        Path invalidPlugin = root.resolve("invalid-compile-only-plugin.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(invalidPlugin.toFile())))) {
            add(jar, "sample/InvalidDerivedActivity.class", activity(
                    "sample/InvalidDerivedActivity", "business/MarkedNonActivityBase", null));
        }
        Path invalidCompileOnly = root.resolve("invalid-compile-only-base.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(invalidCompileOnly.toFile())))) {
            add(jar, "business/MarkedNonActivityBase.class",
                    markedBaseActivity("business/MarkedNonActivityBase", "java/lang/Object",
                            null, null));
        }
        expectCompileOnlyFailure(
                invalidPlugin,
                invalidCompileOnly,
                root.resolve("invalid-compile-only-output.jar"),
                "currently supports only Activity inheritance roots");
    }

    private static void writeInput(Path path) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            add(jar, "sample/FirstActivity.class", activity("sample/FirstActivity", "SINGLE_TOP"));
            add(jar, "sample/SecondActivity.class", activity("sample/SecondActivity", null));
            add(jar, "sample/RootBaseActivity.class", baseActivity(
                    "sample/RootBaseActivity", "android/app/Activity"));
            add(jar, "sample/MiddleBaseActivity.class", baseActivity(
                    "sample/MiddleBaseActivity", "sample/RootBaseActivity"));
            add(jar, "sample/DerivedActivity.class", activity(
                    "sample/DerivedActivity", "sample/MiddleBaseActivity", "SINGLE_TASK"));
            add(jar, "sample/SharedBaseActivity.class", activity(
                    "sample/SharedBaseActivity",
                    "dev/x2c/plugin/base/BasePluginActivity", "SINGLE_INSTANCE"));
            add(jar, "sample/Helper.class", helper());
            add(jar, "sample/ProbeService.class", component(
                    "sample/ProbeService", "android/app/Service",
                    "Ldev/x2c/plugin/api/X2cPluginService;", null));
            add(jar, "sample/ProbeReceiver.class", component(
                    "sample/ProbeReceiver", "android/content/BroadcastReceiver",
                    "Ldev/x2c/plugin/api/X2cPluginReceiver;", null));
            add(jar, "sample/ProbeProvider.class", component(
                    "sample/ProbeProvider", "android/content/ContentProvider",
                    "Ldev/x2c/plugin/api/X2cPluginProvider;", "sample.provider"));
            add(jar, "sample/SharedProbeService.class", component(
                    "sample/SharedProbeService", "dev/x2c/plugin/base/BasePluginService",
                    "Ldev/x2c/plugin/api/X2cPluginService;", null));
            add(jar, "sample/SharedProbeReceiver.class", component(
                    "sample/SharedProbeReceiver", "dev/x2c/plugin/base/BasePluginReceiver",
                    "Ldev/x2c/plugin/api/X2cPluginReceiver;", null));
            add(jar, "sample/SharedProbeProvider.class", component(
                    "sample/SharedProbeProvider", "dev/x2c/plugin/base/BasePluginProvider",
                    "Ldev/x2c/plugin/api/X2cPluginProvider;", "sample.shared.provider"));
            add(jar, "sample/ProviderClient.class", providerClient());
        }
    }

    private static byte[] component(
            String name, String superName, String annotationName, String authority) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                name, null, superName, null);
        AnnotationVisitor annotation = writer.visitAnnotation(annotationName, false);
        if (authority != null) annotation.visit("authority", authority);
        annotation.visitEnd();
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] activity(String name, String launchMode) {
        return activity(name, "android/app/Activity", launchMode);
    }

    private static byte[] activity(String name, String superName, String launchMode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                name, null, superName, null);
        AnnotationVisitor annotation = writer.visitAnnotation(
                "Ldev/x2c/plugin/api/X2cPluginActivity;", false);
        if (launchMode != null) {
            annotation.visitEnum("launchMode", "Ldev/x2c/plugin/api/PluginLaunchMode;", launchMode);
        }
        annotation.visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor create = writer.visitMethod(
                Opcodes.ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitVarInsn(Opcodes.ALOAD, 1);
        create.visitMethodInsn(Opcodes.INVOKESPECIAL,
                superName, "onCreate", "(Landroid/os/Bundle;)V", false);
        create.visitInsn(Opcodes.RETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] baseActivity(String name, String superName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                name, null, superName, null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor create = writer.visitMethod(
                Opcodes.ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitVarInsn(Opcodes.ALOAD, 1);
        create.visitMethodInsn(
                Opcodes.INVOKESPECIAL, superName, "onCreate", "(Landroid/os/Bundle;)V", false);
        create.visitInsn(Opcodes.RETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();
        if ("android/app/Activity".equals(superName)) {
            MethodVisitor application = writer.visitMethod(
                    Opcodes.ACC_PROTECTED, "baseApplication",
                    "()Landroid/app/Application;", null, null);
            application.visitCode();
            application.visitVarInsn(Opcodes.ALOAD, 0);
            application.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "android/app/Activity", "getApplication",
                    "()Landroid/app/Application;", false);
            application.visitInsn(Opcodes.ARETURN);
            application.visitMaxs(0, 0);
            application.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] markedBaseActivity(
            String name, String superName, String helperName, String includedName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                name, null, superName, null);
        AnnotationVisitor marker = writer.visitAnnotation(
                "Ldev/x2c/plugin/api/X2cPluginBase;", false);
        if (includedName != null) {
            AnnotationVisitor include = marker.visitArray("include");
            include.visit(null, org.objectweb.asm.Type.getObjectType(includedName));
            include.visitEnd();
        }
        marker.visitEnd();
        if (helperName != null) {
            writer.visitField(Opcodes.ACC_PRIVATE, "helper", "L" + helperName + ";",
                    null, null).visitEnd();
        }
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] plainObject(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] helper() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/Helper", null,
                "java/lang/Object", null);
        MethodVisitor application = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "application",
                "(Lsample/FirstActivity;)Landroid/app/Application;",
                null,
                null);
        application.visitCode();
        application.visitVarInsn(Opcodes.ALOAD, 0);
        application.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "sample/FirstActivity", "getApplication", "()Landroid/app/Application;", false);
        application.visitInsn(Opcodes.ARETURN);
        application.visitMaxs(0, 0);
        application.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] providerClient() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/ProviderClient", null,
                "java/lang/Object", null);
        MethodVisitor query = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "query",
                "(Landroid/content/ContentResolver;Landroid/net/Uri;)Landroid/database/Cursor;",
                null,
                null);
        query.visitCode();
        query.visitVarInsn(Opcodes.ALOAD, 0);
        query.visitVarInsn(Opcodes.ALOAD, 1);
        query.visitInsn(Opcodes.ACONST_NULL);
        query.visitInsn(Opcodes.ACONST_NULL);
        query.visitInsn(Opcodes.ACONST_NULL);
        query.visitInsn(Opcodes.ACONST_NULL);
        query.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "android/content/ContentResolver", "query",
                "(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;",
                false);
        query.visitInsn(Opcodes.ARETURN);
        query.visitMaxs(0, 0);
        query.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void verifyActivity(byte[] bytes, String expectedMode) {
        require(bytes != null, "Missing transformed Activity");
        final boolean[] rewrittenSuperCall = {false};
        final String[] launchMode = {"STANDARD"};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                require("dev/x2c/plugin/runtime/PluginActivity".equals(superName),
                        "Activity superclass was not rewritten");
            }

            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!"Ldev/x2c/plugin/api/X2cPluginActivity;".equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visitEnum(String name, String descriptor, String value) {
                        if ("launchMode".equals(name)) launchMode[0] = value;
                    }
                };
            }

            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESPECIAL
                                && "dev/x2c/plugin/runtime/PluginActivity".equals(owner)) {
                            rewrittenSuperCall[0] = true;
                        }
                    }
                };
            }
        }, 0);
        require(rewrittenSuperCall[0], "Activity super calls were not rewritten");
        require(expectedMode.equals(launchMode[0]), "Activity launchMode annotation changed");
    }

    private static void verifyActivityHierarchy(Map<String, byte[]> classes) {
        verifySuperclass(classes.get("sample/DerivedActivity.class"),
                "sample/MiddleBaseActivity", "Derived Activity must retain its direct base class");
        verifySuperclass(classes.get("sample/MiddleBaseActivity.class"),
                "sample/RootBaseActivity", "Middle base class must remain in the hierarchy");
        verifySuperclass(classes.get("sample/RootBaseActivity.class"),
                "dev/x2c/plugin/runtime/PluginActivity",
                "Only the framework boundary base must be rewritten");

        final boolean[] rootSuperRewritten = {false};
        final boolean[] baseFinalApiRewritten = {false};
        new ClassReader(classes.get("sample/RootBaseActivity.class")).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(
                            int access, String name, String descriptor, String signature,
                            String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(
                                    int opcode, String owner, String methodName,
                                    String methodDescriptor, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESPECIAL
                                        && "dev/x2c/plugin/runtime/PluginActivity".equals(owner)) {
                                    rootSuperRewritten[0] = true;
                                }
                                if (opcode == Opcodes.INVOKEVIRTUAL
                                        && "dev/x2c/plugin/runtime/PluginActivity".equals(owner)
                                        && "getPluginApplication".equals(methodName)) {
                                    baseFinalApiRewritten[0] = true;
                                }
                            }
                        };
                    }
                }, 0);
        require(rootSuperRewritten[0], "Root BaseActivity super calls were not rewritten");
        require(baseFinalApiRewritten[0], "BaseActivity final API call was not bridged");
    }

    private static void verifySharedBaseHierarchy(Map<String, byte[]> classes) {
        verifySuperclass(classes.get("sample/SharedBaseActivity.class"),
                "dev/x2c/plugin/base/BasePluginActivity",
                "Host-owned plugin base must retain its ClassLoader boundary");
    }

    private static void verifySuperclass(byte[] bytes, String expected, String message) {
        require(bytes != null, "Missing class for superclass verification");
        final String[] actual = {null};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                actual[0] = superName;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(expected.equals(actual[0]), message + ": " + actual[0]);
    }

    private static void verifyHelperCallSite(byte[] bytes) {
        require(bytes != null, "Missing transformed helper");
        final boolean[] rewritten = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if ("dev/x2c/plugin/runtime/PluginActivity".equals(owner)
                                && "getPluginApplication".equals(methodName)) {
                            rewritten[0] = true;
                        }
                    }
                };
            }
        }, 0);
        require(rewritten[0], "Final Activity API call in helper class was not rewritten");
    }

    private static void verifyContentResolverCallSite(byte[] bytes) {
        require(bytes != null, "Missing transformed ContentResolver client");
        final boolean[] rewritten = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "dev/x2c/plugin/runtime/PluginContentResolver".equals(owner)
                                && "query".equals(methodName)) {
                            rewritten[0] = true;
                        }
                    }
                };
            }
        }, 0);
        require(rewritten[0], "ContentResolver URI call was not virtualized");
    }

    private static void verifyRegistry(byte[] bytes) {
        require(bytes != null, "Generated registry is missing");
        final boolean[] contract = {false};
        final Set<String> methods = new HashSet<String>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                contract[0] = Arrays.asList(interfaces).contains(
                        "dev/x2c/plugin/api/PluginComponentRegistry");
            }

            @Override public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                methods.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        Set<String> required = new HashSet<String>(Arrays.asList(
                "runtimeAbiVersion", "pluginId", "contains", "info", "create",
                "containsService", "createService",
                "containsReceiver", "createReceiver",
                "containsProvider", "providerInfo", "providers", "createProvider"));
        require(contract[0] && methods.containsAll(required),
                "Generated registry contract is incomplete: " + methods);
    }

    private static void verifyFailClosed(Path root) throws Exception {
        Path missingAnnotation = root.resolve("missing-annotation.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(missingAnnotation.toFile())))) {
            add(jar, "sample/MissingAnnotation.class", plainActivity("sample/MissingAnnotation"));
        }
        expectFailure(missingAnnotation, root.resolve("missing-output.jar"),
                "No @X2cPlugin* Android components found");

        Path tooMany = root.resolve("too-many.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(tooMany.toFile())))) {
            for (int index = 0; index < 9; index++) {
                String name = "sample/Overflow" + index;
                add(jar, name + ".class", activity(name, "SINGLE_INSTANCE"));
            }
        }
        expectFailure(tooMany, root.resolve("overflow-output.jar"), "more than 8");

        Path unsupportedFinalApi = root.resolve("unsupported-final-api.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(unsupportedFinalApi.toFile())))) {
            add(jar, "sample/FirstActivity.class", activity("sample/FirstActivity", null));
            add(jar, "sample/UnsupportedHelper.class", unsupportedFinalApiHelper());
        }
        expectFailure(unsupportedFinalApi, root.resolve("unsupported-final-api-output.jar"),
                "Unsupported final Activity API in plugin bytecode: showDialog(I)V");

        Path ambiguousFinalApi = root.resolve("ambiguous-final-api.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(ambiguousFinalApi.toFile())))) {
            add(jar, "sample/FirstActivity.class", activity("sample/FirstActivity", null));
            add(jar, "sample/AmbiguousHelper.class", ambiguousFinalApiHelper());
        }
        expectFailure(ambiguousFinalApi, root.resolve("ambiguous-final-api-output.jar"),
                "Ambiguous final Activity API receiver in plugin bytecode");

        Path externalBase = root.resolve("external-base.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(externalBase.toFile())))) {
            add(jar, "sample/ExternalDerivedActivity.class", activity(
                    "sample/ExternalDerivedActivity", "external/HostBaseActivity", null));
        }
        expectFailure(externalBase, root.resolve("external-base-output.jar"),
                "base class must be packaged in the plugin payload closure");

        Path copiedHostBase = root.resolve("copied-host-base.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(copiedHostBase.toFile())))) {
            add(jar, "sample/SharedBaseActivity.class", activity(
                    "sample/SharedBaseActivity",
                    "dev/x2c/plugin/base/BasePluginActivity", null));
            add(jar, "dev/x2c/plugin/base/BasePluginActivity.class", baseActivity(
                    "dev/x2c/plugin/base/BasePluginActivity",
                    "dev/x2c/plugin/runtime/PluginActivity"));
        }
        expectFailure(copiedHostBase, root.resolve("copied-host-base-output.jar"),
                "Host-owned X2C API/runtime/base class must not be packaged");

        Path missingSharedAnnotation = root.resolve("missing-shared-annotation.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(
                        missingSharedAnnotation.toFile())))) {
            add(jar, "sample/FirstActivity.class", activity("sample/FirstActivity", null));
            add(jar, "sample/UnregisteredService.class", plainComponent(
                    "sample/UnregisteredService", "dev/x2c/plugin/base/BasePluginService"));
        }
        expectFailure(missingSharedAnnotation,
                root.resolve("missing-shared-annotation-output.jar"),
                "Every concrete Service in a plugin JAR must use @X2cPluginService");

        Path tooManyServices = root.resolve("too-many-services.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(tooManyServices.toFile())))) {
            for (int index = 0; index < 9; index++) {
                String name = "sample/OverflowService" + index;
                add(jar, name + ".class", component(
                        name, "android/app/Service",
                        "Ldev/x2c/plugin/api/X2cPluginService;", null));
            }
        }
        expectFailure(tooManyServices, root.resolve("service-overflow-output.jar"),
                "more than 8 Services");

        Path providerClient = root.resolve("provider-client.jar");
        try (JarOutputStream jar = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(providerClient.toFile())))) {
            add(jar, "sample/FirstActivity.class", activity("sample/FirstActivity", null));
            add(jar, "sample/UnsupportedProviderClient.class", unsupportedProviderClient());
        }
        expectFailure(providerClient, root.resolve("provider-client-output.jar"),
                "ContentProviderClient acquisition is not supported");
    }

    private static byte[] unsupportedProviderClient() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/UnsupportedProviderClient", null,
                "java/lang/Object", null);
        MethodVisitor acquire = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "acquire",
                "(Landroid/content/ContentResolver;Landroid/net/Uri;)Landroid/content/ContentProviderClient;",
                null,
                null);
        acquire.visitCode();
        acquire.visitVarInsn(Opcodes.ALOAD, 0);
        acquire.visitVarInsn(Opcodes.ALOAD, 1);
        acquire.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "android/content/ContentResolver", "acquireContentProviderClient",
                "(Landroid/net/Uri;)Landroid/content/ContentProviderClient;", false);
        acquire.visitInsn(Opcodes.ARETURN);
        acquire.visitMaxs(0, 0);
        acquire.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] unsupportedFinalApiHelper() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/UnsupportedHelper", null,
                "java/lang/Object", null);
        MethodVisitor show = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "show",
                "(Lsample/FirstActivity;)V",
                null,
                null);
        show.visitCode();
        show.visitVarInsn(Opcodes.ALOAD, 0);
        show.visitInsn(Opcodes.ICONST_1);
        show.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "sample/FirstActivity", "showDialog", "(I)V", false);
        show.visitInsn(Opcodes.RETURN);
        show.visitMaxs(0, 0);
        show.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] ambiguousFinalApiHelper() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/AmbiguousHelper", null,
                "java/lang/Object", null);
        MethodVisitor application = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "application",
                "(Landroid/app/Activity;)Landroid/app/Application;",
                null,
                null);
        application.visitCode();
        application.visitVarInsn(Opcodes.ALOAD, 0);
        application.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "android/app/Activity", "getApplication", "()Landroid/app/Application;", false);
        application.visitInsn(Opcodes.ARETURN);
        application.visitMaxs(0, 0);
        application.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] plainActivity(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                name, null, "android/app/Activity", null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, "android/app/Activity", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] plainComponent(String name, String superName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                name, null, superName, null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void expectFailure(Path input, Path output, String message) throws Exception {
        try {
            new ActivityJarTransformer().transform(input.toFile(), output.toFile(), "sample.plugin");
            throw new AssertionError("Expected transform failure containing: " + message);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message),
                    "Unexpected transform failure: " + expected.getMessage());
        }
    }

    private static void expectDependencyFailure(
            Path input, Path dependency, Path output, String message) throws Exception {
        try {
            new ActivityJarTransformer().transform(
                    input.toFile(), Collections.singleton(dependency.toFile()),
                    output.toFile(), "sample.plugin");
            throw new AssertionError("Expected dependency closure failure containing: " + message);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message),
                    "Unexpected dependency closure failure: " + expected.getMessage());
        }
    }

    private static void expectCompileOnlyFailure(
            Path input, Path compileOnly, Path output, String message) throws Exception {
        try {
            new ActivityJarTransformer().transform(
                    input.toFile(), Collections.<File>emptyList(),
                    Collections.singleton(compileOnly.toFile()),
                    output.toFile(), "sample.plugin");
            throw new AssertionError(
                    "Expected compileOnly closure failure containing: " + message);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(message),
                    "Unexpected compileOnly closure failure: " + expected.getMessage());
        }
    }

    private static void add(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        jar.putNextEntry(entry);
        jar.write(bytes);
        jar.closeEntry();
    }

    private static Map<String, byte[]> readJar(File file) throws Exception {
        Map<String, byte[]> classes = new HashMap<String, byte[]>();
        try (JarFile jar = new JarFile(file)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (java.io.InputStream input = jar.getInputStream(entry)) {
                        classes.put(entry.getName(), read(input));
                    }
                }
            }
        }
        return classes;
    }

    private static byte[] read(java.io.InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
