package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Profiler;

public interface ProfilingScheduler {
    void start(Profiler profiler);
}
