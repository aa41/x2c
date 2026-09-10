package dev.x2c.fixture.secondary;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.plugin.api.PluginLaunchMode;
import dev.x2c.plugin.api.X2cPluginActivity;
import dev.x2c.plugin.runtime.PluginActivity;
import dev.x2c.plugin.runtime.PluginActivityManager;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cImages;
import dev.x2c.runtime.X2cResources;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** Complete Activity/Service/Receiver/Provider showcase delivered as one independent DEX JAR. */
@X2cPluginActivity(launchMode = PluginLaunchMode.STANDARD)
public final class ComponentShowcaseActivity extends BusinessBaseActivity {
    private static final int REQUEST_STANDARD = 7101;
    private static final String LAYOUT_PLUGIN_ID = "dev.x2c.fixture.layout-showcase";
    private static final String LAYOUT_ACTIVITY = "dev.x2c.fixture.producer.DemoActivity";
    private static final String HOST_ACTIVITY = "dev.x2c.fixture.consumer.MainActivity";
    private static final Uri PROVIDER_URI = Uri.parse(
            "content://" + ComponentProbeProvider.AUTHORITY + "/items");

    private X2cResources resources;
    private TextView launchStatus;
    private TextView serviceStatus;
    private TextView receiverStatus;
    private TextView providerStatus;
    private ImageView headerImage;
    private ServiceConnection serviceConnection;
    private boolean serviceBound;
    private String lastActivityResult;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ComponentState.verifyHostApplication(this, ComponentShowcaseActivity.class);
        resources = X2C.resources(ComponentShowcaseActivity.class);
        getWindow().setStatusBarColor(resources.color("secondary_dark"));
        getWindow().setNavigationBarColor(resources.color("secondary_surface"));
        resources.setContentView(this, "component_showcase_activity");
        View root = getWindow().getDecorView();
        launchStatus = required(root, "launch_mode_status");
        serviceStatus = required(root, "service_status");
        receiverStatus = required(root, "receiver_status");
        providerStatus = required(root, "provider_status");
        headerImage = resources.requireView(root, "component_header_image", ImageView.class);
        resources.loadImage(headerImage, "product_hero");
        bindActivities(root);
        bindService(root);
        bindReceiver(root);
        bindProvider(root);
        required(root, "open_layout_showcase").setOnClickListener(ignored ->
                PluginActivityManager.startActivity(
                        this, LAYOUT_PLUGIN_ID, LAYOUT_ACTIVITY,
                        new Intent().putExtra(LaunchModeActivityBase.EXTRA_ORIGIN,
                                "component showcase → layout showcase"), null));
        required(root, "open_host_activity").setOnClickListener(ignored ->
                startActivity(new Intent().setClassName(getPackageName(), HOST_ACTIVITY)
                        .putExtra(LaunchModeActivityBase.EXTRA_ORIGIN,
                                "component plugin → host")));
        required(root, "close_component_showcase").setOnClickListener(ignored -> finish());
        renderSummary("dashboard onCreate");
    }

    @Override protected void onResume() {
        super.onResume();
        if (launchStatus != null) renderSummary("dashboard onResume");
    }

    private void bindActivities(View root) {
        bindLaunch(root, "open_standard", StandardActivity.class);
        bindLaunch(root, "open_single_top", SingleTopActivity.class);
        bindLaunch(root, "open_single_task", SingleTaskActivity.class);
        bindLaunch(root, "open_single_instance", SingleInstanceActivity.class);
        required(root, "open_standard_for_result").setOnClickListener(ignored ->
                startActivityForResult(new Intent(this, StandardActivity.class)
                        .putExtra(LaunchModeActivityBase.EXTRA_ORIGIN,
                                "dashboard startActivityForResult"), REQUEST_STANDARD));
    }

    private void bindLaunch(View root, String id, Class<?> target) {
        required(root, id).setOnClickListener(ignored ->
                startActivity(new Intent(this, target)
                        .putExtra(LaunchModeActivityBase.EXTRA_ORIGIN,
                                "dashboard → " + target.getSimpleName())));
    }

    private void bindService(View root) {
        required(root, "service_start").setOnClickListener(ignored -> {
            startService(new Intent(this, ComponentProbeService.class)
                    .setAction("dev.x2c.fixture.component.START"));
            serviceStatus.postDelayed(this::renderComponentStates, 80L);
        });
        required(root, "service_bind").setOnClickListener(ignored -> {
            if (serviceBound) {
                serviceStatus.setText("Service: already bound");
                return;
            }
            serviceConnection = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    serviceBound = true;
                    boolean typePass = service instanceof ComponentProbeService.ProbeBinder;
                    String snapshot = typePass
                            ? ((ComponentProbeService.ProbeBinder) service).snapshot() : "wrong Binder";
                    serviceStatus.setText("Service bind=" + (typePass ? "PASS" : "FAIL")
                            + "\ncomponent=" + name.getClassName() + "\n" + snapshot);
                }

                @Override public void onServiceDisconnected(ComponentName name) {
                    serviceBound = false;
                    serviceStatus.setText("Service: disconnected · " + name.getClassName());
                }
            };
            boolean accepted = bindService(
                    new Intent(this, ComponentProbeService.class)
                            .setAction("dev.x2c.fixture.component.BIND"),
                    serviceConnection, BIND_AUTO_CREATE);
            serviceStatus.setText("Service: bind requested=" + accepted);
        });
        required(root, "service_stop").setOnClickListener(ignored -> {
            unbindIfNeeded();
            boolean stopped = stopService(new Intent(this, ComponentProbeService.class));
            serviceStatus.setText("Service: stop requested=" + stopped);
            serviceStatus.postDelayed(this::renderComponentStates, 80L);
        });
    }

    private void bindReceiver(View root) {
        required(root, "receiver_normal").setOnClickListener(ignored -> {
            ComponentState.receiver = "Receiver: dispatching normal broadcast";
            sendBroadcast(new Intent(this, ComponentProbeReceiver.class)
                    .setAction("dev.x2c.fixture.component.NORMAL"));
            receiverStatus.postDelayed(this::renderComponentStates, 100L);
            // The routed manifest Receiver may not reach its goAsync continuation before the
            // first UI refresh on a cold plugin ClassLoader. Refresh again so the demo reports
            // the state written immediately before PendingResult.finish(), not a stale snapshot.
            receiverStatus.postDelayed(this::renderComponentStates, 600L);
        });
        required(root, "receiver_ordered").setOnClickListener(ignored -> {
            ComponentState.receiver = "Receiver: dispatching ordered broadcast";
            sendOrderedBroadcast(
                    new Intent(this, ComponentProbeReceiver.class)
                            .setAction("dev.x2c.fixture.component.ORDERED"),
                    null,
                    new BroadcastReceiver() {
                        @Override public void onReceive(Context context, Intent intent) {
                            receiverStatus.setText("ordered result="
                                    + (getResultCode() == 220 ? "PASS" : "FAIL")
                                    + " · code=" + getResultCode()
                                    + " · data=" + getResultData()
                                    + "\n" + ComponentState.receiver);
                        }
                    },
                    null,
                    100,
                    "host-initial",
                    null);
        });
    }

    private void bindProvider(View root) {
        required(root, "provider_run").setOnClickListener(ignored -> runProviderScenario());
    }

    private void runProviderScenario() {
        final int[] observerEvents = {0};
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                observerEvents[0]++;
                providerStatus.append("\nobserver#" + observerEvents[0] + " uri=" + uri);
            }
        };
        getContentResolver().registerContentObserver(PROVIDER_URI, true, observer);
        try {
            ContentValues insertedValues = new ContentValues();
            insertedValues.put("value", "created-from-plugin-activity");
            Uri inserted = getContentResolver().insert(PROVIDER_URI, insertedValues);

            ContentValues updatedValues = new ContentValues();
            updatedValues.put("value", "updated-through-virtual-authority");
            int updated = getContentResolver().update(
                    PROVIDER_URI, updatedValues, null, null);

            ArrayList<ContentProviderOperation> operations =
                    new ArrayList<ContentProviderOperation>();
            operations.add(ContentProviderOperation.newUpdate(PROVIDER_URI)
                    .withValue("value", "applyBatch-value").build());
            ContentProviderResult[] batch = getContentResolver().applyBatch(
                    ComponentProbeProvider.AUTHORITY, operations);

            String queried = null;
            int revision = -1;
            try (Cursor cursor = getContentResolver().query(
                    PROVIDER_URI, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    queried = cursor.getString(0);
                    revision = cursor.getInt(1);
                }
            }
            Bundle called = getContentResolver().call(
                    ComponentProbeProvider.AUTHORITY, "health", "demo", null);
            String fileText;
            try (InputStream input = getContentResolver().openInputStream(PROVIDER_URI)) {
                fileText = input == null ? "null" : readUtf8(input);
            }
            int deleted = getContentResolver().delete(PROVIDER_URI, null, null);
            providerStatus.setText("Provider CRUD/call/batch/file=PASS"
                    + "\ninsert=" + inserted
                    + "\nupdate=" + updated + " · batch=" + batch.length
                    + " · query=" + queried + "@" + revision
                    + "\ncall=" + called.getString("method")
                    + " · file=" + fileText + " · delete=" + deleted);
        } catch (Exception error) {
            providerStatus.setText("Provider scenario=FAIL\n"
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            getContentResolver().unregisterContentObserver(observer);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STANDARD) {
            lastActivityResult = "Activity Result="
                    + (resultCode == RESULT_OK ? "PASS" : "FAIL")
                    + " · " + (data == null ? null
                    : data.getStringExtra(LaunchModeActivityBase.EXTRA_RESULT));
            renderSummary("dashboard onActivityResult");
        }
    }

    private void renderSummary(String event) {
        String origin = getIntent().getStringExtra(LaunchModeActivityBase.EXTRA_ORIGIN);
        boolean sharedBase = BusinessBaseActivity.class.getClassLoader()
                == ComponentShowcaseActivity.class.getClassLoader()
                && BusinessBaseActivity.class.getSuperclass().equals(PluginActivity.class)
                && isBusinessBaseHealthy();
        String result = lastActivityResult == null ? "" : lastActivityResult + "\n";
        launchStatus.setText(result + event + " · sharedBase="
                + (sharedBase ? "PASS" : "FAIL")
                + "\norigin=" + origin + "\n" + businessBaseSummary()
                + "\n" + LaunchModeState.summary());
        renderComponentStates();
    }

    private void renderComponentStates() {
        serviceStatus.setText(ComponentState.service);
        receiverStatus.setText(ComponentState.receiver);
        if (providerStatus.length() == 0) providerStatus.setText(ComponentState.provider);
    }

    private void unbindIfNeeded() {
        if (!serviceBound || serviceConnection == null) return;
        unbindService(serviceConnection);
        serviceBound = false;
        serviceConnection = null;
    }

    @Override protected void onDestroy() {
        unbindIfNeeded();
        if (headerImage != null) X2cImages.cancel(headerImage);
        super.onDestroy();
    }

    private TextView required(View root, String name) {
        return resources.requireView(root, name, TextView.class);
    }

    private static String readUtf8(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toString("UTF-8");
    }
}
