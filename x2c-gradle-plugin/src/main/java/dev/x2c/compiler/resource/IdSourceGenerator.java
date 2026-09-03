package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;

import java.util.Locale;
import java.util.Map;

/** Generates either the synthetic plugin R2 or a host-resource-backed integration R2. */
final class IdSourceGenerator {
    private IdSourceGenerator() {}

    static String generateR2(String packageName, Model model, boolean pluginMode) {
        JavaSource out = new JavaSource(packageName, "R2");
        if (!pluginMode) {
            out.line("import android.content.Context;");
            out.line("import android.content.res.Resources;");
            out.line("import java.util.Objects;");
            out.blank();
        }
        out.open("public final class R2");
        out.line("private R2() {}");
        if (!pluginMode) {
            out.line("private static boolean initialized;");
            out.line("private static String initializedPackage;");
            out.blank();
            out.open("public static synchronized void init(Context context)");
            out.line("Objects.requireNonNull(context, \"context\");");
            out.line("String packageName = context.getPackageName();");
            out.line("if (initialized) {");
            out.indent();
            out.line("if (!initializedPackage.equals(packageName)) throw new IllegalStateException(\"R2 is already initialized for \" + initializedPackage + \"; cannot bind \" + packageName);");
            out.line("return;");
            out.unindent();
            out.line("}");
            for (Map.Entry<String, Map<String, Integer>> type : model.r2Ids.entrySet()) {
                for (String name : type.getValue().keySet()) {
                    out.line(type.getKey() + "." + javaName(name) + " = requireIdentifier(context, \""
                            + name + "\", \"" + type.getKey() + "\");");
                }
            }
            out.line("initializedPackage = packageName;");
            out.line("initialized = true;");
            out.close();
            out.blank();
            out.line("public static synchronized boolean isInitialized() { return initialized; }");
            out.blank();
            out.open("private static int requireIdentifier(Context context, String resName, String resType)");
            out.line("int value = context.getResources().getIdentifier(resName, resType, context.getPackageName());");
            out.line("if (value == 0) throw new Resources.NotFoundException(\"Missing host resource @\" + resType + \"/\" + resName + \" in \" + context.getPackageName());");
            out.line("return value;");
            out.close();
        }
        for (Map.Entry<String, Map<String, Integer>> type : model.r2Ids.entrySet()) {
            out.blank();
            out.open("public static final class " + type.getKey());
            out.line("private " + type.getKey() + "() {}");
            for (Map.Entry<String, Integer> item : type.getValue().entrySet()) {
                if (pluginMode) {
                    out.line(String.format(Locale.ROOT, "public static final int %s = 0x%08X;",
                            javaName(item.getKey()), item.getValue()));
                } else {
                    out.line("public static int " + javaName(item.getKey()) + ";");
                }
            }
            out.close();
        }
        out.close();
        return out.toString();
    }
}
