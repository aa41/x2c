import com.android.builder.model.v2.ide.AndroidArtifact;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import java.io.File;
import java.util.Collection;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;

/** Confirms the generated folder exported to the same Gradle model consumed by Android Studio. */
public final class AndroidGeneratedSourceModelSmoke {
    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <fixture-dir> <agp-version> <gradle-java-home>");
        }
        File projectDirectory = new File(args[0]);
        File javaHome = new File(args[2]);
        String expectedSuffix = "build/generated/java/x2cGenerateRelease";

        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(projectDirectory);
        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<AndroidProject> builder = connection.model(AndroidProject.class)
                    .withArguments("-PagpVersion=" + args[1], "--offline", "--no-configuration-cache")
                    .setJavaHome(javaHome);
            AndroidProject project = builder.get();
            Variant release = null;
            for (Variant variant : project.getVariants()) {
                if ("release".equals(variant.getName())) {
                    release = variant;
                    break;
                }
            }
            if (release == null) {
                throw new AssertionError("Android model has no release variant");
            }
            AndroidArtifact artifact = release.getMainArtifact();
            Collection<File> generated = artifact.getGeneratedSourceFolders();
            for (File directory : generated) {
                String normalized = directory.getAbsolutePath().replace(File.separatorChar, '/');
                if (normalized.endsWith(expectedSuffix)) {
                    System.out.println("Android Studio model contains generated source: " + directory);
                    return;
                }
            }
            throw new AssertionError("Android Studio model is missing " + expectedSuffix + ": " + generated);
        }
    }
}
