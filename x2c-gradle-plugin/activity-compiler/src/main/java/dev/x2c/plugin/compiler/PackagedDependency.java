package dev.x2c.plugin.compiler;

/** One resolved runtime artifact whose classes were copied into the plugin-private payload. */
public final class PackagedDependency implements Comparable<PackagedDependency> {
    public final String artifactName;
    public final String artifactType;
    public final int classCount;
    public final String classesSha256;

    PackagedDependency(
            String artifactName, String artifactType, int classCount, String classesSha256) {
        this.artifactName = artifactName;
        this.artifactType = artifactType;
        this.classCount = classCount;
        this.classesSha256 = classesSha256;
    }

    @Override public int compareTo(PackagedDependency other) {
        int byName = artifactName.compareTo(other.artifactName);
        if (byName != 0) return byName;
        int byType = artifactType.compareTo(other.artifactType);
        if (byType != 0) return byType;
        return classesSha256.compareTo(other.classesSha256);
    }
}
