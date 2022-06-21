package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Snapshot;

public interface Exporter {
    void export(Snapshot snapshot);
}
