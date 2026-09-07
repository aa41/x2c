package dev.x2c.fixture.businessbase.analytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Host-owned lifecycle/analytics facade shared by every native and plugin Activity.
 *
 * <p>The transformed business base references this public ABI, but the implementation and its
 * event model stay in the application ClassLoader. This mirrors a real analytics/account/network
 * bridge whose static state must not be duplicated per plugin.</p>
 */
public final class BusinessLifecycleAnalytics {
    private final int activitySequence;
    private final List<BusinessEvent> events = new ArrayList<>();

    public BusinessLifecycleAnalytics(int activitySequence) {
        this.activitySequence = activitySequence;
        track("analytics.init");
    }

    public synchronized void track(String eventName) {
        events.add(new BusinessEvent(events.size() + 1, eventName));
    }

    public synchronized boolean containsInOrder(String... expected) {
        int expectedIndex = 0;
        for (BusinessEvent event : events) {
            if (expectedIndex < expected.length
                    && expected[expectedIndex].equals(event.name())) {
                expectedIndex++;
            }
        }
        return expectedIndex == expected.length;
    }

    public synchronized String summary() {
        return "analytics.activity#=" + activitySequence
                + " · events=" + events.size()
                + "\ntrace=" + BusinessEventFormatter.format(events);
    }
}
