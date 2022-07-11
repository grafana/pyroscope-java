package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.OverfillQueue;
import io.pyroscope.javaagent.Profiler;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.pyroscope.javaagent.DateUtils.truncate;

public class ContinuousProfilingScheduler implements ProfilingScheduler {
    final Config config;

    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName("PyroscopeProfilingScheduler");
            t.setDaemon(true);
            return t;
        }
    });
    private final Exporter exporter;
    private long profilingIntervalStartTime;

    public ContinuousProfilingScheduler(Config config, Exporter exporter) {
        this.config = config;
        this.exporter = exporter;
    }

    @Override
    public void start(final Profiler profiler) {
        long firstProfilingDuration = startFirst(profiler);
        final Runnable dumpProfile = new Runnable() {
            @Override
            public void run() {
                try {
                    Snapshot snapshot = profiler.dump(
                        alignProfilingIntervalStartTime(ContinuousProfilingScheduler.this.profilingIntervalStartTime, config.uploadInterval)
                    );
                    profilingIntervalStartTime = System.currentTimeMillis() * 1000000;
                    exporter.export(snapshot);
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        };
        executor.scheduleAtFixedRate(dumpProfile,
            firstProfilingDuration, config.uploadInterval, TimeUnit.NANOSECONDS);

    }

    /**
     * Starts the first profiling interval.
     * profilingIntervalStartTime is set to a current time aligned to upload interval
     * Duration of the first profiling interval will be smaller than uploadInterval for alignment.
     * <a href="https://github.com/pyroscope-io/pyroscope-java/issues/40">...</a>
     *
     * @return Duration of the first profiling interval
     */
    private long startFirst(Profiler profiler) {
        long now = System.currentTimeMillis() * 1000000;
        long prevUploadInterval = truncate(now, config.uploadInterval);
        long nextUploadInterval = prevUploadInterval + config.uploadInterval;
        long firstProfilingDuration = nextUploadInterval - now;
        profiler.start();
        profilingIntervalStartTime = prevUploadInterval;
        return firstProfilingDuration;
    }

    /**
     * Aligns profilingIntervalStartTime to the closest aligned upload time either forward or backward
     * For example if upload interval is 10s and profilingIntervalStartTime is 00:00.01 it will return 00:00
     * and if profilingIntervalStartTime is 00:09.239 it will return 00:10
     * <a href="https://github.com/pyroscope-io/pyroscope-java/issues/40">...</a>
     *
     * @param profilingIntervalStartTime the time to align
     * @param uploadInterval
     * @return the aligned
     */
    public static long alignProfilingIntervalStartTime(long profilingIntervalStartTime, long uploadInterval) {
        long prev = truncate(profilingIntervalStartTime, uploadInterval);
        long next = prev + uploadInterval;
        long d1 = profilingIntervalStartTime - prev;
        long d2 = next - profilingIntervalStartTime;
        if (d1 < d2) {
            return prev;
        } else {
            return next;
        }
    }
}
