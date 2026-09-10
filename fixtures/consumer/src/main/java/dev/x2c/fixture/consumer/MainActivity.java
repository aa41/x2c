package dev.x2c.fixture.consumer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.fixture.normal.NormalDemoActivity;
import dev.x2c.plugin.runtime.PluginActivityManager;

/** One APK, three independently navigable demo experiences. */
public final class MainActivity extends BusinessBaseActivity {
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(0xFFF4F6FA);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(1);
    page.setPadding(dp(24), dp(32), dp(24), dp(32));
    page.setBackgroundColor(0xFFF4F6FA);
    page.addView(text("X²  LAB", 16, 0xFF5566DD));
    page.addView(text("三个 Demo，一个 APK", 27, 0xFF14213D));
    page.addView(text("能力验证 · 视频社区 · 标准集成", 15, 0xFF748096));
    Throwable failure = HostApplication.getPluginFailure();
    TextView status =
        text(
            failure == null ? "● 插件已就绪 · 宿主 Application 共享" : "插件安装失败：" + failure,
            13,
            failure == null ? 0xFF078568 : 0xFFCE3A50);
    page.addView(status);
    card(
        page,
        "01",
        "全能力实验室",
        "53 种 framework View · 布局属性 · 自定义 View\n资源、图片与宿主引用 · 四大组件",
        0xFF5865D8,
        () ->
            PluginActivityManager.startActivity(
                this,
                DynamicLibraryLoader.LAYOUT_PLUGIN_ID,
                DynamicLibraryLoader.LAYOUT_ACTIVITY_CLASS),
        failure == null);
    card(
        page,
        "02",
        "哔哩哔哩风格首页",
        "推荐与分区 · 搜索 · 本地视频播放\n点赞、收藏、评论、关注与观看历史",
        0xFFFB7299,
        () ->
            PluginActivityManager.startActivity(
                this,
                DynamicLibraryLoader.COMPONENT_PLUGIN_ID,
                "dev.x2c.fixture.secondary.VideoHomeActivity"),
        failure == null);
    card(
        page,
        "03",
        "X2C API 工作台",
        "普通 AAR · 系统资源 · 字符串布局\nID、图片、inflate 与异常边界",
        0xFF119D91,
        () -> startActivity(new Intent(this, NormalDemoActivity.class)),
        true);
    TextView diagnostics = text("查看安装与版本信息", 14, 0xFF5865D8);
    diagnostics.setPadding(0, dp(20), 0, dp(16));
    diagnostics.setOnClickListener(
        v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("运行环境")
                .setMessage(DynamicLibraryLoader.installationSummary())
                .setPositiveButton("关闭", null)
                .show());
    page.addView(diagnostics);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.addView(page);
    setContentView(scroll);
  }

  private void card(
      LinearLayout page,
      String number,
      String title,
      String detail,
      int color,
      Runnable action,
      boolean enabled) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(1);
    card.setPadding(dp(20), dp(14), dp(20), dp(14));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.WHITE);
    bg.setCornerRadius(dp(20));
    card.setBackground(bg);
    card.addView(text(number + "  /  DEMO                              →", 11, color));
    card.addView(text(title, 21, 0xFF14213D));
    card.addView(text(detail, 13, 0xFF748096));
    card.setClickable(true);
    card.setEnabled(enabled);
    card.setAlpha(enabled ? 1f : .45f);
    card.setContentDescription(title);
    card.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(20);
    page.addView(card, lp);
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    if (size >= 22) t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
