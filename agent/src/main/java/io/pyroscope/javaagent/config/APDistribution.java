package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.impl.DefaultLogger;

/**
 * Selects which bundled async-profiler native library the agent loads.
 */
public enum APDistribution {
    /**
     * The Grafana fork of async-profiler (default). Required for dynamic labels
     * and tracing context integration.
     */
    FORK,
    /**
     * The genuine (upstream) async-profiler. Dynamic labels and tracing context
     * integration are not available with this distribution.
     */
    GENUINE;

    public static APDistribution parse(String value, APDistribution defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        switch (value.trim().toLowerCase()) {
            case "fork":
                return FORK;
            case "genuine":
                return GENUINE;
            default:
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN,
                    "Unknown async-profiler distribution %s, using %s", value, defaultValue);
                return defaultValue;
        }
    }
}
