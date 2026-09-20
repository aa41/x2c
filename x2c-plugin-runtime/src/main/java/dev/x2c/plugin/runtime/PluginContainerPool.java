package dev.x2c.plugin.runtime;

import dev.x2c.plugin.api.PluginLaunchMode;
import dev.x2c.plugin.api.PluginWindowProfile;

/** Type-safe lookup table matching the 64 Activity declarations in the runtime Manifest. */
final class PluginContainerPool {
    @SuppressWarnings("unchecked")
    private static final Class<? extends PluginContainerActivity>[][][] CONTAINERS =
            new Class[][][] {
        {
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
        },
        {
            {
                    PluginContainerActivities.TranslucentStandard0.class,
                    PluginContainerActivities.TranslucentStandard1.class,
                    PluginContainerActivities.TranslucentStandard2.class,
                    PluginContainerActivities.TranslucentStandard3.class,
                    PluginContainerActivities.TranslucentStandard4.class,
                    PluginContainerActivities.TranslucentStandard5.class,
                    PluginContainerActivities.TranslucentStandard6.class,
                    PluginContainerActivities.TranslucentStandard7.class
            },
            {
                    PluginContainerActivities.TranslucentSingleTop0.class,
                    PluginContainerActivities.TranslucentSingleTop1.class,
                    PluginContainerActivities.TranslucentSingleTop2.class,
                    PluginContainerActivities.TranslucentSingleTop3.class,
                    PluginContainerActivities.TranslucentSingleTop4.class,
                    PluginContainerActivities.TranslucentSingleTop5.class,
                    PluginContainerActivities.TranslucentSingleTop6.class,
                    PluginContainerActivities.TranslucentSingleTop7.class
            },
            {
                    PluginContainerActivities.TranslucentSingleTask0.class,
                    PluginContainerActivities.TranslucentSingleTask1.class,
                    PluginContainerActivities.TranslucentSingleTask2.class,
                    PluginContainerActivities.TranslucentSingleTask3.class,
                    PluginContainerActivities.TranslucentSingleTask4.class,
                    PluginContainerActivities.TranslucentSingleTask5.class,
                    PluginContainerActivities.TranslucentSingleTask6.class,
                    PluginContainerActivities.TranslucentSingleTask7.class
            },
            {
                    PluginContainerActivities.TranslucentSingleInstance0.class,
                    PluginContainerActivities.TranslucentSingleInstance1.class,
                    PluginContainerActivities.TranslucentSingleInstance2.class,
                    PluginContainerActivities.TranslucentSingleInstance3.class,
                    PluginContainerActivities.TranslucentSingleInstance4.class,
                    PluginContainerActivities.TranslucentSingleInstance5.class,
                    PluginContainerActivities.TranslucentSingleInstance6.class,
                    PluginContainerActivities.TranslucentSingleInstance7.class
            }
        }
    };

    private PluginContainerPool() {}

    static Class<? extends PluginContainerActivity> container(
            PluginWindowProfile profile, PluginLaunchMode mode, int slot) {
        if (slot < 0 || slot >= 8) throw new IllegalArgumentException("Invalid slot: " + slot);
        return CONTAINERS[profile.ordinal()][mode.ordinal()][slot];
    }
}
