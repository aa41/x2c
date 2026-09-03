package dev.x2c.runtime;

/** Optional lifecycle callbacks for host-owned asynchronous image requests. */
public interface ImageLoadListener {
    default void onStart(ImageAsset asset) {}

    default void onSuccess(ImageAsset asset) {}

    default void onFailure(ImageAsset asset, Throwable error) {}

    default void onCancelled(ImageAsset asset) {}
}
