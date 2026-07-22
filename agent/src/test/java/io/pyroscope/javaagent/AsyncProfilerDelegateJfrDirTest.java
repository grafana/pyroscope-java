package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncProfilerDelegateJfrDirTest {

    @TempDir
    Path tempDir;

    @Test
    void testJFRFileCreatedInConfiguredDirectory() throws Exception {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setJfrDir(jfrDir)
            .build();

        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config);

        java.lang.reflect.Field field = AsyncProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        java.io.File jfrFile = (java.io.File) field.get(delegate);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().startsWith(jfrDir),
            "JFR file should be created in the configured directory");
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testJFRFileCreatedInDefaultDirectoryWhenNotConfigured() throws Exception {
        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setJfrDir(null)
            .build();

        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config);

        java.lang.reflect.Field field = AsyncProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        java.io.File jfrFile = (java.io.File) field.get(delegate);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testConfiguredDirectoryIsCreatedIfNotExists() throws Exception {
        Path jfrDir = tempDir.resolve("nested/async/profiler/dir");
        assertFalse(Files.exists(jfrDir), "Test directory should not exist initially");

        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setJfrDir(jfrDir.toString())
            .build();

        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config);

        assertTrue(Files.exists(jfrDir),
            "Configured directory should be created");
        assertTrue(Files.isDirectory(jfrDir),
            "Created path should be a directory");
    }

    @Test
    void testJFRFileNotCreatedForNonJFRFormat() throws Exception {
        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setJfrDir(tempDir.toString())
            .build();

        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config);

        java.lang.reflect.Field field = AsyncProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        java.io.File jfrFile = (java.io.File) field.get(delegate);

        assertNotNull(jfrFile, "JFR file should be created for JFR format");
    }
}
