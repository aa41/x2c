package dev.x2c.gradle;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class DexJarTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getBootClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getD8Executable();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getD8ImplementationJar();

    @Input
    public abstract Property<Integer> getMinApi();

    @Input
    public abstract Property<Boolean> getPluginMode();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void dex() throws IOException {
        if (!getPluginMode().get()) {
            throw new GradleException(
                    "x2c<Variant>DexJar requires x2c.pluginMode=true. "
                            + "Normal integration mode must publish and consume the AAR.");
        }
        Path work = getTemporaryDir().toPath().resolve("d8-output");
        if (Files.exists(work)) {
            try (Stream<Path> paths = Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException error) {
                        throw new GradleException("Cannot clean D8 work directory", error);
                    }
                });
            }
        }
        Files.createDirectories(work);

        File d8 = getD8Executable().get().getAsFile();
        List<String> arguments = new ArrayList<String>();
        if (isWindows()) {
            arguments.add("cmd.exe");
            arguments.add("/c");
        }
        arguments.add(d8.getAbsolutePath());
        arguments.add("--release");
        arguments.add("--min-api");
        arguments.add(String.valueOf(getMinApi().get()));
        for (File bootFile : getBootClasspath().getFiles()) {
            arguments.add("--lib");
            arguments.add(bootFile.getAbsolutePath());
        }
        arguments.add("--output");
        arguments.add(work.toAbsolutePath().toString());
        arguments.add(getInputJar().get().getAsFile().getAbsolutePath());
        runD8(arguments);

        Path dex = work.resolve("classes.dex");
        if (!Files.isRegularFile(dex)) {
            throw new GradleException("D8 did not produce classes.dex");
        }
        File output = getOutputJar().get().getAsFile();
        Files.createDirectories(output.toPath().getParent());
        try (JarOutputStream jar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            JarEntry entry = new JarEntry("classes.dex");
            entry.setTime(0L);
            jar.putNextEntry(entry);
            Files.copy(dex, jar);
            jar.closeEntry();
        }
    }

    private static void runD8(List<String> arguments) throws IOException {
        Process process = new ProcessBuilder(arguments).inheritIO().start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("D8 failed with exit code " + exitCode);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while waiting for D8", error);
        }
    }

    public static File findD8(File sdkDirectory) {
        File buildTools = new File(sdkDirectory, "build-tools");
        File[] versions = buildTools.listFiles(File::isDirectory);
        if (versions == null) {
            throw new GradleException("Android SDK has no build-tools directory: " + sdkDirectory);
        }
        java.util.Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
        for (File version : versions) {
            File executable = new File(version, isWindows() ? "d8.bat" : "d8");
            File implementation = new File(version, "lib/d8.jar");
            if (executable.isFile() && isCompatibleWithCurrentJava(implementation)) {
                return executable;
            }
        }
        throw new GradleException(
                "Cannot find a D8 installation compatible with Java "
                        + System.getProperty("java.specification.version")
                        + " in Android SDK: " + sdkDirectory);
    }

    private static boolean isCompatibleWithCurrentJava(File implementation) {
        if (!implementation.isFile()) {
            return false;
        }
        try (JarFile jar = new JarFile(implementation)) {
            JarEntry d8Class = jar.getJarEntry("com/android/tools/r8/D8.class");
            if (d8Class == null) {
                return false;
            }
            try (InputStream input = jar.getInputStream(d8Class);
                    DataInputStream data = new DataInputStream(input)) {
                if (data.readInt() != 0xCAFEBABE) {
                    return false;
                }
                data.readUnsignedShort();
                int requiredClassVersion = data.readUnsignedShort();
                int currentClassVersion = (int) Double.parseDouble(
                        System.getProperty("java.class.version"));
                return requiredClassVersion <= currentClassVersion;
            }
        } catch (IOException | NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
