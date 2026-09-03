package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FileSupport.fail;
import static dev.x2c.compiler.resource.ResourceValueParser.*;
import static dev.x2c.compiler.resource.XmlSupport.*;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Parses color-state-list XML with strict state and reference validation. */
final class ColorResourceParser {
    private ColorResourceParser() {}

    static void parseColorStateList(ResourceFile source, Model model) {
        String name = resourceNameFromFile(source.file);
        Document document = parseXml(source.file);
        rejectThemeSyntax(source.file, document);
        Element root = document.getDocumentElement();
        requireTag(source.file, root, "selector");
        rejectAttributes(source.file, root, setOf());
        ColorSelector selector = new ColorSelector();
        selector.file = source.file;
        List<Element> children = childElements(root);
        if (children.isEmpty()) throw fail(source.file, "Color selector requires at least one <item>");
        boolean defaultSeen = false;
        Set<String> stateAttributes = setOf(
                "color", "alpha", "state_pressed", "state_focused", "state_selected", "state_checkable",
                "state_checked", "state_enabled", "state_activated", "state_window_focused", "state_hovered",
                "state_accelerated", "state_active", "state_drag_can_accept", "state_drag_hovered", "state_expanded",
                "state_first", "state_last", "state_long_pressable", "state_middle", "state_single");
        for (int index = 0; index < children.size(); index++) {
            Element element = children.get(index);
            requireTag(source.file, element, "item");
            rejectAttributes(source.file, element, stateAttributes);
            rejectChildElements(source.file, element);
            ColorSelectorItem item = new ColorSelectorItem();
            item.color = requireColorReferenceOrLiteral(source.file, element, "color", model);
            String alpha = androidAttribute(element, "alpha");
            if (!alpha.isEmpty()) {
                float factor = parseFloat(source.file, alpha, "alpha");
                if (factor < 0f || factor > 1f) {
                    throw fail(source.file, "color selector alpha must be between 0 and 1");
                }
                int base = item.color.reference == null ? item.color.literal : model.colors.get(item.color.reference);
                int adjustedAlpha = Math.round(((base >>> 24) & 0xff) * factor);
                item.color = ColorValue.literal((base & 0x00ffffff) | (adjustedAlpha << 24));
            }
            NamedNodeMap attributes = element.getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                String localName = attribute.getLocalName();
                if (!ANDROID_NS.equals(attribute.getNamespaceURI())
                        || localName.equals("color") || localName.equals("alpha")) continue;
                String stateValue = attribute.getNodeValue();
                boolean enabled;
                if (stateValue.startsWith("@bool/")) {
                    String reference = referenceName(stateValue, "bool");
                    if (!model.bools.containsKey(reference)) {
                        throw fail(source.file, "Unknown bool reference: " + stateValue);
                    }
                    enabled = model.bools.get(reference);
                } else {
                    enabled = parseBoolean(source.file, stateValue, localName);
                }
                item.states.put(localName, enabled);
            }
            if (item.states.isEmpty()) {
                if (defaultSeen || index != children.size() - 1) {
                    throw fail(source.file, "Color selector default item must be unique and last");
                }
                defaultSeen = true;
            }
            selector.items.add(item);
        }
        registerColorName(model, name, "selector", source.file);
        putUnique(model.colorSelectors, name, selector, source.file, "color selector");
    }
}
