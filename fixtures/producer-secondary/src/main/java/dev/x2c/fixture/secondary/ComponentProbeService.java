package dev.x2c.fixture.secondary;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import dev.x2c.plugin.api.X2cPluginService;
import dev.x2c.plugin.base.BasePluginService;

/** Started + bound Service example using the public host-owned plugin base. */
@X2cPluginService
public final class ComponentProbeService extends BasePluginService {
    public static final String BINDER_DESCRIPTOR = "x2c-component-service-v1";
    private final ProbeBinder binder = new ProbeBinder();
    private int starts;

    @Override public void onCreate() {
        super.onCreate();
        ComponentState.verifyHostApplication(this, ComponentProbeService.class);
        ComponentState.service = "Service: onCreate · base="
                + BasePluginService.class.getSimpleName();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        starts++;
        ComponentState.service = "Service: onStartCommand #" + starts
                + " · startId=" + startId + " · action="
                + (intent == null ? null : intent.getAction());
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        ComponentState.service = "Service: onBind · " + binder.snapshot();
        return binder;
    }

    @Override public void onDestroy() {
        ComponentState.service = "Service: onDestroy · stopped cleanly";
        super.onDestroy();
    }

    public final class ProbeBinder extends Binder {
        public String descriptor() { return BINDER_DESCRIPTOR; }
        public String snapshot() { return "starts=" + starts + " · pluginId=" + getPluginId(); }
    }
}
