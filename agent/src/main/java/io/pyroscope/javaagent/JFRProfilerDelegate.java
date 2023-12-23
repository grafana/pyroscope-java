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
import java.util.ArrayList;
import java.util.List;

public final class JFRProfilerDelegate implements ProfilerDelegate {
    private Config config;
    private File tempJFRFile;

    JFRProfilerDelegate(Config config) {
        setConfig(config);
    }

    public void setConfig(final Config config) {
        this.config = config;

        try {
            // flight recorder is built on top of a file descriptor, so we need a file.
            tempJFRFile = File.createTempFile("pyroscope", ".jfr");
            tempJFRFile.deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Start JFR profiler
     */
    public synchronized void start() {
        try {
            List<String> commands = new ArrayList<>();
            commands.add("jcmd");
            commands.add(String.valueOf(CurrentPidProvider.getCurrentProcessId()));
            commands.add("JFR.start");
            commands.add("name=Pyroscope");
            commands.add("filename="+tempJFRFile.getAbsolutePath());
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0){
                throw new RuntimeException("Invalid exit code: " + exitCode);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop JFR profiler
     */
    public synchronized void stop() {
        try {
            List<String> commands = new ArrayList<>();
            commands.add("jcmd");
            commands.add(String.valueOf(CurrentPidProvider.getCurrentProcessId()));
            commands.add("JFR.stop");
            commands.add("name=Pyroscope");
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0){
                throw new RuntimeException("Invalid exit code: " + exitCode);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param started - time when profiling has been started
     * @param ended - time when profiling has ended
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
    public synchronized Snapshot dumpProfile(Instant started, Instant ended) {
        return dumpImpl(started, ended);
    }

    private Snapshot dumpImpl(Instant started, Instant ended) {
        if (config.gcBeforeDump) {
            System.gc();
        }
        final byte[] data;
            data = dumpJFR();
        return new Snapshot(
            Format.JFR,
            EventType.CPU,
            started,
            ended,
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
