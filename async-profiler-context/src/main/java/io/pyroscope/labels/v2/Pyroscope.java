package io.pyroscope.labels.v2;


import io.pyroscope.labels.pb.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public final class Pyroscope {
    /**
     * LabelsWrapper accumulates dynamic labels and corelates them with async-profiler's contextId
     * You are expected to call {@link LabelsWrapper#dump()} periodically, {@link io.pyroscope.javaagent.Profiler}
     * does that. If you don't use {@link io.pyroscope.javaagent.Profiler},
     * you need to call {@link LabelsWrapper#dump()} yourself.
     */
    public static class LabelsWrapper {

        public static <T> T run(LabelsSet labels, Callable<T> c) throws Exception {
            try (ScopedContext s = new ScopedContext(labels)) {
                return c.call();
            }
        }

        public static void run(LabelsSet labels, Runnable c) {
            try (ScopedContext s = new ScopedContext(labels)) {
                c.run();
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
            if (ScopedContext.CONTEXTS.isEmpty() && ScopedContext.CONSTANT_CONTEXTS.isEmpty()) {
                return sb.build();
            }
            final StringTableBuilder stb = new StringTableBuilder();
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
    }

    private static Map<String, String> staticLabels = Collections.emptyMap();

    public static void setStaticLabels(Map<String, String> labels) {
        staticLabels = Collections.unmodifiableMap(labels);
    }

    public static Map<String, String> getStaticLabels() {
        return staticLabels;
    }

    static class StringTableBuilder {
        private final Map<String, Long> indexes = new HashMap<>();

        public StringTableBuilder() {
        }

        public long get(String s) {
            Long prev = indexes.get(s);
            if (prev != null) {
                return prev;
            }
            long index = indexes.size();
            indexes.put(s, index);
            return index;

        }
    }
}
