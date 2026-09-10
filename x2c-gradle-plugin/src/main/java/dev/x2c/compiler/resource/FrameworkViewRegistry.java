package dev.x2c.compiler.resource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Single source of truth for supported framework Views, ViewGroups, attributes and LayoutParams.
 */
final class FrameworkViewRegistry {
  static final Set<String> FRAMEWORK_VIEW_TAGS =
      set(
          "View",
          "Space",
          "SurfaceView",
          "TextureView",
          "LinearLayout",
          "FrameLayout",
          "RelativeLayout",
          "GridLayout",
          "TableLayout",
          "TableRow",
          "RadioGroup",
          "ScrollView",
          "HorizontalScrollView",
          "AbsoluteLayout",
          "ViewAnimator",
          "ViewFlipper",
          "ViewSwitcher",
          "TextSwitcher",
          "ImageSwitcher",
          "SearchView",
          "NumberPicker",
          "ZoomControls",
          "DatePicker",
          "TimePicker",
          "CalendarView",
          "TabHost",
          "TabWidget",
          "Toolbar",
          "ActionMenuView",
          "TwoLineListItem",
          "DialerFilter",
          "TextView",
          "Button",
          "EditText",
          "AutoCompleteTextView",
          "MultiAutoCompleteTextView",
          "CheckBox",
          "RadioButton",
          "Switch",
          "ToggleButton",
          "CheckedTextView",
          "Chronometer",
          "ImageView",
          "ImageButton",
          "QuickContactBadge",
          "ProgressBar",
          "SeekBar",
          "RatingBar",
          "ListView",
          "GridView",
          "ExpandableListView",
          "Spinner",
          "android.webkit.WebView");

  static final Set<String> FRAMEWORK_CONTAINERS =
      set(
          "LinearLayout",
          "FrameLayout",
          "RelativeLayout",
          "GridLayout",
          "TableLayout",
          "TableRow",
          "RadioGroup",
          "ScrollView",
          "HorizontalScrollView",
          "AbsoluteLayout",
          "ViewAnimator",
          "ViewFlipper",
          "ViewSwitcher",
          "TextSwitcher",
          "ImageSwitcher",
          "SearchView",
          "NumberPicker",
          "ZoomControls",
          "DatePicker",
          "TimePicker",
          "CalendarView",
          "TabHost",
          "TabWidget",
          "Toolbar",
          "ActionMenuView",
          "TwoLineListItem",
          "DialerFilter",
          "android.webkit.WebView");

  static final Set<String> PARENT_LAYOUT_ATTRIBUTES =
      set(
          "layout_width",
          "layout_height",
          "layout_weight",
          "layout_gravity",
          "layout_margin",
          "layout_marginLeft",
          "layout_marginTop",
          "layout_marginRight",
          "layout_marginBottom",
          "layout_marginStart",
          "layout_marginEnd",
          "layout_x",
          "layout_y",
          "layout_column",
          "layout_span",
          "layout_row",
          "layout_rowSpan",
          "layout_rowWeight",
          "layout_columnSpan",
          "layout_columnWeight",
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
          "layout_alignBaseline",
          "layout_alignParentLeft",
          "layout_alignParentTop",
          "layout_alignParentRight",
          "layout_alignParentBottom",
          "layout_alignParentStart",
          "layout_alignParentEnd",
          "layout_centerHorizontal",
          "layout_centerVertical",
          "layout_centerInParent",
          "layout_alignWithParentIfMissing");

  static final Set<String> TEXT_VIEW_TAGS =
      set(
          "TextView",
          "Button",
          "EditText",
          "AutoCompleteTextView",
          "MultiAutoCompleteTextView",
          "CheckBox",
          "RadioButton",
          "Switch",
          "ToggleButton",
          "CheckedTextView",
          "Chronometer");
  static final Set<String> IMAGE_VIEW_TAGS = set("ImageView", "ImageButton", "QuickContactBadge");
  static final Set<String> COMPOUND_BUTTON_TAGS =
      set("CheckBox", "RadioButton", "Switch", "ToggleButton");
  static final Set<String> PROGRESS_TAGS = set("ProgressBar", "SeekBar", "RatingBar");
  private static final Set<String> RELATIVE_LAYOUT_ATTRIBUTES =
      set(
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
          "layout_alignBaseline",
          "layout_alignParentLeft",
          "layout_alignParentTop",
          "layout_alignParentRight",
          "layout_alignParentBottom",
          "layout_alignParentStart",
          "layout_alignParentEnd",
          "layout_centerHorizontal",
          "layout_centerVertical",
          "layout_centerInParent",
          "layout_alignWithParentIfMissing");
  private static final Set<String> GRID_LAYOUT_ATTRIBUTES =
      set(
          "layout_row",
          "layout_rowSpan",
          "layout_rowWeight",
          "layout_column",
          "layout_columnSpan",
          "layout_columnWeight",
          "layout_gravity");
  private static final Set<String> COMMON_VIEW_ATTRIBUTES =
      set(
          "layout_width",
          "layout_height",
          "layout_weight",
          "layout_gravity",
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
          "background",
          "backgroundTint",
          "foreground",
          "visibility",
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
          "soundEffectsEnabled",
          "hapticFeedbackEnabled",
          "duplicateParentState",
          "filterTouchesWhenObscured",
          "isScrollContainer",
          "clipToOutline",
          "id",
          "contentDescription",
          "tag",
          "tooltipText",
          "transitionName",
          "alpha",
          "elevation",
          "rotation",
          "rotationX",
          "rotationY",
          "scaleX",
          "scaleY",
          "translationX",
          "translationY",
          "translationZ",
          "minWidth",
          "minHeight",
          "layoutDirection",
          "textAlignment",
          "overScrollMode",
          "scrollbars",
          "importantForAccessibility",
          "accessibilityLiveRegion",
          "accessibilityHeading",
          "screenReaderFocusable");

  private FrameworkViewRegistry() {}

  static boolean isContainer(String tag) {
    return FRAMEWORK_CONTAINERS.contains(tag);
  }

  static FrameworkParentKind frameworkParentKind(String tag) {
    if (set("LinearLayout", "SearchView", "NumberPicker", "ZoomControls", "TabWidget")
        .contains(tag)) return FrameworkParentKind.LINEAR;
    if (set(
            "FrameLayout",
            "ScrollView",
            "HorizontalScrollView",
            "ViewAnimator",
            "ViewFlipper",
            "ViewSwitcher",
            "TextSwitcher",
            "ImageSwitcher",
            "DatePicker",
            "TimePicker",
            "CalendarView",
            "TabHost")
        .contains(tag)) return FrameworkParentKind.FRAME;
    if (set("RelativeLayout", "TwoLineListItem", "DialerFilter").contains(tag)) {
      return FrameworkParentKind.RELATIVE;
    }
    if ("GridLayout".equals(tag)) return FrameworkParentKind.GRID;
    if ("TableLayout".equals(tag)) return FrameworkParentKind.TABLE;
    if ("TableRow".equals(tag)) return FrameworkParentKind.TABLE_ROW;
    if ("RadioGroup".equals(tag)) return FrameworkParentKind.RADIO;
    if ("AbsoluteLayout".equals(tag) || "android.webkit.WebView".equals(tag)) {
      return FrameworkParentKind.ABSOLUTE;
    }
    if ("Toolbar".equals(tag)) return FrameworkParentKind.TOOLBAR;
    if ("ActionMenuView".equals(tag)) return FrameworkParentKind.ACTION_MENU;
    return null;
  }

  static boolean isAllowedFrameworkLayoutAttribute(String parentTag, String name) {
    FrameworkParentKind kind = frameworkParentKind(parentTag);
    if (kind == null) return false;
    if (name.equals("layout_width") || name.equals("layout_height")) return true;
    if (name.startsWith("layout_margin")) return kind != FrameworkParentKind.ABSOLUTE;
    if (kind == FrameworkParentKind.RELATIVE) return RELATIVE_LAYOUT_ATTRIBUTES.contains(name);
    if (kind == FrameworkParentKind.GRID) return GRID_LAYOUT_ATTRIBUTES.contains(name);
    if (kind == FrameworkParentKind.ABSOLUTE)
      return name.equals("layout_x") || name.equals("layout_y");
    if (kind == FrameworkParentKind.TABLE_ROW) {
      return set("layout_weight", "layout_gravity", "layout_column", "layout_span").contains(name);
    }
    return set(FrameworkParentKind.LINEAR, FrameworkParentKind.TABLE, FrameworkParentKind.RADIO)
                .contains(kind)
            && set("layout_weight", "layout_gravity").contains(name)
        || set(
                    FrameworkParentKind.FRAME,
                    FrameworkParentKind.TOOLBAR,
                    FrameworkParentKind.ACTION_MENU)
                .contains(kind)
            && name.equals("layout_gravity");
  }

  static boolean isAllowedLayoutAttribute(LayoutNode node, String name) {
    if (COMMON_VIEW_ATTRIBUTES.contains(name)) return true;
    if (TEXT_VIEW_TAGS.contains(node.tag)) {
      boolean commonText =
          set(
                  "text",
                  "textColor",
                  "textSize",
                  "textStyle",
                  "gravity",
                  "maxLines",
                  "minLines",
                  "lines",
                  "singleLine",
                  "ellipsize",
                  "hint",
                  "textColorHint",
                  "includeFontPadding",
                  "letterSpacing",
                  "lineSpacingExtra",
                  "lineSpacingMultiplier",
                  "textAllCaps",
                  "textIsSelectable",
                  "selectAllOnFocus",
                  "inputType",
                  "imeOptions")
              .contains(name);
      return commonText
          || ((COMPOUND_BUTTON_TAGS.contains(node.tag) || node.tag.equals("CheckedTextView"))
              && name.equals("checked"))
          || (COMPOUND_BUTTON_TAGS.contains(node.tag) && name.equals("buttonTint"));
    }
    if (IMAGE_VIEW_TAGS.contains(node.tag)) {
      return set("src", "scaleType", "adjustViewBounds", "cropToPadding", "imageTint")
          .contains(name);
    }
    if (PROGRESS_TAGS.contains(node.tag)) {
      return set("max", "progress", "secondaryProgress", "indeterminate", "progressTint")
              .contains(name)
          || node.tag.equals("SeekBar") && name.equals("thumb")
          || node.tag.equals("RatingBar")
              && set("rating", "numStars", "stepSize", "isIndicator").contains(name);
    }
    if (FRAMEWORK_CONTAINERS.contains(node.tag)) {
      if (set("clipChildren", "clipToPadding", "motionEventSplittingEnabled").contains(name))
        return true;
      if ("LinearLayout".equals(node.tag) || "TableRow".equals(node.tag)) {
        return set(
                "orientation",
                "gravity",
                "weightSum",
                "baselineAligned",
                "baselineAlignedChildIndex",
                "measureWithLargestChild")
            .contains(name);
      }
      if ("RadioGroup".equals(node.tag)) {
        return set(
                "orientation",
                "gravity",
                "weightSum",
                "baselineAligned",
                "baselineAlignedChildIndex",
                "measureWithLargestChild",
                "checkedButton")
            .contains(name);
      }
      if ("TableLayout".equals(node.tag)) {
        return set("gravity", "weightSum", "stretchColumns", "shrinkColumns", "collapseColumns")
            .contains(name);
      }
      if ("FrameLayout".equals(node.tag)
          || "ViewAnimator".equals(node.tag)
          || "ViewFlipper".equals(node.tag)) {
        return set("foregroundGravity", "measureAllChildren").contains(name);
      }
      if ("RelativeLayout".equals(node.tag)) return set("gravity", "ignoreGravity").contains(name);
      if ("GridLayout".equals(node.tag)) {
        return set(
                "orientation",
                "rowCount",
                "columnCount",
                "useDefaultMargins",
                "alignmentMode",
                "rowOrderPreserved",
                "columnOrderPreserved")
            .contains(name);
      }
      if ("ScrollView".equals(node.tag) || "HorizontalScrollView".equals(node.tag)) {
        return set("fillViewport", "smoothScrollingEnabled").contains(name);
      }
      return false;
    }
    return false;
  }

  static String frameworkLayoutParamsClass(String tag) {
    FrameworkParentKind kind = frameworkParentKind(tag);
    switch (kind) {
      case LINEAR:
        return "android.widget.LinearLayout.LayoutParams";
      case FRAME:
        return "android.widget.FrameLayout.LayoutParams";
      case RELATIVE:
        return "android.widget.RelativeLayout.LayoutParams";
      case GRID:
        return "android.widget.GridLayout.LayoutParams";
      case TABLE:
        return "android.widget.TableLayout.LayoutParams";
      case TABLE_ROW:
        return "android.widget.TableRow.LayoutParams";
      case RADIO:
        return "android.widget.RadioGroup.LayoutParams";
      case ABSOLUTE:
        return "android.widget.AbsoluteLayout.LayoutParams";
      case TOOLBAR:
        return "android.widget.Toolbar.LayoutParams";
      case ACTION_MENU:
        return "android.widget.ActionMenuView.LayoutParams";
      default:
        throw new AssertionError(kind);
    }
  }

  @SafeVarargs
  private static <T> Set<T> set(T... values) {
    return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(values)));
  }
}
