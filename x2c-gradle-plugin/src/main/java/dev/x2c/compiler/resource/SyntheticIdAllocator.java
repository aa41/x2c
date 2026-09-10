package dev.x2c.compiler.resource;

import dev.x2c.compiler.ResourceCompilationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Stable synthetic-ID allocation independent of Android's generated R class. */
final class SyntheticIdAllocator {
    // Android's keyed View#setTag(int, Object) rejects keys whose package byte is 0x00 or 0x01.
    // Keep plugin View IDs outside the host's usual 0x7F and framework's 0x01 namespaces while
    // retaining a 24-bit deterministic hash for independently generated class-only modules.
    private static final int VIEW_ID_PREFIX = 0x70000000;

    private SyntheticIdAllocator() {}

    static void assignSyntheticIds(String generatedPackage, Model model) {
        for (Map.Entry<String, java.util.Set<String>> symbols : ResourceSymbols.collect(model).entrySet()) {
            Map<String, Integer> ids = new TreeMap<String, Integer>();
            assignIds(generatedPackage, symbols.getKey(), symbols.getValue(), prefix(symbols.getKey()), ids);
            model.r2Ids.put(symbols.getKey(), ids);
        }
        model.ids.putAll(model.r2Ids.get("id"));
        model.layoutIds.putAll(model.r2Ids.get("layout"));
    }

    private static int prefix(String type) {
        if (type.equals("id")) return VIEW_ID_PREFIX;
        if (type.equals("layout")) return 0x7E000000;
        if (type.equals("string")) return 0x71000000;
        if (type.equals("color")) return 0x72000000;
        if (type.equals("drawable")) return 0x73000000;
        if (type.equals("dimen")) return 0x74000000;
        if (type.equals("bool")) return 0x75000000;
        if (type.equals("integer")) return 0x76000000;
        if (type.equals("array")) return 0x77000000;
        if (type.equals("plurals")) return 0x78000000;
        if (type.equals("fraction")) return 0x79000000;
        throw new AssertionError("Unknown R2 resource type: " + type);
    }

    private static void assignIds(
            String generatedPackage,
            String type,
            Iterable<String> names,
            int prefix,
            Map<String, Integer> output) {
        Map<Integer, String> owners = new TreeMap<>();
        for (String name : names) {
            int value = syntheticId(generatedPackage, type, name, prefix);
            String previous = owners.putIfAbsent(value, name);
            if (previous != null && !previous.equals(name)) {
                throw new ResourceCompilationException(String.format(Locale.ROOT,
                        "Synthetic %s collision 0x%08X between @%s/%s and @%s/%s; rename one symbol",
                        type, value, type, previous, type, name));
            }
            output.put(name, value);
        }
    }

    private static int syntheticId(String generatedPackage, String type, String name, int prefix) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((generatedPackage + ":" + type + ":" + name)
                    .getBytes(StandardCharsets.UTF_8));
            int hash = ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
            return prefix | hash;
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
