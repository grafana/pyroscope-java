package io.pyroscope.javaagent.api;

import java.util.Map;

public interface ProfilerApi {
    void startProfiling();
    boolean isProfilingStarted();
    ProfilerScopedContext createScopedContext(Map<String, String> labels);
}
