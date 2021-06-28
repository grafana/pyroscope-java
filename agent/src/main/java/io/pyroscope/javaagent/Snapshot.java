package io.pyroscope.javaagent;

import java.time.Instant;

final class Snapshot {
    public final Instant started;
    public final Instant finished;
    public final String data;

    Snapshot(final Instant started, final Instant finished, final String data) {
        this.started = started;
        this.finished = finished;
        this.data = data;
    }
}
