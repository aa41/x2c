package dev.x2c.plugin.compiler;

/** Metadata for one generated Service, Receiver, or Provider registry entry. */
public final class TransformedComponent implements Comparable<TransformedComponent> {
    public final String className;
    public final String internalName;
    public final String authority;

    TransformedComponent(String internalName, String authority) {
        this.internalName = internalName;
        this.className = internalName.replace('/', '.');
        this.authority = authority;
    }

    @Override public int compareTo(TransformedComponent other) {
        return className.compareTo(other.className);
    }
}
