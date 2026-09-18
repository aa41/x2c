package dev.x2c.runtime;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Creates a generated layout using the actual parent contract while retaining the legacy
 * {@link LayoutFactory} entry point for previously generated plugins.
 */
public interface ParentAwareLayoutFactory extends LayoutFactory {
    View create(Context context, ViewGroup parent);
}
