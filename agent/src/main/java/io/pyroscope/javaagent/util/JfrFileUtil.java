package io.pyroscope.javaagent.util;

import io.pyroscope.javaagent.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JfrFileUtil {
    private JfrFileUtil() {
    }

    public static File createJfrFile(Config config) throws IOException {
        if (config.jfrDir != null && !config.jfrDir.isEmpty()) {
            Path jfrDirPath = Paths.get(config.jfrDir);
            Files.createDirectories(jfrDirPath);
            return Files.createTempFile(jfrDirPath, "pyroscope", ".jfr").toFile();
        }
        return File.createTempFile("pyroscope", ".jfr");
    }
}
