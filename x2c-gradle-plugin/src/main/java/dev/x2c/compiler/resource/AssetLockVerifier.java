package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FileSupport.read;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Verifies that every bitmap has an exact, HTTPS, content-addressed lock entry. */
final class AssetLockVerifier {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private AssetLockVerifier() {}

    static Map<String, LockedAsset> readAndVerifyAssetLock(File lockFile, Map<String, BitmapAsset> bitmaps)
            throws IOException {
        if (bitmaps.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        if (lockFile == null || !lockFile.isFile()) {
            throw new ResourceCompilationException(
                    "Bitmap drawables require x2c.assetLockFile. Run generation once and inspect assets-candidates.json.");
        }
        AssetLock lock;
        try {
            lock = GSON.fromJson(read(lockFile), AssetLock.class);
        } catch (RuntimeException error) {
            throw new ResourceCompilationException("Invalid asset lock JSON: " + lockFile, error);
        }
        if (lock == null || lock.schema != 1 || lock.assets == null) {
            throw new ResourceCompilationException("Asset lock must use schema 1 and contain an assets array: " + lockFile);
        }
        Map<String, LockedAsset> byName = new TreeMap<>();
        for (LockedAsset asset : lock.assets) {
            if (asset == null || asset.name == null || asset.url == null || asset.sha256 == null || asset.mime == null) {
                throw new ResourceCompilationException("Every asset lock entry requires name, url, sha256, mime, and bytes");
            }
            if (!asset.url.startsWith("https://")) {
                throw new ResourceCompilationException("CDN asset URL must use HTTPS: " + asset.name);
            }
            if (byName.putIfAbsent(asset.name, asset) != null) {
                throw new ResourceCompilationException("Duplicate asset lock entry: " + asset.name);
            }
        }
        for (BitmapAsset bitmap : bitmaps.values()) {
            LockedAsset locked = byName.get(bitmap.name);
            if (locked == null) {
                throw new ResourceCompilationException("Asset lock is missing bitmap drawable: " + bitmap.name);
            }
            if (!bitmap.sha256.equalsIgnoreCase(locked.sha256)
                    || bitmap.bytes != locked.bytes
                    || !bitmap.mime.equals(locked.mime)) {
                throw new ResourceCompilationException("Asset lock metadata does not match source bitmap: " + bitmap.name);
            }
        }
        if (!byName.keySet().equals(bitmaps.keySet())) {
            Set<String> extra = new TreeSet<>(byName.keySet());
            extra.removeAll(bitmaps.keySet());
            throw new ResourceCompilationException("Asset lock contains entries with no source bitmap: " + extra);
        }
        return byName;
    }
}
