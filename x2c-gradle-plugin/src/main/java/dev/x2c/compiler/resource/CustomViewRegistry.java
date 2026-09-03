package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.setOf;
import static dev.x2c.compiler.resource.FileSupport.read;

import com.google.gson.Gson;
import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Parsed and validated build-time contract for custom View constructors and typed setters. */
final class CustomViewRegistry {
    private static final Set<String> CONSTRUCTORS = setOf(
            "CONTEXT", "CONTEXT_ATTRS", "CONTEXT_ATTRS_DEF_STYLE");
    private static final Set<String> ATTRIBUTE_TYPES = setOf(
            "STRING", "COLOR", "DIMENSION", "DIMENSION_INT",
            "BOOLEAN", "INTEGER", "FLOAT", "GRAVITY", "DRAWABLE", "IMAGE_ASSET");

    private final Map<String, ViewSpec> byTag;

    private CustomViewRegistry(Map<String, ViewSpec> byTag) {
        this.byTag = byTag;
    }

    static CustomViewRegistry empty() {
        return new CustomViewRegistry(Collections.<String, ViewSpec>emptyMap());
    }

    static CustomViewRegistry load(File file) throws IOException {
        if (file == null) {
            return empty();
        }
        if (!file.isFile()) {
            throw failure(file, "Custom View spec file does not exist");
        }
        Root root;
        try {
            root = new Gson().fromJson(read(file), Root.class);
        } catch (RuntimeException error) {
            throw new ResourceCompilationException(file.getAbsolutePath() + ": invalid custom View JSON", error);
        }
        if (root == null || root.schema != 1 || root.views == null) {
            throw failure(file, "Custom View spec must use schema 1 and contain a views array");
        }

        Map<String, ViewSpec> specs = new TreeMap<>();
        for (ViewSpec spec : root.views) {
            validateView(file, spec);
            putAlias(file, specs, spec.tag, spec);
            if (!spec.className.equals(spec.tag)) {
                putAlias(file, specs, spec.className, spec);
            }
        }
        return new CustomViewRegistry(Collections.unmodifiableMap(specs));
    }

    ViewSpec find(String tag) {
        return byTag.get(tag);
    }

    int declaredViewCount() {
        return (int) byTag.values().stream().distinct().count();
    }

    private static void validateView(File file, ViewSpec spec) {
        if (spec == null || isBlank(spec.tag)) {
            throw failure(file, "Every custom View requires tag");
        }
        if (isBlank(spec.className)) {
            spec.className = spec.tag;
        }
        validateJavaType(file, spec.className);
        if (isBlank(spec.constructor)) {
            spec.constructor = "CONTEXT";
        }
        if (!CONSTRUCTORS.contains(spec.constructor)) {
            throw failure(file, "Unsupported constructor for " + spec.tag + ": " + spec.constructor);
        }
        if (spec.attributes == null) {
            spec.attributes = new ArrayList<>();
        }
        spec.attributesByName = validateAttributes(file, spec.tag, spec.attributes, false);
        if (spec.container != null) {
            validateContainer(file, spec);
        }
    }

    private static void validateContainer(File file, ViewSpec view) {
        ContainerSpec container = view.container;
        if (isBlank(container.layoutParamsClass)) {
            container.layoutParamsClass = "android.view.ViewGroup.LayoutParams";
        }
        validateJavaType(file, container.layoutParamsClass);
        if (container.marginLayoutParams
                && container.layoutParamsClass.equals("android.view.ViewGroup.LayoutParams")) {
            throw failure(file, "marginLayoutParams requires a ViewGroup.MarginLayoutParams subclass: "
                    + view.tag);
        }
        boolean hasFactoryClass = !isBlank(container.layoutParamsFactoryClass);
        boolean hasFactoryMethod = !isBlank(container.layoutParamsFactoryMethod);
        if (hasFactoryClass != hasFactoryMethod) {
            throw failure(file, "ViewGroup layoutParamsFactoryClass and layoutParamsFactoryMethod must be declared together: "
                    + view.tag);
        }
        if (hasFactoryClass) {
            validateJavaType(file, container.layoutParamsFactoryClass);
            validateMethodName(file, container.layoutParamsFactoryMethod, "LayoutParams factory method");
        }
        if (!isBlank(container.childrenFinishedSetter)) {
            validateMethodName(file, container.childrenFinishedSetter, "children-finished setter");
        }
        if (container.layoutAttributes == null) {
            container.layoutAttributes = new ArrayList<>();
        }
        container.layoutAttributesByName = validateAttributes(
                file, view.tag + " LayoutParams", container.layoutAttributes, true);
        if (container.marginLayoutParams && container.layoutAttributesByName.keySet().stream()
                .anyMatch(name -> name.startsWith("layout_margin"))) {
            throw failure(file, "Do not redeclare layout_margin* when marginLayoutParams is enabled: "
                    + view.tag);
        }
    }

    private static Map<String, AttributeSpec> validateAttributes(
            File file, String owner, List<AttributeSpec> declared, boolean layoutAttributes) {
        Map<String, AttributeSpec> attributes = new TreeMap<>();
        for (AttributeSpec attribute : declared) {
            if (attribute == null || isBlank(attribute.name) || isBlank(attribute.type)) {
                throw failure(file, "Attributes require name, type, and exactly one setter or field: " + owner);
            }
            boolean hasSetter = !isBlank(attribute.setter);
            boolean hasField = !isBlank(attribute.field);
            if (hasSetter == hasField) {
                throw failure(file, "Attribute requires exactly one setter or field: " + owner + "/"
                        + attribute.name);
            }
            if (!attribute.name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw failure(file, "Invalid custom attribute name: " + attribute.name);
            }
            if (layoutAttributes && !attribute.name.startsWith("layout_")) {
                throw failure(file, "ViewGroup LayoutParams attribute must start with layout_: " + attribute.name);
            }
            if (layoutAttributes && setOf("layout_width", "layout_height").contains(attribute.name)) {
                throw failure(file, "layout_width and layout_height are built-in LayoutParams attributes");
            }
            if (hasSetter) {
                validateMethodName(file, attribute.setter, "Custom setter");
            } else {
                validateMethodName(file, attribute.field, "Custom field");
            }
            if (!ATTRIBUTE_TYPES.contains(attribute.type)) {
                throw failure(file, "Unsupported custom attribute type " + attribute.type + " for " + attribute.name);
            }
            if (attributes.putIfAbsent(attribute.name, attribute) != null) {
                throw failure(file, "Duplicate custom attribute " + attribute.name + " for " + owner);
            }
        }
        return Collections.unmodifiableMap(attributes);
    }

    private static void validateMethodName(File file, String method, String label) {
        if (!method.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw failure(file, "Invalid " + label + ": " + method);
        }
    }

    private static void validateJavaType(File file, String type) {
        if (!type.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
            throw failure(file, "Custom View className must be fully qualified: " + type);
        }
    }

    private static void putAlias(File file, Map<String, ViewSpec> specs, String alias, ViewSpec spec) {
        if (specs.putIfAbsent(alias, spec) != null) {
            throw failure(file, "Duplicate custom View tag or className: " + alias);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResourceCompilationException failure(File file, String message) {
        return new ResourceCompilationException(file.getAbsolutePath() + ": " + message);
    }

    static final class ViewSpec {
        private String tag;
        private String className;
        private String constructor;
        private List<AttributeSpec> attributes;
        private ContainerSpec container;
        private transient Map<String, AttributeSpec> attributesByName = Collections.emptyMap();

        String className() {
            return className;
        }

        String constructor() {
            return constructor;
        }

        AttributeSpec attribute(String name) {
            return attributesByName.get(name);
        }

        ContainerSpec container() {
            return container;
        }
    }

    static final class AttributeSpec {
        private String name;
        private String setter;
        private String field;
        private String type;

        String name() {
            return name;
        }

        String setter() {
            return setter;
        }

        String field() {
            return field;
        }

        String type() {
            return type;
        }
    }

    static final class ContainerSpec {
        private String layoutParamsClass;
        private String layoutParamsFactoryClass;
        private String layoutParamsFactoryMethod;
        private boolean marginLayoutParams;
        private String childrenFinishedSetter;
        private List<AttributeSpec> layoutAttributes;
        private transient Map<String, AttributeSpec> layoutAttributesByName = Collections.emptyMap();

        String layoutParamsClass() {
            return layoutParamsClass;
        }

        String layoutParamsFactoryClass() {
            return layoutParamsFactoryClass;
        }

        String layoutParamsFactoryMethod() {
            return layoutParamsFactoryMethod;
        }

        boolean marginLayoutParams() {
            return marginLayoutParams;
        }

        String childrenFinishedSetter() {
            return childrenFinishedSetter;
        }

        AttributeSpec layoutAttribute(String name) {
            return layoutAttributesByName.get(name);
        }

        List<AttributeSpec> layoutAttributes() {
            return Collections.unmodifiableList(new ArrayList<AttributeSpec>(layoutAttributesByName.values()));
        }
    }

    private static final class Root {
        private int schema;
        private List<ViewSpec> views;
    }
}
