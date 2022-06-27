package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.*;

public final class Snapshot {
    public final Format format;
    public final EventType eventType;
    public final long started;
    public final byte[] data;
    public final JfrLabels.Snapshot labels;

    Snapshot(Format format, final EventType eventType, final long started, final byte[] data, JfrLabels.Snapshot labels) {
        this.format = format;
        this.eventType = eventType;
        this.started = started;
        this.data = data;
        this.labels = labels;
    }
}
