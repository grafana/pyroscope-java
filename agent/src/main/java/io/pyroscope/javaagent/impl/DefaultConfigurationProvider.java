package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DefaultConfigurationProvider implements ConfigurationProvider {
    public static final String PYROSCOPE_PROPERTIES = "pyroscope.properties";
    final ConfigurationProvider delegate;

    public DefaultConfigurationProvider() {
        ConfigurationProvider d = null;
        try {
            d = new PropertiesConfigurationProvider(
                Files.newInputStream(Paths.get(PYROSCOPE_PROPERTIES))
            );
        } catch (IOException ignored) {
        }
        if (d == null) {
            try {
                d = new PropertiesConfigurationProvider(
                    this.getClass().getResourceAsStream(PYROSCOPE_PROPERTIES)
                );
            } catch (IOException ignored) {
            }
        }
        if (d == null) {
            d = new EnvConfigurationProvider();
        }
        this.delegate = d;
    }



    @Override
    public String get(String key) {
        return delegate.get(key);
    }
}
