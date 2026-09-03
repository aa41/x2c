package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;

import java.util.Map;

/** Generates ColorStateList factories. */
final class ColorSourceGenerator {
    private ColorSourceGenerator() {}

    static String generateColorStateLists(String packageName, Model model) {
        JavaSource out = new JavaSource(packageName, "X2cColorStateLists");
        out.line("import android.content.res.ColorStateList;");
        out.blank();
        out.open("public final class X2cColorStateLists");
        out.line("private X2cColorStateLists() {}");
        for (Map.Entry<String, ColorSelector> entry : model.colorSelectors.entrySet()) {
            out.blank();
            out.open("public static ColorStateList " + javaName(entry.getKey()) + "()");
            String states = entry.getValue().items.stream().map(item -> "new int[] {" + item.states.entrySet().stream()
                    .map(state -> (state.getValue() ? "" : "-") + "android.R.attr." + state.getKey())
                    .reduce((left, right) -> left + ", " + right).orElse("") + "}")
                    .reduce((left, right) -> left + ", " + right).orElse("");
            String colors = entry.getValue().items.stream().map(item -> colorExpression(item.color))
                    .reduce((left, right) -> left + ", " + right).orElse("");
            out.line("return new ColorStateList(new int[][] {" + states + "}, new int[] {" + colors + "});");
            out.close();
        }
        out.close();
        return out.toString();
    }
}
