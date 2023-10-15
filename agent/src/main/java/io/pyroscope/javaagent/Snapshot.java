package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Snapshot {
    public final Format format;
    public final EventType eventType;
    public final Instant started;
    public final Instant ended;
    public final byte[] data;
    public final JfrLabels.Snapshot labels;
}
