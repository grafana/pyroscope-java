package io.pyroscope.javaagent;

import java.time.Instant;

final class Snapshot {
    public final EventType eventType;
    public final Instant started;
    public final Instant finished;
    public final byte[] data;

    Snapshot(final EventType eventType, final Instant started, final Instant finished, final byte[] data) {
        this.eventType = eventType;
        this.started = started;
        this.finished = finished;
        this.data = data;
    }
}
