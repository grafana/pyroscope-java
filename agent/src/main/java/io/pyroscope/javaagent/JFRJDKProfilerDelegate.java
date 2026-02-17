package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import jdk.jfr.Recording;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static io.pyroscope.labels.v2.Pyroscope.*;
import static java.lang.String.format;

/**
 * This implementation of JFR profiler, uses JDK JFR APi to manage JFR recordings.
 * This only to be used with JDK 9 and above.
 * <p>
 * NOTE: This is an experimental feature and is subject to API changes or may be removed in future releases.
 */
public final class JFRJDKProfilerDelegate implements ProfilerDelegate {
    private static final Duration DEFAULT_LOCK_INTERVAL = Duration.ofNanos(10000);    // 10 us

    private Config config;
    private File tempJFRFile;
    private Recording recording;

    public JFRJDKProfilerDelegate(Config config) {
        setConfig(config);
    }

    @Override
    public void setConfig(final Config config) {
        this.config = config;
        try {
            tempJFRFile = jfrRecordingPath();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create JFR destination path", e);
        }
    }

    private static File jfrRecordingPath() throws IOException {
        File tempJFRFile = File.createTempFile("pyroscope", ".jfr");
        tempJFRFile.deleteOnExit();
        return tempJFRFile;
    }

    /**
     * Start JFR profiler
     */
    @Override
    public synchronized void start() {
        try {
            recording = new Recording();
            switch (config.profilingEvent) {
                case CPU: {
                    recording.enable("jdk.ExecutionSample")
                        .withPeriod(config.profilingInterval)
                        .withStackTrace();
                    break;
                }
                case ALLOC: {
                    recording.enable("jdk.ObjectAllocationInNewTLAB")
                        .withPeriod(config.profilingInterval)
                        .withStackTrace();
                    recording.enable("jdk.ObjectAllocationOutsideTLAB")
                        .withPeriod(config.profilingInterval)
                        .withStackTrace();
                    break;
                }
                case LOCK: {
                    Duration lockDuration = parseDuration(config.profilingLock, DEFAULT_LOCK_INTERVAL);
                    recording.enable("jdk.ThreadPark")
                        .withThreshold(lockDuration)
                        .withPeriod(config.profilingInterval)
                        .withStackTrace();
                    recording.enable("jdk.JavaMonitorEnter")
                        .withThreshold(lockDuration)
                        .withPeriod(config.profilingInterval)
                        .withStackTrace();
                    break;
                }
                default:
                    throw new IllegalArgumentException(format("Unsupported event type: %s", config.profilingEvent));
            }
            recording.setToDisk(true);
            recording.setDestination(tempJFRFile.toPath());
            recording.start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start JFR recording", e);
        }
    }

    // this based on async-profiler parsing logic so we can things compatible
    private static Duration parseDuration(String str, Duration defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }

        // find where the numeric part ends
        int endIndex = 0;
        while (endIndex < str.length()) {
            char c = str.charAt(endIndex);
            if (!Character.isDigit(c) && c != '-' && c != '+') {
                break;
            }
            endIndex++;
        }

        if (endIndex == 0) {
            throw new IllegalArgumentException(format("Invalid duration: %s", str));
        }

        // parse the numeric part
        long result;
        try {
            String numericPart = str.substring(0, endIndex);
            result = Long.parseLong(numericPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration: " + str, e);
        }

        // If no unit suffix, return the numeric value
        if (endIndex >= str.length()) {
            return Duration.of(result, ChronoUnit.NANOS);
        }

        // Get the unit character and convert to lowercase
        char unitChar = Character.toLowerCase(str.charAt(endIndex));

        switch (unitChar) {
            case 'n':
                return Duration.of(result, ChronoUnit.NANOS);
            case 'u':
                return Duration.of(result, ChronoUnit.MICROS);
            case 'm':
                return Duration.of(result, ChronoUnit.MILLIS);
            case 's':
                return Duration.of(result, ChronoUnit.SECONDS);
            default:
                throw new IllegalArgumentException(format("Invalid duration unit: %s", unitChar));
        }
    }

    /**
     * Stop JFR profiler
     */
    @Override
    public synchronized void stop() {
        recording.stop();
    }

    /**
     * @param started - time when profiling has been started
     * @param ended   - time when profiling has ended
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
    @Override
    public synchronized Snapshot dumpProfile(Instant started, Instant ended) {
        return dumpImpl(started, ended);
    }

    private Snapshot dumpImpl(Instant started, Instant ended) {
        if (config.gcBeforeDump) {
            System.gc();
        }
        try {
            byte[] data = Files.readAllBytes(tempJFRFile.toPath());
            return new Snapshot(
                Format.JFR,
                config.profilingEvent,
                started,
                ended,
                data,
                LabelsWrapper.dump()
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
