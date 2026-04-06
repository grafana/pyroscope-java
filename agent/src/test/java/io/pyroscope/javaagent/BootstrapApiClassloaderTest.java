package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BootstrapApiClassloaderTest {

    @Test
    void javaAgentPublishesBootstrapCompatibleProfilerApi() throws Exception {
        String shadowJarPath = System.getProperty("shadowJar.path");
        assertNotNull(shadowJarPath, "System property 'shadowJar.path' is not set. Run this test via Gradle.");
        assertTrue(new File(shadowJarPath).exists(), "shadowJar not found at: " + shadowJarPath);

        String classPath = System.getProperty("java.class.path");
        assertNotNull(classPath, "System property 'java.class.path' is not set");

        String javaBinary = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();

        ProcessBuilder processBuilder = new ProcessBuilder(
            javaBinary,
            "-javaagent:" + shadowJarPath,
            "-cp", classPath,
            BootstrapApiClassloaderProbe.class.getName()
        )
            .redirectErrorStream(true)
            .directory(new File(System.getProperty("user.dir")));
        processBuilder.environment().put("PYROSCOPE_APPLICATION_NAME", "bootstrap-api-test");
        processBuilder.environment().put("PYROSCOPE_LOG_LEVEL", "debug");

        Process process = processBuilder.start();

        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Timed out waiting for javaagent probe process");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "Probe process failed:\n" + output);
        assertTrue(output.contains("bootstrap-api-ok"), "Probe did not report success:\n" + output);
    }
}
