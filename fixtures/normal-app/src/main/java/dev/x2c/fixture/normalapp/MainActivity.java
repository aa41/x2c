package dev.x2c.fixture.normalapp;

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
import android.widget.TextView;
import dev.x2c.fixture.normal.NormalDemoActivity;
import dev.x2c.fixture.normal.NormalLibrary;

/** Consumer that links the library normally; no reflection or custom ClassLoader is involved. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(32));
        page.setBackgroundColor(0xFFF4F7FC);

        TextView title = text("标准 AAR 宿主", 28f, 0xFF172033);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title, matchWrap());

        TextView summary = text(
                NormalLibrary.integrationMode()
                        + "\n\nActivity 来自 Library Manifest 的自动合并，点击后使用普通显式 Intent 跳转。",
                14f,
                0xFF667085);
        summary.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(12);
        page.addView(summary, summaryParams);

        TextView open = text("打开 AAR 登录测试页", 16f, Color.WHITE);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setGravity(Gravity.CENTER);
        open.setClickable(true);
        open.setFocusable(true);
        open.setBackground(roundRect(0xFF3157D5, 14));
        open.setOnClickListener(ignored ->
                startActivity(new Intent(MainActivity.this, NormalDemoActivity.class)));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        openParams.topMargin = dp(24);
        page.addView(open, openParams);

        setContentView(page);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
