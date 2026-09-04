package dev.x2c.plugin.base;

import dev.x2c.plugin.runtime.PluginActivity;

/**
 * Stable host-owned root for Activity base classes shared by multiple plugin payloads.
 *
 * <p>Plugin modules should depend on this artifact with {@code compileOnly}; the host installs the
 * artifact once. A native host Activity must still use its own framework Activity base; behavior
 * shared with native screens belongs in an Activity-neutral controller/helper.</p>
 */
public abstract class BasePluginActivity extends PluginActivity {
    protected BasePluginActivity() {}
}
