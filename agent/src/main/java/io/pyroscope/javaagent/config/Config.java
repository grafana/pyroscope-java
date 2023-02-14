package io.pyroscope.javaagent.config;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.api.ConfigurationProvider;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.impl.DefaultConfigurationProvider;
import io.pyroscope.javaagent.impl.DefaultLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.zip.Deflater;

/**
 * Config allows to tweak parameters of existing pyroscope components at start time
 * through pyroscope.properties file or System.getevn - see io.pyroscope.javaagent.impl.DefaultConfigurationProvider
 */
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
    private static final String PYROSCOPE_PUSH_QUEUE_CAPACITY_CONFIG = "PYROSCOPE_PUSH_QUEUE_CAPACITY";
    private static final String PYROSCOPE_LABELS = "PYROSCOPE_LABELS";

    private static final String PYROSCOPE_INGEST_MAX_TRIES = "PYROSCOPE_INGEST_MAX_TRIES";
    private static final String PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR = "PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR";
    private static final String PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS = "PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS";
    private static final String PYROSCOPE_ALLOC_LIVE = "PYROSCOPE_ALLOC_LIVE";
    private static final String PYROSCOPE_GC_BEFORE_DUMP = "PYROSCOPE_GC_BEFORE_DUMP";

    public static final String DEFAULT_SPY_NAME = "javaspy";
    private static final Duration DEFAULT_PROFILING_INTERVAL = Duration.ofMillis(10);
    private static final EventType DEFAULT_PROFILER_EVENT = EventType.ITIMER;
    private static final String DEFAULT_PROFILER_ALLOC = "";
    private static final String DEFAULT_PROFILER_LOCK = "";
    private static final Duration DEFAULT_UPLOAD_INTERVAL = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVER_ADDRESS = "http://localhost:4040";
    private static final Format DEFAULT_FORMAT = Format.COLLAPSED;
    // The number of snapshots simultaneously stored in memory is limited by this.
    // The number is fairly arbitrary. If an average snapshot is 5KB, it's about 160 KB.
    private static final int DEFAULT_PUSH_QUEUE_CAPACITY = 8;
    private static final int DEFAULT_INGEST_MAX_RETRIES = 8;
    private static final int DEFAULT_COMPRESSION_LEVEL = Deflater.BEST_SPEED;
    private static final String DEFAULT_LABELS = "";
    private static final boolean DEFAULT_ALLOC_LIVE = false;
    private static final boolean DEFAULT_GC_BEFORE_DUMP = false;

    public final String applicationName;
    public final Duration profilingInterval;
    public final EventType profilingEvent;
    public final String profilingAlloc;
    public final String profilingLock;
    public final Duration uploadInterval;
    public final Logger.Level logLevel;
    public final String serverAddress;
    public final String authToken;

    @Deprecated
    public final String timeseriesName;
    public final AppName timeseries;
    public final Format format;
    public final int pushQueueCapacity;
    public final Map<String, String> labels;
    public final int ingestMaxTries;
    public final int compressionLevelJFR;
    public final int compressionLevelLabels;

    public final boolean allocLive;
    public final boolean gcBeforeDump;

    Config(final String applicationName,
           final Duration profilingInterval,
           final EventType profilingEvent,
           final String profilingAlloc,
           final String profilingLock,
           final Duration uploadInterval,
           final Logger.Level logLevel,
           final String serverAddress,
           final String authToken,
           final Format format,
           final int pushQueueCapacity,
           final Map<String, String> labels,
           int ingestMaxRetries,
           int compressionLevelJFR,
           int compressionLevelLabels,
           boolean allocLive,
           boolean gcBeforeDump) {
        this.applicationName = applicationName;
        this.profilingInterval = profilingInterval;
        this.profilingEvent = profilingEvent;
        this.profilingAlloc = profilingAlloc;
        this.profilingLock = profilingLock;
        this.uploadInterval = uploadInterval;
        this.logLevel = logLevel;
        this.serverAddress = serverAddress;
        this.authToken = authToken;
        this.ingestMaxTries = ingestMaxRetries;
        this.compressionLevelJFR = validateCompressionLevel(compressionLevelJFR);
        this.compressionLevelLabels = validateCompressionLevel(compressionLevelLabels);
        this.allocLive = allocLive;
        this.gcBeforeDump = gcBeforeDump;
        this.timeseries = timeseriesName(AppName.parse(applicationName), profilingEvent, format);
        this.timeseriesName = timeseries.toString();
        this.format = format;
        this.pushQueueCapacity = pushQueueCapacity;
        this.labels = Collections.unmodifiableMap(labels);
    }

    public long profilingIntervalInHertz() {
        return durationToHertz(this.profilingInterval);
    }

    @Override
    public String toString() {
        return "Config{" +
            "applicationName='" + applicationName + '\'' +
            ", profilingInterval=" + profilingInterval +
            ", profilingEvent=" + profilingEvent +
            ", profilingAlloc='" + profilingAlloc + '\'' +
            ", profilingLock='" + profilingLock + '\'' +
            ", uploadInterval=" + uploadInterval +
            ", logLevel=" + logLevel +
            ", serverAddress='" + serverAddress + '\'' +
            ", authToken='" + authToken + '\'' +
            ", timeseriesName='" + timeseriesName + '\'' +
            ", timeseries=" + timeseries +
            ", format=" + format +
            ", pushQueueCapacity=" + pushQueueCapacity +
            ", labels=" + labels +
            ", ingestMaxTries=" + ingestMaxTries +
            ", compressionLevelJFR=" + compressionLevelJFR +
            ", compressionLevelLabels=" + compressionLevelLabels +
            ", allocLive=" + allocLive +
            '}';
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    private static long durationToHertz(Duration duration) {
        Duration oneSecond = Duration.ofSeconds(1);
        return oneSecond.toNanos() / duration.toNanos();
    }

    public static Config build() {
        return build(DefaultConfigurationProvider.INSTANCE);
    }

    public static Config build(ConfigurationProvider configurationProvider) {
        String alloc = profilingAlloc(configurationProvider);
        boolean allocLive = bool(configurationProvider, PYROSCOPE_ALLOC_LIVE, DEFAULT_ALLOC_LIVE);
        if (DEFAULT_PROFILER_ALLOC.equals(alloc) && allocLive) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "%s is ignored because %s is not configured",
                PYROSCOPE_ALLOC_LIVE, PYROSCOPE_PROFILER_ALLOC_CONFIG);
            allocLive = false;
        }
        return new Config(
            applicationName(configurationProvider),
            profilingInterval(configurationProvider),
            profilingEvent(configurationProvider),
            alloc,
            profilingLock(configurationProvider),
            uploadInterval(configurationProvider),
            logLevel(configurationProvider),
            serverAddress(configurationProvider),
            authToken(configurationProvider),
            format(configurationProvider),
            pushQueueCapacity(configurationProvider),
            labels(configurationProvider),
            ingestMaxRetries(configurationProvider),
            compressionLevel(configurationProvider, PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR),
            compressionLevel(configurationProvider, PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS),
            allocLive,
            bool(configurationProvider, PYROSCOPE_GC_BEFORE_DUMP, DEFAULT_GC_BEFORE_DUMP));
    }

    private static String applicationName(ConfigurationProvider configurationProvider) {
        String applicationName = configurationProvider.get(PYROSCOPE_APPLICATION_NAME_CONFIG);
        if (applicationName == null || applicationName.isEmpty()) {
            applicationName = generateApplicationName();
        }
        return applicationName;
    }

    @NotNull
    private static String generateApplicationName() {
        String applicationName;
        DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.INFO, "We recommend specifying application name via env variable %s",
            PYROSCOPE_APPLICATION_NAME_CONFIG);
        // TODO transfer name generation algorithm from the Go implementation.

        final UUID uuid = UUID.randomUUID();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        final String random = Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
        applicationName = DEFAULT_SPY_NAME + "." + random;

        DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.INFO, "For now we chose the name for you and it's %s", applicationName);
        return applicationName;
    }

    private static Duration profilingInterval(ConfigurationProvider configurationProvider) {
        final String profilingIntervalStr = configurationProvider.get(PYROSCOPE_PROFILING_INTERVAL_CONFIG);
        if (profilingIntervalStr == null || profilingIntervalStr.isEmpty()) {
            return DEFAULT_PROFILING_INTERVAL;
        }
        try {
            return IntervalParser.parse(profilingIntervalStr);
        } catch (final NumberFormatException e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, using %sms",
                PYROSCOPE_PROFILING_INTERVAL_CONFIG, profilingIntervalStr, DEFAULT_PROFILING_INTERVAL.toMillis());
            return DEFAULT_PROFILING_INTERVAL;
        }
    }

    private AppName timeseriesName(AppName app, EventType eventType, Format format) {
        if (format == Format.JFR)
            return app;
        return app.newBuilder()
            .setName(app.name + "." + eventType.id)
            .build();
    }

    private static EventType profilingEvent(ConfigurationProvider configurationProvider) {
        final String profilingEventStr =
            configurationProvider.get(PYROSCOPE_PROFILER_EVENT_CONFIG);
        if (profilingEventStr == null || profilingEventStr.isEmpty()) {
            return DEFAULT_PROFILER_EVENT;
        }

        final String lowerCaseTrimmed = profilingEventStr.trim().toLowerCase();

        try {
            return EventType.fromId(lowerCaseTrimmed);
        } catch (IllegalArgumentException e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, using %s",
                PYROSCOPE_PROFILER_EVENT_CONFIG, profilingEventStr, DEFAULT_PROFILER_EVENT.id);
            return DEFAULT_PROFILER_EVENT;
        }
    }

    private static String profilingAlloc(ConfigurationProvider configurationProvider) {
        final String profilingAlloc = configurationProvider.get(PYROSCOPE_PROFILER_ALLOC_CONFIG);
        if (profilingAlloc == null || profilingAlloc.isEmpty()) {
            return DEFAULT_PROFILER_ALLOC;
        }
        return profilingAlloc.trim().toLowerCase();
    }

    private static String profilingLock(ConfigurationProvider configurationProvider) {
        final String profilingLock = configurationProvider.get(PYROSCOPE_PROFILER_LOCK_CONFIG);
        if (profilingLock == null || profilingLock.isEmpty()) {
            return DEFAULT_PROFILER_LOCK;
        }
        return profilingLock.trim().toLowerCase();
    }

    private static Duration uploadInterval(ConfigurationProvider configurationProvider) {
        final String uploadIntervalStr = configurationProvider.get(PYROSCOPE_UPLOAD_INTERVAL_CONFIG);
        if (uploadIntervalStr == null || uploadIntervalStr.isEmpty()) {
            return DEFAULT_UPLOAD_INTERVAL;
        }
        try {
            return IntervalParser.parse(uploadIntervalStr);
        } catch (final NumberFormatException e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, using %s",
                PYROSCOPE_UPLOAD_INTERVAL_CONFIG, uploadIntervalStr, DEFAULT_UPLOAD_INTERVAL);
            return DEFAULT_UPLOAD_INTERVAL;
        }
    }

    private static Logger.Level logLevel(ConfigurationProvider configurationProvider) {
        final String logLevel = configurationProvider.get(PYROSCOPE_LOG_LEVEL_CONFIG);
        if (logLevel == null || logLevel.isEmpty()) {
            return Logger.Level.INFO;
        }
        switch (logLevel.toLowerCase(Locale.ROOT)) {
            case "debug":
                return Logger.Level.DEBUG;
            case "info":
                return Logger.Level.INFO;
            case "warn":
                return Logger.Level.WARN;
            case "error":
                return Logger.Level.ERROR;
            default:
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Unknown log level %s, using INFO", logLevel);
                return Logger.Level.INFO;
        }
    }

    private static String serverAddress(ConfigurationProvider configurationProvider) {
        String serverAddress = configurationProvider.get(PYROSCOPE_ADHOC_SERVER_ADDRESS_CONFIG);
        if (serverAddress == null || serverAddress.isEmpty()) {
            serverAddress = configurationProvider.get(PYROSCOPE_SERVER_ADDRESS_CONFIG);
        }
        if (serverAddress == null || serverAddress.isEmpty()) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "%s is not defined, using %s",
                PYROSCOPE_SERVER_ADDRESS_CONFIG, DEFAULT_SERVER_ADDRESS);
            serverAddress = DEFAULT_SERVER_ADDRESS;

        }
        return serverAddress;
    }

    private static String authToken(ConfigurationProvider configurationProvider) {
        return configurationProvider.get(PYROSCOPE_AUTH_TOKEN_CONFIG);
    }

    private static Format format(ConfigurationProvider configurationProvider) {
        final String format = configurationProvider.get(PYROSCOPE_FORMAT_CONFIG);
        if (format == null || format.isEmpty())
            return DEFAULT_FORMAT;
        switch (format.trim().toLowerCase()) {
            case "collapsed":
                return Format.COLLAPSED;
            case "jfr":
                return Format.JFR;
            default:
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Unknown format %s, using %s", format, DEFAULT_FORMAT);
                return DEFAULT_FORMAT;
        }
    }

    private static int pushQueueCapacity(ConfigurationProvider configurationProvider) {
        final String strPushQueueCapacity = configurationProvider.get(PYROSCOPE_PUSH_QUEUE_CAPACITY_CONFIG);
        if (strPushQueueCapacity == null || strPushQueueCapacity.isEmpty()) {
            return DEFAULT_PUSH_QUEUE_CAPACITY;
        }
        try {
            int pushQueueCapacity = Integer.parseInt(strPushQueueCapacity);
            if (pushQueueCapacity <= 0) {
                return DEFAULT_PUSH_QUEUE_CAPACITY;
            } else {
                return pushQueueCapacity;
            }
        } catch (NumberFormatException e) {
            return DEFAULT_PUSH_QUEUE_CAPACITY;
        }
    }

    public static Map<String, String> labels(ConfigurationProvider configurationProvider) {
        String strLabels = configurationProvider.get(PYROSCOPE_LABELS);
        if (strLabels == null) {
            strLabels = DEFAULT_LABELS;
        }
        return AppName.parseLabels(strLabels);
    }

    private static int ingestMaxRetries(ConfigurationProvider configurationProvider) {
        final String strIngestMaxRetries = configurationProvider.get(PYROSCOPE_INGEST_MAX_TRIES);
        if (strIngestMaxRetries == null || strIngestMaxRetries.isEmpty()) {
            return DEFAULT_INGEST_MAX_RETRIES;
        }
        try {
            return Integer.parseInt(strIngestMaxRetries);
        } catch (NumberFormatException e) {
            return DEFAULT_INGEST_MAX_RETRIES;
        }
    }

    public static boolean bool(ConfigurationProvider cp, String key, boolean defaultValue) {
        final String v = cp.get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v);
    }

    public static int compressionLevel(ConfigurationProvider cp, String key) {
        final String sLevel = cp.get(key);
        if (sLevel == null || sLevel.isEmpty()) {
            return DEFAULT_COMPRESSION_LEVEL;
        }
        if ("NO_COMPRESSION".equalsIgnoreCase(sLevel)) {
            return Deflater.NO_COMPRESSION;
        }
        if ("BEST_SPEED".equalsIgnoreCase(sLevel)) {
            return Deflater.BEST_SPEED;
        }
        if ("BEST_COMPRESSION".equalsIgnoreCase(sLevel)) {
            return Deflater.BEST_COMPRESSION;
        }
        if ("DEFAULT_COMPRESSION".equalsIgnoreCase(sLevel)) {
            return Deflater.DEFAULT_COMPRESSION;
        }
        int level;
        try {
            level = Integer.parseInt(sLevel);
        } catch (NumberFormatException e) {
            return DEFAULT_COMPRESSION_LEVEL;
        }
        if (level >= 0 && level <= 9 || level == -1) {
            return level;
        }
        return DEFAULT_COMPRESSION_LEVEL;
    }

    private static int validateCompressionLevel(int level) {
        if (level >= -1 && level <= 9) {
            return level;
        }
        throw new IllegalArgumentException(String.format("wrong deflate compression level %d", level));
    }

    public static class Builder {
        public String applicationName = null;
        public Duration profilingInterval = DEFAULT_PROFILING_INTERVAL;
        public EventType profilingEvent = DEFAULT_PROFILER_EVENT;
        public String profilingAlloc = "";
        public String profilingLock = "";
        public Duration uploadInterval = DEFAULT_UPLOAD_INTERVAL;
        public Logger.Level logLevel = Logger.Level.INFO;
        public String serverAddress = DEFAULT_SERVER_ADDRESS;
        public String authToken = null;
        public Format format = DEFAULT_FORMAT;
        public int pushQueueCapacity = DEFAULT_PUSH_QUEUE_CAPACITY;
        public Map<String, String> labels = Collections.emptyMap();
        public int ingestMaxRetries = DEFAULT_INGEST_MAX_RETRIES;
        public int compressionLevelJFR = DEFAULT_COMPRESSION_LEVEL;
        public int compressionLevelLabels = DEFAULT_COMPRESSION_LEVEL;
        public boolean allocLive = DEFAULT_ALLOC_LIVE;
        public boolean gcBeforeDump = DEFAULT_GC_BEFORE_DUMP;
        public Builder() {
        }

        public Builder(Config buildUpon) {
            applicationName = buildUpon.applicationName;
            profilingInterval = buildUpon.profilingInterval;
            profilingEvent = buildUpon.profilingEvent;
            profilingAlloc = buildUpon.profilingAlloc;
            profilingLock = buildUpon.profilingLock;
            uploadInterval = buildUpon.uploadInterval;
            logLevel = buildUpon.logLevel;
            serverAddress = buildUpon.serverAddress;
            authToken = buildUpon.authToken;
            format = buildUpon.format;
            pushQueueCapacity = buildUpon.pushQueueCapacity;
            compressionLevelJFR = buildUpon.compressionLevelJFR;
            compressionLevelLabels = buildUpon.compressionLevelLabels;
            allocLive = buildUpon.allocLive;
            gcBeforeDump = buildUpon.gcBeforeDump;
        }

        public Builder setApplicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder setProfilingInterval(Duration profilingInterval) {
            this.profilingInterval = profilingInterval;
            return this;
        }

        public Builder setProfilingEvent(EventType profilingEvent) {
            this.profilingEvent = profilingEvent;
            return this;
        }

        public Builder setProfilingAlloc(String profilingAlloc) {
            this.profilingAlloc = profilingAlloc;
            return this;
        }

        public Builder setProfilingLock(String profilingLock) {
            this.profilingLock = profilingLock;
            return this;
        }

        public Builder setUploadInterval(Duration uploadInterval) {
            this.uploadInterval = uploadInterval;
            return this;
        }

        public Builder setLogLevel(Logger.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder setServerAddress(String serverAddress) {
            this.serverAddress = serverAddress;
            return this;
        }

        public Builder setAuthToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder setFormat(Format format) {
            this.format = format;
            return this;
        }

        public Builder setPushQueueCapacity(int pushQueueCapacity) {
            this.pushQueueCapacity = pushQueueCapacity;
            return this;
        }

        public Builder setLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder setIngestMaxRetries(int ingestMaxRetries) {
            this.ingestMaxRetries = ingestMaxRetries;
            return this;
        }

        public Builder setCompressionLevelJFR(int compressionLevelJFR) {
            this.compressionLevelJFR = validateCompressionLevel(compressionLevelJFR);
            return this;
        }

        public Builder setCompressionLevelLabels(int compressionLevelLabels) {
            this.compressionLevelLabels = validateCompressionLevel(compressionLevelLabels);
            return this;
        }

        public Builder setAllocLive(boolean allocLive) {
            this.allocLive = allocLive;
            return this;
        }

        public Builder setGcBeforeDump(boolean gcBeforeDump) {
            this.gcBeforeDump = gcBeforeDump;
            return this;
        }

        public Config build() {
            if (applicationName == null || applicationName.isEmpty()) {
                applicationName = generateApplicationName();
            }
            return new Config(applicationName,
                profilingInterval,
                profilingEvent,
                profilingAlloc,
                profilingLock,
                uploadInterval,
                logLevel,
                serverAddress,
                authToken,
                format,
                pushQueueCapacity,
                labels,
                ingestMaxRetries,
                compressionLevelJFR,
                compressionLevelLabels,
                allocLive,
                gcBeforeDump);
        }
    }
}
