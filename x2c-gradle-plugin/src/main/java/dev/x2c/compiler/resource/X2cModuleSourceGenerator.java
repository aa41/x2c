package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.javaName;

/** Generates the small per-library registry consumed by the host-resident x2c-runtime module. */
final class X2cModuleSourceGenerator {
    private X2cModuleSourceGenerator() {}

    static String generateModule(
            String packageName, Model model, boolean pluginMode, boolean hasImages) {
        JavaSource out = new JavaSource(packageName, "X2cModule");
        out.line("import android.content.Context;");
        out.line("import android.view.View;");
        out.line("import dev.x2c.runtime.LayoutFactory;");
        out.line("import dev.x2c.runtime.X2C;");
        out.line("import java.util.Objects;");
        out.blank();
        out.open("public final class X2cModule");
        out.line("public static final String NAME = \"" + packageName + "\";");
        out.line("private static boolean initialized;");
        out.line("private X2cModule() {}");
        out.blank();
        out.open("public static synchronized void init(Context context)");
        out.line("Objects.requireNonNull(context, \"context\");");
        out.line("X2C.init(context);");
        if (!pluginMode) {
            out.line("R2.init(context);");
        }
        out.line("if (initialized) return;");
        out.line("X2C.registerResourceProvider(NAME, new X2cResourceProviderImpl("
                + (pluginMode ? "" : "context") + "));");
        if (hasImages) {
            out.line("X2cImages.register();");
        }
        for (String layoutName : model.layoutIds.keySet()) {
            String javaName = javaName(layoutName);
            out.open("X2C.registerLayout(NAME, \"" + layoutName + "\", R2.layout." + javaName
                    + ", new LayoutFactory()");
            out.line("@Override public View create(Context factoryContext) {");
            out.indent();
            out.line("return X2cLayouts." + javaName + "(factoryContext);");
            out.unindent();
            out.line("}");
            out.closeWith(");");
        }
        out.line("initialized = true;");
        out.close();
        out.blank();
        out.line("public static synchronized boolean isInitialized() { return initialized; }");
        out.close();
        return out.toString();
    }
}
