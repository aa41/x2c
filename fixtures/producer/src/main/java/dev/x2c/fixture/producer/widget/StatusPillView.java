package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import dev.x2c.runtime.ImageAsset;

/**
 * Fixture with no Context-only constructor, proving the declared constructor strategy is honored.
 */
public final class StatusPillView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private CharSequence label;
  private int accentColor;
  private int spacingPx;
  private boolean emphasized;
  private int maxItems;
  private float opacityFactor;
  private Drawable panelDrawable;
  private ImageAsset heroAsset;
  private Drawable localHeroDrawable;

  public StatusPillView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setLabel(CharSequence label) {
    this.label = label;
    invalidate();
  }

  public void setAccentColor(int accentColor) {
    this.accentColor = accentColor;
    invalidate();
  }

  public void setSpacingPx(int spacingPx) {
    this.spacingPx = spacingPx;
  }

  public void setEmphasized(boolean emphasized) {
    this.emphasized = emphasized;
  }

  public void setMaxItems(int maxItems) {
    this.maxItems = maxItems;
  }

  public void setOpacityFactor(float opacityFactor) {
    this.opacityFactor = opacityFactor;
  }

  public void setPanelDrawable(Drawable panelDrawable) {
    this.panelDrawable = panelDrawable;
    invalidate();
  }

  public void setHeroAsset(ImageAsset heroAsset) {
    this.heroAsset = heroAsset;
    this.localHeroDrawable = null;
  }

  /** Normal-AAR overload for the same logical IMAGE_ASSET custom attribute. */
  public void setHeroAsset(Drawable localHeroDrawable) {
    this.localHeroDrawable = localHeroDrawable;
    this.heroAsset = null;
  }

  public boolean hasExpectedDemoProperties() {
    return label != null
        && label.toString().startsWith("Typed setters")
        && accentColor != 0
        && spacingPx > 0
        && emphasized
        && maxItems == 12
        && Math.abs(opacityFactor - .8f) < .01f
        && panelDrawable != null
        && (heroAsset != null || localHeroDrawable != null);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (panelDrawable != null) {
      panelDrawable.setBounds(0, 0, getWidth(), getHeight());
      panelDrawable.draw(canvas);
    }
    paint.setColor(accentColor == 0 ? 0xFF3157D5 : accentColor);
    paint.setAlpha(Math.round(255 * (opacityFactor == 0 ? 1f : opacityFactor)));
    float radius = getHeight() / 2f;
    canvas.drawCircle(spacingPx + radius / 2f, radius, radius / 3f, paint);
    paint.setAlpha(255);
    paint.setColor(0xFF182033);
    paint.setFakeBoldText(emphasized);
    paint.setTextSize(dp(14));
    String text = label == null ? "Typed custom attributes" : label.toString();
    canvas.drawText(
        text, spacingPx * 2f + radius, radius - (paint.ascent() + paint.descent()) / 2f, paint);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
