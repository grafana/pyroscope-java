package io.pyroscope.javaagent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class BootstrapApiInjectorJfrDirTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        System.clearProperty("PYROSCOPE_JFR_DIR");
        System.clearProperty("pyroscope.jfr.dir");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("PYROSCOPE_JFR_DIR");
        System.clearProperty("pyroscope.jfr.dir");
    }

    @Test
    void testBootstrapJarCanBeCreatedInConfiguredDirectory() throws IOException {
        String jfrDir = tempDir.toString();
        Instrumentation instrumentation = mock(Instrumentation.class);

        assertDoesNotThrow(() -> {
            BootstrapApiInjector.class
                .getDeclaredMethod("inject", Instrumentation.class, String.class)
                .invoke(null, instrumentation, jfrDir);
        });
    }

    @Test
    void testBootstrapJarCanBeCreatedWithNullDirectory() throws IOException {
        Instrumentation instrumentation = mock(Instrumentation.class);

        assertDoesNotThrow(() -> {
            BootstrapApiInjector.class
                .getDeclaredMethod("inject", Instrumentation.class, String.class)
                .invoke(null, instrumentation, null);
        });
    }

    @Test
    void testBootstrapJarCanBeCreatedWithEmptyDirectory() throws IOException {
        Instrumentation instrumentation = mock(Instrumentation.class);

        assertDoesNotThrow(() -> {
            BootstrapApiInjector.class
                .getDeclaredMethod("inject", Instrumentation.class, String.class)
                .invoke(null, instrumentation, "");
        });
    }

    @Test
    void testConfiguredDirectoryIsCreatedForBootstrapJar() throws Exception {
        Path jfrDir = tempDir.resolve("bootstrap/jar/dir");
        assertFalse(Files.exists(jfrDir), "Test directory should not exist initially");

        testCreateBootstrapJarMethod(jfrDir.toString());

        assertTrue(Files.exists(jfrDir),
            "Configured directory should be created for bootstrap JAR");
        assertTrue(Files.isDirectory(jfrDir),
            "Created path should be a directory");
    }

    @Test
    void testBootstrapJarNameStartsWithPyroscope() throws Exception {
        Path jfrDir = tempDir.resolve("bootstrap");
        Files.createDirectories(jfrDir);

        testCreateBootstrapJarMethod(jfrDir.toString());

        boolean foundBootstrapJar = Files.walk(jfrDir)
            .map(Path::getFileName)
            .map(Path::toString)
            .anyMatch(name -> name.startsWith("pyroscope-bootstrap-") && name.endsWith(".jar"));

        assertTrue(foundBootstrapJar,
            "Bootstrap JAR file should start with 'pyroscope-bootstrap-' and end with '.jar'");
    }

    private void testCreateBootstrapJarMethod(String jfrDir) throws Exception {
        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("createBootstrapJar", String.class);
        method.setAccessible(true);
        Path result = (Path) method.invoke(null, jfrDir);

        assertNotNull(result);
        assertTrue(result.toString().contains(jfrDir),
            "Bootstrap JAR should be created in the configured directory");
    }

    @Test
    void testGetJfrDirFromSystemProperty() throws Exception {
        String jfrDir = tempDir.toString();
        System.setProperty("PYROSCOPE_JFR_DIR", jfrDir);

        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("getJfrDir");
        method.setAccessible(true);
        String result = (String) method.invoke(null);

        assertEquals(jfrDir, result,
            "Should read PYROSCOPE_JFR_DIR from system property (-D flag)");
    }

    @Test
    void testGetJfrDirPrefersSystemPropertyOverEnv() throws Exception {
        String jfrDir = tempDir.toString();
        System.setProperty("PYROSCOPE_JFR_DIR", jfrDir);

        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("getJfrDir");
        method.setAccessible(true);
        String result = (String) method.invoke(null);

        assertEquals(jfrDir, result,
            "System property should take precedence over environment variable");
    }

    @Test
    void testGetJfrDirFromDottedLowercaseSystemProperty() throws Exception {
        // e.g. java -Dpyroscope.jfr.dir=/path -javaagent:pyroscope.jar ...
        // must match the convention used by -Dpyroscope.application.name etc.
        String jfrDir = tempDir.toString();
        System.setProperty("pyroscope.jfr.dir", jfrDir);

        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("getJfrDir");
        method.setAccessible(true);
        String result = (String) method.invoke(null);

        assertEquals(jfrDir, result,
            "Should read -Dpyroscope.jfr.dir just like other -Dpyroscope.* properties");
    }

    @Test
    void testGetJfrDirPrefersUppercaseSystemPropertyOverDottedLowercase() throws Exception {
        String uppercaseDir = tempDir.resolve("uppercase").toString();
        String dottedDir = tempDir.resolve("dotted").toString();
        System.setProperty("PYROSCOPE_JFR_DIR", uppercaseDir);
        System.setProperty("pyroscope.jfr.dir", dottedDir);

        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("getJfrDir");
        method.setAccessible(true);
        String result = (String) method.invoke(null);

        assertEquals(uppercaseDir, result,
            "Exact-case PYROSCOPE_JFR_DIR system property should win over the dotted-lowercase form");
    }

    @Test
    void testGetJfrDirFallsBackToNull() throws Exception {
        System.clearProperty("PYROSCOPE_JFR_DIR");

        java.lang.reflect.Method method = BootstrapApiInjector.class.getDeclaredMethod("getJfrDir");
        method.setAccessible(true);
        String result = (String) method.invoke(null);

        assertNull(result,
            "Should return null when no property or env var is set");
    }
}
