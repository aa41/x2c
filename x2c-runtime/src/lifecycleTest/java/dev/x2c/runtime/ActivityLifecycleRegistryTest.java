package dev.x2c.runtime;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure-JVM protocol tests using Android doubles from an unpublished source directory. */
public final class ActivityLifecycleRegistryTest {
    private static final List<String> BASE_EVENTS = Arrays.asList(
            "created", "started", "resumed", "paused", "stopped", "saved", "destroyed");
    private static final List<String> API29_EVENTS = Arrays.asList(
            "preCreated", "created", "postCreated", "preStarted", "started", "postStarted",
            "preResumed", "resumed", "postResumed", "prePaused", "paused", "postPaused",
            "preStopped", "stopped", "postStopped", "preSaved", "saved", "postSaved",
            "preDestroyed", "destroyed", "postDestroyed");

    public static void main(String[] args) throws Exception {
        for (int api : new int[] {21, 28, 29, 35}) {
            Build.VERSION.SDK_INT = api;
            nativeActivitiesPreserveIdentityStateAndOrder();
            pluginCallbacksUseTheAttachedDelegateAndLeaveNativeCallbacksUntouched();
        }
        Build.VERSION.SDK_INT = 35;
        pluginLoaderScopesAreIsolatedAndCancellationIsSymmetric();
        repeatedRegistrationIsIdempotentAndCallbackEqualityIsIgnored();
        directAndBridgeRegistrationsAreIndependent();
        preCreatedAllowsReentrantRegistrationAndCancellation();
        callbackCodeRunsWithoutTheRegistryLock();
        failedCreationNeverLeaksAContainerToBusinessCallbacks();
        registrationFailuresRollBackAndUnregisterFailuresCanBeRetried();
        callbackFailuresPropagateLikeNativeAndroid();
        System.out.println("ActivityLifecycleRegistryTest: passed (API 21/28/29/35, 21 events)");
    }

    private static void nativeActivitiesPreserveIdentityStateAndOrder() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder callbacks = new Recorder();
        registry.register(null, callbacks);
        Activity activity = new Activity();
        Bundle state = new Bundle();
        for (String event : expectedEvents()) application.dispatch(event, activity, state);
        assertEquals(expectedEvents(), callbacks.events);
        callbacks.assertIdentity(activity, state);
        registry.unregister(null, callbacks);
        assertEquals(0, application.listenerCount());
    }

    private static void pluginCallbacksUseTheAttachedDelegateAndLeaveNativeCallbacksUntouched() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder business = new Recorder();
        Recorder nativeCallbacks = new Recorder();
        registry.register(null, business);
        application.registerActivityLifecycleCallbacks(nativeCallbacks);
        Container container = new Container();
        Activity delegate = new Activity();
        Bundle state = new Bundle();
        if (Build.VERSION.SDK_INT >= 29) {
            application.dispatch("preCreated", container, state);
            assertEquals(0, business.events.size());
        }
        container.delegate = delegate;
        registry.dispatchPluginActivityPreCreated(container, state);
        for (String event : expectedEvents()) {
            if (!event.equals("preCreated")) application.dispatch(event, container, state);
        }
        assertEquals(expectedEvents(), business.events);
        business.assertIdentity(delegate, state);
        assertEquals(expectedEvents(), nativeCallbacks.events);
        nativeCallbacks.assertIdentity(container, state);
        registry.unregister(null, business);
        application.dispatch("created", container, state);
        assertEquals(expectedEvents(), business.events);
        assertEquals(1, application.listenerCount());
    }

    private static void pluginLoaderScopesAreIsolatedAndCancellationIsSymmetric() throws Exception {
        Activity first = pluginActivity();
        Activity second = pluginActivity();
        require(first.getClass() != second.getClass(), "same name must not imply same class identity");
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder firstPlugin = new Recorder();
        Recorder secondPlugin = new Recorder();
        Recorder global = new Recorder();
        registry.register(first.getClass().getClassLoader(), firstPlugin);
        registry.register(second.getClass().getClassLoader(), secondPlugin);
        registry.register(null, global);
        Container firstContainer = new Container();
        firstContainer.delegate = first;
        Container secondContainer = new Container();
        secondContainer.delegate = second;
        application.dispatch("created", new Activity(), null);
        application.dispatch("created", firstContainer, null);
        application.dispatch("created", secondContainer, null);
        assertEquals(1, firstPlugin.events.size());
        assertEquals(1, secondPlugin.events.size());
        assertEquals(3, global.events.size());
        require(firstPlugin.activities.get(0) == first, "first plugin identity");
        require(secondPlugin.activities.get(0) == second, "second plugin identity");
        registry.unregister(null, firstPlugin); // Wrong scope must not cancel the plugin listener.
        application.dispatch("started", firstContainer, null);
        assertEquals(2, firstPlugin.events.size());
        registry.unregister(first.getClass().getClassLoader(), firstPlugin);
        application.dispatch("resumed", firstContainer, null);
        application.dispatch("resumed", secondContainer, null);
        assertEquals(2, firstPlugin.events.size());
        assertEquals(2, secondPlugin.events.size());
    }

    private static void repeatedRegistrationIsIdempotentAndCallbackEqualityIsIgnored() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder first = new EqualRecorder();
        Recorder second = new EqualRecorder();
        registry.register(null, first);
        registry.register(null, first);
        registry.register(null, second);
        assertEquals(2, application.registerCount);
        application.dispatch("created", new Activity(), null);
        assertEquals(1, first.events.size());
        assertEquals(1, second.events.size());
        registry.unregister(null, first);
        registry.unregister(null, first);
        assertEquals(1, application.unregisterCount);
        assertEquals(1, application.listenerCount());
        registry.unregister(null, second);
        assertEquals(0, application.listenerCount());
    }

    private static void preCreatedAllowsReentrantRegistrationAndCancellation() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder removed = new Recorder();
        Recorder added = new Recorder();
        Recorder first = new Recorder() {
            @Override public void onActivityPreCreated(Activity activity, Bundle state) {
                super.onActivityPreCreated(activity, state);
                registry.unregister(null, removed);
                registry.register(null, added);
            }
        };
        registry.register(null, first);
        registry.register(null, removed);
        Container container = new Container();
        container.delegate = new Activity();
        registry.dispatchPluginActivityPreCreated(container, null);
        assertEquals(Arrays.asList("preCreated"), first.events);
        assertEquals(0, removed.events.size());
        assertEquals(0, added.events.size()); // No retroactive event for a new registration.
        application.dispatch("created", container, null);
        assertEquals(Arrays.asList("created"), added.events);
        assertEquals(0, removed.events.size());
    }

    private static void directAndBridgeRegistrationsAreIndependent() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder callbacks = new Recorder();
        application.registerActivityLifecycleCallbacks(callbacks);
        registry.register(null, callbacks);
        Container container = new Container();
        container.delegate = new Activity();
        application.dispatch("created", container, null);
        require(callbacks.activities.get(0) == container, "native registration must stay native");
        require(callbacks.activities.get(1) == container.delegate, "bridge registration must map identity");
        registry.unregister(null, callbacks);
        application.dispatch("started", container, null);
        assertEquals(3, callbacks.events.size());
        require(callbacks.activities.get(2) == container, "bridge cancellation removed native callback");
        assertEquals(1, application.listenerCount());
    }

    private static void callbackCodeRunsWithoutTheRegistryLock() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder callbacks = new Recorder() {
            @Override public void onActivityPreCreated(Activity activity, Bundle state) {
                Thread cancellation = new Thread(() -> registry.unregister(null, this));
                cancellation.start();
                try {
                    cancellation.join(2000);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                require(!cancellation.isAlive(), "user callback must run outside registry lock");
            }
        };
        registry.register(null, callbacks);
        Container container = new Container();
        container.delegate = new Activity();
        registry.dispatchPluginActivityPreCreated(container, null);
        assertEquals(0, application.listenerCount());
    }

    private static void failedCreationNeverLeaksAContainerToBusinessCallbacks() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder callbacks = new Recorder();
        registry.register(null, callbacks);
        Container pending = new Container();
        for (String event : API29_EVENTS) application.dispatch(event, pending, null);
        assertEquals(0, callbacks.events.size());
        expectFailure(() -> registry.dispatchPluginActivityPreCreated(pending, null), "attached");
        expectFailure(
                () -> registry.dispatchPluginActivityPreCreated(new Activity(), null), "container");
    }

    private static void registrationFailuresRollBackAndUnregisterFailuresCanBeRetried() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        Recorder callback = new Recorder();
        application.failAfterRegister = true;
        expectFailure(() -> registry.register(null, callback), "register failed");
        assertEquals(0, application.listenerCount());
        application.failAfterRegister = false;
        registry.register(null, callback);
        application.failUnregister = true;
        expectFailure(() -> registry.unregister(null, callback), "unregister failed");
        expectFailure(() -> registry.register(null, callback), "Retry unregister");
        application.dispatch("created", new Activity(), null);
        assertEquals(0, callback.events.size());
        application.failUnregister = false;
        registry.unregister(null, callback);
        assertEquals(0, application.listenerCount());
        registry.register(null, callback);
        application.dispatch("created", new Activity(), null);
        assertEquals(1, callback.events.size());
    }

    private static void callbackFailuresPropagateLikeNativeAndroid() {
        Application application = new Application();
        ActivityLifecycleRegistry registry = new ActivityLifecycleRegistry(application);
        registry.register(null, new Recorder() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                throw new IllegalStateException("business callback failed");
            }
        });
        expectFailure(
                () -> application.dispatch("created", new Activity(), null), "business callback failed");
    }

    private static Activity pluginActivity() throws Exception {
        String name = PluginBusinessActivity.class.getName();
        ClassLoader loader = new ClassLoader(ActivityLifecycleRegistryTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String requested, boolean resolve)
                    throws ClassNotFoundException {
                if (!requested.equals(name)) return super.loadClass(requested, resolve);
                Class<?> loaded = findLoadedClass(requested);
                if (loaded == null) {
                    try (InputStream input = getParent().getResourceAsStream(
                            requested.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(requested);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        byte[] code = bytes.toByteArray();
                        loaded = defineClass(requested, code, 0, code.length);
                    } catch (IOException error) {
                        throw new ClassNotFoundException(requested, error);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        return (Activity) loader.loadClass(name).getConstructor().newInstance();
    }

    private static List<String> expectedEvents() {
        return Build.VERSION.SDK_INT >= 29 ? API29_EVENTS : BASE_EVENTS;
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure: " + message);
        } catch (IllegalArgumentException | IllegalStateException error) {
            require(error.getMessage().contains(message), "wrong failure: " + error);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        require(expected.equals(actual), "Expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class PluginBusinessActivity extends Activity {
        public PluginBusinessActivity() {}
    }

    private static final class Container extends Activity implements X2cActivityLifecycle.ActivitySource {
        Activity delegate;
        @Override public Activity getLifecycleActivity() { return delegate; }
    }

    private static final class EqualRecorder extends Recorder {
        @Override public boolean equals(Object other) { return other instanceof EqualRecorder; }
        @Override public int hashCode() { return 1; }
    }

    private static class Recorder implements Application.ActivityLifecycleCallbacks {
        final List<String> events = new ArrayList<>();
        final List<Activity> activities = new ArrayList<>();
        final List<Bundle> states = new ArrayList<>();

        void record(String event, Activity activity, Bundle state) {
            events.add(event);
            activities.add(activity);
            states.add(state);
        }

        void assertIdentity(Activity activity, Bundle state) {
            for (int index = 0; index < events.size(); index++) {
                require(activities.get(index) == activity, "wrong Activity identity at " + events.get(index));
                String event = events.get(index).toLowerCase();
                if (event.contains("created") || event.contains("saved")) {
                    require(states.get(index) == state, "state copied at " + events.get(index));
                }
            }
        }

        @Override public void onActivityPreCreated(Activity a, Bundle s) { record("preCreated", a, s); }
        @Override public void onActivityCreated(Activity a, Bundle s) { record("created", a, s); }
        @Override public void onActivityPostCreated(Activity a, Bundle s) { record("postCreated", a, s); }
        @Override public void onActivityPreStarted(Activity a) { record("preStarted", a, null); }
        @Override public void onActivityStarted(Activity a) { record("started", a, null); }
        @Override public void onActivityPostStarted(Activity a) { record("postStarted", a, null); }
        @Override public void onActivityPreResumed(Activity a) { record("preResumed", a, null); }
        @Override public void onActivityResumed(Activity a) { record("resumed", a, null); }
        @Override public void onActivityPostResumed(Activity a) { record("postResumed", a, null); }
        @Override public void onActivityPrePaused(Activity a) { record("prePaused", a, null); }
        @Override public void onActivityPaused(Activity a) { record("paused", a, null); }
        @Override public void onActivityPostPaused(Activity a) { record("postPaused", a, null); }
        @Override public void onActivityPreStopped(Activity a) { record("preStopped", a, null); }
        @Override public void onActivityStopped(Activity a) { record("stopped", a, null); }
        @Override public void onActivityPostStopped(Activity a) { record("postStopped", a, null); }
        @Override public void onActivityPreSaveInstanceState(Activity a, Bundle s) { record("preSaved", a, s); }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle s) { record("saved", a, s); }
        @Override public void onActivityPostSaveInstanceState(Activity a, Bundle s) { record("postSaved", a, s); }
        @Override public void onActivityPreDestroyed(Activity a) { record("preDestroyed", a, null); }
        @Override public void onActivityDestroyed(Activity a) { record("destroyed", a, null); }
        @Override public void onActivityPostDestroyed(Activity a) { record("postDestroyed", a, null); }
    }
}
