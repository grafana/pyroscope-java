package io.pyroscope.javaagent;

import io.pyroscope.labels.pb.*;

import java.time.Instant;

final class Snapshot {
    public final EventType eventType;
    public final Instant started;
    public final byte[] data;
    public final JfrLabels.Snapshot labels;

    Snapshot(final EventType eventType, final Instant started, final byte[] data, JfrLabels.Snapshot labels) {
        this.eventType = eventType;
        this.started = started;
        this.data = data;
        this.labels = labels;
    }
}
