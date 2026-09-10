package dev.x2c.fixture.normal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import dev.x2c.runtime.*;
import java.util.ArrayList;
import java.util.List;

/** Normal linked AAR: no generated class references, independent of plugin installation. */
public final class NormalDemoActivity extends Activity {
  private X2cResources res;
  private LinearLayout body;
  private int clicks;
  private final List<String> results = new ArrayList<>();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    clicks = state == null ? 0 : state.getInt("clicks");
    res = X2C.resources(this, NormalDemoActivity.class);
    X2C.setContentView(this, "normal_content");
    body = res.requireView(getWindow().getDecorView(), "api_body", LinearLayout.class);
    label("X2C API 工作台", 26);
    label("普通 AAR / systemResources=" + res.isSystemResources(), 14);
    if (!res.isSystemResources())
      label("生成路径边界：detached 根节点使用通用 LayoutParams；attach 后由父容器转换。返回值也与系统 inflater 不完全相同。", 13);
    button("运行全部 API 断言", this::runTests);
    button("字符串布局重新加载", () -> recreate());
    TextView count = label("点击次数：" + clicks, 16);
    button(
        "点击 +1（旋转/重建后保留）",
        () -> {
          clicks++;
          count.setText("点击次数：" + clicks);
        });
    ImageView image = new ImageView(this);
    body.addView(image, new LinearLayout.LayoutParams(-1, 400));
    if (res.isSystemResources()) res.loadImage(image, "local_product");
    else image.setImageDrawable(res.drawable(this, "local_product"));
    button("返回测试中心", this::finish);
    runTests();
  }

  private void runTests() {
    results.clear();
    test(
        "Application / host identity",
        () ->
            require(
                getApplicationContext() == X2C.hostApplication()
                    && !X2C.isPlugin(NormalDemoActivity.class),
                "host identity"));
    test(
        "Resources / layoutName / id",
        () ->
            require(
                res.layout("normal_content") == R.layout.normal_content
                    && res.id("api_body") == R.id.api_body,
                "real R ids"));
    test(
        "string / text / format",
        () ->
            require(
                res.string("normal_click_count", 3).contains("3")
                    && res.getText("normal_title").length() > 0,
                "strings"));
    test(
        "color / state list / dimensions",
        () ->
            require(
                res.color("normal_primary") == getResources().getColor(R.color.normal_primary)
                    && res.colorStateList("normal_primary") != null
                    && res.getDimensionPixelSize(this, "normal_page_padding") > 0,
                "typed values"));
    test(
        "drawable / local image",
        () ->
            require(
                res.drawable(this, "local_product") != null
                    && res.drawable(this, "normal_button") != null,
                "drawables"));
    test(
        "inflate detached / root dimensions",
        () -> {
          LinearLayout p = new LinearLayout(this);
          View v = res.inflate(this, "api_piece", p, false);
          require(
              p.getChildCount() == 0
                  && v.getParent() == null
                  && v.getLayoutParams() != null
                  && v.getLayoutParams().width == -1
                  && v.getLayoutParams().height
                      == Math.round(48 * getResources().getDisplayMetrics().density),
              "detached");
          if (res.isSystemResources())
            require(
                v.getLayoutParams() instanceof LinearLayout.LayoutParams, "native parent params");
        });
    test(
        "inflate attachToRoot",
        () -> {
          LinearLayout p = new LinearLayout(this);
          View returned = res.inflate(this, "api_piece", p, true);
          require(
              p.getChildCount() == 1
                  && p.getChildAt(0).getLayoutParams() instanceof LinearLayout.LayoutParams,
              "attached parent params");
          require(
              returned == (res.isSystemResources() ? p : p.getChildAt(0)),
              "documented return value");
        });
    test(
        "host native merge",
        () -> {
          LinearLayout p = new LinearLayout(this);
          View v = X2C.resources(this).inflate(this, "demo_api_merge", p, true);
          require(v == p && p.getChildCount() == 2, "merge contract");
        });
    test(
        "missing optional resource",
        () ->
            require(
                res.findIdentifier("missing_resource", "layout") == 0
                    && !res.hasResource("missing_resource", "layout"),
                "missing"));
    test(
        "missing required resource rejects",
        () -> {
          try {
            res.layout("missing_resource");
            throw new AssertionError("accepted missing resource");
          } catch (android.content.res.Resources.NotFoundException
              | IllegalArgumentException expected) {
          }
        });
    test(
        "requireView type mismatch rejects",
        () -> {
          try {
            res.requireView(getWindow().getDecorView(), "api_body", TextView.class);
            throw new AssertionError("accepted wrong view type");
          } catch (IllegalArgumentException | IllegalStateException expected) {
          }
        });
    new android.app.AlertDialog.Builder(this)
        .setTitle("API 结果：" + results.size() + " 项")
        .setMessage(android.text.TextUtils.join("\n\n", results))
        .setPositiveButton("关闭", null)
        .show();
    android.util.Log.i("X2cApiDemo", android.text.TextUtils.join(" | ", results));
  }

  private void test(String name, Runnable test) {
    try {
      test.run();
      results.add("PASS · " + name);
    } catch (RuntimeException | AssertionError e) {
      results.add("FAIL · " + name + "\n" + e);
    }
  }

  private void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private TextView label(String text, int size) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setPadding(0, 16, 0, 16);
    body.addView(v);
    return v;
  }

  private void button(String text, Runnable task) {
    Button v = new Button(this);
    v.setText(text);
    v.setAllCaps(false);
    body.addView(v);
    v.setOnClickListener(w -> task.run());
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("clicks", clicks);
    super.onSaveInstanceState(state);
  }
}
