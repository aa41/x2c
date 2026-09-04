package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.javaName;

import java.util.Map;

/** Generates layout entry points and delegates statement emission per tree. */
final class LayoutSourceGenerator {
    private LayoutSourceGenerator() {}

    static String generateLayouts(String packageName, Model model, boolean pluginMode) {
        JavaSource out = new JavaSource(packageName, "X2cLayouts");
        out.line("import android.content.res.ColorStateList;");
        out.line("import android.content.Context;");
        out.line("import android.graphics.drawable.ColorDrawable;");
        out.line("import android.os.Build;");
        out.line("import android.util.TypedValue;");
        out.line("import android.view.Gravity;");
        out.line("import android.view.View;");
        out.line("import android.view.ViewGroup;");
        out.line("import android.view.SurfaceView;");
        out.line("import android.view.TextureView;");
        out.line("import android.widget.*;");
        out.line("import android.text.InputType;");
        out.line("import android.text.TextUtils;");
        out.line("import android.graphics.Typeface;");
        out.line("import android.view.inputmethod.EditorInfo;");
        out.blank();
        out.open("public final class X2cLayouts");
        out.line("private X2cLayouts() {}");
        for (Map.Entry<String, LayoutNode> item : model.layouts.entrySet()) {
            out.blank();
            out.open("public static View " + javaName(item.getKey()) + "(Context context)");
            LayoutEmitter emitter = new LayoutEmitter(out, model, pluginMode);
            String root = emitter.emit(item.getValue(), null, null);
            out.line("return " + root + ";");
            out.close();
        }
        out.close();
        return out.toString();
    }
}
