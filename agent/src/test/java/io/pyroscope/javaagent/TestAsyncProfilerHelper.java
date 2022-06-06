package io.pyroscope.javaagent;

import one.profiler.AsyncProfiler;

public class TestAsyncProfilerHelper {
    public static void loadAsyncProfiler() {
        AsyncProfiler.getInstance(Profiler.libraryPath);
    }
}
