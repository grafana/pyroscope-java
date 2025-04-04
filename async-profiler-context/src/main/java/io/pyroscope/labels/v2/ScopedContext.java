package io.pyroscope.labels.v2;

import io.pyroscope.labels.io.pyroscope.PyroscopeAsyncProfiler;
import one.profiler.AsyncProfiler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * ScopedContext associates profiling data with a set of labels for the current execution scope.
 * Unlike {@link ConstantContext}, a ScopedContext is designed for dynamic, temporary use and
 * will be properly garbage collected.
 *
 * <p>ScopedContext implements {@link AutoCloseable}, allowing it to be used in try-with-resources
 * blocks for automatic cleanup when the context goes out of scope.
 *
 * <p>When a ScopedContext is closed, it's marked as such but remains in memory until the next call
 * to {@link io.pyroscope.labels.v2.Pyroscope.LabelsWrapper#dump()}. After being included in a dump,
 * closed contexts are removed from the internal map, allowing them to be garbage collected.
 *
 * <p>Use ScopedContext for:
 * <ul>
 *   <li>High-cardinality labels (like request IDs, user IDs, etc.)</li>
 *   <li>Temporary, request-scoped labels that change frequently</li>
 *   <li>User-controlled or dynamically generated values</li>
 *   <li>Any labels that would result in memory leaks if kept indefinitely</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // For a single block of code with labels
 * try (ScopedContext ctx = new ScopedContext(new LabelsSet("request_id", requestId))) {
 *     // Do work that will be profiled with this context
 *     processRequest();
 * } // Context automatically closed here
 *
 * // Or using the helper method in Pyroscope.LabelsWrapper:
 * Pyroscope.LabelsWrapper.run(new LabelsSet("span_id", spanId), () -> {
 *     // Operations to perform with this context
 *     executeSpan();
 * });
 * }</pre>
 */
public final class ScopedContext implements AutoCloseable {
    static final AtomicLong CONTEXT_COUNTER = new AtomicLong(0);
    static final ConcurrentHashMap<Long, ScopedContext> CONTEXTS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, LabelsSet> CONSTANT_CONTEXTS = new ConcurrentHashMap<>();

    private static volatile AsyncProfiler asyncProfiler;

    static AsyncProfiler getAsyncProfiler() {
        if (asyncProfiler != null) {
            return asyncProfiler;
        }
        asyncProfiler = PyroscopeAsyncProfiler.getAsyncProfiler();
        return asyncProfiler;
    }

    final LabelsSet labels;
    final long contextId;
    final long prevContextId;
    final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new ScopedContext with the given labels.
     * The previous context ID is set to 0 (root context).
     *
     * @param labels The labels to associate with this context
     */
    public ScopedContext(LabelsSet labels) {
        this(labels, 0);
    }

    /**
     * Creates a new ScopedContext with the given labels, using the previous context's ID.
     * This allows for proper nesting of contexts.
     *
     * @param labels The labels to associate with this context
     * @param prev   The previous context that this context will replace temporarily
     */
    public ScopedContext(LabelsSet labels, ScopedContext prev) {
        this(labels, prev.contextId);
    }

    /**
     * Internal constructor to create a ScopedContext with specific previous context ID.
     *
     * @param labels        The labels to associate with this context
     * @param prevContextId The context ID to restore when this context is closed
     */
    ScopedContext(LabelsSet labels, long prevContextId) {
        this.labels = labels;
        this.contextId = CONTEXT_COUNTER.incrementAndGet();
        this.prevContextId = prevContextId;
        CONTEXTS.put(contextId, this);
        getAsyncProfiler().setContextId(contextId);
    }

    /**
     * Closes this context, restoring the previous context.
     *
     * <p>The context is marked as closed, but will remain in memory until the next call to
     * {@link io.pyroscope.labels.v2.Pyroscope.LabelsWrapper#dump()} which will clean up
     * closed contexts.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        getAsyncProfiler().setContextId(this.prevContextId);
    }

    /**
     * Applies a consumer function to each label in this context.
     *
     * @param labelConsumer A function that will be called with each key-value pair in the labels
     */
    public void forEachLabel(BiConsumer<String, String> labelConsumer) {
        labels.forEachLabel(labelConsumer);
    }
}
