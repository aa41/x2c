package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.entry;
import static dev.x2c.compiler.resource.CollectionSupport.mapOfEntries;
import static dev.x2c.compiler.resource.CollectionSupport.setOf;
import static dev.x2c.compiler.resource.FrameworkViewRegistry.*;
import static dev.x2c.compiler.resource.JavaExpressions.*;
import static dev.x2c.compiler.resource.ResourceValueParser.*;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/** Emits one validated layout tree as deterministic Java statements. */
final class LayoutEmitter {
  private final JavaSource out;
  private final Model model;
  private final boolean pluginMode;
  private int nextId;

  LayoutEmitter(JavaSource out, Model model, boolean pluginMode) {
    this.out = out;
    this.model = model;
    this.pluginMode = pluginMode;
  }

  String emit(LayoutNode node, LayoutNode parentNode, String parentVariable) {
    String variable = "view" + nextId++;
    out.line(node.javaType + " " + variable + " = " + constructorExpression(node) + ";");
    applyProperties(node, variable);
    String params = null;
    if (parentNode == null) {
      out.line(
          variable
              + ".setLayoutParams(new ViewGroup.LayoutParams("
              + sizeExpression(node.attributes.get("layout_width"))
              + ", "
              + sizeExpression(node.attributes.get("layout_height"))
              + "));");
    } else {
      params = emitLayoutParams(node, parentNode, variable);
    }
    for (LayoutNode child : node.children) {
      emit(child, node, variable);
    }
    if (node.tag.equals("RadioGroup") && node.attributes.containsKey("checkedButton")) {
      out.line(variable + ".check(" + idExpression(node.attributes.get("checkedButton")) + ");");
    }
    if (node.container() != null && node.container().childrenFinishedSetter() != null) {
      out.line(variable + "." + node.container().childrenFinishedSetter() + "();");
    }
    if (parentNode != null) {
      out.line(parentVariable + ".addView(" + variable + ", " + params + ");");
    }
    return variable;
  }

  private String emitLayoutParams(LayoutNode node, LayoutNode parent, String variable) {
    String params = variable + "Params";
    String width = sizeExpression(node.attributes.get("layout_width"));
    String height = sizeExpression(node.attributes.get("layout_height"));
    if (FRAMEWORK_CONTAINERS.contains(parent.tag)) {
      FrameworkParentKind kind = frameworkParentKind(parent.tag);
      switch (kind) {
        case LINEAR:
          emitLinearLayoutParams("LinearLayout.LayoutParams", node, params, width, height);
          break;
        case TABLE:
          emitLinearLayoutParams("TableLayout.LayoutParams", node, params, width, height);
          break;
        case TABLE_ROW:
          emitLinearLayoutParams("TableRow.LayoutParams", node, params, width, height);
          if (node.attributes.containsKey("layout_column")) {
            out.line(
                params
                    + ".column = "
                    + integerExpression(node.attributes.get("layout_column"))
                    + ";");
          }
          if (node.attributes.containsKey("layout_span")) {
            out.line(
                params + ".span = " + integerExpression(node.attributes.get("layout_span")) + ";");
          }
          break;
        case RADIO:
          emitLinearLayoutParams("RadioGroup.LayoutParams", node, params, width, height);
          break;
        case FRAME:
          out.line(
              "FrameLayout.LayoutParams "
                  + params
                  + " = new FrameLayout.LayoutParams("
                  + width
                  + ", "
                  + height
                  + ");");
          emitMargins(node, params);
          if (node.attributes.containsKey("layout_gravity")) {
            out.line(
                params
                    + ".gravity = "
                    + gravityExpression(node.attributes.get("layout_gravity"))
                    + ";");
          }
          break;
        case RELATIVE:
          out.line(
              "RelativeLayout.LayoutParams "
                  + params
                  + " = new RelativeLayout.LayoutParams("
                  + width
                  + ", "
                  + height
                  + ");");
          emitMargins(node, params);
          emitRelativeLayoutRules(node, params);
          break;
        case GRID:
          emitGridLayoutParams(node, params, width, height);
          break;
        case ABSOLUTE:
          out.line(
              "AbsoluteLayout.LayoutParams "
                  + params
                  + " = new AbsoluteLayout.LayoutParams("
                  + width
                  + ", "
                  + height
                  + ", "
                  + roundedDimension(node.attributes.getOrDefault("layout_x", "0px"))
                  + ", "
                  + roundedDimension(node.attributes.getOrDefault("layout_y", "0px"))
                  + ");");
          break;
        case TOOLBAR:
          out.line(
              "Toolbar.LayoutParams "
                  + params
                  + " = new Toolbar.LayoutParams("
                  + width
                  + ", "
                  + height
                  + ");");
          emitMargins(node, params);
          if (node.attributes.containsKey("layout_gravity")) {
            out.line(
                params
                    + ".gravity = "
                    + gravityExpression(node.attributes.get("layout_gravity"))
                    + ";");
          }
          break;
        case ACTION_MENU:
          out.line(
              "ActionMenuView.LayoutParams "
                  + params
                  + " = new ActionMenuView.LayoutParams("
                  + width
                  + ", "
                  + height
                  + ");");
          emitMargins(node, params);
          if (node.attributes.containsKey("layout_gravity")) {
            out.line(
                params
                    + ".gravity = "
                    + gravityExpression(node.attributes.get("layout_gravity"))
                    + ";");
          }
          break;
        default:
          throw new AssertionError(kind);
      }
      return params;
    }
    CustomViewRegistry.ContainerSpec container = parent.container();
    if (container == null) {
      throw new AssertionError("Parent is not a ViewGroup: " + parent.tag);
    }
    if (container.layoutParamsFactoryClass() == null) {
      out.line(
          container.layoutParamsClass()
              + " "
              + params
              + " = new "
              + container.layoutParamsClass()
              + "("
              + width
              + ", "
              + height
              + ");");
    } else {
      out.line(
          container.layoutParamsClass()
              + " "
              + params
              + " = "
              + container.layoutParamsFactoryClass()
              + "."
              + container.layoutParamsFactoryMethod()
              + "(context, "
              + width
              + ", "
              + height
              + ");");
    }
    if (container.marginLayoutParams()) {
      emitMargins(node, params);
    }
    for (CustomAttributeValue attribute : node.layoutAttributes.values()) {
      emitCustomAssignment(params, attribute);
    }
    return params;
  }

  private void emitLinearLayoutParams(
      String type, LayoutNode node, String params, String width, String height) {
    String weight =
        node.attributes.containsKey("layout_weight")
            ? floatExpression(node.attributes.get("layout_weight"))
            : "0.0f";
    out.line(
        type + " " + params + " = new " + type + "(" + width + ", " + height + ", " + weight
            + ");");
    emitMargins(node, params);
    if (node.attributes.containsKey("layout_gravity")) {
      out.line(
          params + ".gravity = " + gravityExpression(node.attributes.get("layout_gravity")) + ";");
    }
  }

  private void emitRelativeLayoutRules(LayoutNode node, String params) {
    Map<String, String> anchoredRules =
        mapOfEntries(
            entry("layout_above", "ABOVE"),
            entry("layout_below", "BELOW"),
            entry("layout_toLeftOf", "LEFT_OF"),
            entry("layout_toRightOf", "RIGHT_OF"),
            entry("layout_toStartOf", "START_OF"),
            entry("layout_toEndOf", "END_OF"),
            entry("layout_alignLeft", "ALIGN_LEFT"),
            entry("layout_alignTop", "ALIGN_TOP"),
            entry("layout_alignRight", "ALIGN_RIGHT"),
            entry("layout_alignBottom", "ALIGN_BOTTOM"),
            entry("layout_alignStart", "ALIGN_START"),
            entry("layout_alignEnd", "ALIGN_END"),
            entry("layout_alignBaseline", "ALIGN_BASELINE"));
    for (Map.Entry<String, String> rule : anchoredRules.entrySet()) {
      if (node.attributes.containsKey(rule.getKey())) {
        out.line(
            params
                + ".addRule(RelativeLayout."
                + rule.getValue()
                + ", "
                + idExpression(node.attributes.get(rule.getKey()))
                + ");");
      }
    }
    Map<String, String> booleanRules =
        mapOfEntries(
            entry("layout_alignParentLeft", "ALIGN_PARENT_LEFT"),
            entry("layout_alignParentTop", "ALIGN_PARENT_TOP"),
            entry("layout_alignParentRight", "ALIGN_PARENT_RIGHT"),
            entry("layout_alignParentBottom", "ALIGN_PARENT_BOTTOM"),
            entry("layout_alignParentStart", "ALIGN_PARENT_START"),
            entry("layout_alignParentEnd", "ALIGN_PARENT_END"),
            entry("layout_centerHorizontal", "CENTER_HORIZONTAL"),
            entry("layout_centerVertical", "CENTER_VERTICAL"),
            entry("layout_centerInParent", "CENTER_IN_PARENT"));
    for (Map.Entry<String, String> rule : booleanRules.entrySet()) {
      if (node.attributes.containsKey(rule.getKey())
          && literalBoolean(node.attributes.get(rule.getKey()))) {
        out.line(params + ".addRule(RelativeLayout." + rule.getValue() + ");");
      }
    }
    if (node.attributes.containsKey("layout_alignWithParentIfMissing")) {
      out.line(
          params
              + ".alignWithParent = "
              + booleanExpression(node.attributes.get("layout_alignWithParentIfMissing"))
              + ";");
    }
  }

  private void emitGridLayoutParams(LayoutNode node, String params, String width, String height) {
    out.line("GridLayout.LayoutParams " + params + " = new GridLayout.LayoutParams();");
    out.line(params + ".width = " + width + ";");
    out.line(params + ".height = " + height + ";");
    emitMargins(node, params);
    emitGridSpec(node, params, "row");
    emitGridSpec(node, params, "column");
    if (node.attributes.containsKey("layout_gravity")) {
      out.line(
          params
              + ".setGravity("
              + gravityExpression(node.attributes.get("layout_gravity"))
              + ");");
    }
  }

  private void emitGridSpec(LayoutNode node, String params, String axis) {
    String startName = "layout_" + axis;
    String spanName = startName + "Span";
    String weightName = startName + "Weight";
    if (!node.attributes.containsKey(startName)
        && !node.attributes.containsKey(spanName)
        && !node.attributes.containsKey(weightName)) {
      return;
    }
    String start =
        node.attributes.containsKey(startName)
            ? integerExpression(node.attributes.get(startName))
            : "GridLayout.UNDEFINED";
    String span =
        node.attributes.containsKey(spanName)
            ? integerExpression(node.attributes.get(spanName))
            : "1";
    String field = axis + "Spec";
    if (node.attributes.containsKey(weightName)) {
      out.line(
          params
              + "."
              + field
              + " = GridLayout.spec("
              + start
              + ", "
              + span
              + ", "
              + floatExpression(node.attributes.get(weightName))
              + ");");
    } else {
      out.line(params + "." + field + " = GridLayout.spec(" + start + ", " + span + ");");
    }
  }

  private void applyProperties(LayoutNode node, String variable) {
    Map<String, String> attrs = node.attributes;
    if (attrs.containsKey("id")) {
      out.line(variable + ".setId(" + idExpression(attrs.get("id")) + ");");
    }
    emitFrameworkContainerProperties(node, variable);
    emitPadding(attrs, variable);
    emitCommon(attrs, variable);
    if (attrs.containsKey("gravity")) {
      out.line(variable + ".setGravity(" + gravityExpression(attrs.get("gravity")) + ");");
    }
    if (TEXT_VIEW_TAGS.contains(node.tag)) {
      if (attrs.containsKey("text")) {
        out.line(variable + ".setText(" + stringExpression(attrs.get("text")) + ");");
      }
      if (attrs.containsKey("textColor")) {
        out.line(
            variable + ".setTextColor(" + colorStateListExpression(attrs.get("textColor")) + ");");
      }
      if (attrs.containsKey("textSize")) {
        String value = attrs.get("textSize");
        if (value.startsWith("@dimen/")) {
          out.line(
              variable
                  + ".setTextSize(TypedValue.COMPLEX_UNIT_PX, "
                  + dimenReference(value)
                  + ");");
        } else {
          Dimension dimen = parseDimension(null, value);
          out.line(
              variable
                  + ".setTextSize("
                  + typedValueUnit(dimen.unit)
                  + ", "
                  + floatLiteral(dimen.value)
                  + ");");
        }
      }
      if (attrs.containsKey("maxLines")) {
        out.line(variable + ".setMaxLines(" + integerExpression(attrs.get("maxLines")) + ");");
      }
      if (attrs.containsKey("minLines")) {
        out.line(variable + ".setMinLines(" + integerExpression(attrs.get("minLines")) + ");");
      }
      if (attrs.containsKey("lines")) {
        out.line(variable + ".setLines(" + integerExpression(attrs.get("lines")) + ");");
      }
      emitTextProperties(node, variable);
    } else if (IMAGE_VIEW_TAGS.contains(node.tag)) {
      if (attrs.containsKey("src")) {
        String source = referenceName(attrs.get("src"), "drawable");
        if (model.bitmaps.containsKey(source)) {
          if (pluginMode) {
            out.line("X2cImages.load(" + variable + ", " + javaString(source) + ");");
          } else {
            out.line(
                variable
                    + ".setImageDrawable(context.getResources().getDrawable("
                    + "X2cModule.identifier(\"drawable\", "
                    + javaString(source)
                    + "), context.getTheme()));");
          }
        } else {
          out.line(variable + ".setImageDrawable(" + drawableExpression(source) + ");");
        }
      }
      if (attrs.containsKey("scaleType")) {
        out.line(
            variable
                + ".setScaleType(ImageView.ScaleType."
                + scaleTypeConstant(attrs.get("scaleType"))
                + ");");
      }
      if (attrs.containsKey("adjustViewBounds")) {
        out.line(
            variable
                + ".setAdjustViewBounds("
                + booleanExpression(attrs.get("adjustViewBounds"))
                + ");");
      }
      emitBooleanSetter(attrs, variable, "cropToPadding", "setCropToPadding");
      if (attrs.containsKey("imageTint")) {
        out.line(
            variable
                + ".setImageTintList("
                + colorStateListExpression(attrs.get("imageTint"))
                + ");");
      }
    } else if (PROGRESS_TAGS.contains(node.tag)) {
      emitProgressProperties(node, variable);
    }
    for (CustomAttributeValue customAttribute : node.customAttributes.values()) {
      emitCustomAssignment(variable, customAttribute);
    }
  }

  private void emitFrameworkContainerProperties(LayoutNode node, String variable) {
    Map<String, String> attrs = node.attributes;
    if (setOf("LinearLayout", "TableRow", "RadioGroup").contains(node.tag)) {
      out.line(
          variable
              + ".setOrientation(LinearLayout."
              + (attrs.getOrDefault("orientation", "horizontal").equals("vertical")
                  ? "VERTICAL"
                  : "HORIZONTAL")
              + ");");
      if (attrs.containsKey("weightSum")) {
        out.line(variable + ".setWeightSum(" + floatExpression(attrs.get("weightSum")) + ");");
      }
      emitBooleanSetter(attrs, variable, "baselineAligned", "setBaselineAligned");
      if (attrs.containsKey("baselineAlignedChildIndex")) {
        out.line(
            variable
                + ".setBaselineAlignedChildIndex("
                + integerExpression(attrs.get("baselineAlignedChildIndex"))
                + ");");
      }
      emitBooleanSetter(
          attrs, variable, "measureWithLargestChild", "setMeasureWithLargestChildEnabled");
    } else if (node.tag.equals("TableLayout")) {
      if (attrs.containsKey("weightSum")) {
        out.line(variable + ".setWeightSum(" + floatExpression(attrs.get("weightSum")) + ");");
      }
      emitTableColumns(
          attrs, variable, "stretchColumns", "setColumnStretchable", "setStretchAllColumns");
      emitTableColumns(
          attrs, variable, "shrinkColumns", "setColumnShrinkable", "setShrinkAllColumns");
      emitTableColumns(attrs, variable, "collapseColumns", "setColumnCollapsed", null);
    } else if (setOf("FrameLayout", "ViewAnimator", "ViewFlipper").contains(node.tag)) {
      if (attrs.containsKey("foregroundGravity")) {
        out.line(
            variable
                + ".setForegroundGravity("
                + gravityExpression(attrs.get("foregroundGravity"))
                + ");");
      }
      emitBooleanSetter(attrs, variable, "measureAllChildren", "setMeasureAllChildren");
    } else if (node.tag.equals("RelativeLayout") && attrs.containsKey("ignoreGravity")) {
      out.line(variable + ".setIgnoreGravity(" + idExpression(attrs.get("ignoreGravity")) + ");");
    } else if (node.tag.equals("GridLayout")) {
      out.line(
          variable
              + ".setOrientation(GridLayout."
              + (attrs.getOrDefault("orientation", "horizontal").equals("vertical")
                  ? "VERTICAL"
                  : "HORIZONTAL")
              + ");");
      if (attrs.containsKey("rowCount")) {
        out.line(variable + ".setRowCount(" + integerExpression(attrs.get("rowCount")) + ");");
      }
      if (attrs.containsKey("columnCount")) {
        out.line(
            variable + ".setColumnCount(" + integerExpression(attrs.get("columnCount")) + ");");
      }
      emitBooleanSetter(attrs, variable, "useDefaultMargins", "setUseDefaultMargins");
      emitBooleanSetter(attrs, variable, "rowOrderPreserved", "setRowOrderPreserved");
      emitBooleanSetter(attrs, variable, "columnOrderPreserved", "setColumnOrderPreserved");
      if (attrs.containsKey("alignmentMode")) {
        out.line(
            variable
                + ".setAlignmentMode(GridLayout."
                + (attrs.get("alignmentMode").equals("alignBounds")
                    ? "ALIGN_BOUNDS"
                    : "ALIGN_MARGINS")
                + ");");
      }
    } else if (node.tag.equals("ScrollView") || node.tag.equals("HorizontalScrollView")) {
      emitBooleanSetter(attrs, variable, "fillViewport", "setFillViewport");
      emitBooleanSetter(attrs, variable, "smoothScrollingEnabled", "setSmoothScrollingEnabled");
    }
    if (FRAMEWORK_CONTAINERS.contains(node.tag)) {
      emitBooleanSetter(attrs, variable, "clipChildren", "setClipChildren");
      emitBooleanSetter(attrs, variable, "clipToPadding", "setClipToPadding");
      emitBooleanSetter(
          attrs, variable, "motionEventSplittingEnabled", "setMotionEventSplittingEnabled");
    }
  }

  private void emitTableColumns(
      Map<String, String> attrs,
      String variable,
      String attribute,
      String setter,
      String allSetter) {
    if (!attrs.containsKey(attribute)) {
      return;
    }
    String value = attrs.get(attribute);
    if (value.equals("*")) {
      out.line(variable + "." + allSetter + "(true);");
      return;
    }
    for (String column : value.split(",")) {
      out.line(variable + "." + setter + "(" + column.trim() + ", true);");
    }
  }

  private void emitTextProperties(LayoutNode node, String variable) {
    Map<String, String> attrs = node.attributes;
    if (attrs.containsKey("textStyle")) {
      out.line(
          variable
              + ".setTypeface("
              + variable
              + ".getTypeface(), "
              + textStyleExpression(attrs.get("textStyle"))
              + ");");
    }
    if (attrs.containsKey("hint")) {
      out.line(variable + ".setHint(" + stringExpression(attrs.get("hint")) + ");");
    }
    if (attrs.containsKey("textColorHint")) {
      out.line(
          variable
              + ".setHintTextColor("
              + colorStateListExpression(attrs.get("textColorHint"))
              + ");");
    }
    emitBooleanSetter(attrs, variable, "singleLine", "setSingleLine");
    emitBooleanSetter(attrs, variable, "includeFontPadding", "setIncludeFontPadding");
    emitBooleanSetter(attrs, variable, "textAllCaps", "setAllCaps");
    emitBooleanSetter(attrs, variable, "textIsSelectable", "setTextIsSelectable");
    emitBooleanSetter(attrs, variable, "selectAllOnFocus", "setSelectAllOnFocus");
    if (attrs.containsKey("ellipsize")) {
      String value = attrs.get("ellipsize");
      out.line(
          variable
              + ".setEllipsize("
              + (value.equals("none")
                  ? "null"
                  : "TextUtils.TruncateAt." + value.toUpperCase(Locale.ROOT))
              + ");");
    }
    emitFloatSetter(attrs, variable, "letterSpacing", "setLetterSpacing");
    if (attrs.containsKey("lineSpacingExtra") || attrs.containsKey("lineSpacingMultiplier")) {
      out.line(
          variable
              + ".setLineSpacing("
              + dimensionValueExpression(attrs.getOrDefault("lineSpacingExtra", "0px"))
              + ", "
              + floatExpression(attrs.getOrDefault("lineSpacingMultiplier", "1"))
              + ");");
    }
    if (attrs.containsKey("inputType")) {
      out.line(
          variable
              + ".setInputType("
              + integerFlagsExpression(attrs.get("inputType"), true)
              + ");");
    }
    if (attrs.containsKey("imeOptions")) {
      out.line(
          variable
              + ".setImeOptions("
              + integerFlagsExpression(attrs.get("imeOptions"), false)
              + ");");
    }
    if ((COMPOUND_BUTTON_TAGS.contains(node.tag) || node.tag.equals("CheckedTextView"))
        && attrs.containsKey("checked")) {
      out.line(variable + ".setChecked(" + booleanExpression(attrs.get("checked")) + ");");
    }
    if (COMPOUND_BUTTON_TAGS.contains(node.tag) && attrs.containsKey("buttonTint")) {
      out.line(
          variable
              + ".setButtonTintList("
              + colorStateListExpression(attrs.get("buttonTint"))
              + ");");
    }
  }

  private void emitProgressProperties(LayoutNode node, String variable) {
    Map<String, String> attrs = node.attributes;
    if (attrs.containsKey("max"))
      out.line(variable + ".setMax(" + integerExpression(attrs.get("max")) + ");");
    if (attrs.containsKey("progress"))
      out.line(variable + ".setProgress(" + integerExpression(attrs.get("progress")) + ");");
    if (attrs.containsKey("secondaryProgress"))
      out.line(
          variable
              + ".setSecondaryProgress("
              + integerExpression(attrs.get("secondaryProgress"))
              + ");");
    emitBooleanSetter(attrs, variable, "indeterminate", "setIndeterminate");
    if (attrs.containsKey("progressTint")) {
      out.line(
          variable
              + ".setProgressTintList("
              + colorStateListExpression(attrs.get("progressTint"))
              + ");");
    }
    if (node.tag.equals("SeekBar") && attrs.containsKey("thumb")) {
      out.line(
          variable
              + ".setThumb("
              + drawableExpression(referenceName(attrs.get("thumb"), "drawable"))
              + ");");
    }
    if (node.tag.equals("RatingBar")) {
      if (attrs.containsKey("rating"))
        out.line(variable + ".setRating(" + floatExpression(attrs.get("rating")) + ");");
      if (attrs.containsKey("numStars"))
        out.line(variable + ".setNumStars(" + integerExpression(attrs.get("numStars")) + ");");
      if (attrs.containsKey("stepSize"))
        out.line(variable + ".setStepSize(" + floatExpression(attrs.get("stepSize")) + ");");
      emitBooleanSetter(attrs, variable, "isIndicator", "setIsIndicator");
    }
  }

  private void emitCustomAssignment(String target, CustomAttributeValue attribute) {
    String expression = customAttributeExpression(attribute);
    if (attribute.spec.setter() != null) {
      out.line(target + "." + attribute.spec.setter() + "(" + expression + ");");
    } else {
      out.line(target + "." + attribute.spec.field() + " = " + expression + ";");
    }
  }

  private void emitCommon(Map<String, String> attrs, String variable) {
    if (attrs.containsKey("background")) {
      String value = attrs.get("background");
      if (value.startsWith("@color/") || value.startsWith("#")) {
        out.line(variable + ".setBackgroundColor(" + colorValueExpression(value) + ");");
      } else {
        out.line(
            variable
                + ".setBackground("
                + drawableExpression(referenceName(value, "drawable"))
                + ");");
      }
    }
    if (attrs.containsKey("backgroundTint")) {
      out.line(
          variable
              + ".setBackgroundTintList("
              + colorStateListExpression(attrs.get("backgroundTint"))
              + ");");
    }
    if (attrs.containsKey("foreground")) {
      out.line(
          "if (Build.VERSION.SDK_INT >= 23) "
              + variable
              + ".setForeground("
              + foregroundExpression(attrs.get("foreground"))
              + ");");
    }
    if (attrs.containsKey("visibility")) {
      out.line(
          variable
              + ".setVisibility(View."
              + attrs.get("visibility").toUpperCase(Locale.ROOT)
              + ");");
    }
    emitBooleanSetter(attrs, variable, "enabled", "setEnabled");
    emitBooleanSetter(attrs, variable, "clickable", "setClickable");
    emitBooleanSetter(attrs, variable, "longClickable", "setLongClickable");
    emitBooleanSetter(attrs, variable, "focusable", "setFocusable");
    emitBooleanSetter(attrs, variable, "focusableInTouchMode", "setFocusableInTouchMode");
    emitBooleanSetter(attrs, variable, "selected", "setSelected");
    emitBooleanSetter(attrs, variable, "activated", "setActivated");
    emitBooleanSetter(attrs, variable, "saveEnabled", "setSaveEnabled");
    emitBooleanSetter(attrs, variable, "keepScreenOn", "setKeepScreenOn");
    emitBooleanSetter(attrs, variable, "fitsSystemWindows", "setFitsSystemWindows");
    emitBooleanSetter(attrs, variable, "soundEffectsEnabled", "setSoundEffectsEnabled");
    emitBooleanSetter(attrs, variable, "hapticFeedbackEnabled", "setHapticFeedbackEnabled");
    emitBooleanSetter(attrs, variable, "duplicateParentState", "setDuplicateParentStateEnabled");
    emitBooleanSetter(attrs, variable, "filterTouchesWhenObscured", "setFilterTouchesWhenObscured");
    emitBooleanSetter(attrs, variable, "isScrollContainer", "setScrollContainer");
    emitBooleanSetter(attrs, variable, "clipToOutline", "setClipToOutline");
    if (attrs.containsKey("contentDescription")) {
      out.line(
          variable
              + ".setContentDescription("
              + stringExpression(attrs.get("contentDescription"))
              + ");");
    }
    if (attrs.containsKey("tag")) {
      out.line(variable + ".setTag(" + stringExpression(attrs.get("tag")) + ");");
    }
    if (attrs.containsKey("tooltipText")) {
      out.line(
          "if (Build.VERSION.SDK_INT >= 26) "
              + variable
              + ".setTooltipText("
              + stringExpression(attrs.get("tooltipText"))
              + ");");
    }
    if (attrs.containsKey("transitionName")) {
      out.line(
          variable + ".setTransitionName(" + stringExpression(attrs.get("transitionName")) + ");");
    }
    emitFloatSetter(attrs, variable, "alpha", "setAlpha");
    emitFloatSetter(attrs, variable, "rotation", "setRotation");
    emitFloatSetter(attrs, variable, "rotationX", "setRotationX");
    emitFloatSetter(attrs, variable, "rotationY", "setRotationY");
    emitFloatSetter(attrs, variable, "scaleX", "setScaleX");
    emitFloatSetter(attrs, variable, "scaleY", "setScaleY");
    emitDimensionSetter(attrs, variable, "elevation", "setElevation");
    emitDimensionSetter(attrs, variable, "translationX", "setTranslationX");
    emitDimensionSetter(attrs, variable, "translationY", "setTranslationY");
    emitDimensionSetter(attrs, variable, "translationZ", "setTranslationZ");
    if (attrs.containsKey("minWidth")) {
      out.line(variable + ".setMinimumWidth(" + roundedDimension(attrs.get("minWidth")) + ");");
    }
    if (attrs.containsKey("minHeight")) {
      out.line(variable + ".setMinimumHeight(" + roundedDimension(attrs.get("minHeight")) + ");");
    }
    if (attrs.containsKey("layoutDirection")) {
      out.line(
          variable
              + ".setLayoutDirection(View.LAYOUT_DIRECTION_"
              + constantName(attrs.get("layoutDirection"))
              + ");");
    }
    if (attrs.containsKey("textAlignment")) {
      out.line(
          variable
              + ".setTextAlignment(View.TEXT_ALIGNMENT_"
              + constantName(attrs.get("textAlignment"))
              + ");");
    }
    if (attrs.containsKey("overScrollMode")) {
      out.line(
          variable
              + ".setOverScrollMode(View.OVER_SCROLL_"
              + constantName(attrs.get("overScrollMode"))
              + ");");
    }
    if (attrs.containsKey("scrollbars")) {
      String scrollbars = attrs.get("scrollbars");
      out.line(
          variable + ".setHorizontalScrollBarEnabled(" + scrollbars.contains("horizontal") + ");");
      out.line(variable + ".setVerticalScrollBarEnabled(" + scrollbars.contains("vertical") + ");");
    }
    if (attrs.containsKey("importantForAccessibility")) {
      out.line(
          variable
              + ".setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_"
              + constantName(attrs.get("importantForAccessibility"))
              + ");");
    }
    if (attrs.containsKey("accessibilityLiveRegion")) {
      out.line(
          variable
              + ".setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_"
              + constantName(attrs.get("accessibilityLiveRegion"))
              + ");");
    }
    if (attrs.containsKey("accessibilityHeading")) {
      out.line(
          "if (Build.VERSION.SDK_INT >= 28) "
              + variable
              + ".setAccessibilityHeading("
              + booleanExpression(attrs.get("accessibilityHeading"))
              + ");");
    }
    if (attrs.containsKey("screenReaderFocusable")) {
      out.line(
          "if (Build.VERSION.SDK_INT >= 28) "
              + variable
              + ".setScreenReaderFocusable("
              + booleanExpression(attrs.get("screenReaderFocusable"))
              + ");");
    }
  }

  private void emitPadding(Map<String, String> attrs, String variable) {
    String all = attrs.get("padding");
    String top = attrs.getOrDefault("paddingTop", all == null ? "0px" : all);
    String bottom = attrs.getOrDefault("paddingBottom", all == null ? "0px" : all);
    if (attrs.containsKey("paddingStart") || attrs.containsKey("paddingEnd")) {
      String start = attrs.getOrDefault("paddingStart", all == null ? "0px" : all);
      String end = attrs.getOrDefault("paddingEnd", all == null ? "0px" : all);
      out.line(
          variable
              + ".setPaddingRelative("
              + roundedDimension(start)
              + ", "
              + roundedDimension(top)
              + ", "
              + roundedDimension(end)
              + ", "
              + roundedDimension(bottom)
              + ");");
      return;
    }
    String left = attrs.getOrDefault("paddingLeft", all == null ? "0px" : all);
    String right = attrs.getOrDefault("paddingRight", all == null ? "0px" : all);
    if (all != null || attrs.keySet().stream().anyMatch(key -> key.startsWith("padding"))) {
      out.line(
          variable
              + ".setPadding("
              + roundedDimension(left)
              + ", "
              + roundedDimension(top)
              + ", "
              + roundedDimension(right)
              + ", "
              + roundedDimension(bottom)
              + ");");
    }
  }

  private void emitMargins(LayoutNode node, String params) {
    Map<String, String> attrs = node.attributes;
    String all = attrs.get("layout_margin");
    String left = attrs.getOrDefault("layout_marginLeft", all == null ? "0px" : all);
    String top = attrs.getOrDefault("layout_marginTop", all == null ? "0px" : all);
    String right = attrs.getOrDefault("layout_marginRight", all == null ? "0px" : all);
    String bottom = attrs.getOrDefault("layout_marginBottom", all == null ? "0px" : all);
    if (all != null || attrs.keySet().stream().anyMatch(key -> key.startsWith("layout_margin"))) {
      out.line(
          params
              + ".setMargins("
              + roundedDimension(left)
              + ", "
              + roundedDimension(top)
              + ", "
              + roundedDimension(right)
              + ", "
              + roundedDimension(bottom)
              + ");");
    }
    if (attrs.containsKey("layout_marginStart")) {
      out.line(
          params + ".setMarginStart(" + roundedDimension(attrs.get("layout_marginStart")) + ");");
    }
    if (attrs.containsKey("layout_marginEnd")) {
      out.line(params + ".setMarginEnd(" + roundedDimension(attrs.get("layout_marginEnd")) + ");");
    }
  }

  private String constructorExpression(LayoutNode node) {
    if (node.customView == null) {
      return "new " + node.javaType + "(context)";
    }
    String constructor = node.customView.constructor();
    if ("CONTEXT".equals(constructor)) return "new " + node.javaType + "(context)";
    if ("CONTEXT_ATTRS".equals(constructor)) return "new " + node.javaType + "(context, null)";
    if ("CONTEXT_ATTRS_DEF_STYLE".equals(constructor))
      return "new " + node.javaType + "(context, null, 0)";
    throw new AssertionError(constructor);
  }

  private String customAttributeExpression(CustomAttributeValue attribute) {
    String type = attribute.spec.type();
    if ("STRING".equals(type)) return stringExpression(attribute.value);
    if ("COLOR".equals(type)) return colorValueExpression(attribute.value);
    if ("DIMENSION".equals(type)) return dimensionValueExpression(attribute.value);
    if ("DIMENSION_INT".equals(type)) return roundedDimension(attribute.value);
    if ("BOOLEAN".equals(type)) return booleanExpression(attribute.value);
    if ("INTEGER".equals(type)) return integerExpression(attribute.value);
    if ("FLOAT".equals(type)) return floatLiteral(floatValue(attribute.value));
    if ("GRAVITY".equals(type)) return gravityExpression(attribute.value);
    if ("DRAWABLE".equals(type)) {
      return drawableExpression(referenceName(attribute.value, "drawable"));
    }
    if ("IMAGE_ASSET".equals(type)) {
      String name = referenceName(attribute.value, "drawable");
      if (pluginMode) {
        return "X2cImages.get(" + javaString(name) + ")";
      }
      // A dual-mode custom View may overload the same setter with ImageAsset for plugins
      // and Drawable for a normal AAR. Normal mode must never synthesize CDN metadata for a
      // bitmap that remains owned by the host resource table.
      return "context.getResources().getDrawable(X2cModule.identifier(\"drawable\", "
          + javaString(name)
          + "), context.getTheme())";
    }
    throw new AssertionError(type);
  }

  private void emitBooleanSetter(
      Map<String, String> attrs, String variable, String attribute, String setter) {
    if (attrs.containsKey(attribute)) {
      out.line(variable + "." + setter + "(" + booleanExpression(attrs.get(attribute)) + ");");
    }
  }

  private void emitFloatSetter(
      Map<String, String> attrs, String variable, String attribute, String setter) {
    if (attrs.containsKey(attribute)) {
      out.line(
          variable + "." + setter + "(" + floatLiteral(floatValue(attrs.get(attribute))) + ");");
    }
  }

  private void emitDimensionSetter(
      Map<String, String> attrs, String variable, String attribute, String setter) {
    if (attrs.containsKey(attribute)) {
      out.line(
          variable + "." + setter + "(" + dimensionValueExpression(attrs.get(attribute)) + ");");
    }
  }

  private String roundedDimension(String value) {
    return "Math.round(" + dimensionValueExpression(value) + ")";
  }

  private String dimensionValueExpression(String value) {
    if (value.startsWith("@dimen/")) {
      return dimenReference(value);
    }
    Dimension dimen = parseDimension(null, value);
    if (dimen.unit.equals("px")) {
      return floatLiteral(dimen.value);
    }
    return "TypedValue.applyDimension("
        + typedValueUnit(dimen.unit)
        + ", "
        + floatLiteral(dimen.value)
        + ", context.getResources().getDisplayMetrics())";
  }

  private String sizeExpression(String value) {
    if (value.equals("match_parent") || value.equals("fill_parent")) {
      return "ViewGroup.LayoutParams.MATCH_PARENT";
    }
    if (value.equals("wrap_content")) {
      return "ViewGroup.LayoutParams.WRAP_CONTENT";
    }
    return roundedDimension(value);
  }

  private String stringExpression(String value) {
    if (value.startsWith("@string/")) {
      String name = referenceName(value, "string");
      return pluginMode
          ? "X2cModule.provider().getString(" + javaString(name) + ")"
          : "X2cValues.Strings." + javaName(name);
    }
    return javaString(value);
  }

  private String colorValueExpression(String value) {
    if (value.startsWith("@color/")) {
      String name = referenceName(value, "color");
      return pluginMode
          ? "X2cModule.provider().getColor(" + javaString(name) + ")"
          : "X2cValues.Colors." + javaName(name);
    }
    return String.format(Locale.ROOT, "0x%08X", parseColor(null, value));
  }

  private String colorStateListExpression(String value) {
    if (value.startsWith("@color/")) {
      String name = referenceName(value, "color");
      if (pluginMode) {
        return "X2cModule.provider().getColorStateList(" + javaString(name) + ")";
      }
      if (model.colorSelectors.containsKey(name)) {
        return "X2cColorStateLists." + javaName(name) + "()";
      }
    }
    return "ColorStateList.valueOf(" + colorValueExpression(value) + ")";
  }

  private String foregroundExpression(String value) {
    if (value.startsWith("@drawable/")) {
      return drawableExpression(referenceName(value, "drawable"));
    }
    return "new ColorDrawable(" + colorValueExpression(value) + ")";
  }

  private String booleanExpression(String value) {
    if (value.startsWith("@bool/")) {
      String name = referenceName(value, "bool");
      return pluginMode
          ? "X2cModule.provider().getBoolean(" + javaString(name) + ")"
          : "X2cValues.Bools." + javaName(name);
    }
    return value;
  }

  private String integerExpression(String value) {
    if (value.startsWith("@integer/")) {
      String name = referenceName(value, "integer");
      return pluginMode
          ? "X2cModule.provider().getInteger(" + javaString(name) + ")"
          : "X2cValues.Integers." + javaName(name);
    }
    return value;
  }

  private String floatExpression(String value) {
    return floatLiteral(floatValue(value));
  }

  private boolean literalBoolean(String value) {
    if (value.startsWith("@bool/")) {
      return model.bools.get(referenceName(value, "bool"));
    }
    return Boolean.parseBoolean(value);
  }

  private String textStyleExpression(String value) {
    if (value.equals("normal")) {
      return "Typeface.NORMAL";
    }
    return Arrays.stream(value.split("\\|"))
        .map(part -> "Typeface." + part.toUpperCase(Locale.ROOT))
        .reduce((left, right) -> left + " | " + right)
        .orElse("Typeface.NORMAL");
  }

  private String integerFlagsExpression(String value, boolean inputType) {
    if (value.startsWith("@integer/")) {
      return integerExpression(value);
    }
    try {
      Integer.decode(value);
      return value;
    } catch (NumberFormatException ignored) {
      // Named flags are converted below.
    }
    return Arrays.stream(value.split("\\|"))
        .map(part -> inputType ? inputTypeConstant(part) : imeOptionConstant(part))
        .reduce((left, right) -> left + " | " + right)
        .orElse("0");
  }

  private String inputTypeConstant(String value) {
    if ("none".equals(value)) return "InputType.TYPE_NULL";
    if ("text".equals(value)) return "InputType.TYPE_CLASS_TEXT";
    if ("textCapCharacters".equals(value)) return "InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS";
    if ("textCapWords".equals(value)) return "InputType.TYPE_TEXT_FLAG_CAP_WORDS";
    if ("textCapSentences".equals(value)) return "InputType.TYPE_TEXT_FLAG_CAP_SENTENCES";
    if ("textAutoCorrect".equals(value)) return "InputType.TYPE_TEXT_FLAG_AUTO_CORRECT";
    if ("textAutoComplete".equals(value)) return "InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE";
    if ("textMultiLine".equals(value)) return "InputType.TYPE_TEXT_FLAG_MULTI_LINE";
    if ("textImeMultiLine".equals(value)) return "InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE";
    if ("textNoSuggestions".equals(value)) return "InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS";
    if ("textUri".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI";
    if ("textEmailAddress".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS";
    if ("textEmailSubject".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT";
    if ("textShortMessage".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE";
    if ("textLongMessage".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE";
    if ("textPersonName".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME";
    if ("textPostalAddress".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS";
    if ("textPassword".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD";
    if ("textVisiblePassword".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD";
    if ("textWebEditText".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT";
    if ("textFilter".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER";
    if ("textPhonetic".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PHONETIC";
    if ("textWebEmailAddress".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS";
    if ("textWebPassword".equals(value))
      return "InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD";
    if ("number".equals(value)) return "InputType.TYPE_CLASS_NUMBER";
    if ("numberSigned".equals(value)) return "InputType.TYPE_NUMBER_FLAG_SIGNED";
    if ("numberDecimal".equals(value)) return "InputType.TYPE_NUMBER_FLAG_DECIMAL";
    if ("numberPassword".equals(value))
      return "InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD";
    if ("phone".equals(value)) return "InputType.TYPE_CLASS_PHONE";
    if ("datetime".equals(value)) return "InputType.TYPE_CLASS_DATETIME";
    if ("date".equals(value))
      return "InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE";
    if ("time".equals(value))
      return "InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME";
    throw new AssertionError(value);
  }

  private String imeOptionConstant(String value) {
    if ("normal".equals(value)) return "EditorInfo.IME_NULL";
    if ("actionUnspecified".equals(value)) return "EditorInfo.IME_ACTION_UNSPECIFIED";
    if ("actionNone".equals(value)) return "EditorInfo.IME_ACTION_NONE";
    if ("actionGo".equals(value)) return "EditorInfo.IME_ACTION_GO";
    if ("actionSearch".equals(value)) return "EditorInfo.IME_ACTION_SEARCH";
    if ("actionSend".equals(value)) return "EditorInfo.IME_ACTION_SEND";
    if ("actionNext".equals(value)) return "EditorInfo.IME_ACTION_NEXT";
    if ("actionDone".equals(value)) return "EditorInfo.IME_ACTION_DONE";
    if ("actionPrevious".equals(value)) return "EditorInfo.IME_ACTION_PREVIOUS";
    if ("flagNoFullscreen".equals(value)) return "EditorInfo.IME_FLAG_NO_FULLSCREEN";
    if ("flagNavigatePrevious".equals(value)) return "EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS";
    if ("flagNavigateNext".equals(value)) return "EditorInfo.IME_FLAG_NAVIGATE_NEXT";
    if ("flagNoExtractUi".equals(value)) return "EditorInfo.IME_FLAG_NO_EXTRACT_UI";
    if ("flagNoAccessoryAction".equals(value)) return "EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION";
    if ("flagNoEnterAction".equals(value)) return "EditorInfo.IME_FLAG_NO_ENTER_ACTION";
    if ("flagForceAscii".equals(value)) return "EditorInfo.IME_FLAG_FORCE_ASCII";
    throw new AssertionError(value);
  }

  private String dimenReference(String value) {
    String name = referenceName(value, "dimen");
    return pluginMode
        ? "X2cModule.provider().getDimension(context, " + javaString(name) + ")"
        : "X2cValues.Dimens." + javaName(name) + "(context)";
  }

  private String drawableExpression(String name) {
    return pluginMode
        ? "X2cModule.provider().getDrawable(context, " + javaString(name) + ")"
        : "X2cDrawables." + javaName(name) + "(context)";
  }

  private String idExpression(String value) {
    IdReference id = parseIdReference(null, value);
    return id.kind == IdKind.ANDROID
        ? "android.R.id." + javaName(id.name)
        : pluginMode
            ? "R2.id." + javaName(id.name)
            : "X2cModule.identifier(\"id\", " + javaString(id.name) + ")";
  }

  private static String constantName(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }
}
