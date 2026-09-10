package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.setOf;
import static dev.x2c.compiler.resource.FileSupport.fail;
import static dev.x2c.compiler.resource.FrameworkViewRegistry.*;
import static dev.x2c.compiler.resource.ResourceValueParser.*;
import static dev.x2c.compiler.resource.XmlSupport.*;

import java.io.File;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Parses layouts into a validated View tree without emitting Java source. */
final class LayoutResourceParser {
  private LayoutResourceParser() {}

  static void collectLayoutIdDeclarations(ResourceFile source, Model model) {
    Document document = parseXml(source.file);
    collectLayoutIdDeclarations(source.file, document.getDocumentElement(), model);
  }

  static void collectLayoutIdDeclarations(File file, Element element, Model model) {
    String value = androidAttribute(element, "id");
    if (!value.isEmpty()) {
      IdReference id = parseIdReference(file, value);
      if (id.kind == IdKind.DECLARE) {
        model.declaredIds.add(id.name);
      }
    }
    for (Element child : childElements(element)) {
      collectLayoutIdDeclarations(file, child, model);
    }
  }

  static void parseLayout(ResourceFile source, Model model) {
    String name = resourceNameFromFile(source.file);
    Document document = parseXml(source.file);
    rejectThemeSyntax(source.file, document);
    LayoutNode root = parseLayoutNode(source.file, document.getDocumentElement(), model, null);
    if (!isContainer(root)) {
      throw fail(
          source.file,
          "Layout root must be a supported framework ViewGroup or registered custom ViewGroup");
    }
    for (String attribute : root.attributes.keySet()) {
      if (attribute.startsWith("layout_")
          && !attribute.equals("layout_width")
          && !attribute.equals("layout_height")) {
        throw fail(
            source.file,
            "Root layout cannot use parent LayoutParams attribute: android:" + attribute);
      }
    }
    putUnique(model.layouts, name, root, source.file, "layout");
  }

  static LayoutNode parseLayoutNode(File file, Element element, Model model, LayoutNode parent) {
    String xmlTag = element.getTagName();
    String resolvedTag = xmlTag;
    if (xmlTag.equals("view")) {
      resolvedTag = element.getAttribute("class");
      if (resolvedTag.trim().isEmpty()) {
        throw fail(file, "<view> requires a class attribute");
      }
    }
    CustomViewRegistry.ViewSpec customView =
        FRAMEWORK_VIEW_TAGS.contains(resolvedTag) ? null : model.customViews.find(resolvedTag);
    if (!FRAMEWORK_VIEW_TAGS.contains(resolvedTag) && customView == null) {
      throw fail(
          file, "Unregistered custom View: " + resolvedTag + ". Declare it in x2c.customViewsFile");
    }
    String javaType = customView == null ? resolvedTag : customView.className();
    LayoutNode node = new LayoutNode(resolvedTag, javaType, customView);
    NamedNodeMap attributes = element.getAttributes();
    for (int index = 0; index < attributes.getLength(); index++) {
      Node attribute = attributes.item(index);
      if (attribute.getNodeName().startsWith("xmlns")) {
        continue;
      }
      if (xmlTag.equals("view") && attribute.getNodeName().equals("class")) {
        continue;
      }
      String name = attribute.getLocalName();
      CustomViewRegistry.AttributeSpec parentLayoutAttribute =
          name == null || parent == null || parent.container() == null
              ? null
              : parent.container().layoutAttribute(name);
      if (ANDROID_NS.equals(attribute.getNamespaceURI())) {
        if (PARENT_LAYOUT_ATTRIBUTES.contains(name)) {
          if (parent == null
              || isAllowedFrameworkLayoutAttribute(parent.tag, name)
              || (parent.container() != null
                  && (name.equals("layout_width")
                      || name.equals("layout_height")
                      || (name.startsWith("layout_margin")
                          && parent.container().marginLayoutParams())))) {
            node.attributes.put(
                name, normalizedAttributeValue(file, name, attribute.getNodeValue(), null));
          } else if (parentLayoutAttribute != null) {
            node.layoutAttributes.put(
                name,
                new CustomAttributeValue(
                    parentLayoutAttribute,
                    normalizedAttributeValue(
                        file, name, attribute.getNodeValue(), parentLayoutAttribute)));
          } else {
            throw fail(
                file,
                "Unsupported LayoutParams attribute for parent "
                    + parent.tag
                    + ": android:"
                    + name);
          }
          continue;
        }
        if (!isAllowedLayoutAttribute(node, name)) {
          throw fail(file, "Unsupported " + resolvedTag + " attribute: android:" + name);
        }
        node.attributes.put(
            name, normalizedAttributeValue(file, name, attribute.getNodeValue(), null));
        continue;
      }
      if (parentLayoutAttribute != null) {
        node.layoutAttributes.put(
            name,
            new CustomAttributeValue(
                parentLayoutAttribute,
                normalizedAttributeValue(
                    file, name, attribute.getNodeValue(), parentLayoutAttribute)));
        continue;
      }
      if (name != null
          && name.startsWith("layout_")
          && parent != null
          && parent.container() != null) {
        throw fail(
            file,
            "Unsupported LayoutParams attribute for parent "
                + parent.tag
                + ": "
                + attribute.getNodeName());
      }
      CustomViewRegistry.AttributeSpec customAttribute =
          customView == null ? null : customView.attribute(name);
      if (customAttribute == null) {
        throw fail(
            file,
            "Unsupported custom attribute on " + resolvedTag + ": " + attribute.getNodeName());
      }
      node.customAttributes.put(
          name,
          new CustomAttributeValue(
              customAttribute,
              normalizedAttributeValue(file, name, attribute.getNodeValue(), customAttribute)));
    }
    requireAttribute(file, element, "layout_width");
    requireAttribute(file, element, "layout_height");
    validateLayoutAttributes(file, node, model);
    for (CustomAttributeValue customAttribute : node.customAttributes.values()) {
      validateCustomAttribute(file, customAttribute, model);
    }
    for (CustomAttributeValue layoutAttribute : node.layoutAttributes.values()) {
      validateCustomAttribute(file, layoutAttribute, model);
    }

    for (Element child : childElements(element)) {
      if (!isContainer(node)) {
        throw fail(file, resolvedTag + " cannot contain child views");
      }
      node.children.add(parseLayoutNode(file, child, model, node));
    }
    if ((resolvedTag.equals("ScrollView") || resolvedTag.equals("HorizontalScrollView"))
        && node.children.size() > 1) {
      throw fail(file, resolvedTag + " can contain only one direct child");
    }
    return node;
  }

  private static String normalizedAttributeValue(
      File file, String name, String value, CustomViewRegistry.AttributeSpec customAttribute) {
    if (customAttribute != null && "STRING".equals(customAttribute.type())) {
      return decodeAndroidText(file, value);
    }
    if (setOf("text", "hint", "contentDescription", "tag", "tooltipText", "transitionName")
        .contains(name)) {
      return decodeAndroidText(file, value);
    }
    return value;
  }

  static boolean isContainer(LayoutNode node) {
    return FRAMEWORK_CONTAINERS.contains(node.tag) || node.container() != null;
  }

  static void validateLayoutAttributes(File file, LayoutNode node, Model model) {
    for (Map.Entry<String, String> item : node.attributes.entrySet()) {
      String name = item.getKey();
      String value = item.getValue();
      if ("id".equals(name)) {
        IdReference id = parseIdReference(file, value);
        if (id.kind == IdKind.LOCAL && !model.declaredIds.contains(id.name)) {
          throw fail(
              file,
              "Unknown ID reference: "
                  + value
                  + ". Declare it with @+id/"
                  + id.name
                  + " or <item type=\"id\" name=\""
                  + id.name
                  + "\" />");
        }
      } else if (setOf("layout_width", "layout_height").contains(name)) {
        parseLayoutSize(file, value, model);
      } else if ("layout_weight".equals(name)) {
        parseFloat(file, value, "layout_weight");
      } else if ("layout_gravity".equals(name)) {
        parseGravity(file, value);
      } else if (setOf(
              "layout_margin",
              "layout_marginLeft",
              "layout_marginTop",
              "layout_marginRight",
              "layout_marginBottom",
              "layout_marginStart",
              "layout_marginEnd",
              "padding",
              "paddingLeft",
              "paddingTop",
              "paddingRight",
              "paddingBottom",
              "paddingStart",
              "paddingEnd",
              "textSize",
              "elevation",
              "translationX",
              "translationY",
              "translationZ",
              "minWidth",
              "minHeight",
              "lineSpacingExtra",
              "layout_x",
              "layout_y")
          .contains(name)) {
        resolveDimension(file, value, model);
      } else if ("background".equals(name)) {
        resolveColorOrDrawable(file, value, model, false);
      } else if (setOf(
              "textColor",
              "backgroundTint",
              "textColorHint",
              "buttonTint",
              "imageTint",
              "progressTint")
          .contains(name)) {
        resolveColorStateList(file, value, model);
      } else if ("foreground".equals(name)) {
        resolveForeground(file, value, model);
      } else if (setOf("text", "contentDescription", "tag", "tooltipText", "transitionName", "hint")
          .contains(name)) {
        resolveString(file, value, model);
      } else if ("src".equals(name)) {
        resolveDrawable(file, value, model);
      } else if ("orientation".equals(name)) {
        requireOneOf(file, name, value, "horizontal", "vertical");
      } else if (setOf("gravity", "foregroundGravity").contains(name)) {
        parseGravity(file, value);
      } else if ("visibility".equals(name)) {
        requireOneOf(file, name, value, "visible", "invisible", "gone");
      } else if (setOf(
              "enabled",
              "clickable",
              "longClickable",
              "focusable",
              "focusableInTouchMode",
              "selected",
              "activated",
              "saveEnabled",
              "keepScreenOn",
              "fitsSystemWindows",
              "adjustViewBounds",
              "soundEffectsEnabled",
              "hapticFeedbackEnabled",
              "duplicateParentState",
              "filterTouchesWhenObscured",
              "isScrollContainer",
              "clipToOutline",
              "accessibilityHeading",
              "screenReaderFocusable",
              "singleLine",
              "includeFontPadding",
              "textAllCaps",
              "textIsSelectable",
              "selectAllOnFocus",
              "checked",
              "cropToPadding",
              "indeterminate",
              "isIndicator",
              "clipChildren",
              "clipToPadding",
              "motionEventSplittingEnabled",
              "baselineAligned",
              "measureWithLargestChild",
              "measureAllChildren",
              "useDefaultMargins",
              "rowOrderPreserved",
              "columnOrderPreserved",
              "fillViewport",
              "smoothScrollingEnabled",
              "layout_alignParentLeft",
              "layout_alignParentTop",
              "layout_alignParentRight",
              "layout_alignParentBottom",
              "layout_alignParentStart",
              "layout_alignParentEnd",
              "layout_centerHorizontal",
              "layout_centerVertical",
              "layout_centerInParent",
              "layout_alignWithParentIfMissing")
          .contains(name)) {
        resolveBoolean(file, value, model, name);
      } else if (setOf(
              "alpha",
              "rotation",
              "rotationX",
              "rotationY",
              "scaleX",
              "scaleY",
              "letterSpacing",
              "lineSpacingMultiplier",
              "weightSum",
              "rating",
              "stepSize",
              "layout_rowWeight",
              "layout_columnWeight")
          .contains(name)) {
        parseFloat(file, value, name);
      } else if ("layoutDirection".equals(name)) {
        requireOneOf(file, name, value, "ltr", "rtl", "inherit", "locale");
      } else if ("textAlignment".equals(name)) {
        requireOneOf(
            file,
            name,
            value,
            "inherit",
            "gravity",
            "textStart",
            "textEnd",
            "center",
            "viewStart",
            "viewEnd");
      } else if ("importantForAccessibility".equals(name)) {
        requireOneOf(file, name, value, "auto", "yes", "no", "noHideDescendants");
      } else if ("overScrollMode".equals(name)) {
        requireOneOf(file, name, value, "always", "ifContentScrolls", "never");
      } else if ("scrollbars".equals(name)) {
        validateScrollbars(file, value);
      } else if ("accessibilityLiveRegion".equals(name)) {
        requireOneOf(file, name, value, "none", "polite", "assertive");
      } else if (setOf(
              "maxLines",
              "minLines",
              "lines",
              "baselineAlignedChildIndex",
              "rowCount",
              "columnCount",
              "max",
              "progress",
              "secondaryProgress",
              "numStars",
              "layout_row",
              "layout_rowSpan",
              "layout_column",
              "layout_columnSpan",
              "layout_span")
          .contains(name)) {
        resolveInteger(file, value, model, name);
      } else if ("scaleType".equals(name)) {
        requireOneOf(
            file,
            name,
            value,
            "center",
            "centerCrop",
            "centerInside",
            "fitCenter",
            "fitStart",
            "fitEnd",
            "fitXY",
            "matrix");
      } else if ("textStyle".equals(name)) {
        parseTextStyle(file, value);
      } else if ("ellipsize".equals(name)) {
        requireOneOf(file, name, value, "start", "middle", "end", "marquee", "none");
      } else if (setOf("inputType", "imeOptions").contains(name)) {
        if (value.startsWith("@integer/")) resolveInteger(file, value, model, name);
        else parseIntegerOrFlags(file, value, name);
      } else if ("thumb".equals(name)) {
        resolveGeneratedDrawable(file, value, model);
      } else if (setOf("ignoreGravity", "checkedButton").contains(name)) {
        validateIdReference(file, value, model);
      } else if ("alignmentMode".equals(name)) {
        requireOneOf(file, name, value, "alignBounds", "alignMargins");
      } else if (setOf("stretchColumns", "shrinkColumns", "collapseColumns").contains(name)) {
        parseColumnList(file, value, name);
      } else if (setOf(
              "layout_above",
              "layout_below",
              "layout_toLeftOf",
              "layout_toRightOf",
              "layout_toStartOf",
              "layout_toEndOf",
              "layout_alignLeft",
              "layout_alignTop",
              "layout_alignRight",
              "layout_alignBottom",
              "layout_alignStart",
              "layout_alignEnd",
              "layout_alignBaseline")
          .contains(name)) {
        validateIdReference(file, value, model);
      }
    }
    boolean absolutePadding =
        node.attributes.keySet().stream()
            .anyMatch(name -> setOf("paddingLeft", "paddingRight").contains(name));
    boolean relativePadding =
        node.attributes.keySet().stream()
            .anyMatch(name -> setOf("paddingStart", "paddingEnd").contains(name));
    if (absolutePadding && relativePadding) {
      throw fail(file, "Do not mix absolute and relative padding attributes");
    }
    boolean absoluteMargins =
        node.attributes.keySet().stream()
            .anyMatch(name -> setOf("layout_marginLeft", "layout_marginRight").contains(name));
    boolean relativeMargins =
        node.attributes.keySet().stream()
            .anyMatch(name -> setOf("layout_marginStart", "layout_marginEnd").contains(name));
    if (absoluteMargins && relativeMargins) {
      throw fail(file, "Do not mix absolute and relative margin attributes");
    }
  }

  static void validateCustomAttribute(File file, CustomAttributeValue value, Model model) {
    switch (value.spec.type()) {
      case "STRING":
        resolveString(file, value.value, model);
        break;
      case "COLOR":
        resolveColor(file, value.value, model);
        break;
      case "DIMENSION":
      case "DIMENSION_INT":
        resolveDimension(file, value.value, model);
        break;
      case "BOOLEAN":
        resolveBoolean(file, value.value, model, value.spec.name());
        break;
      case "INTEGER":
        resolveInteger(file, value.value, model, value.spec.name());
        break;
      case "FLOAT":
        parseFloat(file, value.value, value.spec.name());
        break;
      case "GRAVITY":
        parseGravity(file, value.value);
        break;
      case "DRAWABLE":
        resolveGeneratedDrawable(file, value.value, model);
        break;
      case "IMAGE_ASSET":
        resolveBitmapDrawable(file, value.value, model);
        break;
      default:
        throw new AssertionError(value.spec.type());
    }
  }

  private static void validateScrollbars(File file, String value) {
    if ("none".equals(value)) return;
    boolean horizontal = false;
    boolean vertical = false;
    for (String token : value.split("\\|", -1)) {
      String flag = token.trim();
      if ("horizontal".equals(flag) && !horizontal) {
        horizontal = true;
      } else if ("vertical".equals(flag) && !vertical) {
        vertical = true;
      } else {
        throw fail(
            file,
            "scrollbars must be none, horizontal, vertical, or horizontal|vertical; got: " + value);
      }
    }
  }
}
