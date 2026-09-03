package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.entry;
import static dev.x2c.compiler.resource.CollectionSupport.mapOfEntries;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import static dev.x2c.compiler.resource.FileSupport.fail;
import static dev.x2c.compiler.resource.JavaExpressions.floatValue;
import static dev.x2c.compiler.resource.XmlSupport.*;

import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

/** Parses primitive resource values and validates references against the compilation model. */
final class ResourceValueParser {
    private ResourceValueParser() {}

    static Fraction parseFraction(File file, String raw) {
        String value = raw.trim();
        boolean parent = value.endsWith("%p");
        String suffix = parent ? "%p" : "%";
        if (!value.endsWith(suffix)) {
            throw fail(file, "Fraction must end in % or %p: " + raw);
        }
        try {
            BigDecimal percent = new BigDecimal(value.substring(0, value.length() - suffix.length()));
            return new Fraction(percent.divide(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString(), parent);
        } catch (ArithmeticException | NumberFormatException error) {
            throw fail(file, "Invalid fraction: " + raw);
        }
    }

    static DimensionValue requireDimensionAttribute(File file, Element element, String name, Model model) {
        String value = requireAttribute(file, element, name);
        return resolveDimension(file, value, model);
    }

    static DimensionValue optionalDimensionAttribute(File file, Element element, String name, Model model) {
        String value = androidAttribute(element, name);
        return value.isEmpty() ? null : resolveDimension(file, value, model);
    }

    static ColorValue optionalColorAttribute(File file, Element element, String name, Model model) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("@color/")) {
            String reference = referenceName(value, "color");
            if (!model.colors.containsKey(reference)) {
                throw fail(file, "Unknown color reference: " + value);
            }
            return ColorValue.reference(reference);
        }
        return ColorValue.literal(parseColor(file, value));
    }

    static Boolean optionalBooleanAttribute(File file, Element element, String name, Model model) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("@bool/")) {
            String reference = referenceName(value, "bool");
            if (!model.bools.containsKey(reference)) {
                throw fail(file, "Unknown bool reference: " + value);
            }
            return model.bools.get(reference);
        }
        return parseBoolean(file, value, name);
    }

    static String optionalIntegerAttribute(File file, Element element, String name, Model model) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) {
            return null;
        }
        resolveInteger(file, value, model, name);
        int resolved = value.startsWith("@integer/")
                ? model.integers.get(referenceName(value, "integer")) : Integer.parseInt(value);
        if (resolved < 0) {
            throw fail(file, name + " must not be negative");
        }
        return value;
    }

    static Float optionalPositiveFloatAttribute(File file, Element element, String name) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) {
            return null;
        }
        float parsed = parseFloat(file, value, name);
        if (parsed <= 0f) {
            throw fail(file, name + " must be greater than zero");
        }
        return parsed;
    }

    static Float optionalUnitFloatAttribute(File file, Element element, String name) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) {
            return null;
        }
        float parsed = parseFloat(file, value, name);
        if (parsed < 0f || parsed > 1f) {
            throw fail(file, name + " must be between 0 and 1");
        }
        return parsed;
    }

    static int parseGradientAngle(File file, String value) {
        try {
            int angle = Integer.parseInt(value);
            if (angle < 0 || angle > 315 || angle % 45 != 0) {
                throw new NumberFormatException();
            }
            return angle;
        } catch (NumberFormatException error) {
            throw fail(file, "gradient angle must be a multiple of 45 from 0 through 315: " + value);
        }
    }

    static float optionalPercentAttribute(File file, Element element, String name, float defaultValue) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) return defaultValue;
        if (!value.endsWith("%") || value.endsWith("%p")) {
            throw fail(file, name + " must be a percentage such as 50%");
        }
        try {
            float result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
            if (!Float.isFinite(result) || result < 0f) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) {
            throw fail(file, "Invalid percentage for " + name + ": " + value);
        }
    }

    static float optionalFloatAttribute(File file, Element element, String name, float defaultValue) {
        String value = androidAttribute(element, name);
        return value.isEmpty() ? defaultValue : parseFloat(file, value, name);
    }

    static Pivot parsePivot(File file, String value) {
        if (value.isEmpty()) return new Pivot(0.5f, true);
        if (value.endsWith("%")) {
            return new Pivot(optionalPercentValue(file, value, "pivot"), true);
        }
        return new Pivot(parseFloat(file, value, "pivot"), false);
    }

    static float optionalPercentValue(File file, String value, String name) {
        try {
            float result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
            if (!Float.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) {
            throw fail(file, "Invalid percentage for " + name + ": " + value);
        }
    }

    static int optionalIntLiteralAttribute(File file, Element element, String name, int defaultValue) {
        String value = androidAttribute(element, name);
        if (value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw fail(file, name + " must be a signed 32-bit integer: " + value);
        }
    }

    static ColorValue requireColorReferenceOrLiteral(File file, Element element, String name, Model model) {
        String value = requireAttribute(file, element, name);
        if (value.startsWith("@color/")) {
            String reference = referenceName(value, "color");
            if (!model.colors.containsKey(reference)) {
                throw fail(file, "Unknown color reference: " + value);
            }
            return ColorValue.reference(reference);
        }
        return ColorValue.literal(parseColor(file, value));
    }

    static DimensionValue resolveDimension(File file, String value, Model model) {
        if (value.startsWith("@dimen/")) {
            String reference = referenceName(value, "dimen");
            if (!model.dimens.containsKey(reference)) {
                throw fail(file, "Unknown dimen reference: " + value);
            }
            return DimensionValue.reference(reference);
        }
        return DimensionValue.literal(parseDimension(file, value));
    }

    static void resolveColorOrDrawable(File file, String value, Model model, boolean colorOnly) {
        if (value.startsWith("@color/")) {
            String reference = referenceName(value, "color");
            if (!model.colors.containsKey(reference)) {
                throw fail(file, "Unknown color reference: " + value);
            }
            return;
        }
        if (value.startsWith("#")) {
            parseColor(file, value);
            return;
        }
        if (!colorOnly && value.startsWith("@drawable/")) {
            resolveGeneratedDrawable(file, value, model);
            return;
        }
        throw fail(file, "Unsupported " + (colorOnly ? "color" : "background") + " value: " + value);
    }

    static void resolveColor(File file, String value, Model model) {
        resolveColorOrDrawable(file, value, model, true);
    }

    static void resolveColorStateList(File file, String value, Model model) {
        if (value.startsWith("@color/") && model.colorSelectors.containsKey(referenceName(value, "color"))) {
            return;
        }
        resolveColor(file, value, model);
    }

    static void resolveForeground(File file, String value, Model model) {
        if (value.startsWith("@drawable/")) {
            resolveGeneratedDrawable(file, value, model);
        } else {
            resolveColor(file, value, model);
        }
    }

    static void resolveGeneratedDrawable(File file, String value, Model model) {
        String reference = referenceName(value, "drawable");
        String kind = model.drawableKinds.get(reference);
        if (kind == null || kind.equals("bitmap")) {
            throw fail(file, "Expected a synchronous generated drawable, got: " + value);
        }
    }

    static void resolveBitmapDrawable(File file, String value, Model model) {
        String reference = referenceName(value, "drawable");
        if (!model.bitmaps.containsKey(reference)) {
            throw fail(file, "Expected a CDN bitmap drawable, got: " + value);
        }
    }

    static void resolveBoolean(File file, String value, Model model, String name) {
        if (value.startsWith("@bool/")) {
            String reference = referenceName(value, "bool");
            if (!model.bools.containsKey(reference)) {
                throw fail(file, "Unknown bool reference: " + value);
            }
            return;
        }
        parseBoolean(file, value, name);
    }

    static void resolveInteger(File file, String value, Model model, String name) {
        if (value.startsWith("@integer/")) {
            String reference = referenceName(value, "integer");
            if (!model.integers.containsKey(reference)) {
                throw fail(file, "Unknown integer reference: " + value);
            }
            return;
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw fail(file, name + " must be an integer or @integer reference: " + value);
        }
    }

    static void resolveFraction(File file, String value, Model model) {
        if (value.startsWith("@fraction/")) {
            String reference = referenceName(value, "fraction");
            if (!model.fractions.containsKey(reference)) {
                throw fail(file, "Unknown fraction reference: " + value);
            }
            return;
        }
        parseFraction(file, value);
    }

    static void resolveDrawable(File file, String value, Model model) {
        String reference = referenceName(value, "drawable");
        if (!model.drawableKinds.containsKey(reference)) {
            throw fail(file, "Unknown drawable reference: " + value);
        }
    }

    static void resolveString(File file, String value, Model model) {
        if (!value.startsWith("@")) {
            return;
        }
        String reference = referenceName(value, "string");
        if (!model.strings.containsKey(reference)) {
            throw fail(file, "Unknown string reference: " + value);
        }
    }

    static void parseLayoutSize(File file, String value, Model model) {
        if (java.util.Arrays.asList("match_parent", "fill_parent", "wrap_content").contains(value)) {
            return;
        }
        resolveDimension(file, value, model);
    }

    static int parseColor(File file, String raw) {
        String value = raw.trim();
        if (!value.startsWith("#")) {
            throw fail(file, "Only literal hexadecimal colors are supported: " + value);
        }
        String hex = value.substring(1);
        try {
            switch (hex.length()) {
                case 3:
                    return (int) (0xFF000000L
                            | duplicateNibble(hex.charAt(0)) << 16
                            | duplicateNibble(hex.charAt(1)) << 8
                            | duplicateNibble(hex.charAt(2)));
                case 4:
                    return (duplicateNibble(hex.charAt(0)) << 24)
                            | (duplicateNibble(hex.charAt(1)) << 16)
                            | (duplicateNibble(hex.charAt(2)) << 8)
                            | duplicateNibble(hex.charAt(3));
                case 6:
                    return (int) (0xFF000000L | Long.parseLong(hex, 16));
                case 8:
                    return (int) Long.parseLong(hex, 16);
                default:
                    throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            throw fail(file, "Invalid color literal: " + value);
        }
    }

    static int duplicateNibble(char value) {
        int nibble = Character.digit(value, 16);
        if (nibble < 0) {
            throw new NumberFormatException();
        }
        return nibble * 17;
    }

    static Dimension parseDimension(File file, String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String unit : java.util.Arrays.asList("dip", "dp", "sp", "px")) {
            if (value.endsWith(unit)) {
                String number = value.substring(0, value.length() - unit.length());
                try {
                    BigDecimal parsed = new BigDecimal(number);
                    return new Dimension(parsed.stripTrailingZeros().toPlainString(), unit.equals("dip") ? "dp" : unit);
                } catch (NumberFormatException error) {
                    throw fail(file, "Invalid dimension: " + raw);
                }
            }
        }
        throw fail(file, "Dimension requires dp, sp, or px unit: " + raw);
    }

    static float parseFloat(File file, String value, String name) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw fail(file, name + " must be a finite number: " + value);
        }
    }

    static int parsePositiveInt(File file, String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw fail(file, name + " must be a positive integer: " + value);
        }
    }

    static void parseTextStyle(File file, String value) {
        Set<String> allowed = setOf("normal", "bold", "italic");
        for (String part : value.split("\\|")) {
            if (!allowed.contains(part)) {
                throw fail(file, "Unsupported textStyle value: " + part);
            }
        }
    }

    static void parseIntegerOrFlags(File file, String value, String name) {
        if (value.startsWith("@integer/")) {
            return;
        }
        try {
            Integer.decode(value);
            return;
        } catch (NumberFormatException ignored) {
            // Continue with the named Android flag allowlist.
        }
        Set<String> allowed = name.equals("inputType")
                ? setOf("none", "text", "textCapCharacters", "textCapWords", "textCapSentences", "textAutoCorrect",
                "textAutoComplete", "textMultiLine", "textImeMultiLine", "textNoSuggestions", "textUri",
                "textEmailAddress", "textEmailSubject", "textShortMessage", "textLongMessage", "textPersonName",
                "textPostalAddress", "textPassword", "textVisiblePassword", "textWebEditText", "textFilter",
                "textPhonetic", "textWebEmailAddress", "textWebPassword", "number", "numberSigned",
                "numberDecimal", "numberPassword", "phone", "datetime", "date", "time")
                : setOf("normal", "actionUnspecified", "actionNone", "actionGo", "actionSearch", "actionSend",
                "actionNext", "actionDone", "actionPrevious", "flagNoFullscreen", "flagNavigatePrevious",
                "flagNavigateNext", "flagNoExtractUi", "flagNoAccessoryAction", "flagNoEnterAction", "flagForceAscii");
        for (String part : value.split("\\|")) {
            if (!allowed.contains(part)) {
                throw fail(file, "Unsupported " + name + " flag: " + part);
            }
        }
    }

    static void parseColumnList(File file, String value, String name) {
        if (value.equals("*")) {
            if (!name.equals("stretchColumns") && !name.equals("shrinkColumns")) {
                throw fail(file, name + " does not support '*'");
            }
            return;
        }
        for (String part : value.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException error) {
                throw fail(file, name + " must be '*' or a comma-separated list of non-negative columns: " + value);
            }
        }
    }

    static void validateIdReference(File file, String value, Model model) {
        IdReference id = parseIdReference(file, value);
        if (id.kind == IdKind.DECLARE) {
            throw fail(file, "ID relation attributes must reference an existing @id, got: " + value);
        }
        if (id.kind == IdKind.LOCAL && !model.declaredIds.contains(id.name)) {
            throw fail(file, "Unknown ID reference: " + value);
        }
    }

    static boolean parseBoolean(File file, String value, String name) {
        if (!value.equals("true") && !value.equals("false")) {
            throw fail(file, name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    static int parseGravity(File file, String value) {
        Map<String, Integer> values = mapOfEntries(
                entry("top", 0x30), entry("bottom", 0x50),
                entry("left", 0x03), entry("right", 0x05),
                entry("center_vertical", 0x10), entry("center_horizontal", 0x01),
                entry("center", 0x11), entry("fill_vertical", 0x70),
                entry("fill_horizontal", 0x07), entry("fill", 0x77),
                entry("start", 0x00800003), entry("end", 0x00800005));
        int result = 0;
        for (String part : value.split("\\|")) {
            Integer flag = values.get(part);
            if (flag == null) {
                throw fail(file, "Unsupported gravity value: " + part);
            }
            result |= flag;
        }
        return result;
    }

    static String referenceName(String value, String type) {
        String prefix = "@" + type + "/";
        if (!value.startsWith(prefix)) {
            throw new ResourceCompilationException("Expected " + prefix + " reference, got: " + value);
        }
        String name = value.substring(prefix.length());
        validateResourceName(name, null);
        return name;
    }

    static IdReference parseIdReference(File file, String value) {
        String prefix;
        IdKind kind;
        if (value.startsWith("@+id/")) {
            prefix = "@+id/";
            kind = IdKind.DECLARE;
        } else if (value.startsWith("@id/")) {
            prefix = "@id/";
            kind = IdKind.LOCAL;
        } else if (value.startsWith("@android:id/")) {
            prefix = "@android:id/";
            kind = IdKind.ANDROID;
        } else {
            throw fail(file, "android:id must be @+id/name, @id/name, or @android:id/name; got: " + value);
        }
        String name = value.substring(prefix.length());
        validateResourceName(name, file);
        return new IdReference(kind, name);
    }
}
