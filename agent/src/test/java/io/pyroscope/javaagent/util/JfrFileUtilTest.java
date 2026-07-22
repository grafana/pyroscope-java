package io.pyroscope.javaagent.util;

import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class JfrFileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateJfrFileInConfiguredDirectory() throws Exception {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setJfrDir(jfrDir)
            .build();

        File jfrFile = JfrFileUtil.createJfrFile(config);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().startsWith(jfrDir),
            "JFR file should be created in the configured directory");
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testCreateJfrFileInDefaultDirectoryWhenNotConfigured() throws Exception {
        Config config = new Config.Builder()
            .setJfrDir(null)
            .build();

        File jfrFile = JfrFileUtil.createJfrFile(config);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testCreateJfrFileInDefaultDirectoryWhenEmptyString() throws Exception {
        Config config = new Config.Builder()
            .setJfrDir("")
            .build();

        File jfrFile = JfrFileUtil.createJfrFile(config);

        assertNotNull(jfrFile);
        assertTrue(jfrFile.getAbsolutePath().endsWith(".jfr"),
            "JFR file should have .jfr extension");
    }

    @Test
    void testDirectoriesCreatedIfNotExists() throws Exception {
        Path jfrDir = tempDir.resolve("deeply/nested/jfr/directory");
        assertFalse(Files.exists(jfrDir), "Test directory should not exist initially");

        Config config = new Config.Builder()
            .setJfrDir(jfrDir.toString())
            .build();

        File jfrFile = JfrFileUtil.createJfrFile(config);

        assertTrue(Files.exists(jfrDir),
            "Configured directory should be created");
        assertTrue(Files.isDirectory(jfrDir),
            "Created path should be a directory");
        assertTrue(jfrFile.getAbsolutePath().startsWith(jfrDir.toString()),
            "JFR file should be in the created directory");
    }

    @Test
    void testMultipleFilesInSameDirectory() throws Exception {
        String jfrDir = tempDir.toString();
        Config config = new Config.Builder()
            .setJfrDir(jfrDir)
            .build();

        File jfrFile1 = JfrFileUtil.createJfrFile(config);
        File jfrFile2 = JfrFileUtil.createJfrFile(config);

        assertNotNull(jfrFile1);
        assertNotNull(jfrFile2);
        assertNotEquals(jfrFile1.getAbsolutePath(), jfrFile2.getAbsolutePath(),
            "Each call should create a new unique file");
        assertTrue(Files.exists(jfrFile1.toPath()),
            "First file should exist");
        assertTrue(Files.exists(jfrFile2.toPath()),
            "Second file should exist");
    }

    @Test
    void testJfrFileNamePattern() throws Exception {
        Config config = new Config.Builder()
            .setJfrDir(tempDir.toString())
            .build();

        File jfrFile = JfrFileUtil.createJfrFile(config);
        String fileName = jfrFile.getName();

        assertTrue(fileName.startsWith("pyroscope"),
            "JFR file name should start with 'pyroscope'");
        assertTrue(fileName.endsWith(".jfr"),
            "JFR file name should end with '.jfr'");
    }
}
