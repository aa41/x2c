package dev.x2c.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a compile-only business Activity base into the plugin-private transform closure.
 *
 * <p>The host keeps its normal {@code Activity}-rooted class. The plugin build copies the marked
 * class, its Activity ancestor chain, and structural nest/inner classes, then rewrites only that
 * private transform unit. Additional plugin-private implementations must be listed explicitly
 * with {@link #include()}; all other references resolve from the host ClassLoader when absent from
 * the plugin payload.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface X2cPluginBase {
    Class<?>[] include() default {};
}
