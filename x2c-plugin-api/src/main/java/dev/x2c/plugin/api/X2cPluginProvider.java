package dev.x2c.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a ContentProvider and declares the authority exposed to its delegate implementation. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface X2cPluginProvider {
    String authority();
}
