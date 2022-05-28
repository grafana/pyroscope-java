package io.pyroscope.labels;


import io.pyroscope.labels.pb.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class Labels {

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

    public static JfrLabels.Snapshot dump() {
        JfrLabels.Snapshot.Builder sb = JfrLabels.Snapshot.newBuilder();

        for (Ref<String> it : RefCounted.strings.valueToRef.values()) {
            sb.putStrings(it.id, it.val);
        }
        for (Ref<Map<Ref<String>, Ref<String>>> it : RefCounted.contexts.valueToRef.values()) {

            HashMap<Long, Long> context = new HashMap<>();
            JfrLabels.Context.Builder cb = JfrLabels.Context.newBuilder();
            for (Map.Entry<Ref<String>, Ref<String>> kv : it.val.entrySet()) {
                cb.putLabels(kv.getKey().id, kv.getValue().id);
            }
            sb.putContexts(it.id, cb.build());
        }
        RefCounted.contexts.gc();
        RefCounted.strings.gc();
        return sb.build();
    }

}
