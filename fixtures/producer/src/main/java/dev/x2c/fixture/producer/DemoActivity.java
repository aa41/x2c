package dev.x2c.fixture.producer;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.plugin.api.X2cPluginActivity;
import dev.x2c.plugin.runtime.PluginActivityManager;
import dev.x2c.runtime.*;
import java.util.ArrayList;
import java.util.List;

/** Capability lab: individual XML examples, executable assertions and component navigation. */
@X2cPluginActivity
public final class DemoActivity extends BusinessBaseActivity {
  private X2cResources res;
  private LinearLayout body;
  private final List<ImageView> images = new ArrayList<>();
  private int generation;
  private boolean detail;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    res = X2C.resources(this, DemoActivity.class);
    X2C.setContentView(this, "content");
    body = res.requireView(getWindow().getDecorView(), "lab_body", LinearLayout.class);
    res.requireView(getWindow().getDecorView(), "lab_back", TextView.class)
        .setOnClickListener(
            v -> {
              if (detail) home();
              else finish();
            });
    home();
  }

  private void reset(String title) {
    generation++;
    for (ImageView image : images) X2cImages.cancel(image);
    images.clear();
    destroyWebViews(body);
    body.removeAllViews();
    label(title, 24);
  }

  private void home() {
    detail = false;
    reset("从 XML 到运行结果");
    label("每项包含真实 XML 示例、交互及断言；失败会显示原因，不计为通过。", 14);
    button("01 · 53 种 View / ViewGroup", this::framework);
    button("02 · 组合布局与自定义 View", this::layouts);
    button("03 · Values / Drawable / 宿主资源", this::resources);
    button("04 · 本地图片 → CDN / 宿主图片", this::pictures);
    button(
        "05 · Activity / Service / Receiver / Provider",
        () ->
            PluginActivityManager.startActivity(
                this,
                "dev.x2c.fixture.component-showcase",
                "dev.x2c.fixture.secondary.ComponentShowcaseActivity"));
    button(
        "06 · Application / ClassLoader / ID 隔离",
        () -> {
          detail = true;
          reset("运行环境与身份");
          check(
              "真实宿主 Application",
              () ->
                  require(
                      (android.app.Application) getApplicationContext() == X2C.hostApplication()
                          && getApplication() == X2C.hostApplication(),
                      "Application identity"));
          check("插件代码归属", () -> require(X2C.isPlugin(DemoActivity.class), "plugin anchor"));
          check("宿主类型不能误判插件", () -> require(!X2C.isPlugin(X2C.class), "runtime identity"));
          check(
              "宿主 id/layout 不越界回退",
              () ->
                  require(
                      res.findIdentifier("x2c_host_only_id", "id") == 0
                          && res.findIdentifier("x2c_host_only_layout", "layout") == 0,
                      "synthetic isolation"));
          label(businessBaseSummary(), 13);
        });
  }

  private void framework() {
    detail = true;
    reset("53 项 framework 矩阵");
    label("每个条目独立加载；20 个可组合容器含真实 XML 子树和 LayoutParams，系统内部容器按公开交互配置。", 14);
    button(
        "通用属性 / 状态 / 可访问性",
        () -> {
          detail = true;
          reset("通用属性矩阵");
          View root = res.getView(this, "common_properties");
          body.addView(root);
          View v = root.findViewById(res.id("common_specimen"));
          check(
              "XML 通用属性",
              () ->
                  require(
                      v.isClickable()
                          && v.isLongClickable()
                          && v.isSelected()
                          && v.isActivated()
                          && Math.abs(v.getAlpha() - .8f) < .01f
                          && Math.abs(v.getScaleX() - .95f) < .01f
                          && v.getPaddingLeft() == dp(12)
                          && "common-properties".equals(v.getTag()),
                      "flags / transform / padding"));
          label("XML 同时包含背景/前景、无障碍、过渡名、缩放/平移、最小尺寸等属性。新 API 属性由生成代码按系统版本保护。", 14);
        });
    button("运行 53 项类型 / 属性 / 子树断言", () -> runCase(0, new StringBuilder()));
    for (String tag : FrameworkCases.TAGS) button(tag, () -> preview(tag));
  }

  private void preview(String tag) {
    detail = true;
    reset(tag + " · XML 示例");
    label("验证类型、synthetic ID、通用属性和父 LayoutParams；容器还验证 XML 子树及专属布局规则。", 14);
    try {
      View root = res.getView(this, FrameworkCases.layout(tag));
      body.addView(root);
      check(tag, () -> verify(root, tag));
      View specimen = root.findViewById(res.id("probe_specimen"));
      TextView events = label("交互记录：等待操作", 13);
      if (!(specimen instanceof AdapterView))
        specimen.setOnClickListener(v -> events.setText("click · " + tag));
      if (specimen instanceof CompoundButton)
        ((CompoundButton) specimen)
            .setOnCheckedChangeListener((v, on) -> events.setText("checked=" + on));
      if (specimen instanceof SeekBar)
        ((SeekBar) specimen)
            .setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                  public void onProgressChanged(SeekBar b, int n, boolean u) {
                    events.setText("progress=" + n);
                  }

                  public void onStartTrackingTouch(SeekBar b) {}

                  public void onStopTrackingTouch(SeekBar b) {}
                });
      if (specimen instanceof NumberPicker) {
        NumberPicker picker = (NumberPicker) specimen;
        picker.setMinValue(0);
        picker.setMaxValue(20);
        picker.setValue(10);
      }
      if (specimen instanceof NumberPicker)
        ((NumberPicker) specimen)
            .setOnValueChangedListener((v, a, b) -> events.setText("value=" + b));
      if (specimen instanceof SearchView) {
        SearchView search = (SearchView) specimen;
        search.setIconifiedByDefault(false);
        search.setQueryHint("输入内容验证 query 回调");
        search.setOnQueryTextListener(
            new SearchView.OnQueryTextListener() {
              public boolean onQueryTextSubmit(String q) {
                events.setText("submit=" + q);
                return true;
              }

              public boolean onQueryTextChange(String q) {
                events.setText("query=" + q);
                return true;
              }
            });
      }
      if (specimen instanceof ZoomControls) {
        ZoomControls zoom = (ZoomControls) specimen;
        zoom.setOnZoomInClickListener(v -> events.setText("zoom=in"));
        zoom.setOnZoomOutClickListener(v -> events.setText("zoom=out"));
      }
      if (specimen instanceof DatePicker) {
        DatePicker date = (DatePicker) specimen;
        date.init(
            2026,
            8,
            10,
            (v, year, month, day) ->
                events.setText("date=" + year + "-" + (month + 1) + "-" + day));
      }
      if (specimen instanceof TimePicker)
        ((TimePicker) specimen)
            .setOnTimeChangedListener(
                (v, hour, minute) -> events.setText("time=" + hour + ":" + minute));
      if (specimen instanceof CalendarView)
        ((CalendarView) specimen)
            .setOnDateChangeListener(
                (v, year, month, day) ->
                    events.setText("calendar=" + year + "-" + (month + 1) + "-" + day));
      if (specimen instanceof Chronometer) {
        Chronometer timer = (Chronometer) specimen;
        button(
            "启动 / 停止计时",
            () -> {
              if (Boolean.TRUE.equals(timer.getTag(res.id("probe_runtime_state")))) {
                timer.stop();
                timer.setTag(res.id("probe_runtime_state"), false);
                events.setText("chronometer=stopped");
              } else {
                timer.setBase(android.os.SystemClock.elapsedRealtime());
                timer.start();
                timer.setTag(res.id("probe_runtime_state"), true);
                events.setText("chronometer=running");
              }
            });
      }
      if (specimen instanceof TextSwitcher) {
        TextSwitcher s = (TextSwitcher) specimen;
        if (s.getChildCount() == 0) s.setFactory(() -> new TextView(this));
        s.setCurrentText("TextSwitcher 第一页");
        button("切换文本", s::showNext);
      } else if (specimen instanceof ImageSwitcher) {
        ImageSwitcher s = (ImageSwitcher) specimen;
        if (s.getChildCount() == 0) s.setFactory(() -> new ImageView(this));
        button("切换图片", s::showNext);
      } else if (specimen instanceof ViewAnimator) {
        ViewAnimator a = (ViewAnimator) specimen;
        if (a.getChildCount() == 0) {
          for (String s : new String[] {"第一页", "第二页"}) {
            TextView t = new TextView(this);
            t.setText(s);
            a.addView(t);
          }
        }
        button("下一子页", a::showNext);
      }
      if (specimen instanceof RadioGroup) {
        RadioGroup group = (RadioGroup) specimen;
        if (group.getChildCount() == 0) {
          for (int i = 0; i < 3; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText("选项 " + i);
            group.addView(b);
          }
        }
        group.setOnCheckedChangeListener((g, id) -> events.setText("checkedId=" + id));
      }
      if (specimen instanceof Toolbar) {
        Toolbar bar = (Toolbar) specimen;
        bar.setTitle("Toolbar 标题");
        bar.setSubtitle("菜单与点击事件");
        bar.getMenu()
            .add("操作")
            .setOnMenuItemClickListener(
                item -> {
                  events.setText("menu click");
                  return true;
                });
      }
      if (specimen instanceof ActionMenuView)
        ((ActionMenuView) specimen)
            .getMenu()
            .add("菜单项")
            .setOnMenuItemClickListener(
                item -> {
                  events.setText("action menu click");
                  return true;
                });
      if (specimen instanceof TabHost) {
        TabHost tabs = (TabHost) specimen;
        if (tabs.findViewById(android.R.id.tabs) == null) {
          LinearLayout content = new LinearLayout(this);
          content.setOrientation(1);
          TabWidget strip = new TabWidget(this);
          strip.setId(android.R.id.tabs);
          content.addView(strip);
          FrameLayout area = new FrameLayout(this);
          area.setId(android.R.id.tabcontent);
          content.addView(area);
          tabs.addView(content);
        }
        tabs.setup();
        for (String title : new String[] {"XML", "Java"})
          tabs.addTab(
              tabs.newTabSpec(title)
                  .setIndicator(title)
                  .setContent(
                      key -> {
                        TextView t = new TextView(this);
                        t.setText(key + " 内容");
                        return t;
                      }));
        tabs.setOnTabChangedListener(key -> events.setText("tab=" + key));
      }
      if (specimen instanceof android.webkit.WebView)
        ((android.webkit.WebView) specimen)
            .loadData("<h3>X2C WebView</h3><p>本地 HTML，无网络依赖</p>", "text/html", "UTF-8");
      if (specimen instanceof AdapterView && !(specimen instanceof ExpandableListView))
        ((AdapterView) specimen)
            .setAdapter(
                new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    new String[] {"XML", "Java", "DEX"}));
      if (specimen instanceof AdapterView && !(specimen instanceof Spinner))
        ((AdapterView) specimen)
            .setOnItemClickListener((p, v, n, id) -> events.setText("item=" + n));
      if (specimen instanceof Spinner)
        ((Spinner) specimen)
            .setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                  public void onItemSelected(
                      AdapterView<?> parent, View view, int position, long id) {
                    events.setText("selected=" + position);
                  }

                  public void onNothingSelected(AdapterView<?> parent) {}
                });
      if (specimen instanceof GridView) ((GridView) specimen).setNumColumns(2);
      if (specimen instanceof AutoCompleteTextView) {
        AutoCompleteTextView auto = (AutoCompleteTextView) specimen;
        auto.setThreshold(1);
        auto.setAdapter(
            new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new String[] {"XML2Java", "X2C Runtime", "PluginClassLoader"}));
        if (auto instanceof MultiAutoCompleteTextView)
          ((MultiAutoCompleteTextView) auto)
              .setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
      }
      if (specimen instanceof ExpandableListView) {
        java.util.Map<String, String> g = java.util.Collections.singletonMap("text", "资源分组");
        java.util.Map<String, String> c = java.util.Collections.singletonMap("text", "drawable 子项");
        ((ExpandableListView) specimen)
            .setAdapter(
                new SimpleExpandableListAdapter(
                    this,
                    java.util.Collections.singletonList(g),
                    android.R.layout.simple_expandable_list_item_1,
                    new String[] {"text"},
                    new int[] {android.R.id.text1},
                    java.util.Collections.singletonList(java.util.Collections.singletonList(c)),
                    android.R.layout.simple_list_item_1,
                    new String[] {"text"},
                    new int[] {android.R.id.text1}));
        ((ExpandableListView) specimen).expandGroup(0);
      }
      label("说明：普通容器的子 View 全部由 XML2Java 生成；Surface/Texture 是渲染承载，日期/搜索等系统复合控件使用公开 API 配置。", 13);
    } catch (RuntimeException | AssertionError e) {
      label("FAIL · " + e, 14);
    }
  }

  private void runCase(int index, StringBuilder report) {
    if (index == 0) {
      detail = true;
      reset("批量执行 · 实际结果");
    }
    int token = generation;
    if (index == FrameworkCases.TAGS.length) {
      label(report.toString(), 13);
      label("完成 " + index + " 项（查看逐项 PASS/FAIL）", 18);
      return;
    }
    String tag = FrameworkCases.TAGS[index];
    try {
      View root = res.getView(this, FrameworkCases.layout(tag));
      verify(root, tag);
      destroyWebViews(root);
      report.append("PASS ");
    } catch (RuntimeException | AssertionError e) {
      report.append("FAIL ").append(e.getMessage()).append(" · ");
    }
    report.append(tag).append('\n');
    body.post(
        () -> {
          if (token == generation && !isFinishing()) runCase(index + 1, report);
        });
  }

  private void verify(View root, String tag) {
    View view = root.findViewById(res.id("probe_specimen"));
    require(view != null, "findViewById");
    require(
        view.getClass().getName().endsWith("." + tag.substring(tag.lastIndexOf('.') + 1)), "type");
    require(Math.abs(view.getAlpha() - .9f) < .01f && view.isEnabled(), "alpha/enabled");
    require(
        view.getPaddingLeft() == Math.round(8 * getResources().getDisplayMetrics().density),
        "padding");
    require(view.getLayoutParams() instanceof LinearLayout.LayoutParams, "parent params");
    require(tag.equals(view.getTag()), "tag");
    if (tag.equals("EditText")) {
      TextView editor = (TextView) view;
      require(
          "textColorHint 由 X2C 生成".contentEquals(editor.getHint())
              && editor.getCurrentHintTextColor() == res.color("success"),
          "textColorHint");
    }
    if (view instanceof CompoundButton || view instanceof CheckedTextView) {
      require(((android.widget.Checkable) view).isChecked(), "checked XML state");
    }
    if (tag.equals("ProgressBar")) {
      ProgressBar progress = (ProgressBar) view;
      require(progress.isIndeterminate(), "indeterminate XML state");
    } else if (tag.equals("SeekBar")) {
      SeekBar seek = (SeekBar) view;
      require(
          seek.getMax() == 100 && seek.getProgress() == 40 && seek.getSecondaryProgress() == 75,
          "seek XML state");
    } else if (tag.equals("RatingBar")) {
      require(Math.abs(((RatingBar) view).getRating() - 3.5f) < .01f, "rating XML state");
    }
    verifyXmlChildren(view, root, tag);
  }

  private void verifyXmlChildren(View specimen, View root, String tag) {
    if (!FrameworkCases.hasXmlChildren(tag)) return;
    require(specimen instanceof ViewGroup, "container type");
    View child = root.findViewById(res.id("probe_child"));
    require(child != null && isDescendant(child, (ViewGroup) specimen), "XML child hierarchy");
    ViewGroup group = (ViewGroup) specimen;
    switch (tag) {
      case "LinearLayout":
        require(((LinearLayout) group).getOrientation() == LinearLayout.HORIZONTAL, "orientation");
        require(((LinearLayout.LayoutParams) child.getLayoutParams()).weight == 1f, "weight");
        break;
      case "FrameLayout":
        require(group.getChildCount() == 2, "overlay children");
        break;
      case "RelativeLayout":
        RelativeLayout.LayoutParams relative =
            (RelativeLayout.LayoutParams)
                root.findViewById(res.id("probe_child_secondary")).getLayoutParams();
        require(relative.getRule(RelativeLayout.BELOW) == child.getId(), "relative below rule");
        break;
      case "GridLayout":
        require(
            group.getChildCount() == 3
                && child.getLayoutParams() instanceof GridLayout.LayoutParams,
            "grid rows / columns");
        break;
      case "TableLayout":
      case "TableRow":
        require(group.getChildCount() == 2, "table cells");
        break;
      case "RadioGroup":
        View checked = root.findViewById(res.id("probe_child_secondary"));
        require(((RadioGroup) group).getCheckedRadioButtonId() == checked.getId(), "checkedButton");
        break;
      case "ScrollView":
        require(
            group.getChildCount() == 1
                && specimen.isVerticalScrollBarEnabled()
                && !specimen.isHorizontalScrollBarEnabled(),
            "vertical scroll child / scrollbar");
        break;
      case "HorizontalScrollView":
        require(
            group.getChildCount() == 1
                && specimen.isHorizontalScrollBarEnabled()
                && !specimen.isVerticalScrollBarEnabled(),
            "horizontal scroll child / scrollbar");
        break;
      case "AbsoluteLayout":
        AbsoluteLayout.LayoutParams absolute =
            (AbsoluteLayout.LayoutParams) child.getLayoutParams();
        require(absolute.x == dp(12) && absolute.y == dp(12), "absolute x/y");
        break;
      case "ViewAnimator":
      case "ViewFlipper":
      case "ViewSwitcher":
      case "TextSwitcher":
      case "ImageSwitcher":
        require(group.getChildCount() == 2, "two switch pages");
        break;
      case "TabHost":
        require(
            specimen.findViewById(android.R.id.tabs) != null
                && specimen.findViewById(android.R.id.tabcontent) != null,
            "tab host structure");
        break;
      default:
        require(group.getChildCount() > 0, "visible XML children");
    }
  }

  private boolean isDescendant(View child, ViewGroup ancestor) {
    android.view.ViewParent parent = child.getParent();
    while (parent instanceof View) {
      if (parent == ancestor) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private void layouts() {
    detail = true;
    reset("布局关系 / 自定义构造与 setter");
    label(
        "覆盖 weight、gravity、Relative/Grid/Table/滚动关系，并单独验证 4 类自定义构造、8 种 setter、自定义 LayoutParams 与"
            + " childrenReady。",
        14);
    View custom = res.getView(this, "custom_views");
    body.addView(custom);
    check(
        "自定义 CONTEXT 构造 + onDraw",
        () ->
            require(
                res.requireView(
                        custom,
                        "custom_context",
                        dev.x2c.fixture.producer.widget.ContextBadgeView.class)
                    .constructionMode()
                    .equals("CONTEXT"),
                "context constructor"));
    check(
        "自定义 CONTEXT_ATTRS 构造 + onDraw",
        () ->
            require(
                res.requireView(
                        custom,
                        "custom_attrs",
                        dev.x2c.fixture.producer.widget.AttrsBadgeView.class)
                    .usedGeneratedConstructorContract(),
                "attrs constructor"));
    check(
        "自定义 CONTEXT setter",
        () -> {
          EditText input = res.requireView(custom, "custom_input", EditText.class);
          require(
              "自定义 setter 输入框".contentEquals(input.getHint()) && input.getInputType() == 1,
              "custom string / integer setters");
        });
    check(
        "自定义 CONTEXT_ATTRS_DEF_STYLE + 8 种 typed setter",
        () ->
            require(
                res.requireView(
                        custom, "custom_pill", dev.x2c.fixture.producer.widget.StatusPillView.class)
                    .hasExpectedDemoProperties(),
                "custom typed setters"));
    check(
        "自定义 LayoutParams / childrenReady",
        () ->
            require(
                res.requireView(
                        custom, "custom_flow", dev.x2c.fixture.producer.widget.FlowLayout.class)
                    .hasExpectedDemoParams(),
                "custom LayoutParams"));
    View catalog = res.getView(this, "capability_catalog");
    body.addView(catalog);
    for (String id :
        new String[] {
          "catalog_layout_card",
          "catalog_view_card",
          "catalog_resource_card",
          "catalog_drawable_card",
          "catalog_self_test"
        }) {
      View target = catalog.findViewById(res.id(id));
      if (target != null)
        target.setOnClickListener(
            v ->
                new android.app.AlertDialog.Builder(this)
                    .setMessage("对应 XML 已实例化，点击事件与 synthetic ID 正常：" + id)
                    .setPositiveButton("关闭", null)
                    .show());
    }
    View matrix = res.getView(this, "framework_matrix");
    body.addView(matrix);
    TextView result = res.requireView(matrix, "relative_result", TextView.class);
    res.requireView(matrix, "matrix_action", Button.class)
        .setOnClickListener(
            v -> {
              ProgressBar progress = res.requireView(matrix, "matrix_progress", ProgressBar.class);
              progress.setProgress((progress.getProgress() + 20) % 101);
              result.setText("按钮联动 progress=" + progress.getProgress());
            });
    check(
        "独立 factory / root / id",
        () ->
            require(
                catalog != matrix && matrix.findViewById(res.id("matrix_action")) != null,
                "layout identity"));
  }

  private void resources() {
    detail = true;
    reset("Values 与 Drawable");
    check(
        "string / text / 格式化",
        () ->
            require(
                res.string("library_name").equals("X2C JAR Fixture")
                    && !res.getText("library_name").toString().isEmpty(),
                "plugin string wins"));
    check(
        "bool / integer / dimension",
        () ->
            require(
                res.bool("feature_enabled")
                    && res.integer("max_items") == 12
                    && res.dimension(this, "space_medium") > 0,
                "typed values"));
    check(
        "fraction / arrays / plurals",
        () ->
            require(
                res.fraction("card_width", 100, 100) == 92
                    && res.integerArray("matrix_steps").length == 3
                    && res.array(this, "resource_matrix").length == 6
                    && !res.plural("matrix_cases", X2cQuantity.OTHER, 3).isEmpty(),
                "collections"));
    check(
        "宿主 values 回退",
        () ->
            require(
                res.string("x2c_host_only_string").equals("Host fallback active")
                    && res.integer("x2c_host_only_integer") == 37,
                "host fallback"));
    check(
        "缺失值边界",
        () -> require(res.findIdentifier("does_not_exist", "string") == 0, "optional missing"));
    String[] names = {
      "matrix_gradient",
      "matrix_oval",
      "matrix_line",
      "matrix_ring",
      "matrix_outline",
      "matrix_action_state",
      "matrix_layers",
      "matrix_inset",
      "matrix_clip",
      "matrix_scale",
      "matrix_rotate",
      "matrix_levels",
      "panel_state"
    };
    for (String name : names) {
      label(name, 15);
      ImageView v = new ImageView(this);
      v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(74)));
      body.addView(v);
      check(
          name,
          () -> {
            Drawable d = res.drawable(this, name);
            require(d != null, "drawable");
            v.setImageDrawable(d);
            d.setLevel(7000);
          });
      v.setOnClickListener(
          w -> {
            w.setSelected(!w.isSelected());
            if (v.getDrawable() != null) v.getDrawable().setLevel(v.isSelected() ? 10000 : 3000);
          });
    }
    check(
        "color selector",
        () -> require(res.colorStateList("matrix_text") != null, "ColorStateList"));
  }

  private void pictures() {
    detail = true;
    reset("图片来源与异步状态");
    label("宿主图片直接读取 Android drawable；插件本地 JPG 已转换成 CDN 元数据。离线失败会如实显示，可重试或取消。", 14);
    button("重新加载", this::pictures);
    button(
        "取消全部请求",
        () -> {
          generation++;
          for (ImageView v : images) X2cImages.cancel(v);
          label("已取消；旧回调不会覆盖页面", 14);
        });
    int token = generation;
    for (String name :
        new String[] {
          "hero",
          "plugin_portrait_night",
          "plugin_portrait_blue",
          "host_portrait_sun",
          "host_portrait_forest"
        }) {
      TextView status = label(name + " · 等待", 14);
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      body.addView(image, new LinearLayout.LayoutParams(-1, dp(180)));
      images.add(image);
      if (name.startsWith("host_")) {
        image.setImageDrawable(res.drawable(this, name));
        status.setText(name + " · 宿主 drawable PASS");
      } else
        res.loadImage(
            image,
            name,
            new ImageLoadAdapter() {
              @Override
              public void onStart(ImageAsset a) {
                if (token == generation) status.setText(name + " · LOADING");
              }

              @Override
              public void onSuccess(ImageAsset a) {
                if (token == generation) status.setText(name + " · CDN PASS / SHA256 已校验");
              }

              @Override
              public void onFailure(ImageAsset a, Throwable e) {
                if (token == generation)
                  status.setText(name + " · FAIL " + e.getClass().getSimpleName());
              }
            });
    }
  }

  private void check(String name, Runnable test) {
    try {
      test.run();
      label("PASS · " + name, 14);
    } catch (RuntimeException | AssertionError e) {
      label("FAIL · " + name + " · " + e.getMessage(), 14);
    }
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private TextView label(String text, int size) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(0xFF253047);
    v.setPadding(0, dp(8), 0, dp(8));
    body.addView(v);
    return v;
  }

  private void button(String text, Runnable action) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    body.addView(b, new LinearLayout.LayoutParams(-1, -2));
    b.setOnClickListener(v -> action.run());
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private void destroyWebViews(View v) {
    if (v instanceof android.webkit.WebView) ((android.webkit.WebView) v).destroy();
    else if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        destroyWebViews(((ViewGroup) v).getChildAt(i));
  }

  @Override
  public void onBackPressed() {
    if (detail) home();
    else super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    generation++;
    for (ImageView v : images) X2cImages.cancel(v);
    destroyWebViews(body);
    super.onDestroy();
  }
}
