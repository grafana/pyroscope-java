package io.pyroscope.labels;

import one.profiler.AsyncProfiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ScopedContext implements AutoCloseable {
    static final ThreadLocal<Context> context = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context(0L, Collections.<Ref<String>, Ref<String>>emptyMap());
        }
    };

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

            assertAlive(key.refCount.get());
            assertAlive(value.refCount.get());

            assertAlive(key.refCount.incrementAndGet());
            assertAlive(value.refCount.incrementAndGet());

            nextContext.put(key, value);
        }

        for (int i = 0; i < labels.args.length; i += 2) {
            String ks = labels.args[i].toString();
            String vs = labels.args[i + 1].toString();
            Ref<String> k = RefCounted.strings.acquireRef(ks);
            Ref<String> v = RefCounted.strings.acquireRef(vs);

            Ref<String> prev = nextContext.put(k, v);
            if (prev != null) {
                assertAlive(k.refCount.decrementAndGet());
                assertAlive(prev.refCount.decrementAndGet());
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

    public void forEach(BiConsumer<String, String> consumer) {
        for (Map.Entry<Ref<String>, Ref<String>> it : current.labels.entrySet()) {
            consumer.accept(it.getKey().val, it.getValue().val);
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

    static void assertAlive(long counter) {
        if (counter <= 0) {
            throw new AssertionError();
        }
    }

    public interface BiConsumer<T, U> {
        void accept(T var1, U var2);

    }
}
