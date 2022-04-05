package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
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
        String os;
        String arch;
        final String osProperty = System.getProperty("os.name");
        final String archProperty = System.getProperty("os.arch");
        switch (osProperty) {
            case "Linux":
                os = "linux";
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
     *     {@code libasyncProfiler-linux-x64-7b43b7cc6c864dd729cc7dcdb6e3db8f5ee5b4a4.so}</p>
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

    public static void init() {
        // Do nothing, rely in the static initialization in the static block.
    }

    private final AsyncProfiler instance = AsyncProfiler.getInstance(libraryPath);

    // TODO this is actually start of snapshot, not profiling as a whole
    private Instant profilingStarted = null;
    private File tempFile;

    Profiler(final Logger logger, final EventType eventType, final String alloc, final Duration interval, final Format format) {
        this.logger = logger;
        this.alloc = alloc;
        this.eventType = eventType;
        this.interval = interval;
        this.format = format;
    }

    // TODO new method for starting new snapshot/batch
    final synchronized void start() {
        if (format == Format.JFR) {
            startJFR();
        } else {
            instance.start(eventType.id, interval.toNanos());
        }
        profilingStarted = Instant.now();
        logger.info("Profiling started");
    }

    final synchronized Snapshot dump() {
        if (profilingStarted == null) {
            throw new IllegalStateException("Profiling is not started");
        }

        final Snapshot result = new Snapshot(
            eventType,
            profilingStarted,
            Instant.now(),
            format == Format.JFR ? dumpJFR() : instance.dumpCollapsed(Counter.SAMPLES).getBytes(StandardCharsets.UTF_8)
        );

        // TODO use `this.start()` or analogue
        profilingStarted = Instant.now();
        if (format == Format.JFR) {
            restartJFR();
        } else {
            instance.stop();
            instance.start(eventType.id, interval.toNanos());
        }
        return result;
    }

    final private void startJFR() {
        try {
            // flight recorder is built on top of a file descriptor, so we need a file.
            tempFile = File.createTempFile("pyroscope", ".jfr");
            tempFile.deleteOnExit();
            instance.execute(String.format("start,event=%s,alloc=%s,interval=%s,file=%s", eventType.id, alloc, interval.toNanos(), tempFile.toString()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    final private void restartJFR() {
        try {
            instance.execute(String.format("start,event=%s,alloc=%s,interval=%s,file=%s", eventType.id, alloc, interval.toNanos(), tempFile.toString()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    final private byte[] dumpJFR() {
        try {
            instance.stop();
            byte[] bytes = new byte[(int) tempFile.length()];
            new DataInputStream(new FileInputStream(tempFile)).readFully(bytes);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
