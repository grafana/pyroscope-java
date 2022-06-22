package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Delegates configuration provision to pyroscope.properties file
 * or to System.getenv if the file does not exist
 */
public class DefaultConfigurationProvider implements ConfigurationProvider {
    public static final DefaultConfigurationProvider INSTANCE = new DefaultConfigurationProvider();
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
