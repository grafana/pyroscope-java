package io.pyroscope.javaagent;

import io.pyroscope.labels.ContextsSnapshot;
import io.pyroscope.labels.Labels;

import java.time.Instant;

final class Snapshot {
    public final EventType eventType;
    public final Instant started;
    public final Instant finished;
    public final byte[] data;
    public final ContextsSnapshot labels;

    Snapshot(final EventType eventType, final Instant started, final Instant finished, final byte[] data, ContextsSnapshot labels) {
        this.eventType = eventType;
        this.started = started;
        this.finished = finished;
        this.data = data;
        this.labels = labels;
    }
}
