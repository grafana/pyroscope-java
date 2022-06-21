package io.pyroscope.javaagent;

import io.pyroscope.javaagent.impl.ContinuousProfilingScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProfilingSchedulerTest {
    @Test
    void testAlignProfilingTimeBackward() {
        Instant t = Instant.parse("2022-06-11T16:10:32.239239Z");
        Instant expected = Instant.parse("2022-06-11T16:10:30Z");
        Instant res = ContinuousProfilingScheduler.alignProfilingIntervalStartTime(t, Duration.ofSeconds(10));
        assertEquals(expected, res);
    }
    @Test
    void testAlignProfilingTimeForward() {
        Instant t = Instant.parse("2022-06-11T16:10:39.239239Z");
        Instant expected = Instant.parse("2022-06-11T16:10:40Z");
        Instant res = ContinuousProfilingScheduler.alignProfilingIntervalStartTime(t, Duration.ofSeconds(10));
        assertEquals(expected, res);
    }
}