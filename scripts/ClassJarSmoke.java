import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

final class ClassJarSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected path to codegen.jar");
        }
        URL jar = Path.of(args[0]).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar}, ClassLoader.getPlatformClassLoader())) {
            Class<?> strings = Class.forName("dev.x2c.fixture.generated.X2cValues$Strings", true, loader);
            Object value = strings.getField("library_name").get(null);
            if (!"X2C JAR Fixture".equals(value)) {
                throw new AssertionError("Unexpected generated string: " + value);
            }
            Class<?> ids = Class.forName("dev.x2c.fixture.generated.R2$id", true, loader);
            int syntheticId = ids.getField("matrix_action").getInt(null);
            if (syntheticId == 0 || syntheticId > 0x00ffffff) {
                throw new AssertionError("Invalid synthetic R2.id value: " + syntheticId);
            }
            Class<?> layouts = Class.forName("dev.x2c.fixture.generated.R2$layout", true, loader);
            int contentLayout = layouts.getField("content").getInt(null);
            int matrixLayout = layouts.getField("framework_matrix").getInt(null);
            if ((contentLayout & 0xff000000) != 0x7e000000
                    || (matrixLayout & 0xff000000) != 0x7e000000
                    || contentLayout == matrixLayout) {
                throw new AssertionError("Invalid synthetic R2.layout values");
            }
            assertR2Prefix(loader, "string", "library_name", 0x71000000);
            assertR2Prefix(loader, "color", "primary", 0x72000000);
            assertR2Prefix(loader, "drawable", "login_card", 0x73000000);
            assertR2Prefix(loader, "dimen", "space_page", 0x74000000);
            assertR2Prefix(loader, "bool", "feature_enabled", 0x75000000);
            assertR2Prefix(loader, "integer", "max_items", 0x76000000);
            assertR2Prefix(loader, "array", "login_providers", 0x77000000);
            assertR2Prefix(loader, "plurals", "matrix_cases", 0x78000000);
            assertR2Prefix(loader, "fraction", "card_width", 0x79000000);
            try {
                Class.forName("dev.x2c.fixture.generated.X2cIds", false, loader);
                throw new AssertionError("X2cIds must not duplicate the canonical R2 namespace");
            } catch (ClassNotFoundException expected) {
                // R2 is the only generated synthetic resource namespace.
            }
            Class<?> stringArrays = Class.forName(
                    "dev.x2c.fixture.generated.X2cValues$StringArrays", true, loader);
            String[] providers = (String[]) stringArrays.getMethod("login_providers").invoke(null);
            if (providers.length != 3 || !"微信".equals(providers[0]) || !"Passkey".equals(providers[2])) {
                throw new AssertionError("Generated string-array failed");
            }
            Class<?> fractions = Class.forName(
                    "dev.x2c.fixture.generated.X2cValues$Fractions", true, loader);
            float width = (float) fractions.getMethod("card_width", float.class, float.class)
                    .invoke(null, 100f, 200f);
            if (Math.abs(width - 92f) > 0.001f) {
                throw new AssertionError("Generated fraction failed: " + width);
            }
            Class<?> probe = Class.forName("dev.x2c.fixture.producer.JvmFeatureProbe", true, loader);
            Object probeResult = probe.getMethod("runAll").invoke(null);
            if (!(probeResult instanceof String result) || !result.startsWith("PASS checksum=175")) {
                throw new AssertionError("JVM feature probe failed: " + probeResult);
            }
            Class<?> login = Class.forName("dev.x2c.fixture.producer.LoginLogic", true, loader);
            Object loginResult = login.getMethod("runSelfTests").invoke(null);
            if (!"PASS login-logic cases=6".equals(loginResult)) {
                throw new AssertionError("Login logic probe failed: " + loginResult);
            }
        }
        System.out.println("Class JAR R2 ids/layouts, values, JVM feature, and login logic smoke tests passed");
    }

    private static void assertR2Prefix(
            ClassLoader loader, String type, String field, int expectedPrefix) throws Exception {
        Class<?> symbols = Class.forName("dev.x2c.fixture.generated.R2$" + type, true, loader);
        int value = symbols.getField(field).getInt(null);
        if ((value & 0xff000000) != expectedPrefix) {
            throw new AssertionError("Invalid synthetic R2." + type + "." + field + ": " + value);
        }
    }
}
