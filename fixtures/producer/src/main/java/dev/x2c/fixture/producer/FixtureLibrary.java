package dev.x2c.fixture.producer;

/** Minimal reflective entry point for the layout showcase payload. */
public final class FixtureLibrary {
    private FixtureLibrary() {}

    public static String activityClassName() {
        return DemoActivity.class.getName();
    }
}
