package io.pyroscope.labels.v2;


import io.pyroscope.labels.pb.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static io.pyroscope.Preconditions.checkNotNull;

public final class Pyroscope {
    /**
     * LabelsWrapper accumulates dynamic labels and corelates them with async-profiler's contextId
     * You are expected to call {@link LabelsWrapper#dump()} periodically, io.pyroscope.javaagent.Profiler
     * does that. If you don't use io.pyroscope.javaagent.Profiler,
     * you need to call {@link LabelsWrapper#dump()} yourself.
     */
    public static class LabelsWrapper {

        public static <T> T run(@NotNull LabelsSet labels, @NotNull Callable<T> c) throws Exception {
            try (ScopedContext s = new ScopedContext(checkNotNull(labels, "Labels"))) {
                return checkNotNull(c, "Callable").call();
            }
        }

        public static void run(@NotNull LabelsSet labels, @NotNull Runnable c) {
            try (ScopedContext s = new ScopedContext(checkNotNull(labels, "Labels"))) {
                checkNotNull(c, "Runnable").run();
            }
        }

        /**
         * Emergency method to clear all ScopedContext references when memory leaks are detected.
         *
         * <p><strong>WARNING:</strong> This method should NOT be used in normal operation. It is
         * provided as a last resort for situations where unclosed {@link ScopedContext} instances
         * are causing memory leaks and cannot be fixed by proper closing in the application code.
         *
         * <p>Calling this method will:
         * <ul>
         *   <li>Remove all references to active and unclosed ScopedContext instances</li>
         *   <li>Result in missing labels/labelsets in profiling data</li>
         *   <li>Potentially create inconsistent profiling results</li>
         * </ul>
         *
         * <p>Recommended usage pattern:
         * <ul>
         *   <li>Fix your code to properly close all ScopedContext instances (preferred solution)</li>
         *   <li>If that's not possible in the short term, call this method periodically (e.g., once every N minutes)
         *       as a temporary workaround</li>
         * </ul>
         *
         * <p>Note: This does not affect {@link ConstantContext} instances, which are designed to live
         * for the entire application lifetime.
         */
        public static void clear() {
            ScopedContext.CONTEXTS.clear();
        }

        public static JfrLabels.LabelsSnapshot dump() {
            final JfrLabels.LabelsSnapshot.Builder sb = JfrLabels.LabelsSnapshot.newBuilder();
            final StringTableBuilder stb = new StringTableBuilder();
            stb.indexes.putAll(CONSTANTS);
            final Set<Long> closedContexts = new HashSet<>();
            final BiConsumer<Long, LabelsSet> collect = (contextID, ls) -> {
                final JfrLabels.Context.Builder cb = JfrLabels.Context.newBuilder();
                ls.forEachLabel((k, v) -> {
                    cb.putLabels(stb.get(k), stb.get(v));
                });
                sb.putContexts(contextID, cb.build());
            };
            for (Map.Entry<Long, ScopedContext> it : ScopedContext.CONTEXTS.entrySet()) {
                final Long contextID = it.getKey();
                if (it.getValue().closed.get()) {
                    closedContexts.add(contextID);
                }
                collect.accept(contextID, it.getValue().labels);
            }
            for (Map.Entry<Long, LabelsSet> it : ScopedContext.CONSTANT_CONTEXTS.entrySet()) {
                final Long contextID = it.getKey();
                collect.accept(contextID, it.getValue());
            }
            stb.indexes.forEach((k, v) -> {
                sb.putStrings(v, k);
            });
            for (Long cid : closedContexts) {
                ScopedContext.CONTEXTS.remove(cid);
            }
            return sb.build();
        }

        static final ConcurrentHashMap<String, Long> CONSTANTS = new ConcurrentHashMap<>();

        public static long registerConstant(@NotNull String constant) {
            checkNotNull(constant, "constant");
            Long v = CONSTANTS.get(constant);
            if (v != null) {
                return v;
            }
            synchronized (CONSTANTS) {
                v = CONSTANTS.get(constant);
                if (v != null) {
                    return v;
                }
                long id = CONSTANTS.size() + 1;
                CONSTANTS.put(constant, id);
                return id;
            }
        }
    }

    private static Map<String, String> staticLabels = Collections.emptyMap();

    /**
     * Sets the static labels to be included with all profiling data.
     *
     * <p>Static labels are constant across the entire application lifetime and are used
     * to identify and categorize profiling data at a global level.
     *
     * <p>All label keys and values must be non-null. Attempting to set static labels
     * with null keys or values will result in a NullPointerException.
     *
     * @param labels A map containing key-value pairs for static labels
     * @throws NullPointerException if labels is null or any key or value in the map is null
     */
    public static void setStaticLabels(@NotNull Map<@NotNull String, @NotNull String> labels) {
        checkNotNull(labels, "Labels");

        for (Map.Entry<String, String> entry : labels.entrySet()) {
            checkNotNull(entry.getKey(), "Key");
            checkNotNull(entry.getValue(), "Value");
        }

        staticLabels = Collections.unmodifiableMap(new HashMap<>(labels));
    }

    public static Map<String, String> getStaticLabels() {
        return staticLabels;
    }

    static class StringTableBuilder {
        private final Map<String, Long> indexes = new HashMap<>();

        public StringTableBuilder() {
        }

        public long get(@NotNull String s) {
            Long prev = indexes.get(s);
            if (prev != null) {
                return prev;
            }
            long index = indexes.size() + 1;
            indexes.put(s, index);
            return index;

        }
    }
}
