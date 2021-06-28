package io.pyroscope.javaagent.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IntervalParserTest {
    @Test
    void testNanos() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, () -> IntervalParser.parse("-1"));
        assertEquals("Interval must be positive, but -1 given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, () -> IntervalParser.parse("0"));
        assertEquals("Interval must be positive, but 0 given", numberFormatException.getMessage());

        assertEquals(Duration.ofNanos(10), IntervalParser.parse("10"));
    }

    @Test
    void testMicros() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, () -> IntervalParser.parse("-1us"));
        assertEquals("Interval must be positive, but -1us given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, () -> IntervalParser.parse("0us"));
        assertEquals("Interval must be positive, but 0us given", numberFormatException.getMessage());

        assertEquals(Duration.of(10, ChronoUnit.MICROS), IntervalParser.parse("10us"));
    }

    @Test
    void testMillis() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, () -> IntervalParser.parse("-1ms"));
        assertEquals("Interval must be positive, but -1ms given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, () -> IntervalParser.parse("0ms"));
        assertEquals("Interval must be positive, but 0ms given", numberFormatException.getMessage());

        assertEquals(Duration.ofMillis(10), IntervalParser.parse("10ms"));
    }

    @Test
    void testSeconds() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, () -> IntervalParser.parse("-1s"));
        assertEquals("Interval must be positive, but -1s given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, () -> IntervalParser.parse("0s"));
        assertEquals("Interval must be positive, but 0s given", numberFormatException.getMessage());

        assertEquals(Duration.ofSeconds(10), IntervalParser.parse("10s"));
    }

    @Test
    void testUnknownUnit() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, () -> IntervalParser.parse("10k"));
        assertEquals("Cannot parse interval 10k", numberFormatException.getMessage());
    }
}
