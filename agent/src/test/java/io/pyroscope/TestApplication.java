package io.pyroscope;

import one.profiler.AsyncProfiler;
import one.profiler.Counter;

import java.util.concurrent.TimeUnit;

public class TestApplication {

    public static void main(String[] args) {
        AsyncProfiler asyncProfiler = PyroscopeAsyncProfiler.getAsyncProfiler();
        asyncProfiler.start("cpu", TimeUnit.SECONDS.toNanos(1));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.exit(1);
        }
        asyncProfiler.stop();

        System.out.println(
            asyncProfiler + "-" +
                asyncProfiler.dumpCollapsed(Counter.SAMPLES).split(";").length
        );
    }
}
