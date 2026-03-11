package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * Injected into the bootstrap classloader by the OTel extension at startup.
 * This interface MUST be kept in sync with the copy in pyroscope-java (same package, same
 * method signatures) — do not modify without updating both repos.
 *
 * <p>Any modification to this interface is a <b>breaking change</b> that requires a major release
 * of both pyroscope-java and otel-profiling-java, with release notes documenting the
 * incompatibility between old and new versions.
 */
public interface ProfilerScopedContext {
    void forEachLabel(@NotNull BiConsumer<@NotNull String, @NotNull String> consumer);
    void close();
}
