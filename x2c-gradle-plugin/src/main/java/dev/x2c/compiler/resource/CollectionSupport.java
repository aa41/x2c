package dev.x2c.compiler.resource;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Java 8 equivalents for the immutable collection factories added in Java 9. */
final class CollectionSupport {
    private CollectionSupport() {}

    @SafeVarargs
    static <T> Set<T> setOf(T... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(values)));
    }

    @SafeVarargs
    static <K, V> Map<K, V> mapOfEntries(Map.Entry<K, V>... entries) {
        Map<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : entries) {
            if (result.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate map key: " + entry.getKey());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<K, V>(key, value);
    }
}
