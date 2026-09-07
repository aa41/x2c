package dev.x2c.plugin.gradle;

import dev.x2c.plugin.compiler.ActivityJarTransformer;
import dev.x2c.plugin.compiler.ActivityTransformResult;
import dev.x2c.plugin.compiler.TransformedActivity;
import dev.x2c.plugin.compiler.TransformedComponent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class TransformPluginActivitiesTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    /** Resolved runtime artifacts copied into and transformed as plugin-private classes. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDependencyArtifacts();

    /** Compile-only classpath candidates; only marked Activity base closures are copied. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getCompileOnlyArtifacts();

    /** Android/Java resources, assets and native payloads are forbidden in phase one. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getForbiddenDependencyPayloads();

    @Input
    public abstract Property<String> getPluginId();

    @Input
    public abstract Property<Boolean> getPluginMode();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void transform() throws IOException {
        if (!getPluginMode().get()) {
            throw new GradleException(
                    "Android component transformation requires x2c.pluginMode=true. "
                            + "pluginMode=false is a normal AAR and must use framework components.");
        }
        validateNoDependencyPayloads(getForbiddenDependencyPayloads().getFiles());
        ActivityTransformResult result = new ActivityJarTransformer().transform(
                getInputJar().get().getAsFile(),
                getDependencyArtifacts().getFiles(),
                getCompileOnlyArtifacts().getFiles(),
                getOutputJar().get().getAsFile(),
                getPluginId().get());
        File report = getReportFile().get().getAsFile();
        Files.createDirectories(report.toPath().getParent());
        Files.write(report.toPath(), report(result).getBytes(StandardCharsets.UTF_8));
    }

    private static void validateNoDependencyPayloads(java.util.Collection<File> artifacts)
            throws IOException {
        List<File> sorted = new ArrayList<File>(artifacts);
        Collections.sort(sorted, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return left.getAbsolutePath().compareTo(right.getAbsolutePath());
            }
        });
        for (File artifact : sorted) {
            if (artifact.isDirectory()) {
                try (Stream<Path> paths = Files.walk(artifact.toPath())) {
                    Path payload = paths.filter(Files::isRegularFile).sorted().findFirst().orElse(null);
                    if (payload != null) {
                        throw forbiddenPayload(artifact, artifact.toPath().relativize(payload).toString());
                    }
                }
                continue;
            }
            if (!artifact.isFile() || artifact.length() == 0L) continue;
            try (JarFile jar = new JarFile(artifact)) {
                java.util.Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && !"META-INF/MANIFEST.MF".equals(entry.getName())) {
                        throw forbiddenPayload(artifact, entry.getName());
                    }
                }
            } catch (java.util.zip.ZipException notZip) {
                throw forbiddenPayload(artifact, artifact.getName());
            }
        }
    }

    private static GradleException forbiddenPayload(File artifact, String entry) {
        return new GradleException(
                "First-stage plugin dependency closure is class-only; Android resources, "
                        + "Java resources, assets and native libraries are rejected: "
                        + artifact.getName() + " -> " + entry);
    }

    private static String report(ActivityTransformResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"pluginId\": \"").append(escape(result.pluginId)).append("\",\n")
                .append("  \"runtimeAbiVersion\": ")
                .append(ActivityJarTransformer.CURRENT_RUNTIME_ABI).append(",\n")
                .append("  \"dependencyClosureSha256\": \"")
                .append(result.dependencyClosureSha256).append("\",\n")
                .append("  \"registryClass\": \"").append(result.registryClassName).append("\",\n")
                .append("  \"activities\": [\n");
        for (int index = 0; index < result.activities.size(); index++) {
            TransformedActivity activity = result.activities.get(index);
            json.append("    {\"className\": \"").append(escape(activity.className))
                    .append("\", \"launchMode\": \"").append(activity.launchMode).append("\"}");
            if (index + 1 < result.activities.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ],\n");
        appendComponents(json, "services", result.services, false);
        json.append(",\n");
        appendComponents(json, "receivers", result.receivers, false);
        json.append(",\n");
        appendComponents(json, "providers", result.providers, true);
        json.append(",\n  \"dependencyClosure\": [\n");
        for (int index = 0; index < result.dependencies.size(); index++) {
            dev.x2c.plugin.compiler.PackagedDependency dependency =
                    result.dependencies.get(index);
            json.append("    {\"artifact\": \"")
                    .append(escape(dependency.artifactName))
                    .append("\", \"type\": \"")
                    .append(dependency.artifactType)
                    .append("\", \"classes\": ")
                    .append(dependency.classCount)
                    .append(", \"classesSha256\": \"")
                    .append(dependency.classesSha256).append("\"}");
            if (index + 1 < result.dependencies.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ],\n")
                .append("  \"ownership\": {\n")
                .append("    \"dependencyClasses\": \"explicit-plugin-private\",\n")
                .append("    \"compileOnlyReferences\": \"host-fallback\",\n")
                .append("    \"platformAndX2cRuntime\": \"parent-first\",\n")
                .append("    \"androidxAndResourceAars\": \"rejected-in-phase-1\"\n")
                .append("  }\n");
        return json.append("}\n").toString();
    }

    private static void appendComponents(
            StringBuilder json,
            String label,
            java.util.List<TransformedComponent> components,
            boolean includeAuthority) {
        json.append("  \"").append(label).append("\": [\n");
        for (int index = 0; index < components.size(); index++) {
            TransformedComponent component = components.get(index);
            json.append("    {\"className\": \"")
                    .append(escape(component.className)).append('"');
            if (includeAuthority) {
                json.append(", \"authority\": \"")
                        .append(escape(component.authority)).append('"');
            }
            json.append('}');
            if (index + 1 < components.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ]");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
