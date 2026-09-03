package dev.x2c.compiler.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Package-private immutable-shape model types shared by resource compiler stages. */
final class ResourceModel {
    private ResourceModel() {}
}

final class ResourceFile {
    final String kind;
    final File file;
    final String extension;

    ResourceFile(String kind, File file, String extension) {
        this.kind = kind;
        this.file = file;
        this.extension = extension;
    }
}

final class Dimension {
    final String value;
    final String unit;

    Dimension(String value, String unit) {
        this.value = value;
        this.unit = unit;
    }
}

final class Fraction {
    final String factor;
    final boolean parent;

    Fraction(String factor, boolean parent) {
        this.factor = factor;
        this.parent = parent;
    }
}

final class TypedValueItem {
    final String kind;
    final String value;

    TypedValueItem(String kind, String value) {
        this.kind = kind;
        this.value = value;
    }
}

final class Pivot {
    final float value;
    final boolean relative;

    Pivot(float value, boolean relative) {
        this.value = value;
        this.relative = relative;
    }
}

enum IdKind { DECLARE, LOCAL, ANDROID }

enum FrameworkParentKind {
    LINEAR, FRAME, RELATIVE, GRID, TABLE, TABLE_ROW, RADIO, ABSOLUTE, TOOLBAR, ACTION_MENU
}

final class IdReference {
    final IdKind kind;
    final String name;

    IdReference(IdKind kind, String name) {
        this.kind = kind;
        this.name = name;
    }
}

final class DimensionValue {
    Dimension literal;
    String reference;

    static DimensionValue literal(Dimension value) {
        DimensionValue result = new DimensionValue();
        result.literal = value;
        return result;
    }

    static DimensionValue reference(String value) {
        DimensionValue result = new DimensionValue();
        result.reference = value;
        return result;
    }
}

final class ColorValue {
    Integer literal;
    String reference;

    static ColorValue literal(int value) {
        ColorValue result = new ColorValue();
        result.literal = value;
        return result;
    }

    static ColorValue reference(String value) {
        ColorValue result = new ColorValue();
        result.reference = value;
        return result;
    }
}

final class ShapeDrawable {
    String shape;
    Boolean dither;
    Boolean useLevel;
    ColorValue solidColor;
    DimensionValue cornerRadius;
    DimensionValue topLeftRadius;
    DimensionValue topRightRadius;
    DimensionValue bottomRightRadius;
    DimensionValue bottomLeftRadius;
    DimensionValue strokeWidth;
    ColorValue strokeColor;
    DimensionValue dashWidth;
    DimensionValue dashGap;
    DimensionValue width;
    DimensionValue height;
    DimensionValue paddingLeft;
    DimensionValue paddingTop;
    DimensionValue paddingRight;
    DimensionValue paddingBottom;
    ColorValue gradientStart;
    ColorValue gradientCenter;
    ColorValue gradientEnd;
    String gradientType;
    int gradientAngle;
    Float gradientCenterX;
    Float gradientCenterY;
    DimensionValue gradientRadius;
    Boolean gradientUseLevel;
    DimensionValue innerRadius;
    Float innerRadiusRatio;
    DimensionValue thickness;
    Float thicknessRatio;
}

final class SelectorDrawable {
    File file;
    Boolean dither;
    Boolean autoMirrored;
    Boolean visible;
    String enterFadeDuration;
    String exitFadeDuration;
    final List<SelectorItem> items = new ArrayList<>();
}

final class SelectorItem {
    String drawable;
    final Map<String, Boolean> states = new TreeMap<>();
}

final class ColorSelector {
    File file;
    final List<ColorSelectorItem> items = new ArrayList<>();
}

final class ColorSelectorItem {
    ColorValue color;
    final Map<String, Boolean> states = new TreeMap<>();
}

final class LayerListDrawable {
    File file;
    Boolean autoMirrored;
    String paddingMode;
    final List<LayerItem> items = new ArrayList<>();
}

final class LayerItem {
    String drawable;
    String id;
    DimensionValue left;
    DimensionValue top;
    DimensionValue right;
    DimensionValue bottom;
    DimensionValue start;
    DimensionValue end;
}

final class SingleDrawable {
    File file;
    String kind;
    String drawable;
    DimensionValue left;
    DimensionValue top;
    DimensionValue right;
    DimensionValue bottom;
    String orientation;
    String gravity;
    float widthFactor;
    float heightFactor;
    float fromDegrees;
    float toDegrees;
    Pivot pivotX;
    Pivot pivotY;
    Boolean visible;
}

final class LevelListDrawable {
    File file;
    final List<LevelItem> items = new ArrayList<>();
}

final class LevelItem {
    String drawable;
    int min;
    int max;
}

final class LayoutNode {
    final String tag;
    final String javaType;
    final CustomViewRegistry.ViewSpec customView;
    final Map<String, String> attributes = new TreeMap<>();
    final Map<String, CustomAttributeValue> customAttributes = new TreeMap<>();
    final Map<String, CustomAttributeValue> layoutAttributes = new TreeMap<>();
    final List<LayoutNode> children = new ArrayList<>();

    LayoutNode(String tag, String javaType, CustomViewRegistry.ViewSpec customView) {
        this.tag = tag;
        this.javaType = javaType;
        this.customView = customView;
    }

    CustomViewRegistry.ContainerSpec container() {
        return customView == null ? null : customView.container();
    }
}

final class CustomAttributeValue {
    final CustomViewRegistry.AttributeSpec spec;
    final String value;

    CustomAttributeValue(CustomViewRegistry.AttributeSpec spec, String value) {
        this.spec = spec;
        this.value = value;
    }
}

final class BitmapAsset {
    String name;
    File file;
    String source;
    String sha256;
    String mime;
    long bytes;
}

final class Model {
    final CustomViewRegistry customViews;
    final int minApi;
    final Map<String, String> strings = new TreeMap<>();
    final Map<String, Integer> colors = new TreeMap<>();
    final Map<String, ColorSelector> colorSelectors = new TreeMap<>();
    final Map<String, String> colorKinds = new TreeMap<>();
    final Map<String, Boolean> bools = new TreeMap<>();
    final Map<String, Integer> integers = new TreeMap<>();
    final Map<String, Dimension> dimens = new TreeMap<>();
    final Map<String, Fraction> fractions = new TreeMap<>();
    final Map<String, List<String>> stringArrays = new TreeMap<>();
    final Map<String, List<String>> integerArrays = new TreeMap<>();
    final Map<String, List<TypedValueItem>> typedArrays = new TreeMap<>();
    final Map<String, Map<String, String>> plurals = new TreeMap<>();
    final Set<String> declaredIds = new TreeSet<>();
    final Map<String, Integer> ids = new TreeMap<>();
    final Map<String, Integer> layoutIds = new TreeMap<>();
    final Map<String, Map<String, Integer>> r2Ids = new TreeMap<>();
    final Map<String, ShapeDrawable> shapes = new TreeMap<>();
    final Map<String, SelectorDrawable> selectors = new TreeMap<>();
    final Map<String, LayerListDrawable> layerLists = new TreeMap<>();
    final Map<String, SingleDrawable> singleDrawables = new TreeMap<>();
    final Map<String, LevelListDrawable> levelLists = new TreeMap<>();
    final Map<String, BitmapAsset> bitmaps = new TreeMap<>();
    final Map<String, String> drawableKinds = new TreeMap<>();
    final Set<String> declaredDrawables = new TreeSet<>();
    final Map<String, List<String>> drawableDependencies = new TreeMap<>();
    final Map<String, LayoutNode> layouts = new TreeMap<>();

    Model(CustomViewRegistry customViews, int minApi) {
        this.customViews = customViews;
        this.minApi = minApi;
    }
}

final class AssetLock {
    int schema;
    List<LockedAsset> assets;
}

final class LockedAsset {
    String name;
    String url;
    String sha256;
    String mime;
    long bytes;
}

final class Report {
    int schema;
    String mode;
    String generatedPackage;
    List<String> inputs;
    Map<String, Integer> counts;
    List<String> invariants;
    List<String> frameworkViews;
    Map<String, String> frameworkViewGroups;
    List<String> resourceCapabilities;
}

final class AssetCandidates {
    int schema;
    List<CandidateAsset> assets;
}

final class CandidateAsset {
    String name;
    String source;
    String sha256;
    String mime;
    long bytes;
}
