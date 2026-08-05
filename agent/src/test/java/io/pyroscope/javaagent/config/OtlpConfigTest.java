package io.pyroscope.javaagent.config;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.ConfigurationProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OtlpConfigTest {
    @Test
    void acceptsOtlpFormat() {
        Config config = Config.build(provider("PYROSCOPE_FORMAT", "OTLP"));
        assertEquals(Format.OTLP, config.format);
    }

    @Test
    void rejectsOtlpWithJfrProfiler() {
        ConfigurationProvider provider = provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_TYPE", "JFR");
        assertThrows(IllegalArgumentException.class, () -> Config.build(provider));
    }

    @Test
    void rejectsOtlpWithAllocationProfiling() {
        ConfigurationProvider provider = provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_ALLOC", "512k");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> Config.build(provider));
        assertEquals("OTLP format does not support allocation or lock profiling", exception.getMessage());
    }

    @Test
    void rejectsOtlpWithLockProfiling() {
        ConfigurationProvider provider = provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_LOCK", "10ms");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> Config.build(provider));
        assertEquals("OTLP format does not support allocation or lock profiling", exception.getMessage());
    }

    private static ConfigurationProvider provider(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values::get;
    }
}
