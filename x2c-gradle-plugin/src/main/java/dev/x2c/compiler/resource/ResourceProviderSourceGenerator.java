package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;

import java.util.Map;

/** Generates the private adapter between generated implementation classes and x2c-runtime. */
final class ResourceProviderSourceGenerator {
    private ResourceProviderSourceGenerator() {}

    static String generateResourceProvider(String packageName, Model model, boolean pluginMode) {
        JavaSource out = new JavaSource(packageName, "X2cResourceProviderImpl");
        out.line("import android.content.Context;");
        out.line("import android.content.res.ColorStateList;");
        out.line("import android.content.res.Resources;");
        out.line("import android.graphics.drawable.Drawable;");
        out.line("import dev.x2c.runtime.X2cQuantity;");
        out.line("import dev.x2c.runtime.X2cResourceProvider;");
        if (!pluginMode) {
            out.line("import java.util.Objects;");
        }
        out.blank();
        out.open("final class X2cResourceProviderImpl implements X2cResourceProvider");

        if (!pluginMode) {
            out.line("private final Context context;");
            out.blank();
            out.open("X2cResourceProviderImpl(Context context)");
            out.line("Context application = Objects.requireNonNull(context, \"context\").getApplicationContext();");
            out.line("this.context = application == null ? context : application;");
            out.close();
        }

        generateIdentifier(out, model, pluginMode);
        generateStrings(out, model);
        generateColors(out, model);
        generateBooleans(out, model);
        generateIntegers(out, model);
        generateDimensions(out, model);
        generateFractions(out, model);
        generateStringArrays(out, model);
        generateIntegerArrays(out, model);
        generateTypedArrays(out, model);
        generatePlurals(out, model);
        generateDrawables(out, model, pluginMode);

        out.blank();
        out.open("private static IllegalArgumentException unknown(String type, String name)");
        out.line("return new IllegalArgumentException(\"Unknown X2C resource @" + packageName
                + ":\" + type + \"/\" + name);");
        out.close();
        out.close();
        return out.toString();
    }

    private static void generateIdentifier(JavaSource out, Model model, boolean pluginMode) {
        out.blank();
        out.line("@Override");
        out.open("public int getIdentifier(String type, String name)");
        if (!pluginMode) {
            out.line("requireDeclared(type, name);");
            out.line("int identifier = context.getResources().getIdentifier(name, type, context.getPackageName());");
            out.line("if (identifier == 0) throw new Resources.NotFoundException(\"Missing host resource @\" + type + \"/\" + name + \" in \" + context.getPackageName());");
            out.line("return identifier;");
            out.close();
            out.blank();
            out.open("private static void requireDeclared(String type, String name)");
            out.line("switch (type) {");
            out.indent();
            for (String type : model.r2Ids.keySet()) {
                out.line("case " + javaString(type) + ": requireDeclared_" + javaName(type) + "(name); return;");
            }
            out.line("default: throw unknown(type, name);");
            out.unindent();
            out.line("}");
            out.close();
            for (Map.Entry<String, Map<String, Integer>> type : model.r2Ids.entrySet()) {
                out.blank();
                out.open("private static void requireDeclared_" + javaName(type.getKey()) + "(String name)");
                out.line("switch (name) {");
                out.indent();
                for (String name : type.getValue().keySet()) {
                    out.line("case " + javaString(name) + ": return;");
                }
                out.line("default: throw unknown(" + javaString(type.getKey()) + ", name);");
                out.unindent();
                out.line("}");
                out.close();
            }
            return;
        }
        out.line("switch (type) {");
        out.indent();
        for (String type : model.r2Ids.keySet()) {
            out.line("case " + javaString(type) + ": return identifier_" + javaName(type) + "(name);");
        }
        out.line("default: throw unknown(type, name);");
        out.unindent();
        out.line("}");
        out.close();
        for (Map.Entry<String, Map<String, Integer>> type : model.r2Ids.entrySet()) {
            out.blank();
            out.open("private static int identifier_" + javaName(type.getKey()) + "(String name)");
            out.line("switch (name) {");
            out.indent();
            for (String name : type.getValue().keySet()) {
                out.line("case " + javaString(name) + ": return R2." + type.getKey() + "." + javaName(name) + ";");
            }
            out.line("default: throw unknown(" + javaString(type.getKey()) + ", name);");
            out.unindent();
            out.line("}");
            out.close();
        }
    }

    private static void generateStrings(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public String getString(String name, Object... arguments)");
        out.line("switch (name) {");
        out.indent();
        for (Map.Entry<String, String> item : model.strings.entrySet()) {
            String field = "X2cValues.Strings." + javaName(item.getKey());
            String value = containsFormatSpecifier(item.getValue())
                    ? "arguments.length == 0 ? " + field + " : X2cValues.Strings.format_"
                            + javaName(item.getKey()) + "(arguments)"
                    : field;
            out.line("case " + javaString(item.getKey()) + ": return " + value + ";");
        }
        out.line("default: throw unknown(\"string\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateColors(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public int getColor(String name)");
        out.line("return getColorStateList(name).getDefaultColor();");
        out.close();
        out.blank();
        out.line("@Override");
        out.open("public ColorStateList getColorStateList(String name)");
        out.line("switch (name) {");
        out.indent();
        for (Map.Entry<String, String> item : model.colorKinds.entrySet()) {
            String expression = "selector".equals(item.getValue())
                    ? "X2cColorStateLists." + javaName(item.getKey()) + "()"
                    : "ColorStateList.valueOf(X2cValues.Colors." + javaName(item.getKey()) + ")";
            out.line("case " + javaString(item.getKey()) + ": return " + expression + ";");
        }
        out.line("default: throw unknown(\"color\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateBooleans(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public boolean getBoolean(String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.bools.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.Bools." + javaName(name) + ";");
        }
        out.line("default: throw unknown(\"bool\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateIntegers(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public int getInteger(String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.integers.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.Integers." + javaName(name) + ";");
        }
        out.line("default: throw unknown(\"integer\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateDimensions(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public float getDimension(Context context, String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.dimens.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.Dimens." + javaName(name) + "(context);");
        }
        out.line("default: throw unknown(\"dimen\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateFractions(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public float getFraction(String name, float base, float parentBase)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.fractions.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.Fractions." + javaName(name)
                    + "(base, parentBase);");
        }
        out.line("default: throw unknown(\"fraction\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateStringArrays(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public String[] getStringArray(String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.stringArrays.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.StringArrays." + javaName(name) + "();");
        }
        out.line("default: throw unknown(\"string-array\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateIntegerArrays(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public int[] getIntegerArray(String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.integerArrays.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.IntegerArrays." + javaName(name) + "();");
        }
        out.line("default: throw unknown(\"integer-array\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateTypedArrays(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public Object[] getArray(Context context, String name)");
        out.line("switch (name) {");
        out.indent();
        for (String name : model.typedArrays.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.TypedArrays." + javaName(name) + "(context);");
        }
        out.line("default: throw unknown(\"array\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generatePlurals(JavaSource out, Model model) {
        out.blank();
        out.line("@Override");
        out.open("public String getPlural(String name, X2cQuantity quantity, Object... arguments)");
        if (!model.plurals.isEmpty()) {
            out.line("X2cValues.Quantity generatedQuantity = X2cValues.Quantity.valueOf(quantity.name());");
        }
        out.line("switch (name) {");
        out.indent();
        for (String name : model.plurals.keySet()) {
            out.line("case " + javaString(name) + ": return X2cValues.Plurals." + javaName(name)
                    + "(generatedQuantity, arguments);");
        }
        out.line("default: throw unknown(\"plurals\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }

    private static void generateDrawables(JavaSource out, Model model, boolean pluginMode) {
        out.blank();
        out.line("@Override");
        out.open("public Drawable getDrawable(Context context, String name)");
        out.line("switch (name) {");
        out.indent();
        for (Map.Entry<String, String> item : model.drawableKinds.entrySet()) {
            if ("bitmap".equals(item.getValue()) && !pluginMode) {
                out.line("case " + javaString(item.getKey())
                        + ": return context.getResources().getDrawable(getIdentifier(\"drawable\", name), context.getTheme());");
            } else if (!"bitmap".equals(item.getValue())) {
                out.line("case " + javaString(item.getKey()) + ": return X2cDrawables."
                        + javaName(item.getKey()) + "(context);");
            }
        }
        out.line("default: throw unknown(\""
                + (pluginMode ? "drawable (CDN bitmaps use image())" : "drawable")
                + "\", name);");
        out.unindent();
        out.line("}");
        out.close();
    }
}
