package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;
import static dev.x2c.compiler.resource.ResourceValueParser.*;

import java.util.Locale;
import java.util.List;
import java.util.Map;

/** Generates scalar, array, plural and dimension value classes. */
final class ValuesSourceGenerator {
    private ValuesSourceGenerator() {}

    static String generateValues(String packageName, Model model) {
        JavaSource out = new JavaSource(packageName, "X2cValues");
        out.line("import android.content.Context;");
        out.line("import android.util.TypedValue;");
        out.blank();
        out.open("public final class X2cValues");
        out.line("private X2cValues() {}");
        out.blank();
        out.open("public static final class Strings");
        out.line("private Strings() {}");
        for (Map.Entry<String, String> item : model.strings.entrySet()) {
            out.line("public static final String " + javaName(item.getKey()) + " = " + javaString(item.getValue()) + ";");
            if (containsFormatSpecifier(item.getValue())) {
                out.line("public static String format_" + javaName(item.getKey()) + "(Object... args) {");
                out.indent();
                out.line("return String.format(java.util.Locale.getDefault(), " + javaName(item.getKey()) + ", args);");
                out.unindent();
                out.line("}");
            }
        }
        out.close();
        out.blank();
        out.open("public static final class Colors");
        out.line("private Colors() {}");
        for (Map.Entry<String, Integer> item : model.colors.entrySet()) {
            out.line(String.format(Locale.ROOT, "public static final int %s = 0x%08X;", javaName(item.getKey()), item.getValue()));
        }
        out.close();
        out.blank();
        out.open("public static final class Bools");
        out.line("private Bools() {}");
        for (Map.Entry<String, Boolean> item : model.bools.entrySet()) {
            out.line("public static final boolean " + javaName(item.getKey()) + " = " + item.getValue() + ";");
        }
        out.close();
        out.blank();
        out.open("public static final class Integers");
        out.line("private Integers() {}");
        for (Map.Entry<String, Integer> item : model.integers.entrySet()) {
            out.line("public static final int " + javaName(item.getKey()) + " = " + item.getValue() + ";");
        }
        out.close();
        out.blank();
        out.open("public static final class Dimens");
        out.line("private Dimens() {}");
        for (Map.Entry<String, Dimension> item : model.dimens.entrySet()) {
            out.open("public static float " + javaName(item.getKey()) + "(Context context)");
            out.line("return TypedValue.applyDimension(" + typedValueUnit(item.getValue().unit) + ", "
                    + floatLiteral(item.getValue().value) + ", context.getResources().getDisplayMetrics());");
            out.close();
        }
        out.close();
        out.blank();
        out.open("public static final class Fractions");
        out.line("private Fractions() {}");
        for (Map.Entry<String, Fraction> item : model.fractions.entrySet()) {
            out.open("public static float " + javaName(item.getKey()) + "(float base, float parentBase)");
            out.line("return " + floatLiteral(item.getValue().factor) + " * "
                    + (item.getValue().parent ? "parentBase" : "base") + ";");
            out.close();
        }
        out.close();
        out.blank();
        out.open("public static final class StringArrays");
        out.line("private StringArrays() {}");
        for (Map.Entry<String, List<String>> item : model.stringArrays.entrySet()) {
            out.open("public static String[] " + javaName(item.getKey()) + "()");
            out.line("return new String[] {" + item.getValue().stream()
                    .map(value -> stringValueExpression(value.trim(), value, model))
                    .reduce((left, right) -> left + ", " + right).orElse("") + "};");
            out.close();
        }
        out.close();
        out.blank();
        out.open("public static final class IntegerArrays");
        out.line("private IntegerArrays() {}");
        for (Map.Entry<String, List<String>> item : model.integerArrays.entrySet()) {
            out.open("public static int[] " + javaName(item.getKey()) + "()");
            out.line("return new int[] {" + item.getValue().stream().map(JavaExpressions::integerValueExpression)
                    .reduce((left, right) -> left + ", " + right).orElse("") + "};");
            out.close();
        }
        out.close();
        out.blank();
        out.open("public static final class FractionValue");
        out.line("public final float factor;");
        out.line("public final boolean parent;");
        out.open("private FractionValue(float factor, boolean parent)");
        out.line("this.factor = factor;");
        out.line("this.parent = parent;");
        out.close();
        out.open("public float resolve(float base, float parentBase)");
        out.line("return factor * (parent ? parentBase : base);");
        out.close();
        out.close();
        out.blank();
        out.open("public static final class TypedArrays");
        out.line("private TypedArrays() {}");
        for (Map.Entry<String, List<TypedValueItem>> item : model.typedArrays.entrySet()) {
            out.open("public static Object[] " + javaName(item.getKey()) + "(Context context)");
            out.line("return new Object[] {" + item.getValue().stream()
                    .map(value -> typedArrayItemExpression(value, model))
                    .reduce((left, right) -> left + ", " + right).orElse("") + "};");
            out.close();
        }
        out.close();
        out.blank();
        out.open("public enum Quantity");
        out.line("ZERO, ONE, TWO, FEW, MANY, OTHER");
        out.close();
        out.blank();
        out.open("public static final class Plurals");
        out.line("private Plurals() {}");
        for (Map.Entry<String, Map<String, String>> item : model.plurals.entrySet()) {
            out.open("public static String " + javaName(item.getKey()) + "(Quantity quantity, Object... args)");
            out.line("String template;");
            out.line("switch (quantity) {");
            out.indent();
            for (Map.Entry<String, String> quantity : item.getValue().entrySet()) {
                if (!quantity.getKey().equals("other")) {
                    out.line("case " + quantity.getKey().toUpperCase(Locale.ROOT) + ": template = "
                            + stringValueExpression(quantity.getValue().trim(), quantity.getValue(), model)
                            + "; break;");
                }
            }
            out.line("default: template = " + stringValueExpression(item.getValue().get("other").trim(),
                    item.getValue().get("other"), model) + "; break;");
            out.unindent();
            out.line("}");
            out.line("return args.length == 0 ? template : String.format(java.util.Locale.getDefault(), template, args);");
            out.close();
        }
        out.close();
        out.close();
        return out.toString();
    }

    private static String stringValueExpression(String trimmed, String original, Model model) {
        return trimmed.startsWith("@string/")
                ? "X2cValues.Strings." + javaName(referenceName(trimmed, "string"))
                : javaString(original);
    }

    private static String typedArrayItemExpression(TypedValueItem item, Model model) {
        String value = item.value.trim();
        if ("STRING".equals(item.kind)) {
            return stringValueExpression(value, item.value, model);
        }
        if ("COLOR".equals(item.kind)) {
            return value.startsWith("@color/")
                    ? "X2cValues.Colors." + javaName(referenceName(value, "color"))
                    : String.format(Locale.ROOT, "0x%08X", parseColor(null, value));
        }
        if ("DIMENSION".equals(item.kind)) {
            return value.startsWith("@dimen/")
                    ? "X2cValues.Dimens." + javaName(referenceName(value, "dimen")) + "(context)"
                    : dimensionExpression(DimensionValue.literal(parseDimension(null, value)));
        }
        if ("BOOLEAN".equals(item.kind)) {
            return value.startsWith("@bool/")
                    ? "X2cValues.Bools." + javaName(referenceName(value, "bool")) : value;
        }
        if ("INTEGER".equals(item.kind)) {
            return integerValueExpression(value);
        }
        if ("FRACTION".equals(item.kind)) {
            Fraction fraction = value.startsWith("@fraction/")
                    ? model.fractions.get(referenceName(value, "fraction")) : parseFraction(null, value);
            return "new FractionValue(" + floatLiteral(fraction.factor) + ", " + fraction.parent + ")";
        }
        throw new AssertionError(item.kind);
    }
}
