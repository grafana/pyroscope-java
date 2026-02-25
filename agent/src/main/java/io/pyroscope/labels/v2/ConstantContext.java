package io.pyroscope.labels.v2;

import org.jetbrains.annotations.NotNull;

import static io.pyroscope.Preconditions.checkNotNull;

/**
 * ConstantContext provides a way to define a set of labels that will be permanently stored
 * in memory for the life of the process.
 *
 * <p><strong>IMPORTANT:</strong> This class keeps references to {@link LabelsSet} instances
 * indefinitely (they are never garbage collected). Only use ConstantContext for labels that:
 * <ul>
 *   <li>Have a finite, predetermined set of possible values (low cardinality)</li>
 *   <li>Are constant throughout the application lifetime</li>
 *   <li>Do NOT contain user-controlled values such as user IDs, session IDs, or span IDs</li>
 *   <li>Are reused frequently across different parts of your application</li>
 * </ul>
 *
 * <p>For high-cardinality or ephemeral labels, use {@link ScopedContext} instead, which properly
 * cleans up references after context is closed and labels are dumped.
 *
 * <p>All parameters must be non-null. Attempting to create a ConstantContext with null parameters
 * will result in a NullPointerException.
 *
 * <p>Example usage:
 * <pre>{@code
 * private static final ConstantContext ctx = ConstantContext.of(new LabelsSet(
 *     "path", "/foo/bar",
 *     "method", "GET",
 *     "service", "svc-1"
 * ));
 *
 * // Later in your code:
 * try {
 *     ctx.activate();
 *     // Do work with this context active
 * } finally {
 *     ctx.deactivate();
 * }
 * }</pre>
 */
public class ConstantContext {

    /**
     * Creates a new ConstantContext with the given labels.
     *
     * <p>Warning: The provided LabelsSet will be stored permanently in memory.
     * Only use this for low-cardinality, constant label sets.
     *
     * @param labels The labels to associate with this context
     * @return A new ConstantContext instance
     * @throws NullPointerException if labels is null
     */
    public static @NotNull ConstantContext of(@NotNull LabelsSet labels) {
        checkNotNull(labels, "Labels");
        long contextId = ScopedContext.CONTEXT_COUNTER.incrementAndGet();
        ScopedContext.CONSTANT_CONTEXTS.put(contextId, labels);
        return new ConstantContext(contextId);
    }

    private final long contextId;

    private ConstantContext(long contextId) {
        this.contextId = contextId;
    }

    /**
     * Activates this context for the current thread.
     * This sets the async-profiler's context ID to this context's ID,
     * which will associate profiled samples with these labels.
     */
    public void activate() {
        ScopedContext.getAsyncProfiler().setContextId(contextId);
    }

    /**
     * Deactivates this context for the current thread.
     * This resets the async-profiler's context ID to 0 (no context).
     */
    public void deactivate() {
        ScopedContext.getAsyncProfiler().setContextId(0);
    }
}
