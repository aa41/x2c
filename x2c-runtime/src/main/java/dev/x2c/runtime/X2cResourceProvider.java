package dev.x2c.runtime;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/** Generated-module resource contract consumed only through the host-owned runtime. */
public interface X2cResourceProvider {
    int getIdentifier(String type, String name);

    /** Resources#getIdentifier-style lookup for optional resources. */
    default int findIdentifier(String type, String name) {
        try {
            return getIdentifier(type, name);
        } catch (IllegalArgumentException | Resources.NotFoundException missing) {
            return 0;
        }
    }

    default boolean hasResource(String type, String name) {
        return findIdentifier(type, name) != 0;
    }

    default CharSequence getText(String name) {
        return getString(name);
    }

    String getString(String name, Object... arguments);

    int getColor(String name);

    ColorStateList getColorStateList(String name);

    boolean getBoolean(String name);

    int getInteger(String name);

    float getDimension(Context context, String name);

    default int getDimensionPixelOffset(Context context, String name) {
        return (int) getDimension(context, name);
    }

    /** Matches Resources#getDimensionPixelSize rounding, including non-zero sub-pixel values. */
    default int getDimensionPixelSize(Context context, String name) {
        float value = getDimension(context, name);
        int rounded = (int) (value + 0.5f);
        if (rounded != 0) {
            return rounded;
        }
        if (value == 0) {
            return 0;
        }
        return value > 0 ? 1 : -1;
    }

    float getFraction(String name, float base, float parentBase);

    String[] getStringArray(String name);

    default CharSequence[] getTextArray(String name) {
        return getStringArray(name);
    }

    int[] getIntegerArray(String name);

    default int[] getIntArray(String name) {
        return getIntegerArray(name);
    }

    Object[] getArray(Context context, String name);

    String getPlural(String name, X2cQuantity quantity, Object... arguments);

    default CharSequence getQuantityText(String name, X2cQuantity quantity) {
        return getPlural(name, quantity);
    }

    default String getQuantityString(
            String name, X2cQuantity quantity, Object... arguments) {
        return getPlural(name, quantity, arguments);
    }

    Drawable getDrawable(Context context, String name);
}
