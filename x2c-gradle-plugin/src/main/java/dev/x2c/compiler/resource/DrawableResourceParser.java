package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FileSupport.*;
import static dev.x2c.compiler.resource.ResourceValueParser.*;
import static dev.x2c.compiler.resource.XmlSupport.*;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Parses generated drawables and content-addressed bitmap/CDN assets. */
final class DrawableResourceParser {
    private DrawableResourceParser() {}

    static void parseDrawable(ResourceFile source, Model model) {
        String name = resourceNameFromFile(source.file);
        Document document = parseXml(source.file);
        rejectThemeSyntax(source.file, document);
        Element root = document.getDocumentElement();
        switch (root.getTagName()) {
            case "shape": parseShapeDrawable(source, name, root, model); break;
            case "selector": parseSelectorDrawable(source, name, root, model); break;
            case "layer-list": parseLayerListDrawable(source, name, root, model); break;
            case "inset": parseInsetDrawable(source, name, root, model); break;
            case "clip": parseClipDrawable(source, name, root, model); break;
            case "scale": parseScaleDrawable(source, name, root, model); break;
            case "rotate": parseRotateDrawable(source, name, root, model); break;
            case "level-list": parseLevelListDrawable(source, name, root, model); break;
            default: throw fail(source.file, "Unsupported drawable root: <" + root.getTagName() + ">");
        }
    }

    static void parseShapeDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "shape");
        rejectAttributes(source.file, root, setOf(
                "shape", "dither", "useLevel", "innerRadius", "innerRadiusRatio", "thickness", "thicknessRatio"));
        String shape = androidAttribute(root, "shape");
        shape = shape.isEmpty() ? "rectangle" : shape;
        requireOneOf(source.file, "shape", shape, "rectangle", "oval", "line", "ring");
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.shape = shape;
        drawable.dither = optionalBooleanAttribute(source.file, root, "dither", model);
        drawable.useLevel = optionalBooleanAttribute(source.file, root, "useLevel", model);
        drawable.innerRadius = optionalDimensionAttribute(source.file, root, "innerRadius", model);
        drawable.thickness = optionalDimensionAttribute(source.file, root, "thickness", model);
        drawable.innerRadiusRatio = optionalPositiveFloatAttribute(source.file, root, "innerRadiusRatio");
        drawable.thicknessRatio = optionalPositiveFloatAttribute(source.file, root, "thicknessRatio");
        boolean hasRingSizing = drawable.innerRadius != null || drawable.thickness != null
                || drawable.innerRadiusRatio != null || drawable.thicknessRatio != null;
        if (!shape.equals("ring") && hasRingSizing) {
            throw fail(source.file, "innerRadius/thickness attributes are valid only for ring shapes");
        }
        if (hasRingSizing && model.minApi < 29) {
            throw fail(source.file, "Explicit ring innerRadius/thickness requires x2c.minApi >= 29; public APIs do not exist earlier");
        }
        Set<String> seen = new HashSet<>();
        for (Element child : childElements(root)) {
            if (!seen.add(child.getTagName())) {
                throw fail(source.file, "Duplicate <" + child.getTagName() + "> in shape drawable");
            }
            switch (child.getTagName()) {
                case "solid":
                    rejectAttributes(source.file, child, setOf("color"));
                    drawable.solidColor = requireColorReferenceOrLiteral(source.file, child, "color", model);
                    break;
                case "corners":
                    if (!shape.equals("rectangle")) {
                        throw fail(source.file, "<corners> is supported only for rectangle shapes");
                    }
                    rejectAttributes(source.file, child, setOf(
                            "radius", "topLeftRadius", "topRightRadius", "bottomRightRadius", "bottomLeftRadius"));
                    drawable.cornerRadius = optionalDimensionAttribute(source.file, child, "radius", model);
                    drawable.topLeftRadius = optionalDimensionAttribute(source.file, child, "topLeftRadius", model);
                    drawable.topRightRadius = optionalDimensionAttribute(source.file, child, "topRightRadius", model);
                    drawable.bottomRightRadius = optionalDimensionAttribute(source.file, child, "bottomRightRadius", model);
                    drawable.bottomLeftRadius = optionalDimensionAttribute(source.file, child, "bottomLeftRadius", model);
                    if (drawable.cornerRadius == null && drawable.topLeftRadius == null && drawable.topRightRadius == null
                            && drawable.bottomRightRadius == null && drawable.bottomLeftRadius == null) {
                        throw fail(source.file, "<corners> requires radius or at least one per-corner radius");
                    }
                    break;
                case "stroke":
                    rejectAttributes(source.file, child, setOf("width", "color", "dashWidth", "dashGap"));
                    drawable.strokeWidth = requireDimensionAttribute(source.file, child, "width", model);
                    drawable.strokeColor = requireColorReferenceOrLiteral(source.file, child, "color", model);
                    drawable.dashWidth = optionalDimensionAttribute(source.file, child, "dashWidth", model);
                    drawable.dashGap = optionalDimensionAttribute(source.file, child, "dashGap", model);
                    if ((drawable.dashWidth == null) != (drawable.dashGap == null)) {
                        throw fail(source.file, "stroke dashWidth and dashGap must be declared together");
                    }
                    break;
                case "size":
                    rejectAttributes(source.file, child, setOf("width", "height"));
                    drawable.width = requireDimensionAttribute(source.file, child, "width", model);
                    drawable.height = requireDimensionAttribute(source.file, child, "height", model);
                    break;
                case "padding":
                    rejectAttributes(source.file, child, setOf("left", "top", "right", "bottom"));
                    drawable.paddingLeft = optionalDimensionAttribute(source.file, child, "left", model);
                    drawable.paddingTop = optionalDimensionAttribute(source.file, child, "top", model);
                    drawable.paddingRight = optionalDimensionAttribute(source.file, child, "right", model);
                    drawable.paddingBottom = optionalDimensionAttribute(source.file, child, "bottom", model);
                    if (drawable.paddingLeft == null && drawable.paddingTop == null
                            && drawable.paddingRight == null && drawable.paddingBottom == null) {
                        throw fail(source.file, "<padding> requires at least one side");
                    }
                    break;
                case "gradient":
                    parseShapeGradient(source.file, child, drawable, model);
                    break;
                default:
                    throw fail(source.file, "Unsupported shape child: " + child.getTagName());
            }
            rejectChildElements(source.file, child);
        }
        if (drawable.solidColor != null && drawable.gradientStart != null) {
            throw fail(source.file, "A shape cannot declare both <solid> and <gradient>");
        }
        registerDrawableName(model, name, "shape", source.file);
        putUnique(model.shapes, name, drawable, source.file, "drawable");
    }

    static void parseShapeGradient(File file, Element element, ShapeDrawable drawable, Model model) {
        rejectAttributes(file, element, setOf(
                "startColor", "centerColor", "endColor", "type", "angle", "centerX", "centerY",
                "gradientRadius", "useLevel"));
        drawable.gradientStart = requireColorReferenceOrLiteral(file, element, "startColor", model);
        drawable.gradientEnd = requireColorReferenceOrLiteral(file, element, "endColor", model);
        drawable.gradientCenter = optionalColorAttribute(file, element, "centerColor", model);
        drawable.gradientType = androidAttribute(element, "type");
        drawable.gradientType = drawable.gradientType.isEmpty() ? "linear" : drawable.gradientType;
        requireOneOf(file, "gradient type", drawable.gradientType, "linear", "radial", "sweep");
        String angle = androidAttribute(element, "angle");
        drawable.gradientAngle = angle.isEmpty() ? 0 : parseGradientAngle(file, angle);
        drawable.gradientCenterX = optionalUnitFloatAttribute(file, element, "centerX");
        drawable.gradientCenterY = optionalUnitFloatAttribute(file, element, "centerY");
        drawable.gradientRadius = optionalDimensionAttribute(file, element, "gradientRadius", model);
        drawable.gradientUseLevel = optionalBooleanAttribute(file, element, "useLevel", model);
        if (drawable.gradientType.equals("radial") && drawable.gradientRadius == null) {
            throw fail(file, "radial gradient requires android:gradientRadius");
        }
        if (!drawable.gradientType.equals("linear") && !angle.isEmpty()) {
            throw fail(file, "android:angle is valid only for linear gradients");
        }
        if (drawable.solidColor != null) {
            throw fail(file, "A shape cannot declare both <solid> and <gradient>");
        }
    }

    static void parseSelectorDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "selector");
        rejectAttributes(source.file, root, setOf(
                "dither", "autoMirrored", "visible", "enterFadeDuration", "exitFadeDuration"));
        SelectorDrawable selector = new SelectorDrawable();
        selector.file = source.file;
        selector.dither = optionalBooleanAttribute(source.file, root, "dither", model);
        selector.autoMirrored = optionalBooleanAttribute(source.file, root, "autoMirrored", model);
        selector.visible = optionalBooleanAttribute(source.file, root, "visible", model);
        selector.enterFadeDuration = optionalIntegerAttribute(source.file, root, "enterFadeDuration", model);
        selector.exitFadeDuration = optionalIntegerAttribute(source.file, root, "exitFadeDuration", model);
        List<Element> children = childElements(root);
        if (children.isEmpty()) {
            throw fail(source.file, "Drawable selector requires at least one <item>");
        }
        boolean defaultSeen = false;
        Set<String> allowed = setOf(
                "drawable", "state_pressed", "state_focused", "state_selected", "state_checkable",
                "state_checked", "state_enabled", "state_activated", "state_window_focused", "state_hovered",
                "state_accelerated", "state_active", "state_drag_can_accept", "state_drag_hovered", "state_expanded",
                "state_first", "state_last", "state_long_pressable", "state_middle", "state_single");
        for (int index = 0; index < children.size(); index++) {
            Element child = children.get(index);
            requireTag(source.file, child, "item");
            rejectAttributes(source.file, child, allowed);
            SelectorItem item = new SelectorItem();
            String drawableReference = androidAttribute(child, "drawable");
            List<Element> inlineChildren = childElements(child);
            if (!drawableReference.isEmpty() && !inlineChildren.isEmpty()) {
                throw fail(source.file, "Selector <item> cannot declare both android:drawable and inline drawable");
            }
            if (drawableReference.isEmpty()) {
                if (inlineChildren.size() != 1 || !inlineChildren.get(0).getTagName().equals("shape")) {
                    throw fail(source.file, "Selector <item> requires android:drawable or exactly one inline <shape>");
                }
                item.drawable = name + "_item_" + index;
                parseShapeDrawable(source, item.drawable, inlineChildren.get(0), model);
            } else {
                item.drawable = referenceName(drawableReference, "drawable");
            }
            NamedNodeMap attributes = child.getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                String localName = attribute.getLocalName();
                if (!ANDROID_NS.equals(attribute.getNamespaceURI())
                        || localName.equals("drawable")) {
                    continue;
                }
                String value = attribute.getNodeValue();
                boolean enabled;
                if (value.startsWith("@bool/")) {
                    String reference = referenceName(value, "bool");
                    if (!model.bools.containsKey(reference)) {
                        throw fail(source.file, "Unknown bool reference: " + value);
                    }
                    enabled = model.bools.get(reference);
                } else {
                    enabled = parseBoolean(source.file, value, localName);
                }
                item.states.put(localName, enabled);
            }
            if (item.states.isEmpty()) {
                if (defaultSeen) {
                    throw fail(source.file, "Drawable selector can contain only one default item");
                }
                if (index != children.size() - 1) {
                    throw fail(source.file, "Drawable selector default item must be last");
                }
                defaultSeen = true;
            }
            selector.items.add(item);
        }
        registerDrawableName(model, name, "selector", source.file);
        putUnique(model.selectors, name, selector, source.file, "drawable selector");
        model.drawableDependencies.put(name, selector.items.stream().map(item -> item.drawable)
                .collect(Collectors.toList()));
    }

    static void parseLayerListDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "layer-list");
        rejectAttributes(source.file, root, setOf("autoMirrored", "paddingMode"));
        LayerListDrawable drawable = new LayerListDrawable();
        drawable.file = source.file;
        drawable.autoMirrored = optionalBooleanAttribute(source.file, root, "autoMirrored", model);
        drawable.paddingMode = androidAttribute(root, "paddingMode");
        if (!drawable.paddingMode.isEmpty()) {
            requireOneOf(source.file, "paddingMode", drawable.paddingMode, "nest", "stack");
        }
        List<Element> children = childElements(root);
        if (children.isEmpty()) throw fail(source.file, "layer-list requires at least one <item>");
        for (int index = 0; index < children.size(); index++) {
            Element child = children.get(index);
            requireTag(source.file, child, "item");
            rejectAttributes(source.file, child, setOf(
                    "drawable", "id", "left", "top", "right", "bottom", "start", "end"));
            LayerItem layer = new LayerItem();
            layer.drawable = parseDrawableReferenceOrInline(source, name, index, child, model);
            layer.left = optionalDimensionAttribute(source.file, child, "left", model);
            layer.top = optionalDimensionAttribute(source.file, child, "top", model);
            layer.right = optionalDimensionAttribute(source.file, child, "right", model);
            layer.bottom = optionalDimensionAttribute(source.file, child, "bottom", model);
            layer.start = optionalDimensionAttribute(source.file, child, "start", model);
            layer.end = optionalDimensionAttribute(source.file, child, "end", model);
            if ((layer.start != null || layer.end != null) && (layer.left != null || layer.right != null)) {
                throw fail(source.file, "Do not mix absolute and relative layer-list insets");
            }
            if ((layer.start != null || layer.end != null) && model.minApi < 23) {
                throw fail(source.file, "Relative layer-list insets require x2c.minApi >= 23");
            }
            String id = androidAttribute(child, "id");
            if (!id.isEmpty()) {
                IdReference reference = parseIdReference(source.file, id);
                if (reference.kind == IdKind.DECLARE) model.declaredIds.add(reference.name);
                layer.id = id;
            }
            drawable.items.add(layer);
        }
        registerDrawableName(model, name, "layer-list", source.file);
        putUnique(model.layerLists, name, drawable, source.file, "layer-list drawable");
        model.drawableDependencies.put(name, drawable.items.stream().map(item -> item.drawable)
                .collect(Collectors.toList()));
    }

    static void parseInsetDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "inset");
        rejectAttributes(source.file, root, setOf(
                "drawable", "inset", "insetLeft", "insetTop", "insetRight", "insetBottom"));
        SingleDrawable drawable = new SingleDrawable();
        drawable.file = source.file;
        drawable.kind = "inset";
        drawable.drawable = parseDrawableReferenceOrInline(source, name, 0, root, model);
        DimensionValue all = optionalDimensionAttribute(source.file, root, "inset", model);
        drawable.left = optionalDimensionAttribute(source.file, root, "insetLeft", model);
        drawable.top = optionalDimensionAttribute(source.file, root, "insetTop", model);
        drawable.right = optionalDimensionAttribute(source.file, root, "insetRight", model);
        drawable.bottom = optionalDimensionAttribute(source.file, root, "insetBottom", model);
        if (drawable.left == null) drawable.left = all;
        if (drawable.top == null) drawable.top = all;
        if (drawable.right == null) drawable.right = all;
        if (drawable.bottom == null) drawable.bottom = all;
        registerSingleDrawable(source, name, drawable, model);
    }

    static void parseClipDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "clip");
        rejectAttributes(source.file, root, setOf("drawable", "clipOrientation", "gravity"));
        SingleDrawable drawable = new SingleDrawable();
        drawable.file = source.file;
        drawable.kind = "clip";
        drawable.drawable = parseDrawableReferenceOrInline(source, name, 0, root, model);
        drawable.orientation = requireAttribute(source.file, root, "clipOrientation");
        requireOneOf(source.file, "clipOrientation", drawable.orientation, "horizontal", "vertical");
        drawable.gravity = requireAttribute(source.file, root, "gravity");
        parseGravity(source.file, drawable.gravity);
        registerSingleDrawable(source, name, drawable, model);
    }

    static void parseScaleDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "scale");
        rejectAttributes(source.file, root, setOf("drawable", "scaleGravity", "scaleWidth", "scaleHeight", "useIntrinsicSizeAsMinimum"));
        SingleDrawable drawable = new SingleDrawable();
        drawable.file = source.file;
        drawable.kind = "scale";
        drawable.drawable = parseDrawableReferenceOrInline(source, name, 0, root, model);
        drawable.gravity = androidAttribute(root, "scaleGravity");
        drawable.gravity = drawable.gravity.isEmpty() ? "left" : drawable.gravity;
        parseGravity(source.file, drawable.gravity);
        drawable.widthFactor = optionalPercentAttribute(source.file, root, "scaleWidth", 1f);
        drawable.heightFactor = optionalPercentAttribute(source.file, root, "scaleHeight", 1f);
        if (!androidAttribute(root, "useIntrinsicSizeAsMinimum").isEmpty()) {
            throw fail(source.file, "scale useIntrinsicSizeAsMinimum has no public API on minSdk 21 and is not classifiable");
        }
        registerSingleDrawable(source, name, drawable, model);
    }

    static void parseRotateDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "rotate");
        rejectAttributes(source.file, root, setOf("drawable", "fromDegrees", "toDegrees", "pivotX", "pivotY", "visible"));
        SingleDrawable drawable = new SingleDrawable();
        drawable.file = source.file;
        drawable.kind = "rotate";
        drawable.drawable = parseDrawableReferenceOrInline(source, name, 0, root, model);
        drawable.fromDegrees = optionalFloatAttribute(source.file, root, "fromDegrees", 0f);
        drawable.toDegrees = optionalFloatAttribute(source.file, root, "toDegrees", 360f);
        drawable.pivotX = parsePivot(source.file, androidAttribute(root, "pivotX"));
        drawable.pivotY = parsePivot(source.file, androidAttribute(root, "pivotY"));
        drawable.visible = optionalBooleanAttribute(source.file, root, "visible", model);
        registerSingleDrawable(source, name, drawable, model);
    }

    static void parseLevelListDrawable(ResourceFile source, String name, Element root, Model model) {
        requireTag(source.file, root, "level-list");
        rejectAttributes(source.file, root, setOf());
        LevelListDrawable drawable = new LevelListDrawable();
        drawable.file = source.file;
        List<Element> children = childElements(root);
        if (children.isEmpty()) throw fail(source.file, "level-list requires at least one <item>");
        for (int index = 0; index < children.size(); index++) {
            Element child = children.get(index);
            requireTag(source.file, child, "item");
            rejectAttributes(source.file, child, setOf("drawable", "minLevel", "maxLevel"));
            LevelItem item = new LevelItem();
            item.drawable = parseDrawableReferenceOrInline(source, name, index, child, model);
            item.min = optionalIntLiteralAttribute(source.file, child, "minLevel", 0);
            item.max = optionalIntLiteralAttribute(source.file, child, "maxLevel", 10000);
            if (item.min > item.max) throw fail(source.file, "level-list minLevel must not exceed maxLevel");
            drawable.items.add(item);
        }
        registerDrawableName(model, name, "level-list", source.file);
        putUnique(model.levelLists, name, drawable, source.file, "level-list drawable");
        model.drawableDependencies.put(name, drawable.items.stream().map(item -> item.drawable)
                .collect(Collectors.toList()));
    }

    static String parseDrawableReferenceOrInline(
            ResourceFile source, String outerName, int index, Element element, Model model) {
        String reference = androidAttribute(element, "drawable");
        List<Element> inline = childElements(element);
        if (!reference.isEmpty() && !inline.isEmpty()) {
            throw fail(source.file, "Drawable node cannot declare both android:drawable and inline drawable");
        }
        if (!reference.isEmpty()) return referenceName(reference, "drawable");
        if (inline.size() != 1 || !inline.get(0).getTagName().equals("shape")) {
            throw fail(source.file, "Drawable node requires android:drawable or exactly one inline <shape>");
        }
        String inlineName = outerName + "_item_" + index;
        parseShapeDrawable(source, inlineName, inline.get(0), model);
        return inlineName;
    }

    static void registerSingleDrawable(ResourceFile source, String name, SingleDrawable drawable, Model model) {
        registerDrawableName(model, name, drawable.kind, source.file);
        putUnique(model.singleDrawables, name, drawable, source.file, drawable.kind + " drawable");
        model.drawableDependencies.put(name, Collections.singletonList(drawable.drawable));
    }

    static void validateDrawableGraph(Model model) {
        for (LayerListDrawable drawable : model.layerLists.values()) {
            for (LayerItem item : drawable.items) {
                if (item.id != null) {
                    IdReference id = parseIdReference(drawable.file, item.id);
                    if (id.kind == IdKind.LOCAL && !model.declaredIds.contains(id.name)) {
                        throw fail(drawable.file, "Unknown layer ID reference: " + item.id);
                    }
                }
            }
        }
        for (Map.Entry<String, List<String>> drawable : model.drawableDependencies.entrySet()) {
            for (String dependency : drawable.getValue()) {
                String kind = model.drawableKinds.get(dependency);
                if (kind == null) {
                    throw new ResourceCompilationException("@drawable/" + drawable.getKey()
                            + " references unknown @drawable/" + dependency);
                }
                if (kind.equals("bitmap")) {
                    if (model.selectors.containsKey(drawable.getKey())) {
                        throw fail(model.selectors.get(drawable.getKey()).file,
                                "Drawable selector cannot synchronously include CDN bitmap: @drawable/" + dependency);
                    }
                    throw new ResourceCompilationException("@drawable/" + drawable.getKey()
                            + " cannot synchronously include CDN bitmap @drawable/" + dependency);
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String drawable : model.drawableDependencies.keySet()) {
            validateDrawableAcyclic(drawable, model, visiting, visited);
        }
    }

    static void validateDrawableAcyclic(
            String name, Model model, Set<String> visiting, Set<String> visited) {
        if (visited.contains(name)) {
            return;
        }
        if (!visiting.add(name)) {
            throw new ResourceCompilationException((model.selectors.containsKey(name)
                    ? "Cyclic drawable selector reference: " : "Cyclic generated drawable reference: ")
                    + "@drawable/" + name);
        }
        for (String dependency : model.drawableDependencies.getOrDefault(name, Collections.<String>emptyList())) {
            if (model.drawableDependencies.containsKey(dependency)) {
                validateDrawableAcyclic(dependency, model, visiting, visited);
            }
        }
        visiting.remove(name);
        visited.add(name);
    }

    static void parseBitmap(ResourceFile source, Model model) throws IOException {
        String name = resourceNameFromFile(source.file);
        validateBitmapSignature(source.file, source.extension);
        BitmapAsset asset = new BitmapAsset();
        asset.name = name;
        asset.file = source.file;
        asset.source = source.kind + "/" + source.file.getName();
        asset.bytes = source.file.length();
        asset.sha256 = sha256(source.file.toPath());
        if ("png".equals(source.extension)) asset.mime = "image/png";
        else if ("jpg".equals(source.extension) || "jpeg".equals(source.extension)) asset.mime = "image/jpeg";
        else if ("webp".equals(source.extension)) asset.mime = "image/webp";
        else if ("gif".equals(source.extension)) asset.mime = "image/gif";
        else if ("avif".equals(source.extension)) asset.mime = "image/avif";
        else throw fail(source.file, "Unsupported bitmap extension");
        registerDrawableName(model, name, "bitmap", source.file);
        putUnique(model.bitmaps, name, asset, source.file, "bitmap drawable");
    }
}
