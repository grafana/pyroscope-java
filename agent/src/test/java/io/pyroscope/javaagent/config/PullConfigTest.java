package io.pyroscope.javaagent.config;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PullConfigTest {
    @Test
    void pushRemainsTheDefault() {
        Config config = new Config.Builder().build();
        assertEquals(ProfilingMode.PUSH, config.profilingMode);
        assertEquals(Format.JFR, config.format);
    }

    @Test
    void readsPullConfigurationAndPreservesItWhenCopied() {
        Map<String, String> values = new HashMap<>();
        values.put("PYROSCOPE_PROFILING_MODE", "pull");
        values.put("PYROSCOPE_FORMAT", "pprof");
        values.put("PYROSCOPE_PULL_BIND_ADDRESS", " \tlocalhost\r\n");
        values.put("PYROSCOPE_PULL_PORT", "9090");
        Config config = Config.build(values::get).newBuilder().build();
        assertEquals(ProfilingMode.PULL, config.profilingMode);
        assertEquals(Format.PPROF, config.format);
        assertEquals("localhost", config.pullBindAddress);
        assertEquals(9090, config.pullPort);
    }

    @Test
    void trimsBindAddressFromBuilderAndPreservesItWhenCopied() {
        Config config = pull().setPullBindAddress(" \t127.0.0.1\r\n").build();
        assertEquals("127.0.0.1", config.pullBindAddress);
        assertEquals("127.0.0.1", config.newBuilder().build().pullBindAddress);
    }

    @Test
    void defaultsToLoopbackAndAcceptsAnEphemeralPort() {
        assertEquals("127.0.0.1", pull().build().pullBindAddress);
        assertEquals(4041, pull().build().pullPort);
        assertEquals(0, pull().setPullPort(0).build().pullPort);
    }

    @Test
    void rejectsUnsupportedProfilingConfigurations() {
        assertThrows(IllegalArgumentException.class, () -> pull().setFormat(Format.JFR).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setFormat(Format.OTLP).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setProfilerType(ProfilerType.JFR).build());
        assertThrows(IllegalArgumentException.class, () -> new Config.Builder().setFormat(Format.PPROF).build());
        for (EventType event : new EventType[]{EventType.ALLOC, EventType.LOCK, EventType.WALL, EventType.CTIMER}) {
            assertThrows(IllegalArgumentException.class, () -> pull().setProfilingEvent(event).build());
        }
        assertThrows(IllegalArgumentException.class, () -> pull().setProfilingAlloc("512k").build());
        assertThrows(IllegalArgumentException.class, () -> pull().setProfilingLock("10ms").build());
        assertThrows(IllegalArgumentException.class, () -> pull().setAllocLive(true).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setSamplingDuration(Duration.ofSeconds(1)).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setSamplingEventOrder(Collections.singletonList(EventType.CPU)).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setAPExtraArguments("event=wall").build());
    }

    @Test
    void rejectsInvalidListenerAndInterval() {
        assertThrows(IllegalArgumentException.class, () -> pull().setPullPort(-1).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setPullPort(65536).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setPullBindAddress(" ").build());
        assertThrows(IllegalArgumentException.class, () -> pull().setProfilingInterval(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class, () -> pull().setProfilingInterval(Duration.ofNanos(-1)).build());
    }

    private static Config.Builder pull() {
        return new Config.Builder().setProfilingMode(ProfilingMode.PULL).setFormat(Format.PPROF);
    }
}
