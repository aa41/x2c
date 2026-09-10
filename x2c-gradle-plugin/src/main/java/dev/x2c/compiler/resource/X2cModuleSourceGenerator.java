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
        if (pluginMode) {
            out.line("import dev.x2c.runtime.X2cResourceProvider;");
        }
        out.line("import java.util.Objects;");
        out.blank();
        out.open("public final class X2cModule");
        out.line("public static final String NAME = \"" + packageName + "\";");
        out.line("private static volatile boolean initialized;");
        if (pluginMode) {
            out.line("private static volatile X2cResourceProvider provider;");
        } else {
            out.line("private static volatile X2cResourceProviderImpl provider;");
        }
        out.line("private X2cModule() {}");
        out.blank();
        out.open("public static void init(Context context)");
        out.line("Objects.requireNonNull(context, \"context\");");
        out.line("if (initialized) return;");
        out.open("synchronized (X2cModule.class)");
        out.line("if (initialized) return;");
        out.line("X2C.requireInitialized(context);");
        if (pluginMode) {
            out.line("X2cResourceProvider created = X2C.createPluginResourceProvider("
                    + "context, new X2cResourceProviderImpl());");
            // Publish before registration so generated drawable/value dependency lookups are safe
            // as soon as the provider becomes visible through the host registry.
            out.line("provider = created;");
        } else {
            out.line("X2cResourceProviderImpl created = new X2cResourceProviderImpl(context);");
        }
        out.line("X2C.registerResourceProvider(NAME, created);");
        if (hasImages) {
            out.line("X2cImages.register();");
        }
        for (String layoutName : model.layoutIds.keySet()) {
            String javaName = javaName(layoutName);
            String layoutId = pluginMode
                    ? "R2.layout." + javaName
                    : "created.getIdentifier(\"layout\", \"" + layoutName + "\")";
            out.open("X2C.registerLayout(NAME, \"" + layoutName + "\", " + layoutId
                    + ", new LayoutFactory()");
            out.line("@Override public View create(Context factoryContext) {");
            out.indent();
            out.line("return X2cLayouts." + javaName + "(factoryContext);");
            out.unindent();
            out.line("}");
            out.closeWith(");");
        }
        if (!pluginMode) {
            out.line("provider = created;");
        }
        out.line("initialized = true;");
        out.close();
        out.close();
        out.blank();
        out.line("public static boolean isInitialized() { return initialized; }");
        if (!pluginMode) {
            out.blank();
            out.open("static int identifier(Context context, String type, String name)");
            out.line("if (!initialized) init(context);");
            out.line("return identifier(type, name);");
            out.close();
            out.blank();
            out.open("static int identifier(String type, String name)");
            out.line("X2cResourceProviderImpl current = provider;");
            out.line("if (current == null) throw new IllegalStateException(\"X2cModule.init(context) must be called before resolving resources for \" + NAME);");
            out.line("return current.getIdentifier(type, name);");
            out.close();
        } else {
            out.blank();
            out.open("static X2cResourceProvider provider()");
            out.line("X2cResourceProvider current = provider;");
            out.line("if (current == null) throw new IllegalStateException(\"X2cModule.init(context) must complete before resolving plugin resources for \" + NAME);");
            out.line("return current;");
            out.close();
        }
        out.close();
        return out.toString();
    }
}
