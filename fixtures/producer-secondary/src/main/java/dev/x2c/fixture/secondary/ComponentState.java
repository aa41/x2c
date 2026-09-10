package dev.x2c.fixture.secondary;

/** Process-local state displayed by the component showcase UI. */
final class ComponentState {
    static volatile String service = "Service: waiting";
    static volatile String receiver = "Receiver: waiting";
    static volatile String provider = "Provider: ready";

    private ComponentState() {}

    static void verifyHostApplication(android.content.Context context, Class<?> anchor) {
        if ((android.app.Application) context.getApplicationContext()
                != dev.x2c.runtime.X2C.hostApplication()) {
            throw new AssertionError("Component does not share the real host Application");
        }
        android.util.Log.i("X2cHostContract", anchor.getSimpleName()
                + " Application=PASS plugin=" + dev.x2c.runtime.X2C.isPlugin(anchor));
    }
}
