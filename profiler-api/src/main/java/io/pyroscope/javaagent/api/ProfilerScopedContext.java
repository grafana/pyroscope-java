package io.pyroscope.javaagent.api;

import java.util.function.BiConsumer;

public interface ProfilerScopedContext {
    void forEachLabel(BiConsumer<String, String> consumer);
    void close();
}
