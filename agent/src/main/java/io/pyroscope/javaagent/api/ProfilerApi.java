package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Shared profiling interface for cross-classloader communication between the Pyroscope agent
 * and the OTel extension. The bootstrap-api jar is injected into the bootstrap classloader
 * by the OTel extension at startup, making this interface visible to both classloaders.
 *
 * <p>Any modification to this interface is a <b>breaking change</b> that requires a coordinated
 * release of both pyroscope-java and otel-profiling-java.
 */
public interface ProfilerApi {
    void startProfiling();

    boolean isProfilingStarted();

    @Deprecated
    @NotNull ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels);

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);

    /**
     * Attaches a {@code trace_id} label to samples produced by the current thread.
     * Thread local; pair with {@link #clearTraceId()}.
     */
    // Empty default body so a newer caller against an older impl degrades to no label
    // rather than NoSuchMethodError. Real impl is in ProfilerSdk.
    default void setTraceId(@NotNull String traceId) {}

    default void clearTraceId() {}
}
