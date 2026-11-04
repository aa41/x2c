package dev.x2c.runtime;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Host-bound registration bookkeeping; does not retain Activity instances or plugin containers. */
final class ActivityLifecycleRegistry {
    private final Application application;
    private final Object lock = new Object();
    private final Map<Application.ActivityLifecycleCallbacks,
            Map<ClassLoader, ActivityLifecycleCallbackWrapper>> registrations =
            new IdentityHashMap<>();
    private final List<ActivityLifecycleCallbackWrapper> orderedRegistrations = new ArrayList<>();

    ActivityLifecycleRegistry(Application application) {
        this.application = Objects.requireNonNull(application, "application");
    }

    void register(ClassLoader scope, Application.ActivityLifecycleCallbacks callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            Map<ClassLoader, ActivityLifecycleCallbackWrapper> scopes = registrations.get(callback);
            if (scopes != null && scopes.containsKey(scope)) {
                if (!scopes.get(scope).isActive()) {
                    throw new IllegalStateException(
                            "Previous lifecycle cancellation failed. Retry unregister before registering again.");
                }
                return;
            }
            ActivityLifecycleCallbackWrapper wrapper = ActivityLifecycleCallbackWrapper.create(
                    scope, callback);
            try {
                application.registerActivityLifecycleCallbacks(wrapper);
            } catch (RuntimeException | Error error) {
                wrapper.deactivate();
                try {
                    application.unregisterActivityLifecycleCallbacks(wrapper);
                } catch (RuntimeException | Error cleanupError) {
                    if (cleanupError != error) error.addSuppressed(cleanupError);
                }
                throw error;
            }
            if (scopes == null) {
                scopes = new IdentityHashMap<>();
                registrations.put(callback, scopes);
            }
            scopes.put(scope, wrapper);
            orderedRegistrations.add(wrapper);
        }
    }

    void unregister(ClassLoader scope, Application.ActivityLifecycleCallbacks callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            Map<ClassLoader, ActivityLifecycleCallbackWrapper> scopes = registrations.get(callback);
            ActivityLifecycleCallbackWrapper wrapper = scopes == null ? null : scopes.get(scope);
            if (wrapper == null) return;
            wrapper.deactivate();
            // If an overridden host method fails, retain the inactive registration for retry.
            application.unregisterActivityLifecycleCallbacks(wrapper);
            scopes.remove(scope);
            orderedRegistrations.remove(wrapper);
            if (scopes.isEmpty()) registrations.remove(callback);
        }
    }

    void dispatchPluginActivityPreCreated(Activity container, Bundle state) {
        Objects.requireNonNull(container, "container");
        if (!(container instanceof X2cActivityLifecycle.ActivitySource)) {
            throw new IllegalArgumentException("PreCreated requires a plugin Activity container");
        }
        if (((X2cActivityLifecycle.ActivitySource) container).getLifecycleActivity() == null) {
            throw new IllegalStateException("Plugin Activity must be attached before PreCreated");
        }
        if (Build.VERSION.SDK_INT < 29) return;
        List<ActivityLifecycleCallbackWrapper> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(orderedRegistrations);
        }
        // User code can register/unregister or throw. Never invoke it while holding our lock.
        for (ActivityLifecycleCallbackWrapper wrapper : snapshot) {
            wrapper.dispatchPluginActivityPreCreated(container, state);
        }
    }
}
