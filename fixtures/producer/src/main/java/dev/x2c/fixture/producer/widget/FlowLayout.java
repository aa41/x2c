package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** Custom ViewGroup fixture with its own LayoutParams factory and typed setters. */
public final class FlowLayout extends ViewGroup {
  private boolean x2cChildrenReady;

  public FlowLayout(Context context) {
    super(context);
  }

  public void onX2cChildrenReady() {
    x2cChildrenReady = true;
  }

  public boolean hasExpectedDemoParams() {
    if (!x2cChildrenReady || getChildCount() != 3) return false;
    LayoutParams p = (LayoutParams) getChildAt(0).getLayoutParams();
    LayoutParams second = (LayoutParams) getChildAt(1).getLayoutParams();
    return p.columnSpan == 2
        && p.breakBefore
        && p.itemGravity == Gravity.CENTER
        && p.leftMargin > 0
        && second.columnSpan == 1
        && !second.breakBefore;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int available =
        Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
    int columnWidth = Math.max(1, available / 2);
    int rowHeight = 0;
    int height = getPaddingTop() + getPaddingBottom();
    int column = 0;
    for (int index = 0; index < getChildCount(); index++) {
      View child = getChildAt(index);
      LayoutParams params = (LayoutParams) child.getLayoutParams();
      int span = Math.max(1, Math.min(2, params.columnSpan));
      if ((params.breakBefore || span == 2) && column != 0) {
        height += rowHeight;
        rowHeight = 0;
        column = 0;
      }
      int cellWidth = span == 2 ? available : columnWidth;
      int childWidth = Math.max(0, cellWidth - params.leftMargin - params.rightMargin);
      int childWidthSpec =
          MeasureSpec.makeMeasureSpec(
              childWidth,
              params.width == LayoutParams.WRAP_CONTENT
                  ? MeasureSpec.AT_MOST
                  : MeasureSpec.EXACTLY);
      int childHeightSpec =
          getChildMeasureSpec(
              heightMeasureSpec,
              getPaddingTop() + getPaddingBottom() + params.topMargin + params.bottomMargin,
              params.height);
      child.measure(childWidthSpec, childHeightSpec);
      rowHeight =
          Math.max(rowHeight, child.getMeasuredHeight() + params.topMargin + params.bottomMargin);
      if (span == 2 || column == 1) {
        height += rowHeight;
        rowHeight = 0;
        column = 0;
      } else {
        column = 1;
      }
    }
    if (column != 0) height += rowHeight;
    setMeasuredDimension(
        resolveSize(available + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
        resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int available = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
    int columnWidth = Math.max(1, available / 2);
    int childTop = getPaddingTop();
    int rowHeight = 0;
    int column = 0;
    for (int index = 0; index < getChildCount(); index++) {
      View child = getChildAt(index);
      LayoutParams params = (LayoutParams) child.getLayoutParams();
      int span = Math.max(1, Math.min(2, params.columnSpan));
      if ((params.breakBefore || span == 2) && column != 0) {
        childTop += rowHeight;
        rowHeight = 0;
        column = 0;
      }
      int cellLeft = getPaddingLeft() + (column == 0 ? 0 : columnWidth);
      int childLeft = cellLeft + params.leftMargin;
      int childY = childTop + params.topMargin;
      child.layout(
          childLeft,
          childY,
          childLeft + child.getMeasuredWidth(),
          childY + child.getMeasuredHeight());
      rowHeight =
          Math.max(rowHeight, child.getMeasuredHeight() + params.topMargin + params.bottomMargin);
      if (span == 2 || column == 1) {
        childTop += rowHeight;
        rowHeight = 0;
        column = 0;
      } else {
        column = 1;
      }
    }
  }

  @Override
  protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
  }

  @Override
  protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) {
    return new LayoutParams(params.width, params.height);
  }

  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
    return params instanceof LayoutParams;
  }

  public static final class ParamsFactory {
    private ParamsFactory() {}

    public static LayoutParams create(Context context, int width, int height) {
      return new LayoutParams(width, height);
    }
  }

  public static final class LayoutParams extends ViewGroup.MarginLayoutParams {
    private int columnSpan = 1;
    private boolean breakBefore;
    private int itemGravity = Gravity.NO_GRAVITY;

    public LayoutParams(int width, int height) {
      super(width, height);
    }

    public void setColumnSpan(int columnSpan) {
      this.columnSpan = columnSpan;
    }

    public void setBreakBefore(boolean breakBefore) {
      this.breakBefore = breakBefore;
    }

    public void setItemGravity(int itemGravity) {
      this.itemGravity = itemGravity;
    }
  }
}
