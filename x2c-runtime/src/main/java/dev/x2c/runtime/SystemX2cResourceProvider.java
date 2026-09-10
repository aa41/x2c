package dev.x2c.runtime;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Host resource-table provider used when XML-to-Java generation is disabled or absent. */
final class SystemX2cResourceProvider implements X2cResourceProvider {
    private final Context context;
    private final Resources resources;
    private final String packageName;
    private final ConcurrentMap<String, Integer> identifiers =
            new ConcurrentHashMap<String, Integer>();

    SystemX2cResourceProvider(Context context) {
        Context supplied = Objects.requireNonNull(context, "context");
        // The resource handle is owned by its caller, not a static Activity cache. Preserve
        // Activity themes and createConfigurationContext overrides.
        this.context = supplied;
        this.resources = this.context.getResources();
        this.packageName = this.context.getPackageName();
    }

    @Override
    public int getIdentifier(String type, String name) {
        int identifier = findIdentifier(type, name);
        if (identifier == 0) {
            throw new Resources.NotFoundException(
                    "Missing Android resource @" + type + '/' + name + " in " + packageName);
        }
        return identifier;
    }

    @Override
    public int findIdentifier(String type, String name) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        String key = type + '/' + name;
        Integer cached = identifiers.get(key);
        if (cached != null) return cached.intValue();
        int resolved = resources.getIdentifier(name, type, packageName);
        Integer previous = identifiers.putIfAbsent(key, Integer.valueOf(resolved));
        return previous == null ? resolved : previous.intValue();
    }

    @Override
    public boolean hasResource(String type, String name) {
        return findIdentifier(type, name) != 0;
    }

    @Override
    public CharSequence getText(String name) {
        return resources.getText(getIdentifier("string", name));
    }

    @Override
    public String getString(String name, Object... arguments) {
        int identifier = getIdentifier("string", name);
        return arguments.length == 0
                ? resources.getString(identifier)
                : resources.getString(identifier, arguments);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getColor(String name) {
        int identifier = getIdentifier("color", name);
        return Build.VERSION.SDK_INT >= 23
                ? resources.getColor(identifier, context.getTheme())
                : resources.getColor(identifier);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ColorStateList getColorStateList(String name) {
        int identifier = getIdentifier("color", name);
        return Build.VERSION.SDK_INT >= 23
                ? resources.getColorStateList(identifier, context.getTheme())
                : resources.getColorStateList(identifier);
    }

    @Override
    public boolean getBoolean(String name) {
        return resources.getBoolean(getIdentifier("bool", name));
    }

    @Override
    public int getInteger(String name) {
        return resources.getInteger(getIdentifier("integer", name));
    }

    @Override
    public float getDimension(Context ignored, String name) {
        return resources.getDimension(getIdentifier("dimen", name));
    }

    @Override
    public int getDimensionPixelOffset(Context ignored, String name) {
        return resources.getDimensionPixelOffset(getIdentifier("dimen", name));
    }

    @Override
    public int getDimensionPixelSize(Context ignored, String name) {
        return resources.getDimensionPixelSize(getIdentifier("dimen", name));
    }

    @Override
    public float getFraction(String name, float base, float parentBase) {
        return resources.getFraction(
                getIdentifier("fraction", name), Math.round(base), Math.round(parentBase));
    }

    @Override
    public String[] getStringArray(String name) {
        return resources.getStringArray(getIdentifier("array", name));
    }

    @Override
    public CharSequence[] getTextArray(String name) {
        return resources.getTextArray(getIdentifier("array", name));
    }

    @Override
    public int[] getIntegerArray(String name) {
        return resources.getIntArray(getIdentifier("array", name));
    }

    @Override
    public int[] getIntArray(String name) {
        return getIntegerArray(name);
    }

    @Override
    public Object[] getArray(Context ignored, String name) {
        TypedArray array = resources.obtainTypedArray(getIdentifier("array", name));
        try {
            Object[] result = new Object[array.length()];
            for (int index = 0; index < array.length(); index++) {
                result[index] = typedValue(array, index);
            }
            return result;
        } finally {
            array.recycle();
        }
    }

    @Override
    public String getPlural(String name, X2cQuantity quantity, Object... arguments) {
        throw new UnsupportedOperationException(
                "X2C system-resource mode requires an integer quantity for @plurals/" + name
                        + "; call Android Resources#getQuantityString directly");
    }

    @Override
    public CharSequence getQuantityText(String name, X2cQuantity quantity) {
        return getPlural(name, quantity);
    }

    @Override
    public String getQuantityString(
            String name, X2cQuantity quantity, Object... arguments) {
        return getPlural(name, quantity, arguments);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Drawable getDrawable(Context requestContext, String name) {
        int identifier = getIdentifier("drawable", name);
        return Build.VERSION.SDK_INT >= 21
                ? resources.getDrawable(
                        identifier, Objects.requireNonNull(requestContext, "context").getTheme())
                : resources.getDrawable(identifier);
    }

    void clearIdentifierCache() {
        identifiers.clear();
    }

    ImageAsset image(String moduleName, String name) {
        int identifier = getIdentifier("drawable", name);
        // Packaged resources have no CDN hash or transfer size; no network loader is used.
        return new ImageAsset(moduleName, name,
                "android.resource://" + packageName + '/' + identifier, "", "", 0L);
    }

    private static Object typedValue(TypedArray array, int index) {
        TypedValue value = array.peekValue(index);
        if (value == null) return null;
        if (value.type == TypedValue.TYPE_STRING) return array.getString(index);
        if (value.type == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(value.data);
        if (value.type == TypedValue.TYPE_DIMENSION) return array.getDimension(index, 0f);
        if (value.type == TypedValue.TYPE_FRACTION) return array.getFraction(index, 1, 1, 0f);
        if (value.type == TypedValue.TYPE_INT_BOOLEAN) return array.getBoolean(index, false);
        if (value.type == TypedValue.TYPE_REFERENCE) return array.getResourceId(index, 0);
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return array.getColor(index, 0);
        }
        return array.getInt(index, value.data);
    }
}
