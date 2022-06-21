package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;

public class EnvConfigurationProvider implements ConfigurationProvider {
    @Override
    public String get(String key) {
        return System.getenv(key);
    }
}
