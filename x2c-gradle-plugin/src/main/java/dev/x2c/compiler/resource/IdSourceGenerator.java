package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;

import java.util.Locale;
import java.util.Map;

/** Generates either synthetic plugin constants or a stateless normal-integration facade. */
final class IdSourceGenerator {
    private IdSourceGenerator() {}

    static String generateR2(String packageName, Model model, boolean pluginMode) {
        JavaSource out = new JavaSource(packageName, "R2");
        if (!pluginMode) {
            out.line("import android.content.Context;");
            out.blank();
        }
        out.open("public final class R2");
        out.line("private R2() {}");
        if (!pluginMode) {
            out.blank();
            out.open("private static int identifier(Context context, String type, String name)");
            out.line("return X2cModule.identifier(context, type, name);");
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
                    out.open("public static int " + javaName(item.getKey()) + "(Context context)");
                    out.line("return identifier(context, " + javaString(type.getKey()) + ", "
                            + javaString(item.getKey()) + ");");
                    out.close();
                }
            }
            out.close();
        }
        out.close();
        return out.toString();
    }
}
