package dev.x2c.gradle;

import dev.x2c.compiler.ClassContractVerifier;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class PackageCodegenJarTask extends DefaultTask {
    private static final ClassContractVerifier CLASS_CONTRACT_VERIFIER = new ClassContractVerifier();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputJars();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputDirectories();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Input
    public abstract Property<Boolean> getPluginMode();

    @TaskAction
    public void packageJar() throws IOException {
        Map<String, byte[]> classes = new TreeMap<>();

        for (File directory : getInputDirectories().getFiles()) {
            Path root = directory.toPath();
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .sorted()
                        .forEach(path -> putUnique(
                                classes,
                                root.relativize(path).toString().replace(File.separatorChar, '/'),
                                read(path),
                                getPluginMode().get()));
            }
        }

        for (File inputJar : getInputJars().getFiles()) {
            try (JarFile jar = new JarFile(inputJar)) {
                jar.stream()
                        .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                        .sorted((left, right) -> left.getName().compareTo(right.getName()))
                        .forEach(entry -> {
                            try (InputStream input = new BufferedInputStream(jar.getInputStream(entry))) {
                                putUnique(classes, entry.getName(), read(input), getPluginMode().get());
                            } catch (IOException error) {
                                throw new JarReadException(error);
                            }
                        });
            } catch (JarReadException error) {
                throw error.cause;
            }
        }

        File output = getOutputJar().get().getAsFile();
        Files.createDirectories(output.toPath().getParent());
        try (JarOutputStream jar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            for (Map.Entry<String, byte[]> item : classes.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                entry.setTime(0L);
                jar.putNextEntry(entry);
                jar.write(item.getValue());
                jar.closeEntry();
            }
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            throw new JarReadException(error);
        }
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void putUnique(
            Map<String, byte[]> classes, String name, byte[] bytes, boolean pluginMode) {
        if (name.matches("(?:.*/)?R(?:\\$.*)?\\.class")) {
            // AGP 3.x compiles generated R.java into the same javac destination as user classes.
            // Newer AGP versions keep it in a separate artifact. Filtering here gives both paths
            // the same strict class-only output; references to R are still rejected below when
            // verifying every retained class.
            return;
        }
        try {
            CLASS_CONTRACT_VERIFIER.verify(name, bytes, !pluginMode);
        } catch (RuntimeException error) {
            throw new GradleException("Class violates the strict resource-free JAR contract: "
                    + error.getMessage(), error);
        }
        byte[] previous = classes.putIfAbsent(name, bytes);
        if (previous != null) {
            throw new GradleException("Duplicate class while creating codegen JAR: " + name);
        }
    }

    private static final class JarReadException extends RuntimeException {
        private final IOException cause;

        private JarReadException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
