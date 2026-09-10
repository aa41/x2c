package dev.x2c.runtime;

import android.widget.ImageView;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Host-owned image registry; generated modules only register immutable CDN metadata. */
public final class X2cImages {
    private static final Object LOCK = new Object();
    private static final Map<String, ImageAsset> ASSETS = new HashMap<String, ImageAsset>();
    private static final Map<ImageView, ImageRequest> REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<ImageView, ImageRequest>());
    private static volatile ImageLoader loader;

    private X2cImages() {}

    public static void setLoader(ImageLoader value) {
        loader = Objects.requireNonNull(value, "loader");
    }

    public static void register(ImageAsset asset) {
        Objects.requireNonNull(asset, "asset");
        synchronized (LOCK) {
            ASSETS.put(key(asset.moduleName, asset.name), asset);
        }
    }

    public static ImageAsset get(String moduleName, String name) {
        ImageAsset asset;
        synchronized (LOCK) {
            asset = ASSETS.get(key(moduleName, name));
        }
        if (asset == null) {
            throw new IllegalArgumentException(
                    "Unknown X2C image @" + moduleName + ":drawable/" + name
                            + ". Initialize its generated module first.");
        }
        return asset;
    }

    public static void load(ImageView target, String moduleName, String name) {
        load(target, moduleName, name, ImageLoadAdapter.NONE);
    }

    public static void load(
            ImageView target,
            String moduleName,
            String name,
            ImageLoadListener listener) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(listener, "listener");
        ImageAsset asset = get(moduleName, name);
        cancel(target);
        ImageLoader current = loader;
        if (current == null) {
            throw new IllegalStateException("Install host X2cImages.setLoader(...) before loading CDN images");
        }
        ImageRequest request = current.load(target, asset, listener);
        if (request != null) {
            REQUESTS.put(target, request);
        }
    }

    public static void cancel(ImageView target) {
        Objects.requireNonNull(target, "target");
        ImageRequest request = REQUESTS.remove(target);
        if (request != null) {
            request.cancel();
        }
    }

    static void unregisterModule(String moduleName) {
        synchronized (LOCK) {
            Iterator<Map.Entry<String, ImageAsset>> iterator = ASSETS.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().moduleName.equals(moduleName)) {
                    iterator.remove();
                }
            }
        }
    }

    private static String key(String moduleName, String name) {
        return Objects.requireNonNull(moduleName, "moduleName") + '\u0000'
                + Objects.requireNonNull(name, "name");
    }
}
