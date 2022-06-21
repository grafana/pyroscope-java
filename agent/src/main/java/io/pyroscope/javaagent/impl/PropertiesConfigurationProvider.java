package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesConfigurationProvider implements ConfigurationProvider {
    final Properties properties;

    public PropertiesConfigurationProvider(Properties properties) {
        this.properties = properties;
    }

    public PropertiesConfigurationProvider(InputStream source) throws IOException {
        this.properties = new Properties();
        this.properties.load(source);
    }

    @Override
    public String get(String key) {
        return properties.getProperty(key);
    }
}
