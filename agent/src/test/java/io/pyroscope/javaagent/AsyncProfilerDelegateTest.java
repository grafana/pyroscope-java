package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncProfilerDelegateTest {

    @Test
    void timeoutAddsOneSecond() {
        assertEquals(11, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofSeconds(10)));
    }

    @Test
    void timeoutRoundsUpToWholeSeconds() {
        assertEquals(2, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofMillis(1500)));
        assertEquals(1, AsyncProfilerDelegate.asyncProfilerTimeoutSeconds(Duration.ofMillis(1)));
    }
}
