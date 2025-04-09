package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
    Snapshot dumpProfile(@NotNull Instant profilingStartTime, @NotNull Instant now);

    void setConfig(@NotNull Config config);
}
