package dev.x2c.fixture.secondary;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small deterministic state store used to make launch-mode behavior visible. */
final class LaunchModeState {
    private static final Map<String, Integer> CREATES = new LinkedHashMap<String, Integer>();
    private static final Map<String, Integer> NEW_INTENTS = new LinkedHashMap<String, Integer>();

    private LaunchModeState() {}

    static synchronized int created(String mode) {
        int value = value(CREATES, mode) + 1;
        CREATES.put(mode, Integer.valueOf(value));
        return value;
    }

    static synchronized int newIntent(String mode) {
        int value = value(NEW_INTENTS, mode) + 1;
        NEW_INTENTS.put(mode, Integer.valueOf(value));
        return value;
    }

    static synchronized String summary() {
        return "create=" + CREATES + "\nnewIntent=" + NEW_INTENTS;
    }

    private static int value(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        return value == null ? 0 : value.intValue();
    }
}
