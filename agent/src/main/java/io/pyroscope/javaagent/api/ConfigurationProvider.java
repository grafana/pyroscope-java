package io.pyroscope.javaagent.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConfigurationProvider {
    @Nullable
    String get(@NotNull String key);
}
