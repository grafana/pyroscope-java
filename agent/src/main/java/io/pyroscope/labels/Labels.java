package io.pyroscope.labels;


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

    public static ContextsSnapshot dump() {
        ContextsSnapshot s = new ContextsSnapshot(new HashMap<>(), new HashMap<>());

        for (Ref<String> it : RefCounted.strings.valueToRef.values()) {
            s.strings.put(it.id, it.val);
        }
        for (Ref<Map<Ref<String>, Ref<String>>> it : RefCounted.contexts.valueToRef.values()) {
            HashMap<Long, Long> context = new HashMap<>();
            for (Map.Entry<Ref<String>, Ref<String>> kv : it.val.entrySet()) {
                context.put(kv.getKey().id, kv.getValue().id);
            }
            s.contexts.put(it.id, context);
        }
        RefCounted.contexts.gc();
        RefCounted.strings.gc();
        return s;
    }

}
