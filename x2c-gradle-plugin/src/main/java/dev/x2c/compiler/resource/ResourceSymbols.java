package dev.x2c.compiler.resource;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds the complete R2 namespace for every resource type supported by the compiler. */
final class ResourceSymbols {
    private ResourceSymbols() {}

    static Map<String, Set<String>> collect(Model model) {
        Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
        put(result, "id", model.declaredIds);
        put(result, "layout", model.layouts.keySet());
        put(result, "string", model.strings.keySet());
        put(result, "color", model.colorKinds.keySet());
        put(result, "drawable", model.declaredDrawables);
        put(result, "dimen", model.dimens.keySet());
        put(result, "bool", model.bools.keySet());
        put(result, "integer", model.integers.keySet());
        Set<String> arrays = new TreeSet<String>();
        arrays.addAll(model.stringArrays.keySet());
        arrays.addAll(model.integerArrays.keySet());
        arrays.addAll(model.typedArrays.keySet());
        put(result, "array", arrays);
        put(result, "plurals", model.plurals.keySet());
        put(result, "fraction", model.fractions.keySet());
        return result;
    }

    private static void put(
            Map<String, Set<String>> output, String type, Iterable<String> names) {
        Set<String> sorted = new TreeSet<String>();
        for (String name : names) {
            sorted.add(name);
        }
        output.put(type, sorted);
    }
}
