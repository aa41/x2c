/**
 * Internal resource-to-class compiler pipeline.
 *
 * <p>Dependency direction is intentionally one-way:
 * scanner/parsers -&gt; shared model -&gt; source generators. Parsers never emit Java and generators
 * never read XML. {@link dev.x2c.compiler.resource.ResourceCompilationEngine} only coordinates
 * these stages; the supported framework View matrix lives exclusively in
 * {@code FrameworkViewRegistry}.
 *
 * <p>This package is implementation detail. Gradle integrations should use
 * {@link dev.x2c.compiler.ResourceCompiler}.
 */
package dev.x2c.compiler.resource;
