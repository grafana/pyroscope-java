package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public final class JFRProfilerDelegate implements ProfilerDelegate {
    private static final String RECORDING_NAME = "pyroscope";
    private static final String JFR_SETTINGS_RESOURCE = "/jfr/pyroscope.jfc";

    private static final String OS_NAME = "os.name";
    private Config config;
    private File tempJFRFile;
    private Path jcmdBin;
    private Path jfrSettingsPath;

    public JFRProfilerDelegate(Config config) {
        setConfig(config);
    }

    @Override
    public void setConfig(final Config config) {
        this.config = config;
        jcmdBin = findJcmdBin();
        jfrSettingsPath = findJfrSettingsPath();

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
    @Override
    public synchronized void start() {
        try {
            List<String> commands = new ArrayList<>();
            commands.add(jcmdBin.toString());
            commands.add(String.valueOf(CurrentPidProvider.getCurrentProcessId()));
            commands.add("JFR.start");
            commands.add("name=" + RECORDING_NAME);
            commands.add("filename=" + tempJFRFile.getAbsolutePath());
            commands.add("settings=" + jfrSettingsPath);
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            Process process = processBuilder.inheritIO().start();
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
    @Override
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
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("cannot stop JFR recording", e);
        }
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
                Pyroscope.LabelsWrapper.dump()
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path findJcmdBin() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        String jcmd = jcmdExecutable();
        Path jcmdBin = javaHome.resolve("bin").resolve(jcmd);
        //find jcmd binary
        if (!Files.isExecutable(jcmdBin)) {
            jcmdBin = javaHome.getParent().resolve("bin").resolve(jcmd);
            if (!Files.isExecutable(jcmdBin)) {
                throw new RuntimeException("cannot find executable jcmd in Java home");
            }
        }
        return jcmdBin;
    }

    private static String jcmdExecutable() {
        String jcmd = "jcmd";
        if (isWindowsOS()) {
            jcmd = "jcmd.exe";
        }
        return jcmd;
    }

    private static Path findJfrSettingsPath() {
        try (InputStream inputStream = JFRProfilerDelegate.class.getResourceAsStream(JFR_SETTINGS_RESOURCE)) {
            Path jfrSettingsPath = Files.createTempFile("pyroscope", ".jfc");
            Files.copy(inputStream, jfrSettingsPath, StandardCopyOption.REPLACE_EXISTING);
            return jfrSettingsPath;
        } catch (IOException e) {
            throw new UncheckedIOException(format("unable to load %s from classpath", JFR_SETTINGS_RESOURCE), e);
        }
    }

    private static boolean isWindowsOS() {
        String osName = System.getProperty(OS_NAME);
        if (osName.contains("Windows")) {
            return true;
        }
        return false;
    }

}
