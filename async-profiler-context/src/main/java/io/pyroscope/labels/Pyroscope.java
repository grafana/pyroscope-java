package io.pyroscope.labels;


import io.pyroscope.labels.pb.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

public class Pyroscope {
    /**
     * LabelsWrapper accumulates dynamic labels and corelates them with async-profiler's contextId
     * You are expected to call {@link LabelsWrapper#dump()} periodically, ProfilerAgent
     * does that. If you don't use ProfilerAgent, you need to call {@link LabelsWrapper#dump()}
     * yourself.
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

        public static JfrLabels.LabelsSnapshot dump() {
            JfrLabels.LabelsSnapshot.Builder sb = JfrLabels.LabelsSnapshot.newBuilder();

            for (Ref<String> it : RefCounted.strings.valueToRef.values()) {
                sb.putStrings(it.id, it.val);
            }
            for (Ref<Map<Ref<String>, Ref<String>>> it : RefCounted.contexts.valueToRef.values()) {
                JfrLabels.Context.Builder cb = JfrLabels.Context.newBuilder();
                for (Map.Entry<Ref<String>, Ref<String>> kv : it.val.entrySet()) {
                    cb.putLabels(kv.getKey().id, kv.getValue().id);
                }
                sb.putContexts(it.id, cb.build());
            }
            RefCounted.contexts.gc();
            RefCounted.strings.gc();
            if (ScopedContext.isInBestEffortMode()) {
                RefCounted.contexts.clear();
                RefCounted.strings.clear();
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

}
