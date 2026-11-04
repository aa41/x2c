package dev.x2c.runtime;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

/** Receiver-aware native callback wrapper. API 29 calls are isolated from older Android releases. */
class ActivityLifecycleCallbackWrapper implements Application.ActivityLifecycleCallbacks {
    final Application.ActivityLifecycleCallbacks callback;
    private final ClassLoader scope;
    private volatile boolean active = true;

    static ActivityLifecycleCallbackWrapper create(
            ClassLoader scope, Application.ActivityLifecycleCallbacks callback) {
        return Build.VERSION.SDK_INT >= 29
                ? new Api29(scope, callback) : new ActivityLifecycleCallbackWrapper(scope, callback);
    }

    private ActivityLifecycleCallbackWrapper(
            ClassLoader scope, Application.ActivityLifecycleCallbacks callback) {
        this.scope = scope;
        this.callback = callback;
    }

    final void deactivate() {
        active = false;
    }

    final boolean isActive() {
        return active;
    }

    final Activity activityForCallback(Activity activity) {
        if (!active) return null;
        if (activity instanceof X2cActivityLifecycle.ActivitySource) {
            Activity delegate = ((X2cActivityLifecycle.ActivitySource) activity).getLifecycleActivity();
            return delegate != null && (scope == null || delegate.getClass().getClassLoader() == scope)
                    ? delegate : null;
        }
        return scope == null ? activity : null;
    }

    void dispatchPluginActivityPreCreated(Activity container, Bundle state) {
        // No pre/post callbacks exist before Android API 29.
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityCreated(target, state);
    }

    @Override public void onActivityStarted(Activity activity) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityStarted(target);
    }

    @Override public void onActivityResumed(Activity activity) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityResumed(target);
    }

    @Override public void onActivityPaused(Activity activity) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityPaused(target);
    }

    @Override public void onActivityStopped(Activity activity) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityStopped(target);
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivitySaveInstanceState(target, state);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        Activity target = activityForCallback(activity);
        if (target != null) callback.onActivityDestroyed(target);
    }

    @TargetApi(29)
    private static final class Api29 extends ActivityLifecycleCallbackWrapper {
        Api29(ClassLoader scope, Application.ActivityLifecycleCallbacks callback) {
            super(scope, callback);
        }

        @Override public void onActivityPreCreated(Activity activity, Bundle state) {
            if (activity instanceof X2cActivityLifecycle.ActivitySource) return;
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreCreated(target, state);
        }

        @Override void dispatchPluginActivityPreCreated(Activity container, Bundle state) {
            Activity target = activityForCallback(container);
            if (target != null) callback.onActivityPreCreated(target, state);
        }

        @Override public void onActivityPostCreated(Activity activity, Bundle state) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostCreated(target, state);
        }

        @Override public void onActivityPreStarted(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreStarted(target);
        }

        @Override public void onActivityPostStarted(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostStarted(target);
        }

        @Override public void onActivityPreResumed(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreResumed(target);
        }

        @Override public void onActivityPostResumed(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostResumed(target);
        }

        @Override public void onActivityPrePaused(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPrePaused(target);
        }

        @Override public void onActivityPostPaused(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostPaused(target);
        }

        @Override public void onActivityPreStopped(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreStopped(target);
        }

        @Override public void onActivityPostStopped(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostStopped(target);
        }

        @Override public void onActivityPreSaveInstanceState(Activity activity, Bundle state) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreSaveInstanceState(target, state);
        }

        @Override public void onActivityPostSaveInstanceState(Activity activity, Bundle state) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostSaveInstanceState(target, state);
        }

        @Override public void onActivityPreDestroyed(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPreDestroyed(target);
        }

        @Override public void onActivityPostDestroyed(Activity activity) {
            Activity target = activityForCallback(activity);
            if (target != null) callback.onActivityPostDestroyed(target);
        }
    }
}
