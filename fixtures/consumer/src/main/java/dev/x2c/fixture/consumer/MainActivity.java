package dev.x2c.fixture.consumer;

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
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.plugin.runtime.PluginActivityManager;

/** Host-only launcher. It has no compile/runtime dependency on producer classes. */
public final class MainActivity extends BusinessBaseActivity {
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

        TextView headline = label("两个独立 DEX JAR\n两套完整插件场景", 30f, INK);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setMaxLines(2);
        LinearLayout.LayoutParams headlineParams = matchWrap();
        headlineParams.topMargin = dp(30);
        page.addView(headline, headlineParams);

        TextView subtitle = label("Layout/资源矩阵与四大组件实验室分别由独立 PluginClassLoader 加载。",
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

        TextView open = label("Demo 1 · Layout / 资源 / 图片", 16f, Color.WHITE);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setGravity(Gravity.CENTER);
        open.setClickable(true);
        open.setFocusable(true);
        open.setContentDescription("打开 Layout 与资源完整展示插件");
        open.setBackground(rounded(PRIMARY, 14));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        openParams.topMargin = dp(20);
        page.addView(open, openParams);

        TextView openComponents = label("Demo 2 · Activity / 四大组件", 16f, PRIMARY);
        openComponents.setTypeface(Typeface.DEFAULT_BOLD);
        openComponents.setGravity(Gravity.CENTER);
        openComponents.setClickable(true);
        openComponents.setFocusable(true);
        openComponents.setContentDescription("打开 launchMode 与四大组件完整展示插件");
        openComponents.setBackground(rounded(Color.WHITE, 14));
        LinearLayout.LayoutParams openComponentsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        openComponentsParams.topMargin = dp(10);
        page.addView(openComponents, openComponentsParams);

        TextView note = label("插件 Activity 无需宿主预声明 · 由固定代理容器承载", 12f, MUTED);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(14);
        page.addView(note, noteParams);

        Throwable startupFailure = HostApplication.getPluginFailure();
        if (startupFailure == null) {
            try {
                status.setText(DynamicLibraryLoader.installationSummary() + "\n\n"
                        + "host." + businessBaseSummary() + "\n\n"
                        + DynamicLibraryLoader.componentLibraryName() + "\n"
                        + DynamicLibraryLoader.runJvmSelfTests() + "\n"
                        + DynamicLibraryLoader.runLoginSelfTests());
                open.setOnClickListener(ignored -> PluginActivityManager.startActivity(
                        this,
                        DynamicLibraryLoader.LAYOUT_PLUGIN_ID,
                        DynamicLibraryLoader.LAYOUT_ACTIVITY_CLASS));
                openComponents.setOnClickListener(ignored -> PluginActivityManager.startActivity(
                        this,
                        DynamicLibraryLoader.COMPONENT_PLUGIN_ID,
                        DynamicLibraryLoader.COMPONENT_ACTIVITY_CLASS));
            } catch (Exception error) {
                showFailure(state, status, open, error);
                openComponents.setEnabled(false);
                openComponents.setAlpha(0.45f);
            }
        } else {
            showFailure(state, status, open, startupFailure);
            openComponents.setEnabled(false);
            openComponents.setAlpha(0.45f);
        }

        String origin = getIntent().getStringExtra("x2c.component.ORIGIN");
        if (origin != null) {
            status.append("\n\nNavigation origin: " + origin);
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
