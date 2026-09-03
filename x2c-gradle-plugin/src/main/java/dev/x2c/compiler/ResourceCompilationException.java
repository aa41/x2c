package dev.x2c.compiler;

public final class ResourceCompilationException extends RuntimeException {
    public ResourceCompilationException(String message) {
        super(message);
    }

    public ResourceCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
