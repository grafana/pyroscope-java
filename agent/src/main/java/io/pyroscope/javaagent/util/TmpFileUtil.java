package io.pyroscope.javaagent.util;

import io.pyroscope.javaagent.config.Config;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TmpFileUtil {
    private TmpFileUtil() {
    }

    public static File createJfrFile(Config config) throws IOException {
        return createTempFile(config.tmpDir, "pyroscope", ".jfr");
    }

    public static File createTempFile(@Nullable Path dir, String prefix, String suffix) throws IOException {
        if (dir != null) {
            Files.createDirectories(dir);
            return Files.createTempFile(dir, prefix, suffix).toFile().getAbsoluteFile();
        }
        return File.createTempFile(prefix, suffix).getAbsoluteFile();
    }
}
