package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface ProfilerApi {
    void startProfiling();

    boolean isProfilingStarted();

    @Deprecated
    @NotNull ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels);

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);
}
