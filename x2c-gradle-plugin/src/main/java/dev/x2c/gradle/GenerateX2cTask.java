package dev.x2c.gradle;

import dev.x2c.compiler.ResourceCompiler;
import dev.x2c.compiler.ManifestVerifier;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class GenerateX2cTask extends DefaultTask {
    @Input
    public abstract Property<Boolean> getX2cEnable();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceDirectories();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getManifestFiles();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAssetLockFile();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCustomViewsFile();

    @Input
    public abstract Property<String> getGeneratedPackage();

    /** Android library namespace used as the stable business-code discovery root. */
    @Input
    public abstract Property<String> getModuleNamespace();

    @Input
    public abstract Property<String> getVariantName();

    @Input
    public abstract Property<Integer> getMinApi();

    @Input
    public abstract Property<Boolean> getPluginMode();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getReportDirectory();

    @TaskAction
    public void generate() throws Exception {
        if (!getX2cEnable().get()) {
            if (getPluginMode().get()) {
                throw new org.gradle.api.GradleException(
                        "x2cEnable=false requires pluginMode=false: a resource-free plugin JAR cannot load Android XML resources.");
            }
            writeSystemResourcesReport();
            return;
        }
        new ManifestVerifier().verify(getManifestFiles().getFiles(), getPluginMode().get());
        List<File> roots = new ArrayList<>(getResourceDirectories().getFiles());
        roots.sort(Comparator.comparing(File::getAbsolutePath));
        File lock = getAssetLockFile().isPresent() ? getAssetLockFile().get().getAsFile() : null;
        File customViews = getCustomViewsFile().isPresent() ? getCustomViewsFile().get().getAsFile() : null;
        new ResourceCompiler().compile(
                roots,
                getGeneratedPackage().get(),
                getModuleNamespace().get(),
                lock,
                customViews,
                getMinApi().get(),
                getPluginMode().get(),
                getOutputDirectory().get().getAsFile(),
                getReportDirectory().get().getAsFile());
    }

    private void writeSystemResourcesReport() throws IOException {
        Path sources = getOutputDirectory().get().getAsFile().toPath();
        Path reports = getReportDirectory().get().getAsFile().toPath();
        resetDirectory(sources);
        Files.createDirectories(reports);
        Files.deleteIfExists(reports.resolve("assets-candidates.json"));
        String report = "{\n"
                + "  \"schema\": 1,\n"
                + "  \"mode\": \"SYSTEM_RESOURCES\",\n"
                + "  \"variant\": \"" + json(getVariantName().get()) + "\",\n"
                + "  \"generatedPackage\": \"" + json(getGeneratedPackage().get()) + "\",\n"
                + "  \"x2cEnabled\": false,\n"
                + "  \"generatedSources\": 0\n"
                + "}\n";
        Files.write(
                reports.resolve("report.json"),
                report.getBytes(StandardCharsets.UTF_8));
    }

    private static void resetDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                Path[] ordered = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path path : ordered) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
