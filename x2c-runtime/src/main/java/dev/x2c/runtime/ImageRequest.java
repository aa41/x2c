package dev.x2c.runtime;

/** Cancellable request returned by a host image loader. */
public interface ImageRequest {
    void cancel();
}
