/**
 * Package {@code io.pyroscope.labels.v2} provides a new implementation of Pyroscope's context labeling system.
 *
 * <h2>Why v2?</h2>
 *
 * <p>The previous implementation (v1) used an implicit ThreadLocal reference-counted approach to manage context labels.
 * While convenient for simple cases, this approach had several fundamental issues:
 *
 * <ul>
 *   <li>Cross-thread operations could lead to assertion errors, missing labels, or infinite loops</li>
 *   <li>Closing a ScopedContext on a different thread than where it was created caused unpredictable behavior</li>
 *   <li>Implicit label merging made debugging difficult when labels were unexpectedly combined</li>
 *   <li>Thread pools could inherit unexpected labels from previous operations</li>
 * </ul>
 *
 * <h2>Key Differences in v2</h2>
 *
 * <p>The v2 implementation:
 * <ul>
 *   <li>Eliminates implicit label merging - you must explicitly create contexts with all required labels</li>
 *   <li>Requires explicit passing of parent contexts for proper nesting</li>
 *   <li>Manages memory better by cleaning up closed contexts after they're dumped</li>
 *   <li>Adds {@link io.pyroscope.labels.v2.ConstantContext} for static, low-cardinality labels</li>
 * </ul>
 *
 * <h2>Example: v1 vs v2 Implementation</h2>
 *
 * <p>In v1, implicit merging happened at the ThreadLocal level:
 *
 * <pre>{@code
 * // v1: Implicit merging through ThreadLocal
 * try (ScopedContext ctx = new ScopedContext(new LabelsSet("request_id", "239"))) {
 *     try (ScopedContext ctx2 = new ScopedContext(new LabelsSet("op", "doSomething"))) {
 *         doSomething(); // Runs with BOTH "request_id" and "op" labels
 *     }
 * }
 * }</pre>
 *
 * <p>In v2, you must explicitly include all labels or pass the parent context:
 *
 * <pre>{@code
 * // v2: Explicit passing of parent context
 * try (ScopedContext ctx1 = new ScopedContext(new LabelsSet("request_id", "239"))) {
 *     // Option 1: Create new context with ALL needed labels
 *     try (ScopedContext ctx2 = new ScopedContext(new LabelsSet("request_id", "239", "op", "doSomething"))) {
 *         doSomething();
 *     }
 *
 *     // Option 2: Pass parent context to create proper hierarchy
 *     try (ScopedContext ctx2 = new ScopedContext(new LabelsSet("op", "doSomething"), ctx1)) {
 *         doSomething();
 *     }
 * }
 * }</pre>
 */
package io.pyroscope.labels.v2;
