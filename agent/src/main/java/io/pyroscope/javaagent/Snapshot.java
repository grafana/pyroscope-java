package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.*;

import java.time.Instant;

public final class Snapshot {
    public final Format format;
    public final EventType eventType;
    public final Instant started;
    public final Instant ended;
    public final byte[] data;
    public final JfrLabels.Snapshot labels;

    Snapshot(Format format, final EventType eventType, final Instant started, final Instant ended,final byte[] data, JfrLabels.Snapshot labels) {
        this.format = format;
        this.eventType = eventType;
        this.started = started;
        this.ended = ended;
        this.data = data;
        this.labels = labels;
    }
}
