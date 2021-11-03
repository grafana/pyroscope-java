package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PreConfigLogger;
import org.apache.logging.log4j.Level;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class Config {
    private static final String PYROSCOPE_APPLICATION_NAME_CONFIG = "PYROSCOPE_APPLICATION_NAME";
    private static final String PYROSCOPE_PROFILING_INTERVAL_CONFIG = "PYROSCOPE_PROFILING_INTERVAL";
    private static final String PYROSCOPE_PROFILER_EVENT_CONFIG = "PYROSCOPE_PROFILER_EVENT";
    private static final String PYROSCOPE_UPLOAD_INTERVAL_CONFIG = "PYROSCOPE_UPLOAD_INTERVAL";
    private static final String PYROSCOPE_LOG_LEVEL_CONFIG = "PYROSCOPE_LOG_LEVEL";
    private static final String PYROSCOPE_SERVER_ADDRESS_CONFIG = "PYROSCOPE_SERVER_ADDRESS";
    private static final String PYROSCOPE_AUTH_TOKEN_CONFIG = "PYROSCOPE_AUTH_TOKEN";

    private static final String DEFAULT_SPY_NAME = "javaspy";
    private static final Duration DEFAULT_PROFILING_INTERVAL = Duration.ofMillis(10);
    private static final EventType DEFAULT_PROFILER_EVENT = EventType.ITIMER;
    private static final Duration DEFAULT_UPLOAD_INTERVAL = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVER_ADDRESS = "http://localhost:4040";

    public final String spyName = DEFAULT_SPY_NAME;
    public final String applicationName;
    public final Duration profilingInterval;
    public final EventType profilingEvent;
    public final Duration uploadInterval;
    public final Level logLevel;
    public final String serverAddress;
    public final String authToken;

    Config(final String applicationName,
           final Duration profilingInterval,
           final EventType profilingEvent,
           final Duration uploadInterval,
           final Level logLevel,
           final String serverAddress,
           final String authToken) {
        this.applicationName = applicationName;
        this.profilingInterval = profilingInterval;
        this.profilingEvent = profilingEvent;
        this.uploadInterval = uploadInterval;
        this.logLevel = logLevel;
        this.serverAddress = serverAddress;
        this.authToken = authToken;
    }

    public long profilingIntervalInHertz() {
        return durationToHertz(this.profilingInterval);
    }

    private static long durationToHertz(Duration duration) {
        Duration oneSecond = Duration.ofSeconds(1);
        return oneSecond.toNanos() / duration.toNanos();
    }

    public static Config build() {
        return new Config(applicationName(), profilingInterval(),
                profilingEvent(), uploadInterval(), logLevel(), serverAddress(), authToken());
    }

    private static String applicationName() {
        String applicationName = System.getenv(PYROSCOPE_APPLICATION_NAME_CONFIG);
        if (applicationName == null || applicationName.isEmpty()) {
            PreConfigLogger.LOGGER.info("We recommend specifying application name via env variable {}",
                    PYROSCOPE_APPLICATION_NAME_CONFIG);
            // TODO transfer name generation algorithm from the Go implementation.

            final UUID uuid = UUID.randomUUID();
            final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
            byteBuffer.putLong(uuid.getMostSignificantBits());
            byteBuffer.putLong(uuid.getLeastSignificantBits());
            final String random = Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
            applicationName = DEFAULT_SPY_NAME + "." +  random;

            PreConfigLogger.LOGGER.info("For now we chose the name for you and it's {}", applicationName);
        }
        return applicationName;
    }

    private static Duration profilingInterval() {
        final String profilingIntervalStr = System.getenv(PYROSCOPE_PROFILING_INTERVAL_CONFIG);
        if (profilingIntervalStr == null || profilingIntervalStr.isEmpty()) {
            return DEFAULT_PROFILING_INTERVAL;
        }
        try {
            return IntervalParser.parse(profilingIntervalStr);
        } catch (final NumberFormatException e) {
            PreConfigLogger.LOGGER.warn("Invalid {} value {}, using {}ms",
                    PYROSCOPE_PROFILING_INTERVAL_CONFIG, profilingIntervalStr, DEFAULT_PROFILING_INTERVAL.toMillis());
            return DEFAULT_PROFILING_INTERVAL;
        }
    }

    private static EventType profilingEvent() {
        final String profilingEventStr = System.getenv(PYROSCOPE_PROFILER_EVENT_CONFIG);
        if (profilingEventStr == null || profilingEventStr.isEmpty()) {
            return DEFAULT_PROFILER_EVENT;
        }

        try {
            return EventType.parse(profilingEventStr);
        } catch (IllegalArgumentException e) {
            PreConfigLogger.LOGGER.warn("Invalid {} value {}, using {}",
                    PYROSCOPE_PROFILER_EVENT_CONFIG, profilingEventStr, DEFAULT_PROFILER_EVENT);
            return DEFAULT_PROFILER_EVENT;
        }
    }

    private static Duration uploadInterval() {
        final String uploadIntervalStr = System.getenv(PYROSCOPE_UPLOAD_INTERVAL_CONFIG);
        if (uploadIntervalStr == null || uploadIntervalStr.isEmpty()) {
            return DEFAULT_UPLOAD_INTERVAL;
        }
        try {
            return IntervalParser.parse(uploadIntervalStr);
        } catch (final NumberFormatException e) {
            PreConfigLogger.LOGGER.warn("Invalid {} value {}, using {}",
                    PYROSCOPE_UPLOAD_INTERVAL_CONFIG, uploadIntervalStr, DEFAULT_UPLOAD_INTERVAL);
            return DEFAULT_UPLOAD_INTERVAL;
        }
   }

    private static Level logLevel() {
        final String logLevel = System.getenv(PYROSCOPE_LOG_LEVEL_CONFIG);
        if (logLevel == null || logLevel.isEmpty()) {
            return Level.INFO;
        }
        switch (logLevel.toLowerCase(Locale.ROOT)) {
            case "debug":
                return Level.DEBUG;
            case "info":
                return Level.INFO;
            case "warn":
                return Level.WARN;
            case "error":
                return Level.ERROR;
            default:
                PreConfigLogger.LOGGER.warn("Unknown log level {}, using INFO", logLevel);
                return Level.INFO;
        }
    }

    private static String serverAddress() {
        String serverAddress = System.getenv(PYROSCOPE_SERVER_ADDRESS_CONFIG);
        if (serverAddress == null || serverAddress.isEmpty()) {
            PreConfigLogger.LOGGER.warn("{} is not defined, using {}",
                    PYROSCOPE_SERVER_ADDRESS_CONFIG, DEFAULT_SERVER_ADDRESS);
            serverAddress = DEFAULT_SERVER_ADDRESS;

        }
        return serverAddress;
    }

    private static String authToken() {
        return System.getenv(PYROSCOPE_AUTH_TOKEN_CONFIG);
    }
}
