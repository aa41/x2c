package dev.x2c.fixture.businessbase;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import dev.x2c.plugin.api.X2cPluginBase;

/**
 * Shared-source business base used by both the native host and dynamic plugins.
 *
 * <p>The host loads the normal {@code Activity} subclass. Each plugin packages its own copy and
 * the component transform changes only that copy's root to {@code PluginActivity}. Static state
 * and Class identity are intentionally isolated between those ClassLoaders.</p>
 */
@X2cPluginBase
public abstract class BusinessBaseActivity extends Activity {
    private static int nextInstanceSequence;

    private final StringBuilder lifecycle = new StringBuilder("constructor");
    private final int instanceSequence = nextSequence();
    private int attachCount;
    private int createCount;
    private int startCount;
    private int resumeCount;

    protected BusinessBaseActivity() {
        lifecycle.append(".body");
    }

    @Override protected void attachBaseContext(Context newBase) {
        attachCount++;
        lifecycle.append(">attach.before");
        super.attachBaseContext(newBase);
        lifecycle.append(">attach.after");
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        createCount++;
        lifecycle.append(">create.before");
        super.onCreate(savedInstanceState);
        lifecycle.append(">create.after");
    }

    @Override protected void onStart() {
        startCount++;
        lifecycle.append(">start.before");
        super.onStart();
        lifecycle.append(">start.after");
    }

    @Override protected void onResume() {
        resumeCount++;
        lifecycle.append(">resume.before");
        super.onResume();
        lifecycle.append(">resume.after");
    }

    /** Runtime probe shared by the native and transformed copies. */
    protected final String businessBaseSummary() {
        Application application = getApplication();
        return "BusinessBase lifecycle=" + (isBusinessBaseHealthy() ? "PASS" : "FAIL")
                + " · instance#=" + instanceSequence
                + "\ncounts attach/create/start/resume=" + attachCount + '/' + createCount
                + '/' + startCount + '/' + resumeCount
                + "\nevents=" + lifecycle
                + "\napplication=" + application.getClass().getName();
    }

    protected final boolean isBusinessBaseHealthy() {
        String events = lifecycle.toString();
        return attachCount == 1
                && createCount == 1
                && events.indexOf("constructor.body>attach.before>attach.after>create.before") == 0
                && events.contains(">create.after");
    }

    private static synchronized int nextSequence() {
        return ++nextInstanceSequence;
    }
}
