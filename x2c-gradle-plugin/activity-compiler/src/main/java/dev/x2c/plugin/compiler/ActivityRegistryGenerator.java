package dev.x2c.plugin.compiler;

import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Emits the one generated, direct-constructor component registry in each isolated plugin JAR. */
final class ActivityRegistryGenerator {
    private static final String REGISTRY_INTERNAL_NAME =
            ActivityJarTransformer.REGISTRY_CLASS_NAME.replace('.', '/');
    private static final String REGISTRY = "dev/x2c/plugin/api/PluginComponentRegistry";
    private static final String INFO = "dev/x2c/plugin/api/PluginActivityInfo";
    private static final String MODE = "dev/x2c/plugin/api/PluginLaunchMode";
    private static final String PROVIDER_INFO = "dev/x2c/plugin/api/PluginProviderInfo";

    private ActivityRegistryGenerator() {}

    static byte[] generate(
            String pluginId,
            List<TransformedActivity> activities,
            List<TransformedComponent> services,
            List<TransformedComponent> receivers,
            List<TransformedComponent> providers) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                REGISTRY_INTERNAL_NAME, null, "java/lang/Object", new String[] {REGISTRY});
        emitConstructor(writer);
        emitRuntimeAbiVersion(writer);
        emitPluginId(writer, pluginId);
        emitContains(writer, activities);
        emitInfo(writer, activities);
        emitCreate(writer, activities);
        emitComponentContains(writer, "containsService", services);
        emitComponentCreate(writer, "createService", "android/app/Service", services);
        emitComponentContains(writer, "containsReceiver", receivers);
        emitComponentCreate(
                writer, "createReceiver", "android/content/BroadcastReceiver", receivers);
        emitComponentContains(writer, "containsProvider", providers);
        emitProviderInfo(writer, providers);
        emitProviders(writer, providers);
        emitComponentCreate(
                writer, "createProvider", "android/content/ContentProvider", providers);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emitRuntimeAbiVersion(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "runtimeAbiVersion", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(Integer.valueOf(ActivityJarTransformer.CURRENT_RUNTIME_ABI));
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitPluginId(ClassWriter writer, String pluginId) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "pluginId", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn(pluginId);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitContains(ClassWriter writer, List<TransformedActivity> activities) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "contains", "(Ljava/lang/String;)Z", null, null);
        method.visitCode();
        for (TransformedActivity activity : activities) {
            Label next = new Label();
            emitNameEquals(method, activity.className, next);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
            method.visitLabel(next);
        }
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitInfo(ClassWriter writer, List<TransformedActivity> activities) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "info", "(Ljava/lang/String;)L" + INFO + ";", null, null);
        method.visitCode();
        for (TransformedActivity activity : activities) {
            Label next = new Label();
            emitNameEquals(method, activity.className, next);
            method.visitTypeInsn(Opcodes.NEW, INFO);
            method.visitInsn(Opcodes.DUP);
            method.visitLdcInsn(activity.className);
            method.visitFieldInsn(Opcodes.GETSTATIC, MODE, activity.launchMode, "L" + MODE + ";");
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, INFO, "<init>",
                    "(Ljava/lang/String;L" + MODE + ";)V", false);
            method.visitInsn(Opcodes.ARETURN);
            method.visitLabel(next);
        }
        emitUnknown(method, "Activity");
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitCreate(ClassWriter writer, List<TransformedActivity> activities) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "create", "(Ljava/lang/String;)Landroid/app/Activity;", null, null);
        method.visitCode();
        for (TransformedActivity activity : activities) {
            Label next = new Label();
            emitNameEquals(method, activity.className, next);
            method.visitTypeInsn(Opcodes.NEW, activity.internalName);
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, activity.internalName, "<init>", "()V", false);
            method.visitInsn(Opcodes.ARETURN);
            method.visitLabel(next);
        }
        emitUnknown(method, "Activity");
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitComponentContains(
            ClassWriter writer, String methodName, List<TransformedComponent> components) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, methodName, "(Ljava/lang/String;)Z", null, null);
        method.visitCode();
        for (TransformedComponent component : components) {
            Label next = new Label();
            emitNameEquals(method, component.className, next);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
            method.visitLabel(next);
        }
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitComponentCreate(
            ClassWriter writer,
            String methodName,
            String returnType,
            List<TransformedComponent> components) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, methodName,
                "(Ljava/lang/String;)L" + returnType + ";", null, null);
        method.visitCode();
        for (TransformedComponent component : components) {
            Label next = new Label();
            emitNameEquals(method, component.className, next);
            method.visitTypeInsn(Opcodes.NEW, component.internalName);
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, component.internalName, "<init>", "()V", false);
            method.visitInsn(Opcodes.ARETURN);
            method.visitLabel(next);
        }
        emitUnknown(method, componentLabel(methodName));
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitProviderInfo(
            ClassWriter writer, List<TransformedComponent> providers) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "providerInfo",
                "(Ljava/lang/String;)L" + PROVIDER_INFO + ";", null, null);
        method.visitCode();
        for (TransformedComponent provider : providers) {
            Label next = new Label();
            emitNameEquals(method, provider.className, next);
            method.visitTypeInsn(Opcodes.NEW, PROVIDER_INFO);
            method.visitInsn(Opcodes.DUP);
            method.visitLdcInsn(provider.className);
            method.visitLdcInsn(provider.authority);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, PROVIDER_INFO, "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V", false);
            method.visitInsn(Opcodes.ARETURN);
            method.visitLabel(next);
        }
        emitUnknown(method, "ContentProvider");
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitProviders(
            ClassWriter writer, List<TransformedComponent> providers) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "providers",
                "()[L" + PROVIDER_INFO + ";", null, null);
        method.visitCode();
        pushInt(method, providers.size());
        method.visitTypeInsn(Opcodes.ANEWARRAY, PROVIDER_INFO);
        for (int index = 0; index < providers.size(); index++) {
            TransformedComponent provider = providers.get(index);
            method.visitInsn(Opcodes.DUP);
            pushInt(method, index);
            method.visitTypeInsn(Opcodes.NEW, PROVIDER_INFO);
            method.visitInsn(Opcodes.DUP);
            method.visitLdcInsn(provider.className);
            method.visitLdcInsn(provider.authority);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, PROVIDER_INFO, "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V", false);
            method.visitInsn(Opcodes.AASTORE);
        }
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void pushInt(MethodVisitor method, int value) {
        if (value >= 0 && value <= 5) {
            method.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value <= Byte.MAX_VALUE) {
            method.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value <= Short.MAX_VALUE) {
            method.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            method.visitLdcInsn(value);
        }
    }

    private static void emitNameEquals(MethodVisitor method, String name, Label falseLabel) {
        method.visitLdcInsn(name);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                "(Ljava/lang/Object;)Z", false);
        method.visitJumpInsn(Opcodes.IFEQ, falseLabel);
    }

    private static String componentLabel(String createMethodName) {
        if ("createService".equals(createMethodName)) return "Service";
        if ("createReceiver".equals(createMethodName)) return "BroadcastReceiver";
        if ("createProvider".equals(createMethodName)) return "ContentProvider";
        return "component";
    }

    private static void emitUnknown(MethodVisitor method, String componentLabel) {
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        method.visitInsn(Opcodes.DUP);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("Unknown plugin " + componentLabel + ": ");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
                "(Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                "()Ljava/lang/String;", false);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
    }
}
