package io.pyroscope.javaagent.util;

import io.pyroscope.javaagent.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TmpFileUtil {
    private TmpFileUtil() {
    }

    public static File createJfrFile(Config config) throws IOException {
        return createTempFile(config.tmpDir, "pyroscope", ".jfr");
    }

    public static File createTempFile(String dir, String prefix, String suffix) throws IOException {
        if (dir != null && !dir.isEmpty()) {
            Path dirPath;
            try {
                dirPath = Paths.get(dir);
            } catch (InvalidPathException e) {
                throw new IOException("Invalid tmp dir path: " + dir, e);
            }
            Files.createDirectories(dirPath);
            return Files.createTempFile(dirPath, prefix, suffix).toFile().getAbsoluteFile();
        }
        return File.createTempFile(prefix, suffix).getAbsoluteFile();
    }
}
