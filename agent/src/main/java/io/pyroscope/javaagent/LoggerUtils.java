package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.ConfigurationProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Properties;


public final class LoggerUtils {
    private static final String PYROSCOPE_LOG_LEVEL_CONFIG = "PYROSCOPE_LOG_LEVEL";
    /**
     * The logger to use before the normal logger settings (like the log level) are read from the configuration
     * and the normal logger is configured.
     */
    public static final Logger PRECONFIG_LOGGER = createLogger(Level.INFO);

    @NotNull
    public static Logger createDefaultPyroscopeLogger(Level logLevel) {
        return createLogger(logLevel);
    }

    @NotNull
    private static SimpleLogger createLogger(Level logLevel) {
        return new SimpleLogger(
            "PyroscopeAgent", logLevel,
            false, true, true, false,
            "yyyy-MM-dd HH:mm:ss.SSS",
            ParameterizedNoReferenceMessageFactory.INSTANCE,
            new PropertiesUtil(new Properties()),
            System.err);
    }
}
