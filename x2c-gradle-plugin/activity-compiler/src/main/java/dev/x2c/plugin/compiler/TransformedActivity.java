package dev.x2c.plugin.compiler;

import java.util.Objects;

/** Compiler result independent of Android runtime classes. */
public final class TransformedActivity implements Comparable<TransformedActivity> {
    public final String className;
    public final String internalName;
    public final String launchMode;

    TransformedActivity(String internalName, String launchMode) {
        this.internalName = Objects.requireNonNull(internalName, "internalName");
        this.className = internalName.replace('/', '.');
        this.launchMode = Objects.requireNonNull(launchMode, "launchMode");
    }

    @Override public int compareTo(TransformedActivity other) {
        return className.compareTo(other.className);
    }
}
