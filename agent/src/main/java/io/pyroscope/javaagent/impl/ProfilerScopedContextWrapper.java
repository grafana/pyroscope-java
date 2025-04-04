package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ProfilerScopedContext;
import io.pyroscope.labels.v2.ScopedContext;

import java.util.function.BiConsumer;

public class ProfilerScopedContextWrapper implements ProfilerScopedContext {
    private final ScopedContext ctx;

    public ProfilerScopedContextWrapper(ScopedContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void forEachLabel(BiConsumer<String, String> consumer) {
        ctx.forEachLabel(consumer);
    }

    @Override
    public void close() {
        ctx.close();
    }
}
