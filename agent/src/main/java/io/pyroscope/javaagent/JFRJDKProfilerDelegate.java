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

import static io.pyroscope.labels.v2.Pyroscope.*;

/**
 * This implementation of JFR profiler, uses JDK JFR APi to manage JFR recordings.
 * This only to be used with JDK 9 and above.
 * <p>
 * NOTE: This is an experimental feature and is subject to API changes or may be removed in future releases.
 */
public final class JFRJDKProfilerDelegate implements ProfilerDelegate {
    private static final String RECORDING_NAME = "pyroscope";

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
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            recording.enable("jdk.ThreadPark").withPeriod(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.ObjectAllocationInNewTLAB").withStackTrace();
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace();
            recording.enable("jdk.JavaMonitorEnter").withPeriod(Duration.ofMillis(10)).withStackTrace();
            recording.setToDisk(true);
            recording.setDestination(tempJFRFile.toPath());
            recording.start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start JFR recording", e);
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
                EventType.CPU,
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
