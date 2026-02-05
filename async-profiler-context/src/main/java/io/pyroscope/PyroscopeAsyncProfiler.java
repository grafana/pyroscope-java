package io.pyroscope;

import one.profiler.AsyncProfiler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class PyroscopeAsyncProfiler {
    static final String libraryPath;

    static {
        try {
            libraryPath = deployLibrary();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AsyncProfiler getAsyncProfiler() {
        return AsyncProfiler.getInstance(libraryPath);
    }

    /**
     * Extracts the profiler library file from the JAR and puts it in the temp directory.
     *
     * @return path to the extracted library
     */
    private static String deployLibrary() throws IOException {
        final String fileName = libraryFileName();
        final String userName = System.getProperty("user.name");
        final Path targetDir = Files.createTempDirectory(userName + "-pyroscope");

        try (final InputStream is = loadResource(fileName)) {
            final Path target = targetDir.resolve(targetLibraryFileName(fileName)).toAbsolutePath();
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        }
    }

    /**
     * load resource either from jar resources for production or from local file system for testing
     *
     * @param fileName
     * @return
     * @throws FileNotFoundException
     */
    private static InputStream loadResource(String fileName) throws IOException {
        InputStream res = PyroscopeAsyncProfiler.class.getResourceAsStream("/" + fileName);
        if (res != null) {
            return res; // from shadowJar
        }
        Path filePath = Paths.get("async-profiler-grafana-fork-dist", "lib", fileName);
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
    private static String targetLibraryFileName(final String libraryFileName) throws IOException {
        if (!libraryFileName.endsWith(".so")) {
            throw new IllegalArgumentException("Incorrect library file name: " + libraryFileName);
        }

        final String checksumFileName = libraryFileName + ".sha1";
        String checksum;
        try (final InputStream is = loadResource(checksumFileName)) {
            byte[] buf = new byte[40];
            int bufLen = is.read(buf);
            if (bufLen <= 0) throw new IOException("checksum read fail");
            checksum = new String(buf, 0, bufLen, StandardCharsets.UTF_8);
        }

        return libraryFileName.substring(0, libraryFileName.length() - 3) + "-" + checksum + ".so";
    }
}
