package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.*;

import java.util.Locale;
import java.util.Map;

/** Generates Java factories for the supported drawable graph. */
final class DrawableSourceGenerator {
    private DrawableSourceGenerator() {}

    static String generateDrawables(String packageName, Model model, boolean pluginMode) {
        JavaSource out = new JavaSource(packageName, "X2cDrawables");
        out.line("import android.content.Context;");
        out.line("import android.graphics.drawable.*;");
        out.line("import android.util.TypedValue;");
        out.line("import android.view.Gravity;");
        out.blank();
        out.open("public final class X2cDrawables");
        out.line("private X2cDrawables() {}");
        for (Map.Entry<String, ShapeDrawable> item : model.shapes.entrySet()) {
            ShapeDrawable shape = item.getValue();
            out.blank();
            out.open("public static Drawable " + javaName(item.getKey()) + "(Context context)");
            if (hasShapePadding(shape)) {
                out.open("GradientDrawable drawable = new GradientDrawable() ");
                out.line("@Override public boolean getPadding(android.graphics.Rect padding) {");
                out.indent();
                out.line("padding.set(" + roundedDimensionExpression(shape.paddingLeft) + ", "
                        + roundedDimensionExpression(shape.paddingTop) + ", "
                        + roundedDimensionExpression(shape.paddingRight) + ", "
                        + roundedDimensionExpression(shape.paddingBottom) + ");");
                out.line("return true;");
                out.unindent();
                out.line("}");
                out.closeWith(";");
            } else {
                out.line("GradientDrawable drawable = new GradientDrawable();");
            }
            out.line("drawable.setShape(GradientDrawable." + shape.shape.toUpperCase(Locale.ROOT) + ");");
            if (shape.dither != null) {
                out.line("drawable.setDither(" + shape.dither + ");");
            }
            if (shape.useLevel != null) {
                out.line("drawable.setUseLevel(" + shape.useLevel + ");");
            }
            if (shape.solidColor != null) {
                out.line("drawable.setColor(" + colorExpression(shape.solidColor) + ");");
            }
            if (hasPerCornerRadius(shape)) {
                String fallback = dimensionExpressionOrZero(shape.cornerRadius);
                out.line("drawable.setCornerRadii(new float[] {"
                        + dimensionExpressionOr(shape.topLeftRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.topLeftRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.topRightRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.topRightRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.bottomRightRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.bottomRightRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.bottomLeftRadius, fallback) + ", "
                        + dimensionExpressionOr(shape.bottomLeftRadius, fallback) + "});");
            } else if (shape.cornerRadius != null) {
                out.line("drawable.setCornerRadius(" + dimensionExpression(shape.cornerRadius) + ");");
            }
            if (shape.strokeWidth != null) {
                if (shape.dashWidth != null) {
                    out.line("drawable.setStroke(Math.round(" + dimensionExpression(shape.strokeWidth) + "), "
                            + colorExpression(shape.strokeColor) + ", " + dimensionExpression(shape.dashWidth) + ", "
                            + dimensionExpression(shape.dashGap) + ");");
                } else {
                    out.line("drawable.setStroke(Math.round(" + dimensionExpression(shape.strokeWidth) + "), "
                            + colorExpression(shape.strokeColor) + ");");
                }
            }
            if (shape.width != null && shape.height != null) {
                out.line("drawable.setSize(Math.round(" + dimensionExpression(shape.width) + "), Math.round("
                        + dimensionExpression(shape.height) + "));");
            }
            if (shape.gradientStart != null) {
                String colors = shape.gradientCenter == null
                        ? colorExpression(shape.gradientStart) + ", " + colorExpression(shape.gradientEnd)
                        : colorExpression(shape.gradientStart) + ", " + colorExpression(shape.gradientCenter)
                        + ", " + colorExpression(shape.gradientEnd);
                out.line("drawable.setColors(new int[] {" + colors + "});");
                out.line("drawable.setGradientType(GradientDrawable." + shape.gradientType.toUpperCase(Locale.ROOT)
                        + "_GRADIENT);");
                if (shape.gradientType.equals("linear")) {
                    out.line("drawable.setOrientation(GradientDrawable.Orientation."
                            + gradientOrientation(shape.gradientAngle) + ");");
                }
                if (shape.gradientCenterX != null || shape.gradientCenterY != null) {
                    out.line("drawable.setGradientCenter("
                            + floatLiteral(Float.toString(shape.gradientCenterX == null ? 0.5f : shape.gradientCenterX))
                            + ", "
                            + floatLiteral(Float.toString(shape.gradientCenterY == null ? 0.5f : shape.gradientCenterY))
                            + ");");
                }
                if (shape.gradientRadius != null) {
                    out.line("drawable.setGradientRadius(" + dimensionExpression(shape.gradientRadius) + ");");
                }
                if (shape.gradientUseLevel != null) {
                    out.line("drawable.setUseLevel(" + shape.gradientUseLevel + ");");
                }
            }
            if (shape.innerRadius != null) {
                out.line("drawable.setInnerRadius(Math.round(" + dimensionExpression(shape.innerRadius) + "));");
            } else if (shape.innerRadiusRatio != null) {
                out.line("drawable.setInnerRadiusRatio(" + floatLiteral(Float.toString(shape.innerRadiusRatio)) + ");");
            }
            if (shape.thickness != null) {
                out.line("drawable.setThickness(Math.round(" + dimensionExpression(shape.thickness) + "));");
            } else if (shape.thicknessRatio != null) {
                out.line("drawable.setThicknessRatio(" + floatLiteral(Float.toString(shape.thicknessRatio)) + ");");
            }
            out.line("return drawable;");
            out.close();
        }
        for (Map.Entry<String, SelectorDrawable> item : model.selectors.entrySet()) {
            out.blank();
            out.open("public static Drawable " + javaName(item.getKey()) + "(Context context)");
            out.line("StateListDrawable drawable = new StateListDrawable();");
            SelectorDrawable selector = item.getValue();
            if (selector.dither != null) {
                out.line("drawable.setDither(" + selector.dither + ");");
            }
            if (selector.autoMirrored != null) {
                out.line("drawable.setAutoMirrored(" + selector.autoMirrored + ");");
            }
            if (selector.visible != null) {
                out.line("drawable.setVisible(" + selector.visible + ", false);");
            }
            if (selector.enterFadeDuration != null) {
                out.line("drawable.setEnterFadeDuration("
                        + integerValueExpression(selector.enterFadeDuration) + ");");
            }
            if (selector.exitFadeDuration != null) {
                out.line("drawable.setExitFadeDuration("
                        + integerValueExpression(selector.exitFadeDuration) + ");");
            }
            for (SelectorItem state : selector.items) {
                String states = state.states.entrySet().stream()
                        .map(entry -> (entry.getValue() ? "" : "-") + "android.R.attr." + entry.getKey())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("");
                out.line("drawable.addState(new int[] {" + states + "}, "
                        + javaName(state.drawable) + "(context));");
            }
            out.line("return drawable;");
            out.close();
        }
        for (Map.Entry<String, LayerListDrawable> item : model.layerLists.entrySet()) {
            LayerListDrawable layerList = item.getValue();
            out.blank();
            out.open("public static Drawable " + javaName(item.getKey()) + "(Context context)");
            out.line("LayerDrawable drawable = new LayerDrawable(new Drawable[] {"
                    + layerList.items.stream().map(layer -> javaName(layer.drawable) + "(context)")
                    .reduce((left, right) -> left + ", " + right).orElse("") + "});");
            if (layerList.autoMirrored != null) out.line("drawable.setAutoMirrored(" + layerList.autoMirrored + ");");
            if (!layerList.paddingMode.isEmpty()) {
                out.line("drawable.setPaddingMode(LayerDrawable.PADDING_MODE_"
                        + layerList.paddingMode.toUpperCase(Locale.ROOT) + ");");
            }
            for (int index = 0; index < layerList.items.size(); index++) {
                LayerItem layer = layerList.items.get(index);
                if (layer.start != null || layer.end != null) {
                    out.line("drawable.setLayerInsetRelative(" + index + ", "
                            + roundedDimensionExpression(layer.start) + ", " + roundedDimensionExpression(layer.top)
                            + ", " + roundedDimensionExpression(layer.end) + ", "
                            + roundedDimensionExpression(layer.bottom) + ");");
                } else if (layer.left != null || layer.top != null || layer.right != null || layer.bottom != null) {
                    out.line("drawable.setLayerInset(" + index + ", "
                            + roundedDimensionExpression(layer.left) + ", " + roundedDimensionExpression(layer.top)
                            + ", " + roundedDimensionExpression(layer.right) + ", "
                            + roundedDimensionExpression(layer.bottom) + ");");
                }
                if (layer.id != null) {
                    out.line("drawable.setId(" + index + ", "
                            + generatedIdExpression(layer.id, pluginMode) + ");");
                }
            }
            out.line("return drawable;");
            out.close();
        }
        for (Map.Entry<String, SingleDrawable> item : model.singleDrawables.entrySet()) {
            SingleDrawable value = item.getValue();
            out.blank();
            out.open("public static Drawable " + javaName(item.getKey()) + "(Context context)");
            String child = javaName(value.drawable) + "(context)";
            switch (value.kind) {
                case "inset":
                    out.line("return new InsetDrawable(" + child + ", "
                        + roundedDimensionExpression(value.left) + ", " + roundedDimensionExpression(value.top) + ", "
                        + roundedDimensionExpression(value.right) + ", " + roundedDimensionExpression(value.bottom) + ");");
                    break;
                case "clip":
                    out.line("return new ClipDrawable(" + child + ", "
                        + gravityExpression(value.gravity) + ", ClipDrawable."
                        + value.orientation.toUpperCase(Locale.ROOT) + ");");
                    break;
                case "scale":
                    out.line("return new ScaleDrawable(" + child + ", "
                        + gravityExpression(value.gravity) + ", " + floatLiteral(Float.toString(value.widthFactor))
                        + ", " + floatLiteral(Float.toString(value.heightFactor)) + ");");
                    break;
                case "rotate":
                    out.line("RotateDrawable drawable = new RotateDrawable();");
                    out.line("drawable.setDrawable(" + child + ");");
                    out.line("drawable.setFromDegrees(" + floatLiteral(Float.toString(value.fromDegrees)) + ");");
                    out.line("drawable.setToDegrees(" + floatLiteral(Float.toString(value.toDegrees)) + ");");
                    out.line("drawable.setPivotX(" + floatLiteral(Float.toString(value.pivotX.value)) + ");");
                    out.line("drawable.setPivotXRelative(" + value.pivotX.relative + ");");
                    out.line("drawable.setPivotY(" + floatLiteral(Float.toString(value.pivotY.value)) + ");");
                    out.line("drawable.setPivotYRelative(" + value.pivotY.relative + ");");
                    if (value.visible != null) out.line("drawable.setVisible(" + value.visible + ", false);");
                    out.line("return drawable;");
                    break;
                default:
                    throw new AssertionError(value.kind);
            }
            out.close();
        }
        for (Map.Entry<String, LevelListDrawable> item : model.levelLists.entrySet()) {
            out.blank();
            out.open("public static Drawable " + javaName(item.getKey()) + "(Context context)");
            out.line("LevelListDrawable drawable = new LevelListDrawable();");
            for (LevelItem level : item.getValue().items) {
                out.line("drawable.addLevel(" + level.min + ", " + level.max + ", "
                        + javaName(level.drawable) + "(context));");
            }
            out.line("return drawable;");
            out.close();
        }
        out.close();
        return out.toString();
    }
}
