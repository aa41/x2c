package dev.x2c.plugin.runtime;

import dev.x2c.plugin.api.PluginLaunchMode;

public final class PluginSlotAllocatorTest {
    public static void main(String[] args) {
        allocationIsStableAndLaunchModesAreIndependent();
        capacityIsEightPerModeAcrossTheProcess();
        uninstallReleasesOnlyThatPluginsSlots();
        System.out.println("PluginSlotAllocatorTest: passed");
    }

    private static void allocationIsStableAndLaunchModesAreIndependent() {
        PluginSlotAllocator allocator = new PluginSlotAllocator(8);
        assertEquals(0, allocator.allocate("one", "A", PluginLaunchMode.STANDARD));
        assertEquals(0, allocator.allocate("one", "A", PluginLaunchMode.STANDARD));
        assertEquals(1, allocator.allocate("two", "A", PluginLaunchMode.STANDARD));
        assertEquals(0, allocator.allocate("one", "A", PluginLaunchMode.SINGLE_TOP));
    }

    private static void capacityIsEightPerModeAcrossTheProcess() {
        PluginSlotAllocator allocator = new PluginSlotAllocator(8);
        for (int index = 0; index < 8; index++) {
            assertEquals(index, allocator.allocate(
                    "plugin-" + index, "Activity", PluginLaunchMode.SINGLE_TASK));
        }
        IllegalStateException error;
        try {
            allocator.allocate("overflow", "Activity", PluginLaunchMode.SINGLE_TASK);
            throw new AssertionError("Expected capacity failure");
        } catch (IllegalStateException expected) {
            error = expected;
        }
        assertEquals(
                "No free SINGLE_TASK plugin Activity container; configured capacity is 8",
                error.getMessage());
    }

    private static void uninstallReleasesOnlyThatPluginsSlots() {
        PluginSlotAllocator allocator = new PluginSlotAllocator(8);
        assertEquals(0, allocator.allocate("one", "A", PluginLaunchMode.SINGLE_INSTANCE));
        assertEquals(1, allocator.allocate("two", "A", PluginLaunchMode.SINGLE_INSTANCE));
        allocator.removePlugin("one");
        assertEquals(0, allocator.allocate("three", "A", PluginLaunchMode.SINGLE_INSTANCE));
        assertEquals(1, allocator.allocate("two", "A", PluginLaunchMode.SINGLE_INSTANCE));
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
