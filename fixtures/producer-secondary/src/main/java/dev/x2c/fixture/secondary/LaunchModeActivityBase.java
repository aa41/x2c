package dev.x2c.fixture.secondary;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

/** Shared in-payload Activity base used by all four launch-mode examples. */
public abstract class LaunchModeActivityBase extends BusinessBaseActivity {
    public static final String EXTRA_ORIGIN = "x2c.component.ORIGIN";
    public static final String EXTRA_RESULT = "x2c.component.RESULT";
    private X2cResources resources;
    private TextView status;
    private int createSequence;
    private int newIntentSequence;

    protected abstract String launchModeName();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        createSequence = LaunchModeState.created(launchModeName());
        resources = X2C.resources(getClass());
        resources.setContentView(this, "launch_mode_activity");
        View root = getWindow().getDecorView();
        required(root, "launch_title").setText(launchModeName());
        required(root, "launch_badge").setText("@X2cPluginActivity(" + launchModeName() + ")");
        status = required(root, "launch_status");
        render("onCreate");
        required(root, "relaunch_same").setOnClickListener(ignored ->
                startActivity(new Intent(this, getClass())
                        .putExtra(EXTRA_ORIGIN, launchModeName() + " self relaunch")));
        required(root, "return_result").setOnClickListener(ignored -> {
            setResult(RESULT_OK, new Intent().putExtra(
                    EXTRA_RESULT, launchModeName() + " returned RESULT_OK"));
            finish();
        });
        required(root, "open_component_home").setOnClickListener(ignored ->
                startActivity(new Intent(this, ComponentShowcaseActivity.class)
                        .putExtra(EXTRA_ORIGIN, launchModeName() + " → dashboard")));
        required(root, "close_launch").setOnClickListener(ignored -> finish());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        newIntentSequence = LaunchModeState.newIntent(launchModeName());
        render("onNewIntent: " + intent.getStringExtra(EXTRA_ORIGIN));
    }

    private void render(String event) {
        String origin = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_ORIGIN);
        status.setText("event=" + event
                + "\ninstance=0x" + Integer.toHexString(System.identityHashCode(this))
                + " · taskId=" + getTaskId()
                + "\ncreate#=" + createSequence + " · newIntent#=" + newIntentSequence
                + "\norigin=" + origin
                + "\n" + businessBaseSummary()
                + "\n\n" + LaunchModeState.summary());
    }

    private TextView required(View root, String name) {
        return resources.requireView(root, name, TextView.class);
    }
}
