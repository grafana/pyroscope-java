package io.pyroscope.labels.v2;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LabelsSet represents an immutable set of key-value pairs used for profiling labels.
 *
 * <p>Labels are used by Pyroscope to categorize and filter profiling data, allowing
 * for more detailed analysis of application performance across different contexts.
 *
 * <p>This class stores labels as a flattened array of alternating keys and values,
 * making it memory-efficient while still providing easy iteration over the contained
 * label pairs.
 *
 * <p>LabelsSet instances are immutable once created and should be passed to
 * {@link ScopedContext} or {@link ConstantContext} to associate profiling data
 * with these labels.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Creating a label set with explicit key-value pairs
 * LabelsSet labels = new LabelsSet(
 *     "service", "user-api",
 *     "method", "GET",
 *     "endpoint", "/users"
 * );
 *
 * // Creating a label set from a Map
 * Map<String, String> labelMap = new HashMap<>();
 * labelMap.put("transaction", "payment");
 * labelMap.put("customer_type", "premium");
 * LabelsSet labels = new LabelsSet(labelMap);
 * }</pre>
 */
public final class LabelsSet {
    private final String[] args;

    /**
     * Creates a LabelsSet from alternating key-value pairs.
     *
     * <p>The arguments must be provided as alternating key-value pairs,
     * where even-indexed arguments (0, 2, 4, ...) are keys and
     * odd-indexed arguments (1, 3, 5, ...) are their corresponding values.
     *
     * @param args An array of alternating key-value strings
     * @throws IllegalArgumentException if the number of arguments is not even
     */
    public LabelsSet(String... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("args.length % 2 != 0: " +
                    "api.LabelsSet's  constructor arguments should be key-value pairs");
        }
        this.args = new String[args.length];
        System.arraycopy(args, 0, this.args, 0, args.length);
    }

    /**
     * Creates a LabelsSet from a Map of labels.
     *
     * <p>This constructor converts a Map representation of labels into the
     * internal array representation used by LabelsSet.
     *
     * @param args A map containing key-value pairs for labels
     */
    public LabelsSet(Map<String, String> args) {
        this.args = new String[args.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> it : args.entrySet()) {
            this.args[i] = it.getKey();
            this.args[i + 1] = it.getValue();
            i += 2;
        }
    }

    /**
     * Applies a BiConsumer function to each key-value pair in this label set.
     *
     * <p>This method provides a way to iterate through all labels without
     * exposing the internal representation.
     *
     * @param labelConsumer A function that accepts a key (String) and value (String)
     */
    public void forEachLabel(BiConsumer<String, String> labelConsumer) {
        for (int i = 0; i < args.length; i += 2) {
            labelConsumer.accept(args[i], args[i + 1]);
        }
    }
}
