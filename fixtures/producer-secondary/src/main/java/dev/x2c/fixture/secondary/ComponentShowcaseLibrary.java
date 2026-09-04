package dev.x2c.fixture.secondary;

import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

/** Entry point for the independent component-showcase plugin. */
public final class ComponentShowcaseLibrary {
    private ComponentShowcaseLibrary() {}

    public static String libraryName() {
        X2cResources resources = X2C.resources(ComponentShowcaseLibrary.class);
        if (!resources.hasResource("component_showcase_activity", "layout")
                || !resources.hasResource("launch_mode_activity", "layout")
                || resources.findIdentifier("missing", "layout") != 0) {
            throw new IllegalStateException("Component showcase resource lookup failed");
        }
        return resources.getString("secondary_library_name");
    }

    public static String activityClassName() {
        return ComponentShowcaseActivity.class.getName();
    }
}
