package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Fixture exposing only the Context constructor. */
public final class ContextBadgeView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public ContextBadgeView(Context context) {
    super(context);
    setContentDescription("自定义 View：Context 构造函数");
  }

  public String constructionMode() {
    return "CONTEXT";
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint.setColor(0xFF3157D5);
    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(12), dp(12), paint);
    paint.setColor(0xFFFFFFFF);
    paint.setTextSize(dp(15));
    canvas.drawText(
        "Context 构造 · 自定义 onDraw",
        dp(16),
        getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f,
        paint);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
