package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FileSupport.fail;
import static dev.x2c.compiler.resource.ResourceValueParser.*;
import static dev.x2c.compiler.resource.XmlSupport.*;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Parses and validates values XML into the shared compilation model. */
final class ValuesResourceParser {
    private static final Set<String> UNSUPPORTED_STYLE_RESOURCE_TYPES = setOf("attr", "declare-styleable", "style");

    private ValuesResourceParser() {}

    static void parseValues(ResourceFile source, Model model, boolean pluginMode) {
        Document document = parseXml(source.file);
        if (pluginMode) {
            rejectThemeSyntax(source.file, document);
        }
        Element root = document.getDocumentElement();
        requireTag(source.file, root, "resources");
        rejectAttributes(source.file, root, setOf());
        for (Element element : childElements(root)) {
            String type = element.getTagName();
            if (UNSUPPORTED_STYLE_RESOURCE_TYPES.contains(type)) {
                if (pluginMode) {
                    throw fail(source.file, "style/theme/styleable resources are not supported in strict JAR mode: <"
                            + type + ">");
                }
                // The normal AAR keeps the original resource file for AAPT2. X2C does not need to
                // model declarations that are only used to make custom XML attributes linkable.
                continue;
            }
            String name = requireResourceName(source.file, element);
            switch (type) {
                case "string":
                    rejectAttributes(source.file, element, setOf("name", "translatable"));
                    rejectChildElements(source.file, element);
                    putUnique(model.strings, name,
                            decodeAndroidText(source.file, element.getTextContent()),
                            source.file, "string");
                    break;
                case "color":
                    rejectAttributes(source.file, element, setOf("name"));
                    rejectChildElements(source.file, element);
                    registerColorName(model, name, "scalar", source.file);
                    putUnique(model.colors, name, parseColor(source.file, element.getTextContent()), source.file, "color");
                    break;
                case "bool":
                    rejectAttributes(source.file, element, setOf("name"));
                    rejectChildElements(source.file, element);
                    String value = element.getTextContent().trim();
                    if (!value.equals("true") && !value.equals("false")) {
                        throw fail(source.file, "bool " + name + " must be true or false");
                    }
                    putUnique(model.bools, name, Boolean.valueOf(value), source.file, "bool");
                    break;
                case "integer":
                    rejectAttributes(source.file, element, setOf("name"));
                    rejectChildElements(source.file, element);
                    try {
                        putUnique(model.integers, name, Integer.valueOf(element.getTextContent().trim()), source.file, "integer");
                    } catch (NumberFormatException error) {
                        throw fail(source.file, "integer " + name + " is outside the signed 32-bit range");
                    }
                    break;
                case "dimen":
                    rejectAttributes(source.file, element, setOf("name"));
                    rejectChildElements(source.file, element);
                    putUnique(model.dimens, name, parseDimension(source.file, element.getTextContent()), source.file, "dimen");
                    break;
                case "item":
                    rejectAttributes(source.file, element, setOf("name", "type"));
                    rejectChildElements(source.file, element);
                    String itemType = element.getAttribute("type");
                    if (!itemType.equals("id")) {
                        throw fail(source.file, "Only <item type=\"id\"> is supported; got type=" + itemType);
                    }
                    if (!element.getTextContent().trim().isEmpty()) {
                        throw fail(source.file, "<item type=\"id\"> must not contain a value: " + name);
                    }
                    model.declaredIds.add(name);
                    break;
                case "string-array": parseStringArray(source.file, element, name, model); break;
                case "integer-array": parseIntegerArray(source.file, element, name, model); break;
                case "array": parseTypedArray(source.file, element, name, model); break;
                case "plurals": parsePlurals(source.file, element, name, model); break;
                case "fraction":
                    rejectAttributes(source.file, element, setOf("name"));
                    rejectChildElements(source.file, element);
                    putUnique(model.fractions, name, parseFraction(source.file, element.getTextContent()),
                            source.file, "fraction");
                    break;
                default:
                    if (pluginMode) {
                        throw fail(source.file, "Unsupported values resource type: " + type);
                    }
                    // Preserve normal-AAR-only values through AAPT2 without pretending X2C can
                    // expose them from the generated resource provider.
                    break;
            }
        }
    }

    static void parseStringArray(File file, Element element, String name, Model model) {
        rejectAttributes(file, element, setOf("name"));
        List<String> values = new ArrayList<>();
        for (Element item : childElements(element)) {
            requireTag(file, item, "item");
            rejectAttributes(file, item, setOf());
            rejectChildElements(file, item);
            values.add(decodeAndroidText(file, item.getTextContent()));
        }
        putUnique(model.stringArrays, name, values, file, "string-array");
    }

    static void parseIntegerArray(File file, Element element, String name, Model model) {
        rejectAttributes(file, element, setOf("name"));
        List<String> values = new ArrayList<>();
        for (Element item : childElements(element)) {
            requireTag(file, item, "item");
            rejectAttributes(file, item, setOf());
            rejectChildElements(file, item);
            values.add(item.getTextContent().trim());
        }
        putUnique(model.integerArrays, name, values, file, "integer-array");
    }

    static void parseTypedArray(File file, Element element, String name, Model model) {
        rejectAttributes(file, element, setOf("name"));
        List<TypedValueItem> values = new ArrayList<>();
        for (Element item : childElements(element)) {
            requireTag(file, item, "item");
            rejectAttributes(file, item, setOf());
            rejectChildElements(file, item);
            values.add(parseTypedValueItem(file, item.getTextContent()));
        }
        putUnique(model.typedArrays, name, values, file, "array");
    }

    static void parsePlurals(File file, Element element, String name, Model model) {
        rejectAttributes(file, element, setOf("name"));
        Map<String, String> values = new TreeMap<>();
        for (Element item : childElements(element)) {
            requireTag(file, item, "item");
            rejectAttributes(file, item, setOf("quantity"));
            rejectChildElements(file, item);
            String quantity = item.getAttribute("quantity");
            requireOneOf(file, "plural quantity", quantity, "zero", "one", "two", "few", "many", "other");
            if (values.putIfAbsent(quantity,
                    decodeAndroidText(file, item.getTextContent())) != null) {
                throw fail(file, "Duplicate plural quantity " + quantity + " for " + name);
            }
        }
        if (!values.containsKey("other")) {
            throw fail(file, "Plural " + name + " requires an 'other' quantity");
        }
        putUnique(model.plurals, name, values, file, "plurals");
    }

    static TypedValueItem parseTypedValueItem(File file, String raw) {
        String value = raw.trim();
        if (value.startsWith("@string/")) return new TypedValueItem("STRING", value);
        if (value.startsWith("@color/") || value.startsWith("#")) return new TypedValueItem("COLOR", value);
        if (value.startsWith("@dimen/") || value.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:dp|dip|sp|px)")) {
            return new TypedValueItem("DIMENSION", value);
        }
        if (value.startsWith("@bool/") || value.equals("true") || value.equals("false")) {
            return new TypedValueItem("BOOLEAN", value);
        }
        if (value.startsWith("@integer/") || value.matches("[-+]?\\d+")) {
            return new TypedValueItem("INTEGER", value);
        }
        if (value.startsWith("@fraction/") || value.endsWith("%") || value.endsWith("%p")) {
            return new TypedValueItem("FRACTION", value);
        }
        if (value.startsWith("@")) {
            throw fail(file, "Unsupported typed-array reference: " + value);
        }
        return new TypedValueItem("STRING", decodeAndroidText(file, raw));
    }

    static void validateValueReferences(Model model) {
        for (List<String> values : model.stringArrays.values()) {
            for (String value : values) if (value.trim().startsWith("@")) resolveString(null, value.trim(), model);
        }
        for (List<String> values : model.integerArrays.values()) {
            for (String value : values) resolveInteger(null, value, model, "integer-array item");
        }
        for (Map<String, String> values : model.plurals.values()) {
            for (String value : values.values()) if (value.trim().startsWith("@")) resolveString(null, value.trim(), model);
        }
        for (List<TypedValueItem> values : model.typedArrays.values()) {
            for (TypedValueItem value : values) {
                switch (value.kind) {
                    case "STRING":
                        if (value.value.trim().startsWith("@")) resolveString(null, value.value.trim(), model);
                        break;
                    case "COLOR": resolveColor(null, value.value, model); break;
                    case "DIMENSION": resolveDimension(null, value.value, model); break;
                    case "BOOLEAN": resolveBoolean(null, value.value, model, "array item"); break;
                    case "INTEGER": resolveInteger(null, value.value, model, "array item"); break;
                    case "FRACTION": resolveFraction(null, value.value, model); break;
                    default: throw new AssertionError(value.kind);
                }
            }
        }
    }
}
