package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.Labels;
import one.profiler.AsyncProfiler;
import one.profiler.Counter;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

class Profiler {
    private final Logger logger;
    private final EventType eventType;
    private final String alloc;
    private final String lock;
    private final Duration interval;
    private final Format format;

    private static String libraryPath;

    static {
        try {
            deployLibrary();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts the profiler library file from the JAR and puts it in the temp directory.
     */
    private static void deployLibrary() throws IOException {
        final String fileName = libraryFileName();

        final String userName = System.getProperty("user.name");
        final String tmpDir = System.getProperty("java.io.tmpdir");
        final File targetDir = new File(tmpDir, userName + "-pyroscope/");
        targetDir.mkdirs();

        try (final InputStream is = Objects.requireNonNull(
            Profiler.class.getResourceAsStream("/" + fileName))) {
            final Path target = targetDir.toPath().resolve(targetLibraryFileName(fileName)).toAbsolutePath();
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            libraryPath = target.toString();
        }
    }

    /**
     * Creates the library file name based on the current OS and architecture name.
     */
    private static String libraryFileName() {
        String arch;
        final String osProperty = System.getProperty("os.name");
        final String archProperty = System.getProperty("os.arch");
        switch (osProperty) {
            case "Linux":
                switch (archProperty) {
                    case "amd64":
                        arch = "x64";
                        break;

                    case "aarch64":
                        arch = "arm64";
                        break;

                    default:
                        throw new RuntimeException("Unsupported architecture " + archProperty);
                }

                return "libasyncProfiler-linux-" + arch + ".so";

            case "Mac OS X":
                switch (archProperty) {
                    case "x86_64":
                    case "aarch64":
                        return "libasyncProfiler-macos.so";
                    default:
                        throw new RuntimeException("Unsupported architecture " + archProperty);
                }

            default:
                throw new RuntimeException("Unsupported OS " + osProperty);
        }
    }

    /**
     * <p>Adds the checksum to the library file name.</p>
     *
     * <p>E.g. {@code libasyncProfiler-linux-x64.so} ->
     * {@code libasyncProfiler-linux-x64-7b43b7cc6c864dd729cc7dcdb6e3db8f5ee5b4a4.so}</p>
     */
    private static String targetLibraryFileName(final String libraryFileName) throws IOException {
        if (!libraryFileName.endsWith(".so")) {
            throw new IllegalArgumentException("Incorrect library file name: " + libraryFileName);
        }

        final String checksumFileName = libraryFileName + ".sha1";
        String checksum;
        try (final InputStream is = Objects.requireNonNull(
            Profiler.class.getResourceAsStream("/" + checksumFileName))) {
            checksum = InputStreamUtils.readToString(is);
        }

        return libraryFileName.substring(0, libraryFileName.length() - 3) + "-" + checksum + ".so";
    }

    private final AsyncProfiler instance = AsyncProfiler.getInstance(libraryPath);

    // TODO this is actually start of snapshot, not profiling as a whole
    private Instant profilingStarted = null;
    private final File tempJFRFile;

    Profiler(final Logger logger, final EventType eventType, final String alloc, final String lock, final Duration interval, final Format format) {
        this.logger = logger;
        this.alloc = alloc;
        this.lock = lock;
        this.eventType = eventType;
        this.interval = interval;
        this.format = format;

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

    final synchronized void start() {
        if (format == Format.JFR) {
            try {
                instance.execute(createJFRCommand());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            instance.start(eventType.id, interval.toNanos());
        }
        profilingStarted = Instant.now();
    }

    private String createJFRCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("start,event=").append(eventType.id);
        if (alloc != null && !alloc.isEmpty()) {
            sb.append(",alloc=").append(alloc);
        }
        if (lock != null && !lock.isEmpty()) {
            sb.append(",lock=").append(lock);
        }
        sb.append(",interval=").append(interval.toNanos())
            .append(",file=").append(tempJFRFile.toString());
        return sb.toString();
    }

    final synchronized Snapshot dump() {
        if (profilingStarted == null) {
            throw new IllegalStateException("Profiling is not started");
        }

        instance.stop();

        Snapshot result = dumpImpl();

        start();

        return result;
    }

    private Snapshot dumpImpl() {
        final byte[] data;
        if (format == Format.JFR) {
            data = dumpJFR();
        } else {
            data = instance.dumpCollapsed(Counter.SAMPLES).getBytes(StandardCharsets.UTF_8);
        }
        return new Snapshot(
            eventType,
            profilingStarted,
            Instant.now(),
            data,
            Labels.dump()
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
