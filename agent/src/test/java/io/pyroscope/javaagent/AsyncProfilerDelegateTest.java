package io.pyroscope.javaagent;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.config.ProfilingMode;
import io.pyroscope.labels.v2.Pyroscope;
import one.profiler.AsyncProfiler;
import one.profiler.Counter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncProfilerDelegateTest {

    @AfterEach
    void resetStaticLabels() {
        Pyroscope.setStaticLabels(Collections.emptyMap());
    }

    @Test
    void timeoutAddsOneSecond() {
        assertEquals(11, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofSeconds(10)));
    }

    @Test
    void timeoutRoundsUpToWholeSeconds() {
        assertEquals(2, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofMillis(1500)));
        assertEquals(1, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofMillis(1)));
    }

    @Test
    void pullConfigurationProducesPprofFromCollapsedSamples() throws Exception {
        Map<String, String> configuredLabels = new HashMap<>();
        configuredLabels.put("configured", "true");
        configuredLabels.put("precedence", "configured");
        Pyroscope.setStaticLabels(mapOf("static", "true", "precedence", "static"));
        Config config = new Config.Builder()
            .setProfilingMode(ProfilingMode.PULL)
            .setFormat(Format.PPROF)
            .setApplicationName("demo{application=true,precedence=application}")
            .setLabels(configuredLabels)
            .setProfilingInterval(Duration.ofMillis(10))
            .setUploadInterval(Duration.ofSeconds(2))
            .build();
        AsyncProfiler profiler = mock(AsyncProfiler.class);
        when(profiler.dumpCollapsed(Counter.SAMPLES)).thenReturn("root;leaf 3\n");
        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config, profiler);

        delegate.start();
        Snapshot snapshot = delegate.dumpProfile(Instant.ofEpochSecond(100), Instant.ofEpochSecond(102));
        delegate.stop();

        verify(profiler).execute(AsyncProfilerDelegate.createStartCommand(config, Format.PPROF, null));
        verify(profiler).dumpCollapsed(Counter.SAMPLES);
        verify(profiler).stop();
        assertEquals(Format.PPROF, snapshot.format);
        assertEquals(EventType.ITIMER, snapshot.eventType);
        assertEquals(mapOf(
            "application", "true",
            "configured", "true",
            "static", "true",
            "precedence", "static"), labels(snapshot.data));
    }

    private static Map<String, String> labels(byte[] profileBytes) throws Exception {
        UnknownFieldSet profile;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(profileBytes))) {
            profile = UnknownFieldSet.parseFrom(gzip);
        }
        List<ByteString> strings = profile.getField(6).getLengthDelimitedList();
        UnknownFieldSet sample = UnknownFieldSet.parseFrom(
            profile.getField(2).getLengthDelimitedList().get(0));
        Map<String, String> labels = new HashMap<>();
        for (ByteString encoded : sample.getField(3).getLengthDelimitedList()) {
            UnknownFieldSet label = UnknownFieldSet.parseFrom(encoded);
            int key = label.getField(1).getVarintList().get(0).intValue();
            int value = label.getField(2).getVarintList().get(0).intValue();
            labels.put(strings.get(key).toStringUtf8(), strings.get(value).toStringUtf8());
        }
        return labels;
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
