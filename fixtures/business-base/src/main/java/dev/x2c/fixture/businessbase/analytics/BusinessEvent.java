package dev.x2c.fixture.businessbase.analytics;

/** Immutable business event deliberately kept independent from Android resources. */
public final class BusinessEvent {
    private final int sequence;
    private final String name;

    BusinessEvent(int sequence, String name) {
        if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("event name must not be empty");
        }
        this.sequence = sequence;
        this.name = name;
    }

    int sequence() {
        return sequence;
    }

    String name() {
        return name;
    }
}
