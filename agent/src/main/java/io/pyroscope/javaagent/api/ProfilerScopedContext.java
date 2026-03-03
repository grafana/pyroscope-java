package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public interface ProfilerScopedContext {
    void forEachLabel(@NotNull BiConsumer<@NotNull String, @NotNull String> consumer);
    void close();
}
