package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;
import io.pyroscope.labels.io.pyroscope.PyroscopeAsyncProfiler;
import one.profiler.AsyncProfiler;
import one.profiler.Counter;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public final class Profiler {
    private Config config;
    private EventType eventType;
    private String alloc;
    private String lock;
    private Duration interval;
    private Format format;
    private File tempJFRFile;

    private final AsyncProfiler instance = PyroscopeAsyncProfiler.getAsyncProfiler();

    Profiler(Config config) {
        set(config);
    }

    public void set(final Config config) {
        this.config = config;
        this.alloc = config.profilingAlloc;
        this.lock = config.profilingLock;
        this.eventType = config.profilingEvent;
        this.interval = config.profilingInterval;
        this.format = config.format;

        if (format == Format.JFR) {
            try {
                // flight recorder is built on top of a file descriptor, so we need a file.
                tempJFRFile = File.createTempFile("pyroscope", ".jfr");
                tempJFRFile.deleteOnExit();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            tempJFRFile = null;
        }
    }

    /**
     * Start async-profiler
     */
    public synchronized void start() {
        if (format == Format.JFR) {
            try {
                instance.execute(createJFRCommand());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            instance.start(eventType.id, interval.toNanos());
        }
    }

    /**
     * Stop async-profiler
     */
    public synchronized void stop() {
        instance.stop();
    }

    /**
     *
     * @param profilingIntervalStartTime - time when profiling has been started
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
    public synchronized Snapshot dumpProfile(Instant profilingIntervalStartTime) {
        return dumpImpl(profilingIntervalStartTime);
    }

    /**
     * Stop profiling, dump profiling data, start again
     * Deprecated, use start, stop, dumpProfile methods instead
     */
    @Deprecated
    public synchronized Snapshot dump(Instant profilingIntervalStartTime) {
        instance.stop();

        Snapshot result = dumpImpl(profilingIntervalStartTime);

        start();

        return result;
    }

    private String createJFRCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("start,event=").append(eventType.id);
        if (alloc != null && !alloc.isEmpty()) {
            sb.append(",alloc=").append(alloc);
            if (config.allocLive) {
                sb.append(",live");
            }
        }
        if (lock != null && !lock.isEmpty()) {
            sb.append(",lock=").append(lock);
        }
        sb.append(",interval=").append(interval.toNanos())
            .append(",file=").append(tempJFRFile.toString());
        if (config.APLogLevel != null) {
            sb.append(",loglevel=").append(config.APLogLevel);
        }
        sb.append("jstackdepth=").append(config.javaStackDepthMax);
        if (config.APExtraArguments != null) {
            sb.append(",").append(config.APExtraArguments);
        }
        return sb.toString();
    }

    private Snapshot dumpImpl(Instant profilingIntervalStartTime) {
        if (config.gcBeforeDump) {
            System.gc();
        }
        final byte[] data;
        if (format == Format.JFR) {
            data = dumpJFR();
        } else {
            data = instance.dumpCollapsed(Counter.SAMPLES).getBytes(StandardCharsets.UTF_8);
        }
        return new Snapshot(
            format,
            eventType,
            profilingIntervalStartTime,
            data,
            Pyroscope.LabelsWrapper.dump()
        );
    }

    private byte[] dumpJFR() {
        try {
            byte[] bytes = new byte[(int) tempJFRFile.length()];
            try (DataInputStream ds = new DataInputStream(new FileInputStream(tempJFRFile))) {
                ds.readFully(bytes);
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
