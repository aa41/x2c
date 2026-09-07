package dev.x2c.fixture.businessbase.analytics;

import java.util.List;

/** Formatting policy reached transitively through {@link BusinessLifecycleAnalytics}. */
final class BusinessEventFormatter {
    private BusinessEventFormatter() {}

    static String format(List<BusinessEvent> events) {
        StringBuilder value = new StringBuilder();
        for (BusinessEvent event : events) {
            if (value.length() > 0) value.append(" > ");
            value.append(event.sequence()).append(':').append(event.name());
        }
        return value.toString();
    }
}
