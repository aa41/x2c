package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FileSupport.fail;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Hardened XML parsing and resource-name/attribute validation helpers. */
final class XmlSupport {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private XmlSupport() {}

    static String requireResourceName(File file, Element element) {
        String name = element.getAttribute("name");
        validateResourceName(name, file);
        return name;
    }

    static String resourceNameFromFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        name = dot < 0 ? name : name.substring(0, dot);
        validateResourceName(name, file);
        return name;
    }

    static void validateResourceName(String name, File file) {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
            throw fail(file, "Invalid resource name: " + name);
        }
    }

    static void validatePackage(String value) {
        if (value == null || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new ResourceCompilationException("Invalid generated Java package: " + value);
        }
    }

    static Document parseXml(File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            disableExternalAccessProperties(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException error) throws SAXException {
                    throw error;
                }

                @Override
                public void fatalError(SAXParseException error) throws SAXException {
                    throw error;
                }
            });
            return builder.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException error) {
            throw fail(file, "Cannot parse XML: " + error.getMessage(), error);
        }
    }

    private static void disableExternalAccessProperties(DocumentBuilderFactory factory) {
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Older Xerces versions bundled by older AGP releases do not recognize JAXP 1.5
            // properties. Entity/DOCTYPE features above still block external XML access.
        }
    }

    static void rejectThemeSyntax(File file, Document document) {
        rejectThemeSyntax(file, document.getDocumentElement());
    }

    static void rejectThemeSyntax(File file, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute.getNodeName().equals("style")
                    || (ANDROID_NS.equals(attribute.getNamespaceURI())
                    && attribute.getLocalName().equals("theme"))) {
                throw fail(file, "style/theme/styleable are not supported in strict JAR mode: "
                        + attribute.getNodeName());
            }
            String value = attribute.getNodeValue().trim();
            if (value.startsWith("?attr/") || value.startsWith("?android:attr/")) {
                throw fail(file, "Theme attribute references are not supported in strict JAR mode: " + value);
            }
        }
        for (Element child : childElements(element)) {
            rejectThemeSyntax(file, child);
        }
    }

    static List<Element> childElements(Element element) {
        List<Element> result = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element) {
                result.add((Element) child);
            }
        }
        return result;
    }

    static void rejectChildElements(File file, Element element) {
        if (!childElements(element).isEmpty()) {
            throw fail(file, "Nested markup is not supported in <" + element.getTagName() + ">");
        }
    }

    static void requireTag(File file, Element element, String tag) {
        if (!element.getTagName().equals(tag)) {
            throw fail(file, "Expected <" + tag + "> root, got <" + element.getTagName() + ">");
        }
    }

    static void rejectAttributes(File file, Element element, Set<String> allowedAndroidLocalNames) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String name = attribute.getNodeName();
            if (name.startsWith("xmlns")) {
                continue;
            }
            if (attribute.getNamespaceURI() == null && allowedAndroidLocalNames.contains(name)) {
                continue;
            }
            if (ANDROID_NS.equals(attribute.getNamespaceURI()) && allowedAndroidLocalNames.contains(attribute.getLocalName())) {
                continue;
            }
            throw fail(file, "Unsupported attribute on <" + element.getTagName() + ">: " + name);
        }
    }

    static String requireAttribute(File file, Element element, String localName) {
        String value = androidAttribute(element, localName);
        if (value.isEmpty()) {
            throw fail(file, "Missing android:" + localName + " on <" + element.getTagName() + ">");
        }
        return value;
    }

    static String androidAttribute(Element element, String localName) {
        return element.getAttributeNS(ANDROID_NS, localName);
    }

    /** Decodes the explicit escape sequences accepted by Android text resources. */
    static String decodeAndroidText(File file, String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (++index >= value.length()) {
                throw fail(file, "Android text ends with an incomplete escape sequence");
            }
            char escaped = value.charAt(index);
            switch (escaped) {
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case '\\':
                case '\'':
                case '"':
                case '@':
                case '?':
                case '#':
                    result.append(escaped);
                    break;
                case 'u':
                    if (index + 4 >= value.length()) {
                        throw fail(file, "Android text has an incomplete \\u escape sequence");
                    }
                    String hexadecimal = value.substring(index + 1, index + 5);
                    try {
                        result.append((char) Integer.parseInt(hexadecimal, 16));
                    } catch (NumberFormatException error) {
                        throw fail(file, "Android text has an invalid \\u escape sequence: "
                                + hexadecimal);
                    }
                    index += 4;
                    break;
                default:
                    throw fail(file, "Unsupported Android text escape sequence: \\" + escaped);
            }
        }
        return result.toString();
    }

    static void requireOneOf(File file, String name, String value, String... allowed) {
        if (!setOf(allowed).contains(value)) {
            throw fail(file, name + " must be one of " + Arrays.toString(allowed) + ", got: " + value);
        }
    }

    static <T> void putUnique(Map<String, T> map, String name, T value, File file, String type) {
        if (map.putIfAbsent(name, value) != null) {
            throw fail(file, "Duplicate " + type + " resource: " + name);
        }
    }

    static void registerDrawableName(Model model, String name, String kind, File file) {
        String previous = model.drawableKinds.putIfAbsent(name, kind);
        if (previous != null) {
            throw fail(file, "Conflicting @drawable/" + name + " resources: " + previous + " and " + kind);
        }
    }

    static void registerColorName(Model model, String name, String kind, File file) {
        String previous = model.colorKinds.putIfAbsent(name, kind);
        if (previous != null) {
            throw fail(file, "Conflicting @color/" + name + " resources: " + previous + " and " + kind);
        }
    }
}
