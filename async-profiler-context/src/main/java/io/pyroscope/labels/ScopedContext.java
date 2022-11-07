package io.pyroscope.labels;

import one.profiler.AsyncProfiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ScopedContext implements AutoCloseable {
    static final ThreadLocal<Context> context = ThreadLocal.withInitial(() ->
            new Context(0L, Collections.emptyMap(), null)
    );

    final Context previous;
    final Context current;
    final Ref<Map<Ref<String>, Ref<String>>> currentRef;
    boolean closed = false;
    final Thread owner = Thread.currentThread();
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
        current = new Context(currentRef.id, nextContext, this);
        context.set(current);
    }


    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (previous.scoped != null && previous.scoped.closed) {
            throw new AssertionError("previous.scoped.closed");
        }
        Thread current = Thread.currentThread();
        if (current != owner) {
            throw new AssertionError(String.format("Thread check failure: %s %d != %s %d", owner, owner.getId(), current, current.getId()));
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
        final ScopedContext scoped;

        public Context(Long id, Map<Ref<String>, Ref<String>> labels, ScopedContext ctx2) {
            this.id = id;
            this.labels = labels;
            this.scoped = ctx2;
        }
    }

    static void assertAlive(long counter) {
        if (counter <= 0) {
            throw new AssertionError("counter " + counter);
        }
    }
}
