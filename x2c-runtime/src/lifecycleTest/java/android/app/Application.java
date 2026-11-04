package android.app;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Deterministic native callback dispatcher. No business callbacks are invoked during registration. */
public final class Application {
    private final List<ActivityLifecycleCallbacks> callbacks = new ArrayList<>();
    public int registerCount;
    public int unregisterCount;
    public boolean failAfterRegister;
    public boolean failUnregister;

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        registerCount++;
        callbacks.add(callback);
        if (failAfterRegister) throw new IllegalStateException("register failed");
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        unregisterCount++;
        if (failUnregister) throw new IllegalStateException("unregister failed");
        callbacks.remove(callback);
    }

    public int listenerCount() { return callbacks.size(); }

    public void dispatch(String event, Activity activity, Bundle state) {
        for (ActivityLifecycleCallbacks callback : new ArrayList<>(callbacks)) {
            switch (event) {
                case "preCreated": callback.onActivityPreCreated(activity, state); break;
                case "created": callback.onActivityCreated(activity, state); break;
                case "postCreated": callback.onActivityPostCreated(activity, state); break;
                case "preStarted": callback.onActivityPreStarted(activity); break;
                case "started": callback.onActivityStarted(activity); break;
                case "postStarted": callback.onActivityPostStarted(activity); break;
                case "preResumed": callback.onActivityPreResumed(activity); break;
                case "resumed": callback.onActivityResumed(activity); break;
                case "postResumed": callback.onActivityPostResumed(activity); break;
                case "prePaused": callback.onActivityPrePaused(activity); break;
                case "paused": callback.onActivityPaused(activity); break;
                case "postPaused": callback.onActivityPostPaused(activity); break;
                case "preStopped": callback.onActivityPreStopped(activity); break;
                case "stopped": callback.onActivityStopped(activity); break;
                case "postStopped": callback.onActivityPostStopped(activity); break;
                case "preSaved": callback.onActivityPreSaveInstanceState(activity, state); break;
                case "saved": callback.onActivitySaveInstanceState(activity, state); break;
                case "postSaved": callback.onActivityPostSaveInstanceState(activity, state); break;
                case "preDestroyed": callback.onActivityPreDestroyed(activity); break;
                case "destroyed": callback.onActivityDestroyed(activity); break;
                case "postDestroyed": callback.onActivityPostDestroyed(activity); break;
                default: throw new AssertionError("Unknown event " + event);
            }
        }
    }

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle state);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, Bundle state);
        void onActivityDestroyed(Activity activity);
        default void onActivityPreCreated(Activity activity, Bundle state) {}
        default void onActivityPostCreated(Activity activity, Bundle state) {}
        default void onActivityPreStarted(Activity activity) {}
        default void onActivityPostStarted(Activity activity) {}
        default void onActivityPreResumed(Activity activity) {}
        default void onActivityPostResumed(Activity activity) {}
        default void onActivityPrePaused(Activity activity) {}
        default void onActivityPostPaused(Activity activity) {}
        default void onActivityPreStopped(Activity activity) {}
        default void onActivityPostStopped(Activity activity) {}
        default void onActivityPreSaveInstanceState(Activity activity, Bundle state) {}
        default void onActivityPostSaveInstanceState(Activity activity, Bundle state) {}
        default void onActivityPreDestroyed(Activity activity) {}
        default void onActivityPostDestroyed(Activity activity) {}
    }
}
