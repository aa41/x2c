package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.ColorSourceGenerator.generateColorStateLists;
import static dev.x2c.compiler.resource.DrawableSourceGenerator.generateDrawables;
import static dev.x2c.compiler.resource.IdSourceGenerator.generateR2;
import static dev.x2c.compiler.resource.ImageSourceGenerator.generateImages;
import static dev.x2c.compiler.resource.LayoutSourceGenerator.generateLayouts;
import static dev.x2c.compiler.resource.ModuleBootstrapSourceGenerator.generateBootstrap;
import static dev.x2c.compiler.resource.ResourceProviderSourceGenerator.generateResourceProvider;
import static dev.x2c.compiler.resource.FileSupport.write;
import static dev.x2c.compiler.resource.ValuesSourceGenerator.generateValues;
import static dev.x2c.compiler.resource.X2cModuleSourceGenerator.generateModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Coordinates the fixed generated-source set without owning any resource parsing. */
final class GeneratedSourceWriter {
    private GeneratedSourceWriter() {}

    static void writeGeneratedSources(
            Path output,
            String packageName,
            String moduleNamespace,
            Model model,
            Map<String, LockedAsset> assets,
            boolean pluginMode) throws IOException {
        Path packageDirectory = output.resolve(packageName.replace('.', File.separatorChar));
        Files.createDirectories(packageDirectory);
        write(packageDirectory.resolve("R2.java"), generateR2(packageName, model, pluginMode));
        if (hasValues(model)) {
            write(packageDirectory.resolve("X2cValues.java"), generateValues(packageName, model));
        }
        if (!model.colorSelectors.isEmpty()) {
            write(packageDirectory.resolve("X2cColorStateLists.java"), generateColorStateLists(packageName, model));
        }
        if (hasSynchronousDrawables(model)) {
            write(packageDirectory.resolve("X2cDrawables.java"),
                    generateDrawables(packageName, model, pluginMode));
        }
        if (!assets.isEmpty()) {
            write(packageDirectory.resolve("X2cImages.java"), generateImages(packageName, assets));
        }
        if (!model.layouts.isEmpty()) {
            write(packageDirectory.resolve("X2cLayouts.java"), generateLayouts(packageName, model, pluginMode));
        }
        write(packageDirectory.resolve("X2cResourceProviderImpl.java"),
                generateResourceProvider(packageName, model, pluginMode));
        write(packageDirectory.resolve("X2cModule.java"),
                generateModule(packageName, model, pluginMode, !assets.isEmpty()));

        String bootstrapPackage = moduleNamespace + ".x2c";
        Path bootstrapDirectory = output.resolve(bootstrapPackage.replace('.', File.separatorChar));
        Files.createDirectories(bootstrapDirectory);
        write(bootstrapDirectory.resolve(ModuleBootstrapSourceGenerator.CLASS_NAME + ".java"),
                generateBootstrap(bootstrapPackage, packageName));
    }

    private static boolean hasValues(Model model) {
        return !model.strings.isEmpty()
                || !model.colors.isEmpty()
                || !model.bools.isEmpty()
                || !model.integers.isEmpty()
                || !model.dimens.isEmpty()
                || !model.fractions.isEmpty()
                || !model.stringArrays.isEmpty()
                || !model.integerArrays.isEmpty()
                || !model.typedArrays.isEmpty()
                || !model.plurals.isEmpty();
    }

    private static boolean hasSynchronousDrawables(Model model) {
        return !model.shapes.isEmpty()
                || !model.selectors.isEmpty()
                || !model.layerLists.isEmpty()
                || !model.singleDrawables.isEmpty()
                || !model.levelLists.isEmpty();
    }
}
