package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.CollectionSupport.setOf;

import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic, fail-closed discovery of supported Android resource files. */
final class ResourceScanner {
    static final Set<String> BITMAP_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "avif");
    private static final Set<String> ALLOWED_DIRECTORIES = setOf("values", "layout", "drawable", "color");
    private static final Set<String> IGNORED_FILES = setOf(".DS_Store");

    private ResourceScanner() {}

    static List<File> canonicalRoots(List<File> roots) throws IOException {
        Map<String, File> unique = new TreeMap<>();
        for (File root : roots) {
            if (!root.exists()) continue;
            File canonical = root.getCanonicalFile();
            unique.put(canonical.getAbsolutePath(), canonical);
        }
        return new ArrayList<>(unique.values());
    }

    static List<ResourceFile> discover(List<File> roots) throws IOException {
        List<ResourceFile> result = new ArrayList<>();
        for (File root : roots) {
            if (!root.isDirectory()) {
                throw failure(root, "Resource input is not a directory");
            }
            File[] directories = root.listFiles();
            if (directories == null) continue;
            Arrays.sort(directories, Comparator.comparing(File::getName));
            for (File directory : directories) {
                if (IGNORED_FILES.contains(directory.getName())) continue;
                if (!directory.isDirectory()) {
                    throw failure(directory, "Only Android resource type directories are allowed under res/");
                }
                String directoryName = directory.getName();
                if (directoryName.contains("-")) {
                    throw failure(directory,
                            "Resource qualifiers are not supported in strict MVP mode: " + directoryName);
                }
                if (!ALLOWED_DIRECTORIES.contains(directoryName)) {
                    throw failure(directory, "Unsupported resource directory: " + directoryName);
                }
                File[] children = directory.listFiles();
                if (children == null) continue;
                Arrays.sort(children, Comparator.comparing(File::getName));
                for (File child : children) {
                    if (IGNORED_FILES.contains(child.getName())) continue;
                    if (!child.isFile()) {
                        throw failure(child, "Nested resource directories are not supported");
                    }
                    String extension = extension(child.getName());
                    if ((directoryName.equals("values") || directoryName.equals("layout")
                            || directoryName.equals("color")) && !extension.equals("xml")) {
                        throw failure(child, directoryName + " resources must be XML");
                    }
                    if (directoryName.equals("drawable")
                            && !extension.equals("xml") && !BITMAP_EXTENSIONS.contains(extension)) {
                        throw failure(child, "Unsupported drawable format: " + extension);
                    }
                    result.add(new ResourceFile(directoryName, child, extension));
                }
            }
        }
        result.sort(Comparator.comparing((ResourceFile item) -> item.kind)
                .thenComparing(item -> item.file.getName())
                .thenComparing(item -> item.file.getAbsolutePath()));
        return result;
    }

    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static ResourceCompilationException failure(File file, String message) {
        return new ResourceCompilationException(file.getAbsolutePath() + ": " + message);
    }
}
