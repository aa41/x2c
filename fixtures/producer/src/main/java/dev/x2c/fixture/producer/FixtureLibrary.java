package dev.x2c.fixture.producer;

import android.content.Context;
import android.view.View;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

public final class FixtureLibrary {
    private FixtureLibrary() {}

    public static View createContent(Context context) {
        return resources().getView(context, "content");
    }

    public static View createFrameworkMatrix(Context context) {
        return resources().getView(context, "framework_matrix");
    }

    public static String libraryName() {
        return resources().string("library_name");
    }

    public static String runJvmSelfTests() {
        return JvmFeatureProbe.runAll();
    }

    public static String runLoginSelfTests() {
        return LoginLogic.runSelfTests();
    }

    public static String activityClassName() {
        return DemoActivity.class.getName();
    }

    private static X2cResources resources() {
        return X2C.resources(FixtureLibrary.class);
    }
}
