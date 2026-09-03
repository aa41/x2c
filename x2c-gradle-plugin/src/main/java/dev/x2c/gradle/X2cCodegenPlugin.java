package dev.x2c.gradle;

import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.LibraryVariant;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Android library integration shared by AGP 3.5 through 8.x.
 *
 * <p>The old Variant API is intentionally used here because its
 * {@code registerJavaGeneratingTask} contract is the only public API spanning the complete
 * supported range. Both AGP 3.5 and AGP 8.x add the folder to Java compilation, attach the task
 * dependency, and expose it as generated source in the Android Studio model.</p>
 */
public final class X2cCodegenPlugin implements Plugin<Project> {
    private static final String ANDROID_LIBRARY_PLUGIN = "com.android.library";

    @Override
    public void apply(final Project project) {
        final X2cExtension extension = project.getExtensions().create("x2c", X2cExtension.class);
        extension.getPluginMode().convention(true);
        extension.getMinApi().convention(21);

        project.getPluginManager().withPlugin(ANDROID_LIBRARY_PLUGIN, new Action<org.gradle.api.plugins.AppliedPlugin>() {
            @Override
            public void execute(org.gradle.api.plugins.AppliedPlugin ignored) {
                configureAndroidLibrary(project, extension);
            }
        });
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project ignored) {
                if (!project.getPluginManager().hasPlugin(ANDROID_LIBRARY_PLUGIN)) {
                    throw new GradleException("dev.x2c.codegen must be applied to a com.android.library project");
                }
            }
        });
    }

    private static void configureAndroidLibrary(final Project project, final X2cExtension extension) {
        final LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);

        android.getLibraryVariants().all(new Action<LibraryVariant>() {
            @Override
            public void execute(LibraryVariant variant) {
                registerVariant(project, android, extension, variant);
            }
        });
    }

    private static void registerVariant(
            final Project project,
            final LibraryExtension android,
            final X2cExtension extension,
            final LibraryVariant variant) {
        final String capitalized = capitalize(variant.getName());
        final String generateTaskName = "x2cGenerate" + capitalized;
        final File generatedJavaDirectory = new File(
                project.getBuildDir(), "generated/java/" + generateTaskName);

        final TaskProvider<GenerateX2cTask> generate = project.getTasks().register(
                generateTaskName,
                GenerateX2cTask.class,
                new Action<GenerateX2cTask>() {
                    @Override
                    public void execute(GenerateX2cTask task) {
                        task.setGroup("x2c");
                        task.setDescription("Compiles Android resources into Java source for " + variant.getName());
                        task.getResourceDirectories().from(resourceDirectories(variant));
                        task.getManifestFiles().from(manifestFiles(variant));
                        if (extension.getGeneratedPackage().isPresent()) {
                            task.getGeneratedPackage().set(extension.getGeneratedPackage());
                        } else {
                            task.getGeneratedPackage().set(defaultGeneratedPackage(android, variant));
                        }
                        task.getModuleNamespace().set(androidNamespace(android, variant));
                        task.getVariantName().set(variant.getName());
                        task.getMinApi().set(extension.getMinApi());
                        task.getPluginMode().set(extension.getPluginMode());
                        task.getOutputDirectory().set(generatedJavaDirectory);
                        task.getReportDirectory().set(new File(
                                project.getBuildDir(), "reports/x2c/" + variant.getName()));
                        if (extension.getAssetLockFile().isPresent()) {
                            task.getAssetLockFile().set(extension.getAssetLockFile());
                        }
                        if (extension.getCustomViewsFile().isPresent()) {
                            task.getCustomViewsFile().set(extension.getCustomViewsFile());
                        }
                    }
                });

        // Public compatibility contract present from AGP 3.5 through 8.x. It performs all three
        // required operations: compile source registration, builtBy wiring, and IDE model export.
        variant.registerJavaGeneratingTask(generate.get(), generatedJavaDirectory);
        // Keep an explicit Javac edge as well. Some IDE/AGP combinations expose the generated
        // folder in the model but lose the compile source edge after variant/model resync.
        // JavaCompile.source + dependsOn are public Gradle APIs across the supported range.
        variant.getJavaCompileProvider().configure(new Action<JavaCompile>() {
            @Override
            public void execute(JavaCompile javaCompile) {
                javaCompile.dependsOn(generate);
                javaCompile.source(generatedJavaDirectory);
            }
        });

        final TaskProvider<PackageCodegenJarTask> jar = project.getTasks().register(
                "x2c" + capitalized + "Jar",
                PackageCodegenJarTask.class,
                new Action<PackageCodegenJarTask>() {
                    @Override
                    public void execute(PackageCodegenJarTask task) {
                        task.setGroup("x2c");
                        task.setDescription("Creates a deterministic class-only JAR for " + variant.getName());
                        task.getPluginMode().set(extension.getPluginMode());
                        task.dependsOn(variant.getJavaCompileProvider());
                        task.getInputDirectories().from(project.provider(new java.util.concurrent.Callable<File>() {
                            @Override
                            public File call() {
                                return javaCompileOutput(variant.getJavaCompileProvider().get());
                            }
                        }));
                        task.getOutputJar().set(new File(
                                project.getBuildDir(), "outputs/x2c/" + variant.getName() + "/codegen.jar"));
                    }
                });

        includeOptionalJvmCompilerOutputs(project, variant, jar);

        project.getTasks().register(
                "x2c" + capitalized + "DexJar",
                DexJarTask.class,
                new Action<DexJarTask>() {
                    @Override
                    public void execute(DexJarTask task) {
                        task.setGroup("x2c");
                        task.setDescription("Runs D8 and packages classes.dex for DexClassLoader ("
                                + variant.getName() + ")");
                        task.getInputJar().set(jar.flatMap(PackageCodegenJarTask::getOutputJar));
                        File d8Executable = DexJarTask.findD8(android.getSdkDirectory());
                        task.getD8Executable().set(d8Executable);
                        task.getD8ImplementationJar().set(new File(
                                d8Executable.getParentFile(), "lib/d8.jar"));
                        task.getBootClasspath().from(android.getBootClasspath());
                        task.getMinApi().set(extension.getMinApi());
                        task.getOutputJar().set(new File(
                                project.getBuildDir(), "outputs/x2c/" + variant.getName() + "/codegen-dex.jar"));
                    }
                });
    }

    private static void includeOptionalJvmCompilerOutputs(
            final Project project,
            LibraryVariant variant,
            final TaskProvider<PackageCodegenJarTask> jar) {
        final Set<String> compilerTaskNames = new LinkedHashSet<String>();
        String capitalized = capitalize(variant.getName());
        compilerTaskNames.add("compile" + capitalized + "Kotlin");
        compilerTaskNames.add("compile" + capitalized + "Scala");

        project.getTasks().configureEach(new Action<Task>() {
            @Override
            public void execute(final Task candidate) {
                if (!compilerTaskNames.contains(candidate.getName())) {
                    return;
                }
                jar.configure(new Action<PackageCodegenJarTask>() {
                    @Override
                    public void execute(PackageCodegenJarTask packageTask) {
                        packageTask.dependsOn(candidate);
                        packageTask.getInputDirectories().from(candidate.getOutputs().getFiles());
                    }
                });
            }
        });
    }

    private static Collection<File> resourceDirectories(LibraryVariant variant) {
        Set<File> directories = new LinkedHashSet<File>();
        for (SourceProvider sourceProvider : variant.getSourceSets()) {
            directories.addAll(sourceProvider.getResDirectories());
        }
        return directories;
    }

    private static Collection<File> manifestFiles(LibraryVariant variant) {
        List<File> manifests = new ArrayList<File>();
        for (SourceProvider sourceProvider : variant.getSourceSets()) {
            File manifest = sourceProvider.getManifestFile();
            if (manifest != null) {
                manifests.add(manifest);
            }
        }
        return manifests;
    }

    private static String defaultGeneratedPackage(LibraryExtension android, LibraryVariant variant) {
        return androidNamespace(android, variant) + ".x2c";
    }

    private static String androidNamespace(LibraryExtension android, LibraryVariant variant) {
        String namespace = invokeStringGetter(android, "getNamespace");
        if (namespace == null || namespace.trim().isEmpty()) {
            namespace = variant.getApplicationId();
        }
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new GradleException(
                    "Cannot determine the generated package for variant " + variant.getName()
                            + ". Configure x2c.generatedPackage explicitly (required on older AGP when the manifest has no package).");
        }
        return namespace;
    }

    private static String invokeStringGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException error) {
            throw new GradleException("Cannot call Android Gradle Plugin method " + methodName, error);
        } catch (InvocationTargetException error) {
            throw new GradleException("Android Gradle Plugin method " + methodName + " failed", error.getCause());
        }
    }

    private static File javaCompileOutput(JavaCompile javaCompile) {
        try {
            Method modern = javaCompile.getClass().getMethod("getDestinationDirectory");
            Object directoryProperty = modern.invoke(javaCompile);
            Method get = directoryProperty.getClass().getMethod("get");
            Object directory = get.invoke(directoryProperty);
            Method asFile = directory.getClass().getMethod("getAsFile");
            return (File) asFile.invoke(directory);
        } catch (NoSuchMethodException ignored) {
            try {
                Method legacy = javaCompile.getClass().getMethod("getDestinationDir");
                return (File) legacy.invoke(javaCompile);
            } catch (ReflectiveOperationException error) {
                throw new GradleException("Cannot locate JavaCompile output directory", error);
            }
        } catch (ReflectiveOperationException error) {
            throw new GradleException("Cannot locate JavaCompile output directory", error);
        }
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
