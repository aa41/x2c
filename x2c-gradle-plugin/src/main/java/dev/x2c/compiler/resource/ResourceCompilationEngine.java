package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.AssetLockVerifier.readAndVerifyAssetLock;
import static dev.x2c.compiler.resource.ColorResourceParser.parseColorStateList;
import static dev.x2c.compiler.resource.CompilationReportWriter.writeAssetCandidates;
import static dev.x2c.compiler.resource.CompilationReportWriter.writeReport;
import static dev.x2c.compiler.resource.DrawableResourceParser.parseBitmap;
import static dev.x2c.compiler.resource.DrawableResourceParser.parseDrawable;
import static dev.x2c.compiler.resource.DrawableResourceParser.validateDrawableGraph;
import static dev.x2c.compiler.resource.FileSupport.recreateDirectory;
import static dev.x2c.compiler.resource.GeneratedSourceWriter.writeGeneratedSources;
import static dev.x2c.compiler.resource.LayoutResourceParser.collectLayoutIdDeclarations;
import static dev.x2c.compiler.resource.LayoutResourceParser.parseLayout;
import static dev.x2c.compiler.resource.ResourceScanner.BITMAP_EXTENSIONS;
import static dev.x2c.compiler.resource.ResourceScanner.canonicalRoots;
import static dev.x2c.compiler.resource.ResourceScanner.discover;
import static dev.x2c.compiler.resource.SyntheticIdAllocator.assignSyntheticIds;
import static dev.x2c.compiler.resource.ValuesResourceParser.parseValues;
import static dev.x2c.compiler.resource.ValuesResourceParser.validateValueReferences;
import static dev.x2c.compiler.resource.XmlSupport.validatePackage;

import dev.x2c.compiler.ResourceCompilationException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Strict, deterministic pipeline for the explicitly supported Android resource subset.
 *
 * @apiNote Internal implementation; Gradle integrations use {@code ResourceCompiler}.
 */
public final class ResourceCompilationEngine {
    public void compile(
            List<File> resourceRoots,
            String generatedPackage,
            File assetLockFile,
            File sourceOutput,
            File reportOutput) throws IOException {
        compile(resourceRoots, generatedPackage, assetLockFile, null, sourceOutput, reportOutput);
    }

    public void compile(
            List<File> resourceRoots,
            String generatedPackage,
            File assetLockFile,
            File customViewsFile,
            File sourceOutput,
            File reportOutput) throws IOException {
        compile(resourceRoots, generatedPackage, assetLockFile, customViewsFile, 21, sourceOutput, reportOutput);
    }

    public void compile(
            List<File> resourceRoots,
            String generatedPackage,
            File assetLockFile,
            File customViewsFile,
            int minApi,
            File sourceOutput,
            File reportOutput) throws IOException {
        compile(resourceRoots, generatedPackage, assetLockFile, customViewsFile, minApi, true,
                sourceOutput, reportOutput);
    }

    public void compile(
            List<File> resourceRoots,
            String generatedPackage,
            File assetLockFile,
            File customViewsFile,
            int minApi,
            boolean pluginMode,
            File sourceOutput,
            File reportOutput) throws IOException {
        compile(resourceRoots, generatedPackage, inferredNamespace(generatedPackage), assetLockFile,
                customViewsFile, minApi, pluginMode, sourceOutput, reportOutput);
    }

    public void compile(
            List<File> resourceRoots,
            String generatedPackage,
            String moduleNamespace,
            File assetLockFile,
            File customViewsFile,
            int minApi,
            boolean pluginMode,
            File sourceOutput,
            File reportOutput) throws IOException {
        validatePackage(generatedPackage);
        validatePackage(moduleNamespace);
        if (minApi < 21) {
            throw new ResourceCompilationException("x2c.minApi must be at least 21");
        }
        recreateDirectory(sourceOutput.toPath());
        recreateDirectory(reportOutput.toPath());

        Model model = new Model(CustomViewRegistry.load(customViewsFile), minApi);
        List<File> uniqueRoots = canonicalRoots(resourceRoots);
        List<ResourceFile> files = discover(uniqueRoots);
        for (ResourceFile file : files) {
            if (file.kind.equals("values")) {
                parseValues(file, model);
            }
        }
        validateValueReferences(model);
        for (ResourceFile file : files) {
            if (file.kind.equals("color")) {
                parseColorStateList(file, model);
            }
        }
        for (ResourceFile file : files) {
            if (file.kind.equals("drawable") && file.extension.equals("xml")) {
                model.declaredDrawables.add(XmlSupport.resourceNameFromFile(file.file));
                parseDrawable(file, model);
            } else if (file.kind.equals("drawable") && BITMAP_EXTENSIONS.contains(file.extension)) {
                model.declaredDrawables.add(XmlSupport.resourceNameFromFile(file.file));
                parseBitmap(file, model);
            }
        }
        validateDrawableGraph(model);
        for (ResourceFile file : files) {
            if (file.kind.equals("layout")) {
                collectLayoutIdDeclarations(file, model);
            }
        }
        for (ResourceFile file : files) {
            if (file.kind.equals("layout")) {
                parseLayout(file, model);
            }
        }
        assignSyntheticIds(generatedPackage, model);

        if (pluginMode) {
            writeAssetCandidates(reportOutput.toPath(), model);
        }
        Map<String, LockedAsset> lockedAssets = pluginMode
                ? readAndVerifyAssetLock(assetLockFile, model.bitmaps)
                : java.util.Collections.emptyMap();
        writeGeneratedSources(sourceOutput.toPath(), generatedPackage, moduleNamespace, model,
                lockedAssets, pluginMode);
        writeReport(reportOutput.toPath(), generatedPackage, files, model, lockedAssets, pluginMode);
    }

    private static String inferredNamespace(String generatedPackage) {
        if (generatedPackage.endsWith(".generated")) {
            return generatedPackage.substring(0, generatedPackage.length() - ".generated".length());
        }
        if (generatedPackage.endsWith(".x2c")) {
            return generatedPackage.substring(0, generatedPackage.length() - ".x2c".length());
        }
        return generatedPackage;
    }


















}
