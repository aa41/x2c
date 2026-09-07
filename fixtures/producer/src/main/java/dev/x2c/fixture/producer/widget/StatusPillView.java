package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import dev.x2c.runtime.ImageAsset;

/** Fixture with no Context-only constructor, proving the declared constructor strategy is honored. */
public final class StatusPillView extends View {
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
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
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
}
