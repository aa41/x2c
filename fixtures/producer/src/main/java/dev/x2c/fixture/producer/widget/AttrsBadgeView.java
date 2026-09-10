package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Fixture exposing only the Context + AttributeSet constructor. */
public final class AttrsBadgeView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean attributesWereNull;

  public AttrsBadgeView(Context context, AttributeSet attrs) {
    super(context, attrs);
    attributesWereNull = attrs == null;
    setContentDescription("自定义 View：Context + AttributeSet 构造函数");
  }

  public boolean usedGeneratedConstructorContract() {
    return attributesWereNull;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint.setColor(0xFF087A55);
    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(12), dp(12), paint);
    paint.setColor(0xFFFFFFFF);
    paint.setTextSize(dp(15));
    canvas.drawText(
        "Context + Attrs(null) · 构造策略",
        dp(16),
        getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f,
        paint);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
