package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public interface ProfilerDelegate {

    void start();

    void stop();

    @NotNull
    Snapshot dumpProfile(@NotNull Instant profilingStartTime, @NotNull Instant now);

    void setConfig(@NotNull Config config);
}
