package io.pyroscope.javaagent.config;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.api.ConfigurationProvider;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.impl.DefaultConfigurationProvider;
import io.pyroscope.javaagent.impl.DefaultLogger;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/**
 * Config allows to tweak parameters of existing pyroscope components at start time
 * through pyroscope.properties file or System.getevn - see io.pyroscope.javaagent.impl.DefaultConfigurationProvider
 */
@Getter
@Setter
@ToString
@Builder(toBuilder = true, buildMethodName = "buildInternal")
public final class Config {
    private static final String PYROSCOPE_APPLICATION_NAME_CONFIG = "PYROSCOPE_APPLICATION_NAME";
    private static final String PYROSCOPE_PROFILING_INTERVAL_CONFIG = "PYROSCOPE_PROFILING_INTERVAL";
    private static final String PYROSCOPE_PROFILER_EVENT_CONFIG = "PYROSCOPE_PROFILER_EVENT";
    private static final String PYROSCOPE_PROFILER_ALLOC_CONFIG = "PYROSCOPE_PROFILER_ALLOC";
    private static final String PYROSCOPE_PROFILER_LOCK_CONFIG = "PYROSCOPE_PROFILER_LOCK";
    private static final String PYROSCOPE_UPLOAD_INTERVAL_CONFIG = "PYROSCOPE_UPLOAD_INTERVAL";
    private static final String PYROSCOPE_JAVA_STACK_DEPTH_MAX = "PYROSCOPE_JAVA_STACK_DEPTH_MAX";
    private static final String PYROSCOPE_LOG_LEVEL_CONFIG = "PYROSCOPE_LOG_LEVEL";
    private static final String PYROSCOPE_AP_LOG_LEVEL_CONFIG = "PYROSCOPE_AP_LOG_LEVEL";
    private static final String PYROSCOPE_AP_EXTRA_ARGUMENTS_CONFIG = "PYROSCOPE_AP_EXTRA_ARGUMENTS";
    private static final String PYROSCOPE_SERVER_ADDRESS_CONFIG = "PYROSCOPE_SERVER_ADDRESS";
    private static final String PYROSCOPE_ADHOC_SERVER_ADDRESS_CONFIG = "PYROSCOPE_ADHOC_SERVER_ADDRESS";
    private static final String PYROSCOPE_AUTH_TOKEN_CONFIG = "PYROSCOPE_AUTH_TOKEN";
    private static final String PYROSCOPE_BASIC_AUTH_USER_CONFIG = "PYROSCOPE_BASIC_AUTH_USER";
    private static final String PYROSCOPE_BASIC_AUTH_PASSWORD_CONFIG = "PYROSCOPE_BASIC_AUTH_PASSWORD";
    private static final String PYROSCOPE_FORMAT_CONFIG = "PYROSCOPE_FORMAT";
    private static final String PYROSCOPE_PUSH_QUEUE_CAPACITY_CONFIG = "PYROSCOPE_PUSH_QUEUE_CAPACITY";
    private static final String PYROSCOPE_LABELS = "PYROSCOPE_LABELS";

    private static final String PYROSCOPE_INGEST_MAX_TRIES = "PYROSCOPE_INGEST_MAX_TRIES";
    private static final String PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR = "PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR";
    private static final String PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS = "PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS";
    private static final String PYROSCOPE_ALLOC_LIVE = "PYROSCOPE_ALLOC_LIVE";
    private static final String PYROSCOPE_GC_BEFORE_DUMP = "PYROSCOPE_GC_BEFORE_DUMP";
    private static final String PYROSCOPE_HTTP_HEADERS = "PYROSCOPE_HTTP_HEADERS";
    private static final String PYROSCOPE_TENANT_ID = "PYROSCOPE_TENANT_ID";

    /**
     * Experimental feature, may be removed in the future
     */
    private static final String PYROSCOPE_SAMPLING_RATE = "PYROSCOPE_SAMPLING_RATE";
    /**
     * Experimental feature, may be removed in the future
     */
    private static final String PYROSCOPE_SAMPLING_DURATION = "PYROSCOPE_SAMPLING_DURATION";
    /**
     * Experimental feature, may be removed in the future
     */
    private static final String PYROSCOPE_SAMPLING_EVENT_ORDER_CONFIG = "PYROSCOPE_SAMPLING_EVENT_ORDER";

    public static final String DEFAULT_SPY_NAME = "javaspy";
    private static final Duration DEFAULT_PROFILING_INTERVAL = Duration.ofMillis(10);
    private static final EventType DEFAULT_PROFILER_EVENT = EventType.ITIMER;
    private static final String DEFAULT_PROFILER_ALLOC = "";
    private static final String DEFAULT_PROFILER_LOCK = "";
    private static final Duration DEFAULT_UPLOAD_INTERVAL = Duration.ofSeconds(10);
    private static final List<EventType> DEFAULT_SAMPLING_EVENT_ORDER = null;
    private static final int DEFAULT_JAVA_STACK_DEPTH_MAX = 2048;
    private static final String DEFAULT_SERVER_ADDRESS = "http://localhost:4040";
    private static final Format DEFAULT_FORMAT = Format.JFR;
    // The number of snapshots simultaneously stored in memory is limited by this.
    // The number is fairly arbitrary. If an average snapshot is 5KB, it's about 160 KB.
    private static final int DEFAULT_PUSH_QUEUE_CAPACITY = 8;
    private static final int DEFAULT_INGEST_MAX_RETRIES = 8;
    private static final int DEFAULT_COMPRESSION_LEVEL = Deflater.BEST_SPEED;
    private static final String DEFAULT_LABELS = "";
    private static final boolean DEFAULT_ALLOC_LIVE = false;
    private static final boolean DEFAULT_GC_BEFORE_DUMP = false;
    private static final Duration DEFAULT_SAMPLING_DURATION = null;

    @Builder.Default
    public final String applicationName = generateApplicationName();
    @Builder.Default
    public final Duration profilingInterval = DEFAULT_PROFILING_INTERVAL;
    @Builder.Default
    public final EventType profilingEvent = DEFAULT_PROFILER_EVENT;
    @Builder.Default
    public final String profilingAlloc = "";
    @Builder.Default
    public final String profilingLock = "";
    @Builder.Default
    public List<EventType> samplingEventOrder = null;
    @Builder.Default
    public final Duration uploadInterval = DEFAULT_UPLOAD_INTERVAL;
    @Builder.Default
    public final int javaStackDepthMax = DEFAULT_JAVA_STACK_DEPTH_MAX;
    @Builder.Default
    public final Logger.Level logLevel = Logger.Level.INFO;
    @Builder.Default
    public final String serverAddress = DEFAULT_SERVER_ADDRESS;
    @Builder.Default
    public final String authToken = null;

    @Deprecated
    private String timeseriesName;
    private AppName timeseries;
    @Builder.Default
    public final Format format = DEFAULT_FORMAT;
    @Builder.Default
    public final int pushQueueCapacity = DEFAULT_PUSH_QUEUE_CAPACITY;
    @Builder.Default
    public final Map<String, String> labels = Collections.emptyMap();
    @Builder.Default
    public final int ingestMaxTries = DEFAULT_INGEST_MAX_RETRIES;
    @Builder.Default
    public final int compressionLevelJFR = validateCompressionLevel(DEFAULT_COMPRESSION_LEVEL);
    @Builder.Default
    public final int compressionLevelLabels = validateCompressionLevel(DEFAULT_COMPRESSION_LEVEL);

    @Builder.Default
    public final boolean allocLive = DEFAULT_ALLOC_LIVE;
    @Builder.Default
    public final boolean gcBeforeDump = DEFAULT_GC_BEFORE_DUMP;

    @Builder.Default
    public final Map<String, String> httpHeaders = new HashMap<>();
    @Builder.Default
    public final Duration samplingDuration = DEFAULT_SAMPLING_DURATION;
    @Builder.Default
    public final String tenantID = null;
    @Builder.Default
    public final String APLogLevel = null;
    @Builder.Default
    public final String APExtraArguments = null;
    public final String basicAuthUser;
    public final String basicAuthPassword;

    public static class ConfigBuilder {
        public ConfigBuilder() {
            super();
        }

        public Config build() {
            Config config = buildInternal();
            config.setTimeseries(createTimeseriesName(AppName.parse(config.getApplicationName()), config.getProfilingEvent(), config.getFormat()));
            config.setTimeseriesName(config.getTimeseries().toString());

            HttpUrl serverAddressUrl = HttpUrl.parse(config.getServerAddress());
            if (serverAddressUrl == null) {
                throw new IllegalArgumentException("invalid url " + config.getServerAddress());
            }
            if (config.getAuthToken() != null && config.getBasicAuthUser() != null) {
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN,
                    "auth token is ignored (both auth token and basic auth specified)");
            }
            config.setSamplingEventOrder(resolve(config.getSamplingEventOrder(), config.getProfilingEvent(), config.getProfilingAlloc(), config.getProfilingLock(), config.getSamplingDuration()));
            if ("0".equals(config.getProfilingAlloc())) {
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN,
                    "Setting PYROSCOPE_PROFILER_ALLOC to 0 registers every allocation event, causing significant overhead and results in large profiles, making it not ideal for production. We recommend a starting value of 512k, adjusting as needed.");
            }
            if ("0".equals(config.getProfilingLock())) {
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN,
                    "Setting PYROSCOPE_PROFILER_LOCK to 0 registers every lock event, causing significant overhead and results in large profiles, making it not ideal for production. We recommend a starting value of 10ms, adjusting as needed.");
            }
            if (config.getFormat() == Format.COLLAPSED) {
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "COLLAPSED format is deprecated");
            }
            return config;
        }

        private AppName createTimeseriesName(AppName app, EventType eventType, Format format) {
            if (format == Format.JFR)
                return app;
            return AppName.builder()
                .name(app.name + "." + eventType.id)
                .build();
        }
    }

    public long profilingIntervalInHertz() {
        return durationToHertz(this.profilingInterval);
    }

    private static long durationToHertz(Duration duration) {
        Duration oneSecond = Duration.ofSeconds(1);
        return oneSecond.toNanos() / duration.toNanos();
    }

    public static Config build() {
        return build(DefaultConfigurationProvider.INSTANCE);
    }

    public static Config build(ConfigurationProvider cp) {
        String alloc = profilingAlloc(cp);
        boolean allocLive = bool(cp, PYROSCOPE_ALLOC_LIVE, DEFAULT_ALLOC_LIVE);
        if (DEFAULT_PROFILER_ALLOC.equals(alloc) && allocLive) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "%s is ignored because %s is not configured",
                PYROSCOPE_ALLOC_LIVE, PYROSCOPE_PROFILER_ALLOC_CONFIG);
            allocLive = false;
        }
        return Config.builder()
            .applicationName(applicationName(cp))
            .profilingInterval(profilingInterval(cp))
            .profilingEvent(profilingEvent(cp))
            .profilingAlloc(alloc)
            .profilingLock(profilingLock(cp))
            .samplingEventOrder(samplingEventOrder(cp))
            .uploadInterval(uploadInterval(cp))
            .javaStackDepthMax(javaStackDepthMax(cp))
            .logLevel(logLevel(cp))
            .serverAddress(serverAddress(cp))
            .authToken(authToken(cp))
            .format(format(cp))
            .pushQueueCapacity(pushQueueCapacity(cp))
            .labels(labels(cp))
            .ingestMaxTries(ingestMaxRetries(cp))
            .compressionLevelJFR(compressionLevel(cp, PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR))
            .compressionLevelLabels(compressionLevel(cp, PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS))
            .allocLive(allocLive)
            .gcBeforeDump(bool(cp, PYROSCOPE_GC_BEFORE_DUMP, DEFAULT_GC_BEFORE_DUMP))
            .httpHeaders(httpHeaders(cp))
            .samplingDuration(samplingDuration(cp))
            .tenantID(tenantID(cp))
            .APLogLevel(cp.get(PYROSCOPE_AP_LOG_LEVEL_CONFIG))
            .APExtraArguments(cp.get(PYROSCOPE_AP_EXTRA_ARGUMENTS_CONFIG))
            .basicAuthUser(cp.get(PYROSCOPE_BASIC_AUTH_USER_CONFIG))
            .basicAuthPassword(cp.get(PYROSCOPE_BASIC_AUTH_PASSWORD_CONFIG))
            .build();
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

    private static List<EventType> samplingEventOrder(final ConfigurationProvider cp) {
        final String samplingEventOrder = cp.get(PYROSCOPE_SAMPLING_EVENT_ORDER_CONFIG);
        if (null == samplingEventOrder || samplingEventOrder.isEmpty()) {
            return DEFAULT_SAMPLING_EVENT_ORDER;
        }
        DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "keep upload interval >= sampling duration * distinct event count to avoid unexpected behaviour");
        return Stream.of(samplingEventOrder.split("\\s*,\\s*"))
            .map(s -> {
                try {
                    return EventType.fromId(s);
                } catch (final IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(t -> null != t)
            .collect(Collectors.toCollection(() -> new ArrayList<>()));
    }

    // extra args events not supported
    private static List<EventType> resolve(final List<EventType> samplingEventOrder, final EventType type, final String alloc, final String lock, final Duration samplingDuration) {
        if (null == samplingEventOrder)
            return null;
        if (null == samplingDuration) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "not implemented: sampling event order is only implemented in sampling mode");
            return null;
        }
        // effectively set size is upper bounded by 3
        final LinkedHashSet<EventType> set = new LinkedHashSet<>();
        final boolean _alloc = null != alloc && !alloc.isEmpty();
        final boolean _lock = null != lock && !lock.isEmpty();

        // filter unmacthed and dedupe
        for (final EventType t : samplingEventOrder)
            if (t.equals(type) || (EventType.ALLOC.equals(t) && _alloc) || (EventType.LOCK.equals(t) && _lock))
                set.add(t);
        // append missing
        set.add(type);
        if (_alloc)
            set.add(EventType.ALLOC);
        if (_lock)
            set.add(EventType.LOCK);
        return new ArrayList<>(set);
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


    private static int javaStackDepthMax(ConfigurationProvider configurationProvider) {
        final String javaStackDepthMaxStr = configurationProvider.get(PYROSCOPE_JAVA_STACK_DEPTH_MAX);
        if (null == javaStackDepthMaxStr || javaStackDepthMaxStr.isEmpty()) {
            return DEFAULT_JAVA_STACK_DEPTH_MAX;
        }
        try {
            return Integer.parseInt(javaStackDepthMaxStr);
        } catch (final NumberFormatException e) {
            return DEFAULT_JAVA_STACK_DEPTH_MAX;
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

    public static Map<String, String> httpHeaders(ConfigurationProvider cp) {
        final String sHttpHeaders = cp.get(PYROSCOPE_HTTP_HEADERS);
        if (sHttpHeaders == null || sHttpHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Moshi moshi = new Moshi.Builder().build();

        Type headersType = Types.newParameterizedType(Map.class, String.class, String.class);
        JsonAdapter<Map<String, String>> adapter = moshi.adapter(headersType);

        try {
            Map<String, String> httpHeaders = adapter.fromJson(sHttpHeaders);
            if (httpHeaders == null) {
                return Collections.emptyMap();
            }
            return httpHeaders;
        } catch (Exception e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.ERROR, "Failed to parse %s = %s configuration. " +
                "Falling back to no extra http headers. %s: ", PYROSCOPE_HTTP_HEADERS, sHttpHeaders, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String tenantID(ConfigurationProvider cp) {
        return cp.get(PYROSCOPE_TENANT_ID);
    }

    private static Duration samplingDuration(ConfigurationProvider configurationProvider) {
        Duration uploadInterval = uploadInterval(configurationProvider);

        final String samplingDurationStr = configurationProvider.get(PYROSCOPE_SAMPLING_DURATION);
        if (samplingDurationStr != null && !samplingDurationStr.isEmpty()) {
            try {
                Duration samplingDuration = IntervalParser.parse(samplingDurationStr);
                if (samplingDuration.compareTo(uploadInterval) > 0) {
                    DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, ignore it",
                        PYROSCOPE_SAMPLING_DURATION, samplingDurationStr);
                } else {
                    return samplingDuration;
                }
            } catch (final NumberFormatException e) {
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, ignore it",
                    PYROSCOPE_SAMPLING_DURATION, samplingDurationStr);
            }
            return DEFAULT_SAMPLING_DURATION;
        }

        final String samplingRateStr = configurationProvider.get(PYROSCOPE_SAMPLING_RATE);
        if (samplingRateStr == null || samplingRateStr.isEmpty()) {
            return DEFAULT_SAMPLING_DURATION;
        }
        try {
            double samplingRate = Double.parseDouble(samplingRateStr);
            if (samplingRate <= 0.0 || samplingRate >= 1.0) {
                return DEFAULT_SAMPLING_DURATION;
            }
            long uploadIntervalMillis = uploadInterval.toMillis();
            long samplingDurationMillis = Math.min(uploadIntervalMillis, Math.round(uploadIntervalMillis * samplingRate));
            return Duration.ofMillis(samplingDurationMillis);
        } catch (final NumberFormatException e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN, "Invalid %s value %s, ignore it",
                PYROSCOPE_SAMPLING_RATE, samplingRateStr);
            return DEFAULT_SAMPLING_DURATION;
        }
    }
}
