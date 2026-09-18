package dev.x2c.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an Activity for compile-time conversion into a host-container delegate. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface X2cPluginActivity {
    PluginLaunchMode launchMode() default PluginLaunchMode.STANDARD;

    /**
     * Stable host theme contract key. An empty value requests the host's default plugin Activity
     * theme; the plugin never embeds or persists a host resource ID.
     */
    String themeKey() default "";
}
