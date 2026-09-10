package dev.x2c.plugin.gradle;

import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.LibraryVariant;
import dev.x2c.gradle.DexJarTask;
import dev.x2c.gradle.X2cExtension;
import java.io.File;
import java.util.Locale;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;

/** Adds an Android component transformation stage between the class-only X2C JAR and D8. */
public final class X2cActivityPlugin implements Plugin<Project> {
    @Override public void apply(final Project project) {
        project.getPluginManager().withPlugin("com.android.library", ignored ->
                project.getPluginManager().withPlugin("dev.x2c.codegen", ignoredX2c ->
                        configure(project)));
        project.afterEvaluate(ignored -> {
            if (!project.getPluginManager().hasPlugin("com.android.library")
                    || !project.getPluginManager().hasPlugin("dev.x2c.codegen")) {
                throw new GradleException(
                        "dev.x2c.activity-plugin requires com.android.library and dev.x2c.codegen");
            }
            X2cExtension x2c = project.getExtensions().getByType(X2cExtension.class);
            if (x2c.getPluginMode().getOrElse(x2c.getX2cEnable().get())
                    && (!x2c.getPluginId().isPresent()
                    || x2c.getPluginId().get().trim().isEmpty())) {
                throw new GradleException("Configure x2c.pluginId");
            }
        });
    }

    private static void configure(final Project project) {
        LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);
        X2cExtension x2c = project.getExtensions().getByType(X2cExtension.class);
        android.getLibraryVariants().all(new Action<LibraryVariant>() {
            @Override public void execute(final LibraryVariant variant) {
                String capitalized = capitalize(variant.getName());
                String x2cJarTaskName = "x2c" + capitalized + "Jar";
                TaskProvider<TransformPluginActivitiesTask> transform = project.getTasks().register(
                        "x2cTransform" + capitalized + "Activities",
                        TransformPluginActivitiesTask.class,
                        task -> {
                            task.setGroup("x2c plugin");
                            task.setDescription(
                                    "Rewrites plugin components and generates their direct registry");
                            task.dependsOn(x2cJarTaskName);
                            // Keep the task input complete in normal mode so an accidental direct
                            // invocation reaches the explicit pluginMode guard instead of failing
                            // Gradle property validation with an unrelated missing-value message.
                            task.getPluginId().set(x2c.getPluginId());
                            task.getPluginMode().set(x2c.getPluginMode().orElse(x2c.getX2cEnable()));
                            // Runtime configuration is the complete transitive dependency closure
                            // of this Android variant. The task accepts JARs, class directories and
                            // resource-free AARs, then applies one transform to all retained classes.
                            FileCollection runtimeClasses = runtimeDependencyClasses(variant);
                            task.getDependencyArtifacts().from(runtimeClasses);
                            // Compile configuration also contains implementation dependencies.
                            // Subtracting runtime inputs keeps the common case small; selection is
                            // additionally class-name bounded, so AGP variants that expose distinct
                            // compile/runtime JAR paths still cannot duplicate packaged classes.
                            task.getCompileOnlyArtifacts().from(
                                    compileDependencyClasses(variant).minus(runtimeClasses));
                            task.getForbiddenDependencyPayloads().from(
                                    runtimeDependencyArtifact(variant, "android-res"),
                                    runtimeDependencyArtifact(variant, "android-assets"),
                                    runtimeDependencyArtifact(variant, "android-jni"),
                                    runtimeDependencyArtifact(variant, "android-java-res"));
                            task.getInputJar().set(new File(project.getBuildDir(),
                                    "outputs/x2c/" + variant.getName() + "/codegen.jar"));
                            task.getOutputJar().set(new File(project.getBuildDir(),
                                    "outputs/x2c/" + variant.getName() + "/activity-plugin.jar"));
                            task.getReportFile().set(new File(project.getBuildDir(),
                                    "reports/x2c/" + variant.getName() + "/activities.json"));
                        });

                project.getTasks().named(
                        "x2c" + capitalized + "DexJar", DexJarTask.class).configure(task -> {
                    task.dependsOn(transform);
                    task.getInputJar().set(transform.flatMap(
                            TransformPluginActivitiesTask::getOutputJar));
                });
            }
        });
    }

    /**
     * Requests AGP's stable classes artifact instead of resolving the multi-artifact Android
     * configuration as an untyped FileCollection. The "android-classes" transform exists in both
     * AGP 3.5 and 8.x and normalizes project AAR and external JAR dependencies to class inputs.
     */
    private static FileCollection runtimeDependencyClasses(LibraryVariant variant) {
        return runtimeDependencyArtifact(variant, "android-classes");
    }

    private static FileCollection compileDependencyClasses(LibraryVariant variant) {
        final Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        return variant.getCompileConfiguration().getIncoming().artifactView(view ->
                view.attributes(attributes ->
                        attributes.attribute(artifactType, "android-classes"))).getFiles();
    }

    private static FileCollection runtimeDependencyArtifact(
            LibraryVariant variant, String requestedType) {
        final Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        return variant.getRuntimeConfiguration().getIncoming().artifactView(view ->
                view.attributes(attributes ->
                        attributes.attribute(artifactType, requestedType))).getFiles();
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
