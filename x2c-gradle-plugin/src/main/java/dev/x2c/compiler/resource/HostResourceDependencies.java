package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Host resources inferred from unresolved references while parsing plugin layouts. */
final class HostResourceDependencies {
    private static final Set<String> SUPPORTED_TYPES = setOf(
            "string", "color", "bool", "integer", "dimen", "drawable");

    private final boolean inferenceEnabled;
    private final Map<String, Set<String>> byType = new TreeMap<>();

    private HostResourceDependencies(boolean inferenceEnabled) {
        this.inferenceEnabled = inferenceEnabled;
    }

    static HostResourceDependencies disabled() {
        return new HostResourceDependencies(false);
    }

    static HostResourceDependencies inferred() {
        return new HostResourceDependencies(true);
    }

    boolean use(String type, String name) {
        if (!inferenceEnabled || !SUPPORTED_TYPES.contains(type)) {
            return false;
        }
        byType.computeIfAbsent(type, ignored -> new TreeSet<String>()).add(name);
        return true;
    }

    Map<String, List<String>> entries() {
        Map<String, List<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : byType.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    int size() {
        int result = 0;
        for (Set<String> names : byType.values()) {
            result += names.size();
        }
        return result;
    }
}
