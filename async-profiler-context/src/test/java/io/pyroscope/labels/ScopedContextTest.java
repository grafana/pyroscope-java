package io.pyroscope.labels;

import io.pyroscope.labels.io.pyroscope.PyroscopeAsyncProfiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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
        // Shouldn't error
        new ScopedContext(new LabelsSet("k1", "v1"));
    }

    @Test
    void assertThreadNotLeaking() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<WeakReference<Thread>> threadRef = new AtomicReference<>();
        new Thread(() -> {
            threadRef.set(new WeakReference<>(Thread.currentThread()));
            ScopedContext context = new ScopedContext(new LabelsSet("k1", "v1"));
            context.close();
            future.complete(null);
        }).start();
        future.get();
        Pyroscope.LabelsWrapper.dump();

        // Force GC to clean the thread
        System.gc();

        Assertions.assertNull(threadRef.get().get(), "The thread should be garbage collected");

        // In WeakHashMap we need to interact with the map so it can clean the stale references
        ScopedContext.threadContext.forEach((thread, context) -> {});
        Assertions.assertTrue(ScopedContext.threadContext.isEmpty(), "The map should be empty, as the thread doesn't exists anymore");
    }
}
