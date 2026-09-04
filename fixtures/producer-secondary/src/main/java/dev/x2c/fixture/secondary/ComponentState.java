package dev.x2c.fixture.secondary;

/** Process-local state displayed by the component showcase UI. */
final class ComponentState {
    static volatile String service = "Service: waiting";
    static volatile String receiver = "Receiver: waiting";
    static volatile String provider = "Provider: ready";

    private ComponentState() {}
}
