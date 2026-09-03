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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getPaddingLeft() + getPaddingRight();
        int height = getPaddingTop() + getPaddingBottom();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, height);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            width = Math.max(width, getPaddingLeft() + getPaddingRight()
                    + child.getMeasuredWidth() + params.leftMargin + params.rightMargin);
            height += child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childTop = getPaddingTop();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            childTop += params.topMargin;
            int childLeft = getPaddingLeft() + params.leftMargin;
            child.layout(childLeft, childTop,
                    childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
            childTop += child.getMeasuredHeight() + params.bottomMargin;
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
