package io.pyroscope.javaagent.config;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.api.ConfigurationProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
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
    void acceptsOtlpWithAllocationProfiling() {
        Config config = Config.build(provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_EVENT", "alloc",
            "PYROSCOPE_PROFILER_ALLOC", "512k"));
        assertEquals(EventType.ALLOC, config.profilingEvent);
        assertEquals("512k", config.profilingAlloc);
    }

    @Test
    void acceptsOtlpWithLockProfiling() {
        Config config = Config.build(provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_EVENT", "lock",
            "PYROSCOPE_PROFILER_LOCK", "10ms"));
        assertEquals(EventType.LOCK, config.profilingEvent);
        assertEquals("10ms", config.profilingLock);
    }

    @Test
    void acceptsExplicitJfrEventSelection() {
        Config config = Config.build(provider(
            "PYROSCOPE_JFR_EVENTS", "lock"));
        assertEquals(EnumSet.of(EventType.LOCK), config.jfrEvents);
    }

    @Test
    void acceptsAllJfrEvents() {
        Config config = Config.build(provider(
            "PYROSCOPE_JFR_EVENTS", "all"));
        assertEquals(EnumSet.of(EventType.CPU, EventType.ALLOC, EventType.LOCK), config.jfrEvents);
    }

    @Test
    void copiedBuilderDefaultsFollowProfilingEvent() {
        Config original = new Config.Builder().build();
        Config copy = original.newBuilder().setProfilingEvent(EventType.ALLOC).build();
        Config secondCopy = copy.newBuilder().setProfilingEvent(EventType.LOCK).build();

        assertEquals(EnumSet.of(EventType.CPU), original.jfrEvents);
        assertEquals(EnumSet.of(EventType.ALLOC), copy.jfrEvents);
        assertEquals(EnumSet.of(EventType.LOCK), secondCopy.jfrEvents);
    }

    @Test
    void copiedProviderDefaultsFollowProfilingEvent() {
        for (String value : new String[] {null, "", " ", "invalid", "cpu,invalid", ","}) {
            Config original = Config.build(provider("PYROSCOPE_JFR_EVENTS", value));
            Config copy = original.newBuilder().setProfilingEvent(EventType.ALLOC).build();

            assertEquals(EnumSet.of(EventType.CPU), original.jfrEvents);
            assertEquals(EnumSet.of(EventType.ALLOC), copy.jfrEvents, "Selection: " + value);
        }
    }

    @Test
    void copiedExplicitSelectionsDoNotFollowProfilingEvent() {
        Config[] originals = {
            new Config.Builder().setJfrEvents(EnumSet.of(EventType.CPU)).build(),
            new Config.Builder().setJfrEvents(EnumSet.of(EventType.ALLOC, EventType.LOCK)).build(),
            Config.build(provider("PYROSCOPE_JFR_EVENTS", "cpu")),
            Config.build(provider("PYROSCOPE_JFR_EVENTS", "all"))
        };
        for (Config original : originals) {
            Config copy = original.newBuilder().setProfilingEvent(EventType.LOCK).build();
            Config secondCopy = copy.newBuilder().setProfilingEvent(EventType.ALLOC).build();

            assertEquals(original.jfrEvents, copy.jfrEvents);
            assertEquals(original.jfrEvents, secondCopy.jfrEvents);
        }
    }

    @Test
    void clearingExplicitSelectionRestoresDefaultAcrossCopies() {
        Config original = new Config.Builder().setJfrEvents(EnumSet.of(EventType.LOCK)).build();
        for (EnumSet<EventType> selection : Arrays.<EnumSet<EventType>>asList(null, EnumSet.noneOf(EventType.class))) {
            Config cleared = original.newBuilder().setJfrEvents(selection).build();
            Config copy = cleared.newBuilder().setProfilingEvent(EventType.ALLOC).build();

            assertEquals(EnumSet.of(EventType.CPU), cleared.jfrEvents);
            assertEquals(EnumSet.of(EventType.ALLOC), copy.jfrEvents);
        }
    }

    @Test
    void unsupportedExplicitSelectionFollowsDefaultAcrossCopies() {
        Config original = new Config.Builder().setJfrEvents(EnumSet.of(EventType.ITIMER)).build();
        Config copy = original.newBuilder().setProfilingEvent(EventType.LOCK).build();

        assertEquals(EnumSet.of(EventType.CPU), original.jfrEvents);
        assertEquals(EnumSet.of(EventType.LOCK), copy.jfrEvents);
    }

    @Test
    void rejectsOtlpWithSimultaneousAllocationProfiling() {
        ConfigurationProvider provider = provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_ALLOC", "512k");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> Config.build(provider));
        assertEquals("OTLP format does not support multiple profiling events simultaneously", exception.getMessage());
    }

    @Test
    void rejectsOtlpWithSimultaneousLockProfiling() {
        ConfigurationProvider provider = provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_LOCK", "10ms");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> Config.build(provider));
        assertEquals("OTLP format does not support multiple profiling events simultaneously", exception.getMessage());
    }

    @Test
    void acceptsOtlpWithSequentialSamplingEvents() {
        Config config = Config.build(provider(
            "PYROSCOPE_FORMAT", "otlp",
            "PYROSCOPE_PROFILER_ALLOC", "512k",
            "PYROSCOPE_PROFILER_LOCK", "10ms",
            "PYROSCOPE_SAMPLING_DURATION", "1s",
            "PYROSCOPE_SAMPLING_EVENT_ORDER", "cpu,alloc,lock"));
        assertEquals(3, config.samplingEventOrder.size());
    }

    private static ConfigurationProvider provider(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values::get;
    }
}
