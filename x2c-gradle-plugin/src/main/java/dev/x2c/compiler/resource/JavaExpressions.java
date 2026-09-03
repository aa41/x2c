package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.ResourceValueParser.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Converts validated model values into deterministic Java source expressions. */
final class JavaExpressions {
    private JavaExpressions() {}

    static String dimensionExpression(DimensionValue value) {
        if (value.reference != null) {
            return "X2cValues.Dimens." + javaName(value.reference) + "(context)";
        }
        if (value.literal.unit.equals("px")) {
            return floatLiteral(value.literal.value);
        }
        return "android.util.TypedValue.applyDimension(" + typedValueUnit(value.literal.unit) + ", "
                + floatLiteral(value.literal.value) + ", context.getResources().getDisplayMetrics())";
    }

    static boolean hasShapePadding(ShapeDrawable shape) {
        return shape.paddingLeft != null || shape.paddingTop != null
                || shape.paddingRight != null || shape.paddingBottom != null;
    }

    static boolean hasPerCornerRadius(ShapeDrawable shape) {
        return shape.topLeftRadius != null || shape.topRightRadius != null
                || shape.bottomRightRadius != null || shape.bottomLeftRadius != null;
    }

    static String dimensionExpressionOrZero(DimensionValue value) {
        return value == null ? "0.0f" : dimensionExpression(value);
    }

    static String dimensionExpressionOr(DimensionValue value, String fallback) {
        return value == null ? fallback : dimensionExpression(value);
    }

    static String roundedDimensionExpression(DimensionValue value) {
        return value == null ? "0" : "Math.round(" + dimensionExpression(value) + ")";
    }

    static String gradientOrientation(int angle) {
        switch (angle) {
            case 0: return "LEFT_RIGHT";
            case 45: return "BL_TR";
            case 90: return "BOTTOM_TOP";
            case 135: return "BR_TL";
            case 180: return "RIGHT_LEFT";
            case 225: return "TR_BL";
            case 270: return "TOP_BOTTOM";
            case 315: return "TL_BR";
            default: throw new AssertionError(angle);
        }
    }

    static String colorExpression(ColorValue value) {
        if (value.reference != null) {
            return "X2cValues.Colors." + javaName(value.reference);
        }
        return String.format(Locale.ROOT, "0x%08X", value.literal);
    }

    static String integerValueExpression(String value) {
        return value.startsWith("@integer/")
                ? "X2cValues.Integers." + javaName(referenceName(value, "integer"))
                : value;
    }

    static String generatedIdExpression(String value) {
        IdReference id = parseIdReference(null, value);
        return id.kind == IdKind.ANDROID
                ? "android.R.id." + javaName(id.name)
                : "R2.id." + javaName(id.name);
    }

    static String typedValueUnit(String unit) {
        if ("dp".equals(unit) || "dip".equals(unit)) {
            return "TypedValue.COMPLEX_UNIT_DIP";
        }
        if ("sp".equals(unit)) {
            return "TypedValue.COMPLEX_UNIT_SP";
        }
        if ("px".equals(unit)) {
            return "TypedValue.COMPLEX_UNIT_PX";
        }
        throw new IllegalArgumentException("Unknown dimension unit " + unit);
    }

    static String gravityExpression(String value) {
        return Arrays.stream(value.split("\\|"))
                .map(part -> "Gravity." + part.toUpperCase(Locale.ROOT))
                .reduce((left, right) -> left + " | " + right)
                .orElse("Gravity.NO_GRAVITY");
    }

    static String scaleTypeConstant(String value) {
        if ("center".equals(value)) return "CENTER";
        if ("centerCrop".equals(value)) return "CENTER_CROP";
        if ("centerInside".equals(value)) return "CENTER_INSIDE";
        if ("fitCenter".equals(value)) return "FIT_CENTER";
        if ("fitStart".equals(value)) return "FIT_START";
        if ("fitEnd".equals(value)) return "FIT_END";
        if ("fitXY".equals(value)) return "FIT_XY";
        if ("matrix".equals(value)) return "MATRIX";
        throw new IllegalArgumentException(value);
    }

    static String javaName(String resourceName) {
        if (new java.util.HashSet<String>(Arrays.asList("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
                "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
                "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while")).contains(resourceName)) {
            return resourceName + "_";
        }
        return resourceName;
    }

    static String javaString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': result.append("\\\\"); break;
                case '"': result.append("\\\""); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (character < 0x20 || character == 0x7F) {
                        result.append(String.format(Locale.ROOT, "\\u%04X", (int) character));
                    } else {
                        result.append(character);
                    }
            }
        }
        return result.append('"').toString();
    }

    static boolean containsFormatSpecifier(String value) {
        return value.matches(".*%(?:\\d+\\$)?[a-zA-Z].*");
    }

    static String floatLiteral(String value) {
        return value + (value.contains(".") ? "f" : ".0f");
    }

    static String floatValue(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }
}
