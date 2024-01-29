package io.pyroscope.labels.io.pyroscope;

import one.profiler.AsyncProfiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        final String tmpDir = System.getProperty("java.io.tmpdir");
        final File targetDir = new File(tmpDir, userName + "-pyroscope/");
        targetDir.mkdirs();

        try (final InputStream is = loadResource(fileName)) {
            final Path target = targetDir.toPath().resolve(targetLibraryFileName(fileName)).toAbsolutePath();
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
        Path filePath = Paths.get("build", "async-profiler", "native", fileName);
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
                        if (isMusl()) {
                            arch = "musl-x64";
                        } else {
                            arch = "x64";
                        }
                        break;

                    case "aarch64":
                        if (isMusl()) {
                            arch = "musl-arm64";
                        } else {
                            arch = "arm64";
                        }
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

    private static boolean isMusl() {
        // allow user to force musl/glibc it in case next checks fails
        String env = System.getenv("PYROSCOPE_MUSL");
        if (env == null) {
            env = System.getProperty("pyroscope.musl");
        }
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        // check ldd on currently running jvm
        // $ ldd /usr/lib/jvm/java-11-openjdk/bin/java
        //    /lib/ld-musl-x86_64.so.1 (0x7f337ca6c000)
        //    libjli.so => /usr/lib/jvm/java-11-openjdk/bin/../lib/jli/libjli.so (0x7f337ca55000)
        //    libc.musl-x86_64.so.1 => /lib/ld-musl-x86_64.so.1 (0x7f337ca6c000)
        File javaExecutable = new File(System.getProperty("java.home") + "/bin/java");
        if (javaExecutable.exists()) {
            for (String l : runProcess("ldd", javaExecutable.getAbsolutePath())) {
                if (l.contains("ld-musl-") || l.contains("libc.musl-")) {
                    return true;
                }
            }
            return false;
        }
        // $  ldd --version
        // musl libc (x86_64)
        for (String l : runProcess("ldd", "--version")) {
            if (l.contains("musl")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> runProcess(String... cmd) {
        List<String> lines = new ArrayList<>();
        try {
            Process pr = new ProcessBuilder(Arrays.<String>asList(cmd))
                .redirectErrorStream(true) // ldd --version prints to stderr
                .start();
            BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                lines.add(line);
            }
            try {
                pr.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pr.destroy();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }
}
