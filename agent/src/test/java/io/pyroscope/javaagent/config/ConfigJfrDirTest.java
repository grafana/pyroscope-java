package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.api.ConfigurationProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigJfrDirTest {

    @Test
    void testConfigBuilderWithJfrDir() {
        String testDir = "/custom/jfr/path";
        Config config = new Config.Builder()
            .setJfrDir(testDir)
            .build();

        assertEquals(testDir, config.jfrDir);
    }

    @Test
    void testConfigBuilderWithoutJfrDir() {
        Config config = new Config.Builder()
            .build();

        assertNull(config.jfrDir);
    }

    @Test
    void testConfigBuilderWithEmptyJfrDir() {
        Config config = new Config.Builder()
            .setJfrDir("")
            .build();

        assertEquals("", config.jfrDir);
    }

    @Test
    void testConfigBuildFromConfigurationProviderWithJfrDir() {
        ConfigurationProvider cp = mock(ConfigurationProvider.class);
        when(cp.get("PYROSCOPE_AGENT_ENABLED")).thenReturn(null);
        when(cp.get("PYROSCOPE_APPLICATION_NAME")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_TYPE")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILING_INTERVAL")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_EVENT")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_ALLOC")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_LOCK")).thenReturn(null);
        when(cp.get("PYROSCOPE_UPLOAD_INTERVAL")).thenReturn(null);
        when(cp.get("PYROSCOPE_JAVA_STACK_DEPTH_MAX")).thenReturn(null);
        when(cp.get("PYROSCOPE_LOG_LEVEL")).thenReturn(null);
        when(cp.get("PYROSCOPE_SERVER_ADDRESS")).thenReturn(null);
        when(cp.get("PYROSCOPE_AUTH_TOKEN")).thenReturn(null);
        when(cp.get("PYROSCOPE_JFR_PROFILER_SETTINGS")).thenReturn(null);
        when(cp.get("PYROSCOPE_FORMAT")).thenReturn(null);
        when(cp.get("PYROSCOPE_PUSH_QUEUE_CAPACITY")).thenReturn(null);
        when(cp.get("PYROSCOPE_LABELS")).thenReturn(null);
        when(cp.get("PYROSCOPE_INGEST_MAX_TRIES")).thenReturn(null);
        when(cp.get("PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR")).thenReturn(null);
        when(cp.get("PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS")).thenReturn(null);
        when(cp.get("PYROSCOPE_ALLOC_LIVE")).thenReturn(null);
        when(cp.get("PYROSCOPE_GC_BEFORE_DUMP")).thenReturn(null);
        when(cp.get("PYROSCOPE_HTTP_HEADERS")).thenReturn(null);
        when(cp.get("PYROSCOPE_SAMPLING_DURATION")).thenReturn(null);
        when(cp.get("PYROSCOPE_TENANT_ID")).thenReturn(null);
        when(cp.get("PYROSCOPE_AP_LOG_LEVEL")).thenReturn(null);
        when(cp.get("PYROSCOPE_AP_EXTRA_ARGUMENTS")).thenReturn(null);
        when(cp.get("PYROSCOPE_BASIC_AUTH_USER")).thenReturn(null);
        when(cp.get("PYROSCOPE_BASIC_AUTH_PASSWORD")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILE_EXPORT_TIMEOUT")).thenReturn(null);

        String testDir = "/var/lib/pyroscope/jfr";
        when(cp.get("PYROSCOPE_JFR_DIR")).thenReturn(testDir);

        Config config = Config.build(cp);

        assertEquals(testDir, config.jfrDir);
    }

    @Test
    void testConfigBuildFromConfigurationProviderWithoutJfrDir() {
        ConfigurationProvider cp = mock(ConfigurationProvider.class);
        when(cp.get("PYROSCOPE_AGENT_ENABLED")).thenReturn(null);
        when(cp.get("PYROSCOPE_APPLICATION_NAME")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_TYPE")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILING_INTERVAL")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_EVENT")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_ALLOC")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILER_LOCK")).thenReturn(null);
        when(cp.get("PYROSCOPE_UPLOAD_INTERVAL")).thenReturn(null);
        when(cp.get("PYROSCOPE_JAVA_STACK_DEPTH_MAX")).thenReturn(null);
        when(cp.get("PYROSCOPE_LOG_LEVEL")).thenReturn(null);
        when(cp.get("PYROSCOPE_SERVER_ADDRESS")).thenReturn(null);
        when(cp.get("PYROSCOPE_AUTH_TOKEN")).thenReturn(null);
        when(cp.get("PYROSCOPE_JFR_PROFILER_SETTINGS")).thenReturn(null);
        when(cp.get("PYROSCOPE_FORMAT")).thenReturn(null);
        when(cp.get("PYROSCOPE_PUSH_QUEUE_CAPACITY")).thenReturn(null);
        when(cp.get("PYROSCOPE_LABELS")).thenReturn(null);
        when(cp.get("PYROSCOPE_INGEST_MAX_TRIES")).thenReturn(null);
        when(cp.get("PYROSCOPE_EXPORT_COMPRESSION_LEVEL_JFR")).thenReturn(null);
        when(cp.get("PYROSCOPE_EXPORT_COMPRESSION_LEVEL_LABELS")).thenReturn(null);
        when(cp.get("PYROSCOPE_ALLOC_LIVE")).thenReturn(null);
        when(cp.get("PYROSCOPE_GC_BEFORE_DUMP")).thenReturn(null);
        when(cp.get("PYROSCOPE_HTTP_HEADERS")).thenReturn(null);
        when(cp.get("PYROSCOPE_SAMPLING_DURATION")).thenReturn(null);
        when(cp.get("PYROSCOPE_TENANT_ID")).thenReturn(null);
        when(cp.get("PYROSCOPE_AP_LOG_LEVEL")).thenReturn(null);
        when(cp.get("PYROSCOPE_AP_EXTRA_ARGUMENTS")).thenReturn(null);
        when(cp.get("PYROSCOPE_BASIC_AUTH_USER")).thenReturn(null);
        when(cp.get("PYROSCOPE_BASIC_AUTH_PASSWORD")).thenReturn(null);
        when(cp.get("PYROSCOPE_PROFILE_EXPORT_TIMEOUT")).thenReturn(null);
        when(cp.get("PYROSCOPE_JFR_DIR")).thenReturn(null);

        Config config = Config.build(cp);

        assertNull(config.jfrDir);
    }

    @Test
    void testConfigNewBuilderPreservesJfrDir() {
        String testDir = "/custom/jfr/path";
        Config original = new Config.Builder()
            .setJfrDir(testDir)
            .build();

        Config copy = original.newBuilder()
            .build();

        assertEquals(testDir, copy.jfrDir);
    }

    @Test
    void testConfigNewBuilderCanOverrideJfrDir() {
        String originalDir = "/original/path";
        String newDir = "/new/path";

        Config original = new Config.Builder()
            .setJfrDir(originalDir)
            .build();

        Config updated = original.newBuilder()
            .setJfrDir(newDir)
            .build();

        assertEquals(newDir, updated.jfrDir);
    }
}
