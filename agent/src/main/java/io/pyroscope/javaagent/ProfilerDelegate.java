package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.config.ProfilerType;

import java.time.Instant;

public interface ProfilerDelegate {
    /**
     * Creates profiler delegate instance based on configuration.
     *
     * @param config
     * @return
     */
    static ProfilerDelegate create(Config config) {
        return config.profilerType.create(config);
    }

    void start();

    void stop();

    Snapshot dumpProfile(Instant profilingStartTime, Instant now);

    void setConfig(Config config);
}
