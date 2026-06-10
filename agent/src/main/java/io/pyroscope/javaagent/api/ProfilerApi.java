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
    // Default methods so an older ProfilerApi implementation can run against a newer ProfilerApi
    // injected into the bootstrap classloader (no label rather than AbstractMethodError).
    // Note: this does not help if the runtime ProfilerApi itself is older and lacks this method.
    default void setTraceId(@NotNull String traceId) {}

    default void clearTraceId() {}
}
