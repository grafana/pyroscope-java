package io.pyroscope.javaagent;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.util.Properties;

/**
 * The logger to use before the normal logger settings (like the log level) are read from the configuration
 * and the normal logger is configured.
 */
public final class PreConfigLogger {
    public static final Logger LOGGER = new SimpleLogger(
            "PyroscopeAgent", Level.INFO,
            false, true, true, false,
            "yyyy-MM-dd HH:mm:ss.SSS",
            ParameterizedNoReferenceMessageFactory.INSTANCE,
            new PropertiesUtil(new Properties()),
            System.err);
}
