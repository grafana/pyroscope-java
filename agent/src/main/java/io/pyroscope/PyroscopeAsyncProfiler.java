package io.pyroscope;

import io.pyroscope.javaagent.config.APDistribution;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.DefaultConfigurationProvider;
import one.profiler.AsyncProfiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class PyroscopeAsyncProfiler {
    private static AsyncProfiler instance;
    private static boolean labelsSupported;
    private static boolean tracingContextSupported;
    private static boolean traceIdSupported;

    /**
     * Returns the async-profiler instance, initializing it if necessary from
     * environment variables / system properties / pyroscope.properties
     * (PYROSCOPE_AP_LIBRARY_PATH, PYROSCOPE_AP_DISTRIBUTION).
     */
    public static synchronized AsyncProfiler getAsyncProfiler() {
        if (instance != null) {
            return instance;
        }
        DefaultConfigurationProvider cp = DefaultConfigurationProvider.INSTANCE;
        return getAsyncProfiler(Config.apLibraryPath(cp), Config.apDistribution(cp));
    }

    /**
     * Returns the async-profiler instance, initializing it if necessary from the given config.
     */
    public static synchronized AsyncProfiler getAsyncProfiler(Config config) {
        return getAsyncProfiler(config.apLibraryPath, config.apDistribution);
    }

    /**
     * Returns the async-profiler instance. The library used is determined on the first call:
     * an explicit library path wins over the bundled distribution selection.
     */
    public static synchronized AsyncProfiler getAsyncProfiler(String externalLibraryPath, APDistribution distribution) {
        if (instance != null) {
            return instance;
        }
        final String libraryPath;
        if (externalLibraryPath != null && !externalLibraryPath.isEmpty()) {
            libraryPath = Paths.get(externalLibraryPath).toAbsolutePath().toString();
        } else {
            try {
                libraryPath = deployLibrary(distribution == null ? APDistribution.FORK : distribution);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
        final AsyncProfiler profiler = AsyncProfiler.getInstance(libraryPath);
        // Fork-only natives stay unbound when a genuine/external library is loaded,
        // so probe them once to know which features are available.
        labelsSupported = probe(() -> profiler.setContextId(0));
        tracingContextSupported = probe(() -> profiler.setTracingContext(0, 0));
        traceIdSupported = probe(() -> profiler.setTraceId(0, 0));
        instance = profiler;
        return instance;
    }

    /**
     * Whether the loaded library supports dynamic labels (setContextId).
     * Only the Grafana fork of async-profiler supports this.
     */
    public static synchronized boolean isLabelsSupported() {
        getAsyncProfiler();
        return labelsSupported;
    }

    /**
     * Whether the loaded library supports setTracingContext.
     * Only the Grafana fork of async-profiler supports this.
     */
    public static synchronized boolean isTracingContextSupported() {
        getAsyncProfiler();
        return tracingContextSupported;
    }

    /**
     * Whether the loaded library supports setTraceId.
     * Only the Grafana fork of async-profiler supports this.
     */
    public static synchronized boolean isTraceIdSupported() {
        getAsyncProfiler();
        return traceIdSupported;
    }

    private static boolean probe(Runnable call) {
        try {
            call.run();
            return true;
        } catch (final UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Extracts the profiler library file from the JAR and puts it in the temp directory.
     *
     * @return path to the extracted library
     */
    private static String deployLibrary(APDistribution distribution) throws IOException {
        final String fileName = libraryFileName();
        final String userName = System.getProperty("user.name");
        final Path targetDir = Files.createTempDirectory(userName + "-pyroscope");

        try (final InputStream is = loadResource(distribution, fileName)) {
            final Path target = targetDir.resolve(targetLibraryFileName(distribution, fileName)).toAbsolutePath();
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        }
    }

    /**
     * load resource either from jar resources for production or from local file system for testing
     *
     * @param fileName
     * @return
     * @throws IOException
     */
    private static InputStream loadResource(APDistribution distribution, String fileName) throws IOException {
        final String resourcePrefix = distribution == APDistribution.GENUINE ? "/genuine/" : "/";
        InputStream res = PyroscopeAsyncProfiler.class.getResourceAsStream(resourcePrefix + fileName);
        if (res != null) {
            return res; // from shadowJar
        }
        final String distDir = distribution == APDistribution.GENUINE
            ? "async-profiler-genuine-dist"
            : "async-profiler-grafana-fork-dist";
        Path filePath = Paths.get(distDir, "lib", fileName);
        return Files.newInputStream(filePath);
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
    private static String targetLibraryFileName(final APDistribution distribution, final String libraryFileName) throws IOException {
        if (!libraryFileName.endsWith(".so")) {
            throw new IllegalArgumentException("Incorrect library file name: " + libraryFileName);
        }

        final String checksumFileName = libraryFileName + ".sha1";
        String checksum;
        try (final InputStream is = loadResource(distribution, checksumFileName)) {
            byte[] buf = new byte[40];
            int bufLen = is.read(buf);
            if (bufLen <= 0) throw new IOException("checksum read fail");
            checksum = new String(buf, 0, bufLen, StandardCharsets.UTF_8);
        }

        return libraryFileName.substring(0, libraryFileName.length() - 3) + "-" + checksum + ".so";
    }
}
