package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class JFRProfilerDelegate implements ProfilerDelegate {
    private static final String RECORDING_NAME = "pyroscope";
    private Config config;
    private File tempJFRFile;
    private Path jcmdBin;

    public JFRProfilerDelegate(Config config) {
        setConfig(config);
    }

    public void setConfig(final Config config) {
        this.config = config;
        jcmdBin = findJcmdBin();
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
            commands.add(jcmdBin.toString());
            commands.add(String.valueOf(CurrentPidProvider.getCurrentProcessId()));
            commands.add("JFR.start");
            commands.add("name=" + RECORDING_NAME);
            commands.add("filename=" + tempJFRFile.getAbsolutePath());
            commands.add("settings=pyroscope");
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
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
            commands.add(jcmdBin.toString());
            commands.add(String.valueOf(CurrentPidProvider.getCurrentProcessId()));
            commands.add("JFR.stop");
            commands.add("name=" + RECORDING_NAME);
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Invalid exit code: " + exitCode);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param started - time when profiling has been started
     * @param ended   - time when profiling has ended
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
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
                Pyroscope.LabelsWrapper.dump()
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path findJcmdBin() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        //find jcmd binary
        Path jcmdBin = javaHome.resolve("bin/jcmd");
        if (!Files.isExecutable(jcmdBin)) {
            jcmdBin = javaHome.getParent().resolve("bin/jcmd");
            if (!Files.isExecutable(jcmdBin)) {
                throw new RuntimeException("cannot find executable jcmd in Java home");
            }
        }
        return jcmdBin;
    }
}
