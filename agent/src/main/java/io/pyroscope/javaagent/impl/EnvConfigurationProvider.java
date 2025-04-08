package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class EnvConfigurationProvider implements ConfigurationProvider {

    private final Map<String, String> env;

    public EnvConfigurationProvider() {
        env = System.getenv();
    }

    @Override
    @Nullable
    public String get(@NotNull String key) {
        return env.get(key);
    }
}
