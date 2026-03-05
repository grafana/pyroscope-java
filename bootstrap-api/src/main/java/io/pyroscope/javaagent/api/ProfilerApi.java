package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Shared profiling interface injected into the bootstrap classloader by the OTel extension.
 * This interface MUST be kept in sync with the copy in pyroscope-java
 * (same package, same method signatures) — the JVM must consider them the same type across
 * classloaders for the cross-classloader cast to work. Do not modify without updating both repos.
 *
 * <p>Any modification to this interface is a <b>breaking change</b> that requires a major release
 * of both pyroscope-java and otel-profiling-java, with release notes documenting the
 * incompatibility between old and new versions.
 */
public interface ProfilerApi {
    void startProfiling();

    boolean isProfilingStarted();

    @Deprecated
    @NotNull ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels);

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);
}
