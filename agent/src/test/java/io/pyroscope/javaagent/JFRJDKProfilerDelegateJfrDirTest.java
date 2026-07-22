package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class JFRJDKProfilerDelegateJfrDirTest {

    @TempDir
    Path tempDir;

    @Test
    void testJFRFileCreatedInConfiguredDirectory() throws Exception {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setJfrDir(jfrDir)
            .build();

        JFRJDKProfilerDelegate delegate = new JFRJDKProfilerDelegate(config);

        java.lang.reflect.Field field = JFRJDKProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        File jfrFile = (File) field.get(delegate);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().startsWith(jfrDir),
            "JFR file should be created in the configured directory");
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testJFRFileCreatedInDefaultDirectoryWhenNotConfigured() throws Exception {
        Config config = new Config.Builder()
            .setJfrDir(null)
            .build();

        JFRJDKProfilerDelegate delegate = new JFRJDKProfilerDelegate(config);

        java.lang.reflect.Field field = JFRJDKProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        File jfrFile = (File) field.get(delegate);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testJFRFileCreatedInDefaultDirectoryWhenEmptyString() throws Exception {
        Config config = new Config.Builder()
            .setJfrDir("")
            .build();

        JFRJDKProfilerDelegate delegate = new JFRJDKProfilerDelegate(config);

        java.lang.reflect.Field field = JFRJDKProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        File jfrFile = (File) field.get(delegate);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testConfiguredDirectoryIsCreatedIfNotExists() throws Exception {
        Path jfrDir = tempDir.resolve("nested/jdk/jfr/directory");
        assertFalse(Files.exists(jfrDir), "Test directory should not exist initially");

        Config config = new Config.Builder()
            .setJfrDir(jfrDir.toString())
            .build();

        JFRJDKProfilerDelegate delegate = new JFRJDKProfilerDelegate(config);

        assertTrue(Files.exists(jfrDir),
            "Configured directory should be created");
        assertTrue(Files.isDirectory(jfrDir),
            "Created path should be a directory");
    }

    @Test
    void testJFRFileNameStartsWithPyroscope() throws Exception {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setJfrDir(jfrDir)
            .build();

        JFRJDKProfilerDelegate delegate = new JFRJDKProfilerDelegate(config);

        java.lang.reflect.Field field = JFRJDKProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        File jfrFile = (File) field.get(delegate);

        String fileName = jfrFile.getName();
        assertTrue(fileName.startsWith("pyroscope"),
            "JFR file name should start with 'pyroscope'");
    }
}
