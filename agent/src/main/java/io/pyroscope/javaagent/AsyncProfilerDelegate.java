package io.pyroscope.javaagent;

import io.pyroscope.PyroscopeAsyncProfiler;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.util.TmpFileUtil;
import io.pyroscope.labels.pb.JfrLabels;
import io.pyroscope.labels.v2.Pyroscope;
import one.profiler.AsyncProfiler;
import one.profiler.Counter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static io.pyroscope.Preconditions.checkNotNull;

public final class AsyncProfilerDelegate implements ProfilerDelegate {
    private static final String PROFILER_NOT_ACTIVE = "Profiler is not active";

    private Config config;
    private EventType eventType;
    private Format format;
    private File tempJFRFile;
    private SnapshotFileRotation snapshotFiles;
    private Logger logger;

    private final AsyncProfiler instance = PyroscopeAsyncProfiler.getAsyncProfiler();

    public AsyncProfilerDelegate(@NotNull Config config) {
        setConfig(config);
    }

    @Override
    public void setConfig(@NotNull final Config config) {
        checkNotNull(config, "config");
        this.config = config;
        this.eventType = config.profilingEvent;
        this.format = config.format;

        if (format == Format.JFR && null == tempJFRFile) {
            try {
                // Async-profiler recreates the file with default permissions after each rotation, so keep the
                // recordings in a directory only the owner can access. Register the directory for deletion before
                // the files in it: the JVM deletes them in reverse order.
                Path dir = TmpFileUtil.createTempDirectory(config.tmpDir, "pyroscope");
                dir.toFile().deleteOnExit();
                tempJFRFile = dir.resolve("pyroscope.jfr").toFile();
                tempJFRFile.deleteOnExit();
                Files.createFile(tempJFRFile.toPath());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Makes JFR dumps return a {@link SnapshotFile} that keeps the recording on disk, rotating through
     * {@code slots} files next to the temporary JFR file. Only use this when the exporter handles {@link SnapshotFile}.
     * <p>
     * Calling this again changes the number of slots. Does nothing if the filesystem cannot atomically replace files.
     */
    public synchronized void enableSnapshotFiles(int slots, @NotNull Logger logger) {
        checkNotNull(logger, "logger");
        if (tempJFRFile == null) {
            return;
        }
        this.logger = logger;
        if (snapshotFiles != null) {
            snapshotFiles.resize(slots);
            return;
        }
        Path source = tempJFRFile.toPath();
        try {
            SnapshotFileRotation.checkAtomicReplace(source.getParent());
        } catch (IOException e) {
            logger.log(Logger.Level.INFO, "Keeping JFR snapshots in memory, %s does not support atomic file replacement: %s",
                source.getParent(), e);
            return;
        }
        snapshotFiles = new SnapshotFileRotation(source, slots);
    }

    /**
     * Start async-profiler
     */
    @Override
    public synchronized void start() {
        if (format == Format.JFR || format == Format.OTLP) {
            try {
                instance.execute(createStartCommand());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            instance.start(eventType.id, config.profilingInterval.toNanos());
        }
    }

    /**
     * Stop async-profiler
     */
    @Override
    public synchronized void stop() {
        try {
            instance.stop();
        } catch (IllegalStateException e) {
            // async-profiler throws when stop is called after the recording timeout has already elapsed.
            if (!PROFILER_NOT_ACTIVE.equals(e.getMessage())) {
                throw e;
            }
        }
    }

    /**
     * @param started - time when profiling has been started
     * @param ended   - time when profiling has ended
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
    @Override
    @NotNull
    public synchronized Snapshot dumpProfile(@NotNull Instant started, @NotNull Instant ended) {
        return dumpImpl(started, ended);
    }

    String createStartCommand() {
        return createStartCommand(config, format, tempJFRFile);
    }

    static String createStartCommand(Config config, Format format, File tempJFRFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("start,event=").append(config.profilingEvent.id);
        if (config.profilingAlloc != null && !config.profilingAlloc.isEmpty()) {
            sb.append(",alloc=").append(config.profilingAlloc);
            if (config.allocLive) {
                sb.append(",live");
            }
        }
        if (config.profilingLock != null && !config.profilingLock.isEmpty()) {
            sb.append(",lock=").append(config.profilingLock);
        }
        sb.append(",interval=").append(config.profilingInterval.toNanos());
        if (format == Format.JFR) {
            sb.append(",file=").append(tempJFRFile.toString());
        }
        if (format == Format.JFR || format == Format.OTLP) {
            sb.append(",timeout=").append(asyncProfilerTimeoutSeconds(config.uploadInterval));
        }
        if (config.APLogLevel != null) {
            sb.append(",loglevel=").append(config.APLogLevel);
        }
        sb.append(",jstackdepth=").append(config.javaStackDepthMax);
        if (config.APExtraArguments != null) {
            sb.append(",").append(config.APExtraArguments);
        }
        return sb.toString();
    }

    static long asyncProfilerTimeoutSeconds(Duration profilingDuration) {
        return profilingDuration.getSeconds() + 1;
    }

    private Snapshot dumpImpl(Instant started, Instant ended) {
        if (config.gcBeforeDump) {
            System.gc();
        }
        // This also removes closed scoped contexts. Keep draining them even though the
        // current OTLP exporter does not include the resulting labels snapshot.
        final JfrLabels.LabelsSnapshot labels = Pyroscope.LabelsWrapper.dump();

        final byte[] data;
        if (format == Format.JFR) {
            if (snapshotFiles != null) {
                try {
                    return snapshotFiles.take(format, eventType, started, ended, labels);
                } catch (IOException e) {
                    logger.log(Logger.Level.WARN, "Failed to move JFR recording aside, reading it into memory instead: %s", e);
                }
            }
            data = dumpJFR();
        } else if (format == Format.OTLP) {
            data = instance.dumpOtlp(Counter.TOTAL);
        } else {
            data = instance.dumpCollapsed(Counter.SAMPLES).getBytes(StandardCharsets.UTF_8);
        }
        return new Snapshot(
                format,
                eventType,
                started,
                ended,
                data,
                labels
        );
    }

    private byte[] dumpJFR() {
        try {
            return Files.readAllBytes(tempJFRFile.toPath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
