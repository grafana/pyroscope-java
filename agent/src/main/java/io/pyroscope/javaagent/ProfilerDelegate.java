package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;

import java.time.Instant;

public interface ProfilerDelegate {
    void start();

    void stop();

    Snapshot dumpProfile(Instant profilingStartTime, Instant now);

    void setConfig(Config config);
}
