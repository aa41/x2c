package dev.x2c.runtime;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.Objects;

/** Stable business-facing handle for one generated module; it exposes no generated Java types. */
public final class X2cResources {
    private final String moduleName;
    private final X2cResourceProvider provider;
    private final boolean systemResources;

    X2cResources(String moduleName, X2cResourceProvider provider) {
        this(moduleName, provider, false);
    }

    private X2cResources(
            String moduleName, X2cResourceProvider provider, boolean systemResources) {
        this.moduleName = moduleName;
        this.provider = provider;
        this.systemResources = systemResources;
    }

    static X2cResources system(String moduleName, X2cResourceProvider provider) {
        return new X2cResources(moduleName, provider, true);
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isSystemResources() {
        return systemResources;
    }

    public int identifier(String type, String name) {
        return provider.getIdentifier(type, name);
    }

    public int getIdentifier(String name, String type) {
        return provider.getIdentifier(type, name);
    }

    public int findIdentifier(String name, String type) {
        return provider.findIdentifier(type, name);
    }

    public boolean hasResource(String name, String type) {
        return provider.hasResource(type, name);
    }

    public int id(String name) {
        return identifier("id", name);
    }

    public int layout(String name) {
        return identifier("layout", name);
    }

    public String string(String name, Object... arguments) {
        return provider.getString(name, arguments);
    }

    public CharSequence getText(String name) {
        return provider.getText(name);
    }

    public String getString(String name, Object... arguments) {
        return provider.getString(name, arguments);
    }

    public int color(String name) {
        return provider.getColor(name);
    }

    public int getColor(String name) {
        return provider.getColor(name);
    }

    public ColorStateList colorStateList(String name) {
        return provider.getColorStateList(name);
    }

    public ColorStateList getColorStateList(String name) {
        return provider.getColorStateList(name);
    }

    public boolean bool(String name) {
        return provider.getBoolean(name);
    }

    public boolean getBoolean(String name) {
        return provider.getBoolean(name);
    }

    public int integer(String name) {
        return provider.getInteger(name);
    }

    public int getInteger(String name) {
        return provider.getInteger(name);
    }

    public float dimension(Context context, String name) {
        return provider.getDimension(context, name);
    }

    public float getDimension(Context context, String name) {
        return provider.getDimension(context, name);
    }

    public int getDimensionPixelOffset(Context context, String name) {
        return (int) provider.getDimension(context, name);
    }

    public int getDimensionPixelSize(Context context, String name) {
        float value = provider.getDimension(context, name);
        int rounded = (int) (value + 0.5f);
        if (rounded != 0) return rounded;
        if (value == 0f) return 0;
        return value > 0f ? 1 : -1;
    }

    public float fraction(String name, float base, float parentBase) {
        return provider.getFraction(name, base, parentBase);
    }

    public float getFraction(String name, float base, float parentBase) {
        return provider.getFraction(name, base, parentBase);
    }

    public String[] stringArray(String name) {
        return provider.getStringArray(name);
    }

    public CharSequence[] getTextArray(String name) {
        return provider.getTextArray(name);
    }

    public String[] getStringArray(String name) {
        return provider.getStringArray(name);
    }

    public int[] integerArray(String name) {
        return provider.getIntegerArray(name);
    }

    public int[] getIntArray(String name) {
        return provider.getIntArray(name);
    }

    public Object[] array(Context context, String name) {
        return provider.getArray(context, name);
    }

    public Object[] getArray(Context context, String name) {
        return provider.getArray(context, name);
    }

    public String plural(String name, X2cQuantity quantity, Object... arguments) {
        return provider.getPlural(name, quantity, arguments);
    }

    public CharSequence getQuantityText(String name, X2cQuantity quantity) {
        return provider.getQuantityText(name, quantity);
    }

    public String getQuantityString(
            String name, X2cQuantity quantity, Object... arguments) {
        return provider.getQuantityString(name, quantity, arguments);
    }

    public Drawable drawable(Context context, String name) {
        return provider.getDrawable(context, name);
    }

    public Drawable getDrawable(Context context, String name) {
        return provider.getDrawable(context, name);
    }

    public ImageAsset image(String name) {
        if (systemResources) {
            return ((SystemX2cResourceProvider) provider).image(moduleName, name);
        }
        return X2cImages.get(moduleName, name);
    }

    public void loadImage(ImageView target, String name) {
        loadImage(target, name, ImageLoadAdapter.NONE);
    }

    public void loadImage(ImageView target, String name, ImageLoadListener listener) {
        if (systemResources) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(listener, "listener");
            ImageAsset asset = image(name);
            X2cImages.cancel(target);
            listener.onStart(asset);
            Drawable drawable;
            try {
                drawable = provider.getDrawable(target.getContext(), name);
            } catch (RuntimeException error) {
                listener.onFailure(asset, error);
                return;
            }
            target.setImageDrawable(drawable);
            listener.onSuccess(asset);
            return;
        }
        X2cImages.load(target, moduleName, name, listener);
    }

    public void setContentView(Activity activity, String layoutName) {
        if (systemResources) {
            X2C.setSystemContentView(activity, layoutName);
        } else {
            X2C.setContentView(activity, moduleName, layoutName);
        }
    }

    public View getView(Context context, String layoutName) {
        return systemResources
                ? X2C.getSystemView(context, layoutName)
                : X2C.getView(context, moduleName, layoutName);
    }

    public View inflate(Context context, String layoutName, ViewGroup parent) {
        return inflate(context, layoutName, parent, parent != null);
    }

    public View inflate(
            Context context,
            String layoutName,
            ViewGroup parent,
            boolean attachToRoot) {
        return systemResources
                ? X2C.inflateSystem(context, layoutName, parent, attachToRoot)
                : X2C.inflate(context, moduleName, layoutName, parent, attachToRoot);
    }

    public <T extends View> T requireView(View root, String idName, Class<T> type) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(type, "type");
        int id = id(idName);
        View found = root.findViewById(id);
        if (!type.isInstance(found)) {
            throw new IllegalStateException(String.format(
                    "Missing X2C view @%s:id/%s (0x%08X), expected %s",
                    moduleName, idName, id, type.getName()));
        }
        return type.cast(found);
    }
}
