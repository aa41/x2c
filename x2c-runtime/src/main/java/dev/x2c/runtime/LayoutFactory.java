package dev.x2c.runtime;

import android.content.Context;
import android.view.View;

/** Creates one generated layout without reading an Android XML resource. */
public interface LayoutFactory {
    View create(Context context);
}
