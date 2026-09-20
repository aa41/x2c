package dev.x2c.plugin.runtime;

import dev.x2c.plugin.api.PluginLaunchMode;
import dev.x2c.plugin.api.PluginWindowProfile;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Thread-safe stable slot assignment shared by all installed plugins in this process. */
final class PluginSlotAllocator {
    private final int capacity;
    private final EnumMap<PluginWindowProfile,
            EnumMap<PluginLaunchMode, Map<String, Integer>>> slots =
            new EnumMap<PluginWindowProfile,
                    EnumMap<PluginLaunchMode, Map<String, Integer>>>(PluginWindowProfile.class);

    PluginSlotAllocator(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        for (PluginWindowProfile profile : PluginWindowProfile.values()) {
            EnumMap<PluginLaunchMode, Map<String, Integer>> profileSlots =
                    new EnumMap<PluginLaunchMode, Map<String, Integer>>(PluginLaunchMode.class);
            for (PluginLaunchMode mode : PluginLaunchMode.values()) {
                profileSlots.put(mode, new HashMap<String, Integer>());
            }
            slots.put(profile, profileSlots);
        }
    }

    synchronized int allocate(
            String pluginId,
            String activityClass,
            PluginWindowProfile profile,
            PluginLaunchMode mode) {
        if (pluginId == null || activityClass == null || profile == null || mode == null) {
            throw new NullPointerException("slot key, windowProfile and launchMode must not be null");
        }
        Map<String, Integer> modeSlots = slots.get(profile).get(mode);
        String key = pluginId + '/' + activityClass;
        Integer existing = modeSlots.get(key);
        if (existing != null) return existing.intValue();
        if (modeSlots.size() >= capacity) {
            throw new IllegalStateException(
                    "No free " + profile + ' ' + mode
                            + " plugin Activity container; configured capacity is " + capacity);
        }
        int free = 0;
        while (modeSlots.containsValue(Integer.valueOf(free))) free++;
        modeSlots.put(key, Integer.valueOf(free));
        return free;
    }

    synchronized void removePlugin(String pluginId) {
        String prefix = pluginId + '/';
        for (EnumMap<PluginLaunchMode, Map<String, Integer>> profileSlots : slots.values()) {
            for (Map<String, Integer> modeSlots : profileSlots.values()) {
                Iterator<String> keys = modeSlots.keySet().iterator();
                while (keys.hasNext()) {
                    if (keys.next().startsWith(prefix)) keys.remove();
                }
            }
        }
    }
}
