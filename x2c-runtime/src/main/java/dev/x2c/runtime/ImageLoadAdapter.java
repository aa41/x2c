package dev.x2c.runtime;

/**
 * No-op image callback adapter safe to subclass from an independently desugared plugin DEX.
 *
 * <p>The listener interface deliberately has no Java 8 default methods. Android desugars the host
 * APK and a downloaded plugin DEX independently, so an anonymous plugin implementation that omits
 * a default callback can otherwise fail at runtime with {@link AbstractMethodError}.
 */
public class ImageLoadAdapter implements ImageLoadListener {
    public static final ImageLoadListener NONE = new ImageLoadAdapter();

    @Override
    public void onStart(ImageAsset asset) {}

    @Override
    public void onSuccess(ImageAsset asset) {}

    @Override
    public void onFailure(ImageAsset asset, Throwable error) {}

    @Override
    public void onCancelled(ImageAsset asset) {}
}
