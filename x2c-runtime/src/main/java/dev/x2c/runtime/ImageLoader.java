package dev.x2c.runtime;

import android.widget.ImageView;

/** Host-provided Coil/Glide/etc. bridge shared by all dynamically loaded modules. */
public interface ImageLoader {
    ImageRequest load(ImageView target, ImageAsset asset);

    /**
     * Callback-aware overload. Existing loaders remain source/binary compatible; loaders that can
     * observe completion should override this method.
     */
    default ImageRequest load(
            ImageView target, ImageAsset asset, ImageLoadListener listener) {
        listener.onStart(asset);
        return load(target, asset);
    }
}
