package dev.x2c.plugin.compiler;

import java.util.Collections;
import java.util.List;

public final class ActivityTransformResult {
    public final String pluginId;
    public final String registryClassName;
    public final List<TransformedActivity> activities;
    public final List<TransformedComponent> services;
    public final List<TransformedComponent> receivers;
    public final List<TransformedComponent> providers;
    public final List<PackagedDependency> dependencies;
    public final String dependencyClosureSha256;

    ActivityTransformResult(
            String pluginId,
            String registryClassName,
            List<TransformedActivity> activities,
            List<TransformedComponent> services,
            List<TransformedComponent> receivers,
            List<TransformedComponent> providers,
            List<PackagedDependency> dependencies,
            String dependencyClosureSha256) {
        this.pluginId = pluginId;
        this.registryClassName = registryClassName;
        this.activities = Collections.unmodifiableList(activities);
        this.services = Collections.unmodifiableList(services);
        this.receivers = Collections.unmodifiableList(receivers);
        this.providers = Collections.unmodifiableList(providers);
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.dependencyClosureSha256 = dependencyClosureSha256;
    }
}
