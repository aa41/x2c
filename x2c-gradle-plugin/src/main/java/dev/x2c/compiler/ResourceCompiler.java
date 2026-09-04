package dev.x2c.compiler;

import dev.x2c.compiler.resource.ResourceCompilationEngine;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Stable public entry point for the resource-to-class compiler. */
public final class ResourceCompiler {
    private final ResourceCompilationEngine engine = new ResourceCompilationEngine();

    /**
     * Compiles resources and emits an auto-discovery bootstrap below the Android namespace.
     * Business code never needs to reference generated names.
     */
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
        engine.compile(resourceRoots, generatedPackage, moduleNamespace, assetLockFile,
                customViewsFile, minApi, pluginMode, sourceOutput, reportOutput);
    }
}
