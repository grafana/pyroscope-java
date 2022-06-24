package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;

import java.util.Map;

public class EnvConfigurationProvider implements ConfigurationProvider {

    private final Map<String, String> env;

    public EnvConfigurationProvider() {
        env = System.getenv();
    }

    @Override
    public String get(String key) {
        return env.get(key);
    }
}
