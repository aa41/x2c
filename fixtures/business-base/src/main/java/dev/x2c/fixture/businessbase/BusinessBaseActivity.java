package dev.x2c.fixture.businessbase;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import dev.x2c.fixture.businessbase.analytics.BusinessLifecycleAnalytics;
import dev.x2c.plugin.api.X2cPluginBase;

/**
 * Shared-source business base used by both the native host and dynamic plugins.
 *
 * <p>The host loads the normal {@code Activity} subclass. Each plugin packages a transform-unit
 * copy whose root becomes {@code PluginActivity}. Ordinary dependencies such as analytics are not
 * copied and resolve naturally through the host ClassLoader fallback.</p>
 */
@X2cPluginBase
public abstract class BusinessBaseActivity extends Activity {
    private static int nextInstanceSequence;

    private final StringBuilder lifecycle = new StringBuilder("constructor");
    private final int instanceSequence = nextSequence();
    private final BusinessLifecycleAnalytics analytics =
            new BusinessLifecycleAnalytics(instanceSequence);
    private int attachCount;
    private int createCount;
    private int startCount;
    private int resumeCount;

    protected BusinessBaseActivity() {
        lifecycle.append(".body");
        analytics.track("constructor.body");
    }

    @Override protected void attachBaseContext(Context newBase) {
        attachCount++;
        lifecycle.append(">attach.before");
        analytics.track("attach.before");
        super.attachBaseContext(newBase);
        lifecycle.append(">attach.after");
        analytics.track("attach.after");
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        createCount++;
        lifecycle.append(">create.before");
        analytics.track("create.before");
        super.onCreate(savedInstanceState);
        lifecycle.append(">create.after");
        analytics.track("create.after");
    }

    @Override protected void onStart() {
        startCount++;
        lifecycle.append(">start.before");
        analytics.track("start.before");
        super.onStart();
        lifecycle.append(">start.after");
        analytics.track("start.after");
    }

    @Override protected void onResume() {
        resumeCount++;
        lifecycle.append(">resume.before");
        analytics.track("resume.before");
        super.onResume();
        lifecycle.append(">resume.after");
        analytics.track("resume.after");
    }

    @Override protected void onPause() {
        analytics.track("pause.before");
        super.onPause();
        analytics.track("pause.after");
    }

    @Override protected void onStop() {
        analytics.track("stop.before");
        super.onStop();
        analytics.track("stop.after");
    }

    @Override protected void onDestroy() {
        analytics.track("destroy.before");
        super.onDestroy();
        analytics.track("destroy.after");
    }

    /** Runtime probe shared by the native and transformed copies. */
    protected final String businessBaseSummary() {
        Application application = getApplication();
        return "BusinessBase lifecycle=" + (isBusinessBaseHealthy() ? "PASS" : "FAIL")
                + " · instance#=" + instanceSequence
                + "\ncounts attach/create/start/resume=" + attachCount + '/' + createCount
                + '/' + startCount + '/' + resumeCount
                + "\nevents=" + lifecycle
                + "\n" + analytics.summary()
                + "\nbase.loader=" + loaderName(BusinessBaseActivity.class.getClassLoader())
                + "\nanalytics.loader="
                + loaderName(BusinessLifecycleAnalytics.class.getClassLoader())
                + "\napplication=" + application.getClass().getName();
    }

    /** Shared business action hook representing a normal analytics integration in a base class. */
    protected final void recordBusinessAction(String action) {
        analytics.track("action." + action);
    }

    protected final boolean isBusinessBaseHealthy() {
        String events = lifecycle.toString();
        return attachCount == 1
                && createCount == 1
                && events.indexOf("constructor.body>attach.before>attach.after>create.before") == 0
                && events.contains(">create.after")
                && analytics.containsInOrder(
                        "analytics.init", "constructor.body", "attach.before", "attach.after",
                        "create.before", "create.after");
    }

    /** Verifies native same-loader ownership or transformed-base/host-fallback ownership. */
    protected final boolean isBusinessDependencyFallbackHealthy() {
        ClassLoader baseLoader = BusinessBaseActivity.class.getClassLoader();
        ClassLoader analyticsLoader = BusinessLifecycleAnalytics.class.getClassLoader();
        boolean transformed = "dev.x2c.plugin.runtime.PluginActivity".equals(
                BusinessBaseActivity.class.getSuperclass().getName());
        return transformed ? baseLoader != analyticsLoader : baseLoader == analyticsLoader;
    }

    private static synchronized int nextSequence() {
        return ++nextInstanceSequence;
    }

    private static String loaderName(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }
}
