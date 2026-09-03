package dev.x2c.fixture.consumer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Host-only launcher. It has no compile/runtime dependency on producer classes. */
public final class MainActivity extends Activity {
    private static final int PAGE = 0xFFF3F6FC;
    private static final int INK = 0xFF182033;
    private static final int MUTED = 0xFF667085;
    private static final int PRIMARY = 0xFF3157D5;
    private static final int SUCCESS = 0xFF087A55;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout page = column();
        page.setPadding(dp(24), dp(24), dp(24), dp(32));
        page.setBackgroundColor(PAGE);

        LinearLayout brand = row();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("X²", 22f, Color.WHITE);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(PRIMARY, 16));
        brand.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout brandCopy = column();
        LinearLayout.LayoutParams brandCopyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandCopyParams.setMarginStart(dp(12));
        TextView brandTitle = label("X2C 动态组件实验室", 19f, INK);
        brandTitle.setTypeface(Typeface.DEFAULT_BOLD);
        brandCopy.addView(brandTitle);
        TextView brandCaption = label("Resource-free JAR Runtime", 12f, MUTED);
        LinearLayout.LayoutParams captionParams = wrap();
        captionParams.topMargin = dp(3);
        brandCopy.addView(brandCaption, captionParams);
        brand.addView(brandCopy, brandCopyParams);
        page.addView(brand);

        TextView headline = label("从独立 DEX JAR\n加载完整登录组件", 30f, INK);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setMaxLines(2);
        LinearLayout.LayoutParams headlineParams = matchWrap();
        headlineParams.topMargin = dp(30);
        page.addView(headline, headlineParams);

        TextView subtitle = label("宿主 APK 不静态依赖业务 Library。点击后由 PluginClassLoader 实例化 JAR 内 Activity。",
                15f, MUTED);
        subtitle.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(12);
        page.addView(subtitle, subtitleParams);

        LinearLayout card = column();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, 22));
        card.setElevation(dp(3));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(24);

        TextView state = label("●  动态载荷已通过完整性校验", 14f, SUCCESS);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(state);
        TextView status = label("正在读取验证信息…", 11f, MUTED);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextIsSelectable(true);
        status.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(14);
        card.addView(status, statusParams);
        page.addView(card, cardParams);

        TextView open = label("打开动态登录页面", 16f, Color.WHITE);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setGravity(Gravity.CENTER);
        open.setClickable(true);
        open.setFocusable(true);
        open.setContentDescription("使用 PluginClassLoader 打开 JAR 登录页面");
        open.setBackground(rounded(PRIMARY, 14));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        openParams.topMargin = dp(20);
        page.addView(open, openParams);

        TextView openShop = label("打开动态电商页面", 16f, PRIMARY);
        openShop.setTypeface(Typeface.DEFAULT_BOLD);
        openShop.setGravity(Gravity.CENTER);
        openShop.setClickable(true);
        openShop.setFocusable(true);
        openShop.setContentDescription("使用第二个 PluginClassLoader 打开 JAR 电商页面");
        openShop.setBackground(rounded(Color.WHITE, 14));
        LinearLayout.LayoutParams openShopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        openShopParams.topMargin = dp(10);
        page.addView(openShop, openShopParams);

        TextView note = label("Activity 声明位于宿主 Manifest · 页面、逻辑与资源代码均来自 JAR", 12f, MUTED);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(14);
        page.addView(note, noteParams);

        Throwable startupFailure = HostApplication.getPluginFailure();
        if (startupFailure == null) {
            try {
                status.setText(DynamicLibraryLoader.installationSummary() + "\n\n"
                        + DynamicLibraryLoader.secondaryLibraryName() + "\n"
                        + DynamicLibraryLoader.runJvmSelfTests() + "\n"
                        + DynamicLibraryLoader.runLoginSelfTests());
                open.setOnClickListener(ignored -> startActivity(new Intent()
                        .setClassName(getPackageName(), DynamicLibraryLoader.PLUGIN_ACTIVITY_CLASS)));
                openShop.setOnClickListener(ignored -> startActivity(new Intent()
                        .setClassName(getPackageName(), DynamicLibraryLoader.SECONDARY_ACTIVITY_CLASS)));
            } catch (Exception error) {
                showFailure(state, status, open, error);
                openShop.setEnabled(false);
                openShop.setAlpha(0.45f);
            }
        } else {
            showFailure(state, status, open, startupFailure);
            openShop.setEnabled(false);
            openShop.setAlpha(0.45f);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private static void showFailure(TextView state, TextView status, TextView open, Throwable error) {
        state.setText("●  动态组件安装失败");
        state.setTextColor(0xFFD92D20);
        open.setEnabled(false);
        open.setAlpha(0.45f);
        status.setText(error.getClass().getName() + ": " + error.getMessage());
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = column();
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
