package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncProfilerDelegateCommandTest {
    @Test
    void otlpStartCommandIncludesConfiguredProfilerOptionsWithoutJfrFile() {
        Config config = new Config.Builder()
            .setFormat(Format.OTLP)
            .setProfilingAlloc("512k")
            .setProfilingLock("10ms")
            .setJavaStackDepthMax(1024)
            .setAPLogLevel("debug")
            .setAPExtraArguments("threads")
            .build();

        String command = AsyncProfilerDelegate.createStartCommand(config, Format.OTLP, null);

        assertTrue(command.contains("alloc=512k"));
        assertTrue(command.contains("lock=10ms"));
        assertTrue(command.contains("jstackdepth=1024"));
        assertTrue(command.contains("loglevel=debug"));
        assertTrue(command.contains(",threads"));
        assertFalse(command.contains("file="));
    }
}
