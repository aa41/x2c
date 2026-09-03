import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import dev.x2c.runtime.LayoutFactory;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cQuantity;
import dev.x2c.runtime.X2cResourceProvider;
import dev.x2c.runtime.X2cResources;

/** Host-JVM smoke test for module isolation and default Resources-style provider methods. */
public final class RuntimeRegistrySmoke {
    public static void main(String[] arguments) {
        String first = "smoke.module.first";
        String second = "smoke.module.second";
        int deliberatelyCollidingLayoutId = 0x7E123456;
        X2C.registerResourceProvider(first, new Provider("first"));
        X2C.registerResourceProvider(second, new Provider("second"));
        LayoutFactory emptyFactory = context -> null;
        X2C.registerLayout(first, "screen", deliberatelyCollidingLayoutId, emptyFactory);
        X2C.registerLayout(second, "screen", deliberatelyCollidingLayoutId, emptyFactory);

        X2cResources firstResources = X2C.resources(first);
        X2cResources secondResources = X2C.resources(second);
        require("first".equals(firstResources.getString("label")), "first provider replaced");
        require("second".contentEquals(secondResources.getText("label")), "second text lookup");
        require(firstResources.hasResource("screen", "layout"), "known identifier missing");
        require(firstResources.findIdentifier("missing", "layout") == 0, "optional lookup must return 0");
        require(firstResources.getDimensionPixelOffset(null, "subpixel") == 0, "pixel offset mismatch");
        require(firstResources.getDimensionPixelSize(null, "subpixel") == 1, "pixel size mismatch");
        require(firstResources.getTextArray("labels").length == 2, "text array mismatch");
        require("first".equals(firstResources.getQuantityString(
                "count", X2cQuantity.OTHER)), "quantity string mismatch");

        try {
            X2C.registerResourceProvider(first, new Provider("replacement"));
            throw new AssertionError("duplicate module name was accepted");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("Duplicate X2C module name"),
                    "unexpected duplicate error: " + expected.getMessage());
        } finally {
            X2C.unregisterModule(first);
            X2C.unregisterModule(second);
        }
        System.out.println("Runtime provider API and colliding multi-module registry smoke test passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Provider implements X2cResourceProvider {
        private final String marker;

        Provider(String marker) {
            this.marker = marker;
        }

        @Override public int getIdentifier(String type, String name) {
            if ("layout".equals(type) && "screen".equals(name)) {
                return 0x7E123456;
            }
            throw new IllegalArgumentException("missing");
        }
        @Override public String getString(String name, Object... values) { return marker; }
        @Override public int getColor(String name) { return 0xff000000; }
        @Override public ColorStateList getColorStateList(String name) { return null; }
        @Override public boolean getBoolean(String name) { return true; }
        @Override public int getInteger(String name) { return 1; }
        @Override public float getDimension(Context context, String name) { return 0.25f; }
        @Override public float getFraction(String name, float base, float parentBase) { return 0.5f * base; }
        @Override public String[] getStringArray(String name) { return new String[] {marker, marker}; }
        @Override public int[] getIntegerArray(String name) { return new int[] {1, 2}; }
        @Override public Object[] getArray(Context context, String name) { return new Object[] {marker}; }
        @Override public String getPlural(
                String name, X2cQuantity quantity, Object... values) { return marker; }
        @Override public Drawable getDrawable(Context context, String name) { return null; }
    }
}
