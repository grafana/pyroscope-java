package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class IntervalParserTest {
    @Test
    public void testNanos() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    IntervalParser.parse("-1");
                }
            });
        assertEquals("Interval must be positive, but -1 given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                IntervalParser.parse("0");
            }
        });
        assertEquals("Interval must be positive, but 0 given", numberFormatException.getMessage());

        assertEquals(10, IntervalParser.parse("10"));
    }

    @Test
    public void testMicros() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    IntervalParser.parse("-1us");
                }
            });
        assertEquals("Interval must be positive, but -1us given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                IntervalParser.parse("0us");
            }
        });
        assertEquals("Interval must be positive, but 0us given", numberFormatException.getMessage());

        assertEquals(10 * 1_000L, IntervalParser.parse("10us"));
    }

    @Test
    public void testMillis() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    IntervalParser.parse("-1ms");
                }
            });
        assertEquals("Interval must be positive, but -1ms given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                IntervalParser.parse("0ms");
            }
        });
        assertEquals("Interval must be positive, but 0ms given", numberFormatException.getMessage());

        assertEquals(10  * 1_000_000L, IntervalParser.parse("10ms"));
    }

    @Test
    public void testSeconds() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    IntervalParser.parse("-1s");
                }
            });
        assertEquals("Interval must be positive, but -1s given", numberFormatException.getMessage());

        numberFormatException = assertThrows(NumberFormatException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                IntervalParser.parse("0s");
            }
        });
        assertEquals("Interval must be positive, but 0s given", numberFormatException.getMessage());

        assertEquals(10 * DateUtils.NANOS_PER_SECOND, IntervalParser.parse("10s"));
    }

    @Test
    public void testUnknownUnit() {
        NumberFormatException numberFormatException = assertThrows(
                NumberFormatException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    IntervalParser.parse("10k");
                }
            });
        assertEquals("Cannot parse interval 10k", numberFormatException.getMessage());
    }
    private <T> T assertThrows(Class<T> cls, Executable e) {
        try {
            e.execute();
        } catch (Throwable th) {
            if (th.getClass() == cls) {
                return (T) th;
            }
        }
        throw new AssertionError("not thrown ");
    }
}
