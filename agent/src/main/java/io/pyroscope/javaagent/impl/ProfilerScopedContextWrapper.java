package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ProfilerScopedContext;
import io.pyroscope.labels.v2.ScopedContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class ProfilerScopedContextWrapper implements ProfilerScopedContext {
    private final ScopedContext ctx;

    public ProfilerScopedContextWrapper(@NotNull ScopedContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void forEachLabel(@NotNull BiConsumer<@NotNull String, @NotNull String> consumer) {
        ctx.forEachLabel(consumer);
    }

    @Override
    public void close() {
        ctx.close();
    }
}
