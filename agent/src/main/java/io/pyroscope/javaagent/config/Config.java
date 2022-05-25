package io.pyroscope.javaagent.config;

import io.pyroscope.http.Format;
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
    private static final String PYROSCOPE_PROFILER_ALLOC_CONFIG = "PYROSCOPE_PROFILER_ALLOC";
    private static final String PYROSCOPE_PROFILER_LOCK_CONFIG = "PYROSCOPE_PROFILER_LOCK";
    private static final String PYROSCOPE_UPLOAD_INTERVAL_CONFIG = "PYROSCOPE_UPLOAD_INTERVAL";
    private static final String PYROSCOPE_LOG_LEVEL_CONFIG = "PYROSCOPE_LOG_LEVEL";
    private static final String PYROSCOPE_SERVER_ADDRESS_CONFIG = "PYROSCOPE_SERVER_ADDRESS";
    private static final String PYROSCOPE_ADHOC_SERVER_ADDRESS_CONFIG = "PYROSCOPE_ADHOC_SERVER_ADDRESS";
    private static final String PYROSCOPE_AUTH_TOKEN_CONFIG = "PYROSCOPE_AUTH_TOKEN";
    private static final String PYROSCOPE_FORMAT_CONFIG = "PYROSCOPE_FORMAT";

    private static final String DEFAULT_SPY_NAME = "javaspy";
    private static final Duration DEFAULT_PROFILING_INTERVAL = Duration.ofMillis(10);
    private static final EventType DEFAULT_PROFILER_EVENT = EventType.ITIMER;
    private static final String DEFAULT_PROFILER_ALLOC = "";
    private static final String DEFAULT_PROFILER_LOCK = "";
    private static final Duration DEFAULT_UPLOAD_INTERVAL = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVER_ADDRESS = "http://localhost:4040";
    private static final Format DEFAULT_FORMAT = Format.COLLAPSED;

    public final String spyName = DEFAULT_SPY_NAME;
    public final String applicationName;
    public final Duration profilingInterval;
    public final EventType profilingEvent;
    public final String profilingAlloc;
    public final String profilingLock;
    public final Duration uploadInterval;
    public final Level logLevel;
    public final String serverAddress;
    public final String authToken;
    public final String timeseriesName;
    public final Format format;

    Config(final String applicationName,
           final Duration profilingInterval,
           final EventType profilingEvent,
           final String profilingAlloc,
           final String profilingLock,
           final Duration uploadInterval,
           final Level logLevel,
           final String serverAddress,
           final String authToken,
           final Format format
        ) {
        this.applicationName = applicationName;
        this.profilingInterval = profilingInterval;
        this.profilingEvent = profilingEvent;
        this.profilingAlloc = profilingAlloc;
        this.profilingLock = profilingLock;
        this.uploadInterval = uploadInterval;
        this.logLevel = logLevel;
        this.serverAddress = serverAddress;
        this.authToken = authToken;
        this.timeseriesName = timeseriesName(applicationName, profilingEvent, format);
        this.format = format;
    }

    public long profilingIntervalInHertz() {
        return durationToHertz(this.profilingInterval);
    }

    private static long durationToHertz(Duration duration) {
        Duration oneSecond = Duration.ofSeconds(1);
        return oneSecond.toNanos() / duration.toNanos();
    }

    public static Config build() {
        return new Config(
            applicationName(),
            profilingInterval(),
            profilingEvent(),
            profilingAlloc(),
            profilingLock(),
            uploadInterval(),
            logLevel(),
            serverAddress(),
            authToken(),
            format()
        );
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

    private String timeseriesName(String applicationName, EventType eventType, Format format) {
        if (format == Format.JFR)
            return applicationName;
        return applicationName + "." + eventType.id;
    }

    private static EventType profilingEvent() {
        final String profilingEventStr =
            System.getenv(PYROSCOPE_PROFILER_EVENT_CONFIG);
        if (profilingEventStr == null || profilingEventStr.isEmpty()) {
            return DEFAULT_PROFILER_EVENT;
        }

        final String lowerCaseTrimmed = profilingEventStr.trim().toLowerCase();

        try {
            return EventType.fromId(lowerCaseTrimmed);
        } catch (IllegalArgumentException e) {
            PreConfigLogger.LOGGER.warn("Invalid {} value {}, using {}",
                    PYROSCOPE_PROFILER_EVENT_CONFIG, profilingEventStr, DEFAULT_PROFILER_EVENT.id);
            return DEFAULT_PROFILER_EVENT;
        }
    }

    private static String profilingAlloc() {
        final String profilingAlloc = System.getenv(PYROSCOPE_PROFILER_ALLOC_CONFIG);
        if (profilingAlloc == null || profilingAlloc.isEmpty()) {
            return DEFAULT_PROFILER_ALLOC;
        }
        return profilingAlloc.trim().toLowerCase();
    }

    private static String profilingLock() {
        final String profilingLock = System.getenv(PYROSCOPE_PROFILER_LOCK_CONFIG);
        if (profilingLock == null || profilingLock.isEmpty()) {
            return DEFAULT_PROFILER_LOCK;
        }
        return profilingLock.trim().toLowerCase();
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
        String serverAddress = System.getenv(PYROSCOPE_ADHOC_SERVER_ADDRESS_CONFIG);
        if (serverAddress == null || serverAddress.isEmpty()) {
            serverAddress = System.getenv(PYROSCOPE_SERVER_ADDRESS_CONFIG);
        }
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

    private static Format format() {
        final String format = System.getenv(PYROSCOPE_FORMAT_CONFIG);
        if (format == null || format.isEmpty())
            return DEFAULT_FORMAT;
        switch (format.trim().toLowerCase()) {
            case "collapsed":
                return Format.COLLAPSED;
            case "jfr":
                return Format.JFR;
            default:
                PreConfigLogger.LOGGER.warn("Unknown format {}, using {}", format, DEFAULT_FORMAT);
                return DEFAULT_FORMAT;
        }
    }
}
