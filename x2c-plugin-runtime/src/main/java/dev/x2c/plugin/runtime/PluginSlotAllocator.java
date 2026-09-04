package dev.x2c.plugin.runtime;

import dev.x2c.plugin.api.PluginLaunchMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Thread-safe stable slot assignment shared by all installed plugins in this process. */
final class PluginSlotAllocator {
    private final int capacity;
    private final EnumMap<PluginLaunchMode, Map<String, Integer>> slots =
            new EnumMap<PluginLaunchMode, Map<String, Integer>>(PluginLaunchMode.class);

    PluginSlotAllocator(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        for (PluginLaunchMode mode : PluginLaunchMode.values()) {
            slots.put(mode, new HashMap<String, Integer>());
        }
    }

    synchronized int allocate(String pluginId, String activityClass, PluginLaunchMode mode) {
        if (pluginId == null || activityClass == null || mode == null) {
            throw new NullPointerException("slot key and launchMode must not be null");
        }
        Map<String, Integer> modeSlots = slots.get(mode);
        String key = pluginId + '/' + activityClass;
        Integer existing = modeSlots.get(key);
        if (existing != null) return existing.intValue();
        if (modeSlots.size() >= capacity) {
            throw new IllegalStateException(
                    "No free " + mode + " plugin Activity container; configured capacity is "
                            + capacity);
        }
        int free = 0;
        while (modeSlots.containsValue(Integer.valueOf(free))) free++;
        modeSlots.put(key, Integer.valueOf(free));
        return free;
    }

    synchronized void removePlugin(String pluginId) {
        String prefix = pluginId + '/';
        for (Map<String, Integer> modeSlots : slots.values()) {
            Iterator<String> keys = modeSlots.keySet().iterator();
            while (keys.hasNext()) {
                if (keys.next().startsWith(prefix)) keys.remove();
            }
        }
    }
}
