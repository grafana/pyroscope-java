package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JFRJCMDProfilerDelegateJfrDirTest {

    @TempDir
    Path tempDir;

    @Test
    void testJFRFileCreatedInConfiguredDirectory() throws IOException {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setTmpDir(jfrDir)
            .build();

        JFRJCMDProfilerDelegate delegate = new JFRJCMDProfilerDelegate(config);

        assertNotNull(delegate);
        File jfrFile = getPrivateFieldValue(delegate, "tempJFRFile");

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().startsWith(jfrDir),
            "JFR file should be created in the configured directory");
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testJFRFileCreatedInDefaultDirectoryWhenNotConfigured() throws IOException {
        Config config = new Config.Builder()
            .setTmpDir(null)
            .build();

        JFRJCMDProfilerDelegate delegate = new JFRJCMDProfilerDelegate(config);

        assertNotNull(delegate);
        File jfrFile = getPrivateFieldValue(delegate, "tempJFRFile");

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testJFRFileCreatedInDefaultDirectoryWhenEmptyString() throws IOException {
        Config config = new Config.Builder()
            .setTmpDir("")
            .build();

        JFRJCMDProfilerDelegate delegate = new JFRJCMDProfilerDelegate(config);

        assertNotNull(delegate);
        File jfrFile = getPrivateFieldValue(delegate, "tempJFRFile");

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testConfiguredDirectoryIsCreatedIfNotExists() throws IOException {
        Path jfrDir = tempDir.resolve("nested/jfr/directory");
        assertFalse(Files.exists(jfrDir), "Test directory should not exist initially");

        Config config = new Config.Builder()
            .setTmpDir(jfrDir.toString())
            .build();

        JFRJCMDProfilerDelegate delegate = new JFRJCMDProfilerDelegate(config);

        assertNotNull(delegate);
        assertTrue(Files.exists(jfrDir),
            "Configured directory should be created");
        assertTrue(Files.isDirectory(jfrDir),
            "Created path should be a directory");
    }

    @Test
    void testJFRFileNameStartsWithPyroscope() throws IOException {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setTmpDir(jfrDir)
            .build();

        JFRJCMDProfilerDelegate delegate = new JFRJCMDProfilerDelegate(config);

        File jfrFile = getPrivateFieldValue(delegate, "tempJFRFile");
        String fileName = jfrFile.getName();

        assertTrue(fileName.startsWith("pyroscope"),
            "JFR file name should start with 'pyroscope'");
    }

    private <T> T getPrivateFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
