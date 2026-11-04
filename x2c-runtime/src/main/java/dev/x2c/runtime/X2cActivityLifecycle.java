package dev.x2c.runtime;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.Objects;

/**
 * Explicit lifecycle bridge shared by normal Activities and resource-free JAR plugins.
 *
 * <p>Callbacks registered here receive the business Activity, not its host container. Callbacks
 * registered directly on the host Application remain native and continue to receive containers.
 * Registration is identity-based and idempotent within one observation scope; unregister using
 * the same callback object and scope. This bridge does not make a plugin delegate a framework-
 * attached Activity or adapt arbitrary host SDKs.</p>
 */
public final class X2cActivityLifecycle {
    private X2cActivityLifecycle() {}

    /** Observes normal Activities and all plugin business Activities in this process. */
    public static void registerActivityLifecycleCallbacks(
            Application.ActivityLifecycleCallbacks callback) {
        X2C.activityLifecycleRegistry().register(null, callback);
    }

    /**
     * Observes only the defining plugin loader of {@code ownerClass}. A host-owned class selects
     * the global scope, so the same business code works in normal and plugin builds. Multiple
     * plugin modules deliberately sharing a defining loader also share this observation scope.
     */
    public static void registerActivityLifecycleCallbacks(
            Class<?> ownerClass, Application.ActivityLifecycleCallbacks callback) {
        X2C.activityLifecycleRegistry().register(scope(ownerClass), callback);
    }

    /** Removes a global registration; unrelated loader-scoped registrations remain installed. */
    public static void unregisterActivityLifecycleCallbacks(
            Application.ActivityLifecycleCallbacks callback) {
        X2C.activityLifecycleRegistry().unregister(null, callback);
    }

    /** Removes the registration selected by the same owner scope used when registering. */
    public static void unregisterActivityLifecycleCallbacks(
            Class<?> ownerClass, Application.ActivityLifecycleCallbacks callback) {
        X2C.activityLifecycleRegistry().unregister(scope(ownerClass), callback);
    }

    private static ClassLoader scope(Class<?> ownerClass) {
        Objects.requireNonNull(ownerClass, "ownerClass");
        if (X2C.isPlugin(ownerClass)) {
            return ownerClass.getClassLoader();
        }
        try {
            if (X2C.requireHostClassLoader().loadClass(ownerClass.getName()) == ownerClass) {
                return null;
            }
        } catch (ClassNotFoundException ignored) {
            // Unknown child loaders must be explicitly registered, not treated as host code.
        }
        throw new IllegalArgumentException(
                "Lifecycle owner is neither host-owned nor from a registered plugin loader: "
                        + ownerClass.getName());
    }

    /**
     * Host-container contract. Return null before the business delegate has been attached; keep
     * returning the same delegate through the post-destroyed notification. Business Activities
     * must not implement this interface themselves.
     */
    public interface ActivitySource {
        Activity getLifecycleActivity();
    }

    /**
     * Runtime integration hook, not a business notification API. The container calls this once,
     * after delegate attachment and before business onCreate. Native PreCreated occurs too early
     * to expose that delegate; on API 29+ this supplies its corresponding plugin notification.
     */
    public static void dispatchPluginActivityPreCreated(Activity container, Bundle state) {
        X2C.activityLifecycleRegistry().dispatchPluginActivityPreCreated(container, state);
    }
}
