package dev.x2c.gradle;

import dev.x2c.compiler.ResourceCompiler;
import dev.x2c.compiler.ManifestVerifier;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
}
