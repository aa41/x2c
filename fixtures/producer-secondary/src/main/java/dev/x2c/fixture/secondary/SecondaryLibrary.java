package dev.x2c.fixture.secondary;

import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

/** Entry point used to prove that a second JAR remains isolated from the first module. */
public final class SecondaryLibrary {
    private SecondaryLibrary() {}

    public static String libraryName() {
        X2cResources resources = X2C.resources(SecondaryLibrary.class);
        if (!resources.hasResource("secondary_screen", "layout")
                || resources.findIdentifier("missing", "layout") != 0) {
            throw new IllegalStateException("Secondary resource lookup contract failed");
        }
        return resources.getString("secondary_library_name");
    }
}
