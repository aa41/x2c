package dev.x2c.plugin.runtime;

import dev.x2c.plugin.api.PluginLaunchMode;

/** Type-safe lookup table matching the 32 manifest declarations. */
final class PluginContainerPool {
    @SuppressWarnings("unchecked")
    private static final Class<? extends PluginContainerActivity>[][] CONTAINERS = new Class[][] {
            {
                    PluginContainerActivities.Standard0.class,
                    PluginContainerActivities.Standard1.class,
                    PluginContainerActivities.Standard2.class,
                    PluginContainerActivities.Standard3.class,
                    PluginContainerActivities.Standard4.class,
                    PluginContainerActivities.Standard5.class,
                    PluginContainerActivities.Standard6.class,
                    PluginContainerActivities.Standard7.class
            },
            {
                    PluginContainerActivities.SingleTop0.class,
                    PluginContainerActivities.SingleTop1.class,
                    PluginContainerActivities.SingleTop2.class,
                    PluginContainerActivities.SingleTop3.class,
                    PluginContainerActivities.SingleTop4.class,
                    PluginContainerActivities.SingleTop5.class,
                    PluginContainerActivities.SingleTop6.class,
                    PluginContainerActivities.SingleTop7.class
            },
            {
                    PluginContainerActivities.SingleTask0.class,
                    PluginContainerActivities.SingleTask1.class,
                    PluginContainerActivities.SingleTask2.class,
                    PluginContainerActivities.SingleTask3.class,
                    PluginContainerActivities.SingleTask4.class,
                    PluginContainerActivities.SingleTask5.class,
                    PluginContainerActivities.SingleTask6.class,
                    PluginContainerActivities.SingleTask7.class
            },
            {
                    PluginContainerActivities.SingleInstance0.class,
                    PluginContainerActivities.SingleInstance1.class,
                    PluginContainerActivities.SingleInstance2.class,
                    PluginContainerActivities.SingleInstance3.class,
                    PluginContainerActivities.SingleInstance4.class,
                    PluginContainerActivities.SingleInstance5.class,
                    PluginContainerActivities.SingleInstance6.class,
                    PluginContainerActivities.SingleInstance7.class
            }
    };

    private PluginContainerPool() {}

    static Class<? extends PluginContainerActivity> container(PluginLaunchMode mode, int slot) {
        if (slot < 0 || slot >= 8) throw new IllegalArgumentException("Invalid slot: " + slot);
        return CONTAINERS[mode.ordinal()][slot];
    }
}
