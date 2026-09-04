package dev.x2c.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Service for compile-time conversion into a host-container delegate. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface X2cPluginService {}
