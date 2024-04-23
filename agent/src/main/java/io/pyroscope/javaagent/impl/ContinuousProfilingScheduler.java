package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.Profiler;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import kotlin.random.Random;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;


public class ContinuousProfilingScheduler implements ProfilingScheduler {
    public static final ThreadFactory THREAD_FACTORY = r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("PyroscopeProfilingScheduler");
        t.setDaemon(true);
        return t;
    };
    private final Config config;

    private ScheduledExecutorService executor;
    private final Exporter exporter;
    private final Logger logger;
    private final Object lock = new Object();
    private Instant profilingIntervalStartTime;
    private ScheduledFuture<?> job;
    private boolean started;
    private Profiler profiler;

    public ContinuousProfilingScheduler(Config config, Exporter exporter, Logger logger) {
        this.config = config;
        this.exporter = exporter;
        this.logger = logger;
    }

    @Override
    public void start(Profiler profiler) {
        this.logger.log(Logger.Level.DEBUG, "ContinuousProfilingScheduler#start");
        synchronized (lock) {
            if (started) {
                throw new IllegalStateException("already started");
            }
            Duration firstProfilingDuration;
            try {
                firstProfilingDuration = startFirst(profiler);
            } catch (Throwable throwable) {
                stopSchedulerLocked();
                throw new IllegalStateException(throwable);
            }
            this.profiler = profiler;
            this.executor = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
            this.job = executor.scheduleAtFixedRate(this::schedulerTick,
                firstProfilingDuration.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);
            this.started = true;
            logger.log(Logger.Level.DEBUG, "ContinuousProfilingScheduler started");
        }
    }

    @Override
    public void stop() {
        ScheduledExecutorService svc;
        synchronized (lock) {
            stopSchedulerLocked();
            svc = this.executor;
            this.executor = null;
        }
        // shutdown here not under lock to avoid deadlock ( the task may block to wait for lock and
        // we are holding the lock and waiting for task to finish)
        // There is still synchronization happens from the PyroscopeAgent class,
        // so there are no concurrent calls to start/stop. So there is no lock here
        awaitTermination(svc);
        this.logger.log(Logger.Level.DEBUG, "ContinuousProfilingScheduler stopped");
    }

    private static void awaitTermination(ScheduledExecutorService svc) {
        try {
            boolean terminated = svc.awaitTermination(10, TimeUnit.SECONDS);
            if (!terminated) {
                throw new IllegalStateException("failed to terminate scheduler's executor");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to terminate scheduler's executor", e);
        }
    }

    private void stopSchedulerLocked() {
        if (!this.started) {
            return;
        }
        this.logger.log(Logger.Level.DEBUG, "ContinuousProfilingScheduler stopping");
        try {
            this.profiler.stop();
        } catch (Throwable throwable) {
            logger.log(Logger.Level.ERROR, "Error stopping profiler %s", throwable);
        }
        job.cancel(true);
        executor.shutdown();
        this.started = false;
    }


    private void schedulerTick() {

        synchronized (lock) {
            if (!started) {
                return;
            }
            logger.log(Logger.Level.DEBUG, "ContinuousProfilingScheduler#schedulerTick");
            Snapshot snapshot;
            Instant now;
            try {
                profiler.stop();
                now = Instant.now();
                snapshot = profiler.dumpProfile(this.profilingIntervalStartTime, now);
                profiler.start();
            } catch (Throwable throwable) {
                logger.log(Logger.Level.ERROR, "Error dumping profiler %s", throwable);
                stopSchedulerLocked();
                return;
            }
            profilingIntervalStartTime = now;
            exporter.export(snapshot);
        }
    }


    /**
     * Starts the first profiling interval.
     * profilingIntervalStartTime is set to now
     * Duration of the first profiling interval is a random fraction of uploadInterval not smaller than 2000ms.
     *
     * @return Duration of the first profiling interval
     */
    private Duration startFirst(Profiler profiler) {
        Instant now = Instant.now();

        long uploadIntervalMillis = config.uploadInterval.toMillis();
        float randomOffset = Random.Default.nextFloat();
        uploadIntervalMillis = (long) ((float) uploadIntervalMillis * randomOffset);
        if (uploadIntervalMillis < 2000) {
            uploadIntervalMillis = 2000;
        }
        Duration firstProfilingDuration = Duration.ofMillis(uploadIntervalMillis);

        profiler.start();
        profilingIntervalStartTime = now;
        return firstProfilingDuration;
    }


}
