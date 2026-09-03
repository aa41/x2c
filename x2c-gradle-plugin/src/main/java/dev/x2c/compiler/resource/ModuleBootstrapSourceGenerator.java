package dev.x2c.compiler.resource;

/** Generates the convention-based entry point used to discover a module from business code. */
final class ModuleBootstrapSourceGenerator {
    static final String CLASS_NAME = "X2cModuleBootstrap";

    private ModuleBootstrapSourceGenerator() {}

    static String generateBootstrap(String bootstrapPackage, String generatedPackage) {
        JavaSource out = new JavaSource(bootstrapPackage, CLASS_NAME);
        out.line("import android.content.Context;");
        out.blank();
        out.open("public final class " + CLASS_NAME);
        out.line("private " + CLASS_NAME + "() {}");
        out.blank();
        out.open("public static String initialize(Context context)");
        out.line(generatedPackage + ".X2cModule.init(context);");
        out.line("return " + generatedPackage + ".X2cModule.NAME;");
        out.close();
        out.close();
        return out.toString();
    }
}
