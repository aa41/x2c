package dev.x2c.runtime;

/** Lifecycle callbacks for host-owned asynchronous image requests. */
public interface ImageLoadListener {
    void onStart(ImageAsset asset);

    void onSuccess(ImageAsset asset);

    void onFailure(ImageAsset asset, Throwable error);

    void onCancelled(ImageAsset asset);
}
