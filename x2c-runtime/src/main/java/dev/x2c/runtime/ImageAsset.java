package dev.x2c.runtime;

import java.util.Objects;

/** Immutable, content-addressed metadata for a bitmap moved to HTTPS CDN storage. */
public final class ImageAsset {
    public final String moduleName;
    public final String name;
    public final String url;
    public final String sha256;
    public final String mime;
    public final long bytes;

    public ImageAsset(
            String moduleName,
            String name,
            String url,
            String sha256,
            String mime,
            long bytes) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.url = Objects.requireNonNull(url, "url");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.mime = Objects.requireNonNull(mime, "mime");
        this.bytes = bytes;
    }
}
