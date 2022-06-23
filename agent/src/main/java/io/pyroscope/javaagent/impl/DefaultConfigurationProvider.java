package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.ConfigurationProvider;
import io.pyroscope.javaagent.api.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegates configuration provision to multiple sources
 * - System.getProperties
 * - System.getenv
 * - pyroscope.properties configuration file
 * pyroscope.properties file can be overridden by PYROSCOPE_CONFIGURATION_FILE_CONFIG
 */
public class DefaultConfigurationProvider implements ConfigurationProvider {
    private static final String PYROSCOPE_CONFIGURATION_FILE_CONFIG = "PYROSCOPE_CONFIGURATION_FILE";
    private static final String DEFAULT_CONFIGURATION_FILE = "pyroscope.properties";

    public static final DefaultConfigurationProvider INSTANCE = new DefaultConfigurationProvider();

    final List<ConfigurationProvider> delegates = new ArrayList<>();

    public DefaultConfigurationProvider() {
        delegates.add(new PropertiesConfigurationProvider(System.getProperties()));
        delegates.add(new EnvConfigurationProvider());
        String configFile = getPropertiesFile();
        try {
            delegates.add(new PropertiesConfigurationProvider(
                Files.newInputStream(Paths.get(configFile))
            ));
        } catch (IOException ignored) {
        }
        try {
            delegates.add(new PropertiesConfigurationProvider(
                this.getClass().getResourceAsStream(configFile)
            ));
        } catch (IOException ignored) {
        }
        if (!configFile.equals(DEFAULT_CONFIGURATION_FILE) && delegates.size() == 2) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "%s configuration file was specified but was not found", configFile);
        }
    }

    @Override
    public String get(String key) {
        for (int i = 0; i < delegates.size(); i++) {
            String v = delegates.get(i).get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private String getPropertiesFile() {
        String f = get(PYROSCOPE_CONFIGURATION_FILE_CONFIG);
        if (f == null) {
            return DEFAULT_CONFIGURATION_FILE;
        }
        return f;
    }

}
