package dev.x2c.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a compile-only business Activity base into the plugin-private transform closure.
 *
 * <p>The host keeps its normal {@code Activity}-rooted class. The plugin build copies the marked
 * class and its reachable same-artifact class dependencies, then rewrites only that private copy.
 * Types reached only through reflection can be listed explicitly with {@link #include()}.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface X2cPluginBase {
    Class<?>[] include() default {};
}
