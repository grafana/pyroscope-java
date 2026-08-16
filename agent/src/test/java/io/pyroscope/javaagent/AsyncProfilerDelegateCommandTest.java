package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncProfilerDelegateCommandTest {
    @Test
    void otlpStartCommandIncludesConfiguredOptionsWithoutJfrFile() {
        Config config = new Config.Builder()
            .setFormat(Format.OTLP)
            .setJavaStackDepthMax(1024)
            .setAPLogLevel("debug")
            .setAPExtraArguments("threads")
            .setUploadInterval(Duration.ofSeconds(10))
            .build();

        String command = AsyncProfilerDelegate.createStartCommand(config, Format.OTLP, null);

        assertTrue(command.contains("jstackdepth=1024"));
        assertTrue(command.contains("loglevel=debug"));
        assertTrue(command.contains(",threads"));
        assertTrue(command.contains("timeout=11"));
        assertFalse(command.contains("file="));
    }
}
