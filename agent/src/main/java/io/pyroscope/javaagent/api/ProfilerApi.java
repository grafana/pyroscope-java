package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface ProfilerApi {
    void startProfiling();
    boolean isProfilingStarted();
    @NotNull ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels);
}
