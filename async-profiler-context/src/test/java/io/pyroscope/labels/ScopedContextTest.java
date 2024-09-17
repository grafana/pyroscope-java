package io.pyroscope.labels;

import io.pyroscope.labels.io.pyroscope.PyroscopeAsyncProfiler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScopedContextTest {

    static {
        PyroscopeAsyncProfiler.getAsyncProfiler();
    }


    /**
     * This test demonstrates the issue reported <a href="https://github.com/grafana/pyroscope-java/issues/70">here</a>
     */
    @Test
    void closeContextOnOtherThread() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        ScopedContext context = new ScopedContext(new LabelsSet("k1", "v1"));
        executorService.submit(context::close).get();
        Pyroscope.LabelsWrapper.dump();
        new ScopedContext(new LabelsSet("k1", "v1"));
    }
}
