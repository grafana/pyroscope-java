package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * Companion interface for scoped label management.
 *
 * <p>Any modification to this interface is a <b>breaking change</b> that requires a coordinated
 * release of both pyroscope-java and otel-profiling-java.
 */
public interface ProfilerScopedContext {
    void forEachLabel(@NotNull BiConsumer<@NotNull String, @NotNull String> consumer);
    void close();
}
