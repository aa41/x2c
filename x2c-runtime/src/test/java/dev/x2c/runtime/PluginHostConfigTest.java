package dev.x2c.runtime;

public final class PluginHostConfigTest {
    private PluginHostConfigTest() {}

    public static void main(String[] args) {
        configurationIsImmutableAndNormalizesHostStyleNames();
        equivalentConfigurationsHaveValueSemantics();
        invalidAndDuplicateContractsFailClosed();
        System.out.println("PluginHostConfigTest: passed");
    }

    private static void equivalentConfigurationsHaveValueSemantics() {
        PluginHostConfig first = PluginHostConfig.builder()
                .defaultActivityTheme("@style/HostPluginDefault")
                .activityTheme("commerce.detail.light.v1", 0x7f120001)
                .build();
        PluginHostConfig equivalent = PluginHostConfig.builder()
                .defaultActivityTheme("style/HostPluginDefault")
                .activityTheme("commerce.detail.light.v1", 0x7f120001)
                .build();
        PluginHostConfig different = PluginHostConfig.builder()
                .defaultActivityTheme("HostPluginDefault")
                .activityTheme("commerce.detail.light.v1", 0x7f120002)
                .build();

        if (!first.sameAs(equivalent)) {
            throw new AssertionError("Equivalent immutable host theme configurations must match");
        }
        if (first.sameAs(different)) {
            throw new AssertionError("Different host theme configurations must not match");
        }
    }

    private static void configurationIsImmutableAndNormalizesHostStyleNames() {
        PluginHostConfig.Builder builder = PluginHostConfig.builder()
                .defaultActivityTheme("@style/HostPluginDefault")
                .activityTheme("commerce.detail.light.v1", 0x7f120001);
        PluginHostConfig config = builder.build();
        builder.activityTheme("video.home.light.v1", "style/HostPluginVideo");

        assertEquals("HostPluginDefault", config.defaultActivityTheme.resourceName);
        assertEquals(1, config.activityThemes.size());
        assertEquals(
                0x7f120001,
                config.activityThemes.get("commerce.detail.light.v1").resourceId);
        try {
            config.activityThemes.clear();
            throw new AssertionError("Expected immutable Activity theme map");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void invalidAndDuplicateContractsFailClosed() {
        expectFailure(() -> PluginHostConfig.builder().defaultActivityTheme(0), "must not be 0");
        expectFailure(
                () -> PluginHostConfig.builder().activityTheme("bad key", 1),
                "themeKey must contain");
        expectFailure(
                () -> PluginHostConfig.builder().activityTheme(
                        "valid.key", "other.package:style/Theme"),
                "local style entry");
        expectFailure(
                () -> PluginHostConfig.builder().activityTheme("valid.key", "@HostTheme"),
                "local style entry");
        expectFailure(
                () -> PluginHostConfig.builder()
                        .activityTheme("same.key", 1)
                        .activityTheme("same.key", 2),
                "Duplicate");
        expectFailure(
                () -> PluginHostConfig.builder()
                        .defaultActivityTheme(1)
                        .defaultActivityTheme(2),
                "already configured");
    }

    private static void expectFailure(Runnable action, String messagePart) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + messagePart);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            if (!expected.getMessage().contains(messagePart)) {
                throw new AssertionError(
                        "Expected message containing '" + messagePart + "' but was '"
                                + expected.getMessage() + "'");
            }
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but was '" + actual + "'");
        }
    }
}
