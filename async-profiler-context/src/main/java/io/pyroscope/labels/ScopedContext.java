package io.pyroscope.labels;

import one.profiler.AsyncProfiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class ScopedContext implements AutoCloseable {
    static final ThreadLocal<Context> context = ThreadLocal.withInitial(() ->
            new Context(0L, Collections.emptyMap())
    );

    final Context previous;
    final Context current;
    final Ref<Map<Ref<String>, Ref<String>>> currentRef;
    boolean closed = false;
    public ScopedContext(LabelsSet labels) {
        previous = context.get();
        Map<Ref<String>, Ref<String>> nextContext = new HashMap<>(
                previous.labels.size() + labels.args.length / 2
        );
        for (Map.Entry<Ref<String>, Ref<String>> it : previous.labels.entrySet()) {
            Ref<String> key = it.getKey();
            Ref<String> value = it.getValue();

            checkBestEffortMode(key.refCount.get());
            checkBestEffortMode(value.refCount.get());

            checkBestEffortMode(key.refCount.incrementAndGet());
            checkBestEffortMode(value.refCount.incrementAndGet());

            nextContext.put(key, value);
        }

        for (int i = 0; i < labels.args.length; i += 2) {
            String ks = labels.args[i].toString();
            String vs = labels.args[i + 1].toString();
            Ref<String> k = RefCounted.strings.acquireRef(ks);
            Ref<String> v = RefCounted.strings.acquireRef(vs);

            Ref<String> prev = nextContext.put(k, v);
            if (prev != null) {
                checkBestEffortMode(k.refCount.decrementAndGet());
                checkBestEffortMode(prev.refCount.decrementAndGet());
            }
        }

        boolean[] fresh = new boolean[1];
        currentRef = RefCounted.contexts.acquireRef(nextContext, fresh);
        if (!fresh[0]) {
            for (Map.Entry<Ref<String>, Ref<String>> it : nextContext.entrySet()) {
                it.getKey().refCount.decrementAndGet();
                it.getValue().refCount.decrementAndGet();
            }
        }

        AsyncProfiler.getInstance().setContextId(currentRef.id);
        current = new Context(currentRef.id, nextContext);
        context.set(current);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        currentRef.refCount.decrementAndGet();
        context.set(previous);
        AsyncProfiler.getInstance().setContextId(previous.id);
    }

    public void forEachLabel(BiConsumer<String, String> labelConsumer) {
        for (Map.Entry<Ref<String>, Ref<String>> it : current.labels.entrySet()) {
            labelConsumer.accept(it.getKey().val, it.getValue().val);
        }
    }

    static class Context {
        public final Long id;
        public final Map<Ref<String>, Ref<String>> labels;

        public Context(Long id, Map<Ref<String>, Ref<String>> labels) {
            this.id = id;
            this.labels = labels;
        }
    }

    private static void checkBestEffortMode(long counter) {
        if (counter <= 0) {
            BEST_EFFORT_MODE.set(true);
            boolean warn = WARN_BEST_EFFORT_MODE_ONCE.compareAndSet(false, true);
            if (warn) {
//                // todo better message
//                // todo should we even warn users?
//                System.err.println("WARNING: RefCounted is in best effort mode.");
            }
        }
    }

    private static final AtomicBoolean BEST_EFFORT_MODE = new AtomicBoolean(false);
    private static final AtomicBoolean WARN_BEST_EFFORT_MODE_ONCE = new AtomicBoolean(false);
    public static boolean isInBestEffortMode() {
        return BEST_EFFORT_MODE.get();
    }
    static void resetBestEffortModeForTesting() {
        BEST_EFFORT_MODE.set(false);
        WARN_BEST_EFFORT_MODE_ONCE.set(false);
        RefCounted.strings.clear();
        RefCounted.strings.resetForTesting();
        RefCounted.contexts.clear();
        RefCounted.contexts.resetForTesting();
        context.set(new Context(0L, Collections.emptyMap()));
    }
}
