package dev.x2c.fixture.normal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

/** Activity delivered by a regular AAR and launched through a normal explicit Intent. */
public final class NormalDemoActivity extends Activity {
    private int clickCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The runtime discovers the generated bootstrap from this business class. No generated
        // package, module constant, DexClassLoader, or host-side declaration is needed.
        X2cResources resources = X2C.resources(this, NormalDemoActivity.class);
        resources.setContentView(this, "normal_content");

        assertHostResourceIds(resources);
        getWindow().setStatusBarColor(resources.color("normal_page"));
        getWindow().setNavigationBarColor(resources.color("normal_surface"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        TextView status = resources.requireView(
                getWindow().getDecorView(), "normal_status", TextView.class);
        Button action = resources.requireView(
                getWindow().getDecorView(), "normal_action_button", Button.class);
        TextView close = resources.requireView(
                getWindow().getDecorView(), "normal_close", TextView.class);
        ImageView localImage = resources.requireView(
                getWindow().getDecorView(), "normal_local_image", ImageView.class);
        if (localImage.getDrawable() == null || resources.drawable(this, "local_product") == null) {
            throw new IllegalStateException("Normal AAR local bitmap was not loaded from host Resources");
        }

        status.setText("X2C layout ID = Android R.layout: true\n"
                + "X2C view ID = Android R.id: true\n"
                + "Local drawable = host Resources · "
                + localImage.getDrawable().getIntrinsicWidth() + "×"
                + localImage.getDrawable().getIntrinsicHeight() + "\n"
                + "ClassLoader = " + getClassLoader().getClass().getName());
        action.setOnClickListener(ignored -> {
            clickCount++;
            status.setText(resources.string("normal_click_count", clickCount));
        });
        close.setOnClickListener(ignored -> finish());
    }

    private static void assertHostResourceIds(X2cResources resources) {
        if (resources.layout("normal_content") != R.layout.normal_content) {
            throw new IllegalStateException("X2C Provider layout ID differs from Android R.layout");
        }
        if (resources.id("normal_action_button") != R.id.normal_action_button) {
            throw new IllegalStateException("X2C Provider view ID differs from Android R.id");
        }
    }
}
