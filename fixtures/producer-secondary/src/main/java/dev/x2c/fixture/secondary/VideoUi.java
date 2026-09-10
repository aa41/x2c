package dev.x2c.fixture.secondary;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import dev.x2c.runtime.X2cResources;

/** Presentation tokens sampled from the approved 8.14.0 reference. */
final class VideoUi {
  static final int PINK = 0xFFFF6699;
  static final int INK = 0xFF222326;
  static final int MUTED = 0xFF676B73;
  static final int LIGHT = 0xFF999EA6;
  static final int BACKGROUND = 0xFFF1F2F3;

  static int dp(Context c, float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable round(Context c, int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static void touch(View view, float radius) {
    view.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x22FF6699),
            round(view.getContext(), 0xFFFFFFFF, radius),
            round(view.getContext(), 0xFFFFFFFF, radius)));
    view.setClickable(true);
    view.setFocusable(true);
  }

  static void icon(ImageView view, X2cResources resources, String name, int color) {
    view.setImageDrawable(resources.drawable(view.getContext(), "bili_ref_" + name));
    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setColorFilter(color);
  }

  static TextView label(Context c, String text, float size, int color) {
    TextView view = new TextView(c);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setIncludeFontPadding(false);
    return view;
  }

  private VideoUi() {}
}
