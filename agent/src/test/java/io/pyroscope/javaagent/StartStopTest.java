package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StartStopTest {

    public static final Config INVALID = new Config.Builder()
        .setApplicationName("demo.app{qweqwe=asdasd}")
        .setFormat(Format.JFR)
        .setProfilingAlloc("512k")
        .setAPExtraArguments("event=qwe") // java.lang.IllegalArgumentException: Duplicate event argument
        .setProfilingEvent(EventType.ITIMER)
        .setLogLevel(Logger.Level.DEBUG)
        .build();

    public static final Config VALID = new Config.Builder()
        .setApplicationName("demo.app{qweqwe=asdasd}")
        .setFormat(Format.JFR)
        .setProfilingEvent(EventType.ITIMER)
        .setLogLevel(Logger.Level.DEBUG)
        .build();


    @Test
    void testStartFail() {
        notStarted();

        PyroscopeAgent.start(INVALID);
        notStarted();

        PyroscopeAgent.start(INVALID);
        notStarted();

        PyroscopeAgent.stop();
        notStarted();
        PyroscopeAgent.stop();
        notStarted();

        PyroscopeAgent.start(VALID);
        started();

        PyroscopeAgent.stop();
        notStarted();
    }

    private static void started() {
        assertTrue(PyroscopeAgent.isStarted());
    }

    private static void notStarted() {
        assertFalse(PyroscopeAgent.isStarted());
    }
}
