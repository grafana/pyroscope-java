package io.pyroscope.api;


import com.google.gson.annotations.SerializedName;
import one.profiler.AsyncProfiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class Labels {
    private static final Object lock = new Object();

    private static final AtomicLong stringCounter = new AtomicLong(0);
    private static final AtomicLong contextCounter = new AtomicLong(0);

    private static final Function<String, Long> NEXT_STRING_ID = s -> stringCounter.incrementAndGet();

    static final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();
    static final RefCounted<String> values = new RefCounted<>(new AtomicCounterFactory<>(stringCounter));
    static final RefCounted<Map<Long, Long>> contexts = new RefCounted<>(new AtomicCounterFactory<>(contextCounter));

    static final ThreadLocal<Context> context = ThreadLocal.withInitial(() ->
        new Context(0L, new LabelsMap(Collections.emptyMap()))
    );


    public static <T> T run(LabelsSet labels, SafeCallable<T> c) {
        try (ScopedContext s = new ScopedContext(labels)) {
            return c.call();
        }
    }

    public static void run(LabelsSet labels, Runnable c) {
        try (ScopedContext s = new ScopedContext(labels)) {
            c.run();
        }
    }

    public static class LabelsSet {
        final Object[] args;

        public LabelsSet(Object... args) {
            this.args = args;
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("args.length % 2 != 0: " +
                    "LabelsSet's  constructor arguments should be key-value pairs");
            }
        }
    }

    public static ContextsSnapshot dump() {
        ContextsSnapshot s = new ContextsSnapshot(new HashMap<>(), new HashMap<>());

        for (Map.Entry<String, Long> it : keys.entrySet()) {
            s.strings.put(it.getValue(), it.getKey());
        }
        for (ValueRef<String> it : values.idToRef.values()) {
            s.strings.put(it.id, it.val);
        }
        for (ValueRef<Map<Long, Long>> it : contexts.idToRef.values()) {
            s.contexts.put(it.id, it.val);
        }
        gc();
        return s;
    }

    private static void gc() {
        values.gc();
        contexts.gc();
    }


    private static Long key(String s) {
        return keys.computeIfAbsent(s, NEXT_STRING_ID);
    }

    private static class AtomicCounterFactory<T> implements Function<T, ValueRef<T>> {

        private final AtomicLong counter;

        private AtomicCounterFactory(AtomicLong counter) {
            this.counter = counter;
        }

        @Override
        public ValueRef<T> apply(T s) {
            return new ValueRef<>(s, counter.incrementAndGet());
        }
    }

    public static class ContextsSnapshot {
        @SerializedName("contexts")
        public final HashMap<Long, Map<Long, Long>> contexts;
        @SerializedName("strings")
        public final HashMap<Long, String> strings;

        public ContextsSnapshot(HashMap<Long, Map<Long, Long>> contexts, HashMap<Long, String> strings) {
            this.contexts = contexts;
            this.strings = strings;
        }
    }

    public interface SafeCallable<V> {
        V call();
    }

    public static class RefCounted<T> {
        final Function<T, ValueRef<T>> factory;
        final ConcurrentHashMap<Long, ValueRef<T>> idToRef = new ConcurrentHashMap<>();
        final ConcurrentHashMap<T, ValueRef<T>> valueToRef = new ConcurrentHashMap<>();

        private Function<T, ValueRef<T>> myFactory = new Function<T, ValueRef<T>>() {
            @Override
            public ValueRef<T> apply(T t) {
                ValueRef<T> res = factory.apply(t);
                idToRef.put(res.id, res);
                return res;
            }
        };

        RefCounted(Function<T, ValueRef<T>> factory) {
            this.factory = factory;
        }

        ValueRef<T> createRef(T v) {
            return valueToRef.computeIfAbsent(v, myFactory);
        }

        public ValueRef<T> getRefById(Long valueId) {
            return idToRef.get(valueId);
        }

        public void gc() {
            Iterator<ValueRef<T>> it = valueToRef.values().iterator();
            while (it.hasNext()) {
                ValueRef<T> ref = it.next();
                if (ref.refCounter.get() == 0) {
                    it.remove();
                    idToRef.remove(ref.id);
                }
            }
        }
    }

    public static class ValueRef<T> {
        public final T val;
        private final AtomicLong refCounter = new AtomicLong(0);
        public final Long id;

        public ValueRef(T val, Long id) {
            this.val = val;
            this.id = id;
        }

        public long acquire() {
            return refCounter.incrementAndGet();
        }

        public long release() {
            return refCounter.decrementAndGet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueRef<T> valueRef = (ValueRef<T>) o;
            return id.equals(valueRef.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "ValueRef{" +
                "val='" + val + '\'' +
                ", refCounter=" + refCounter +
                ", id=" + id +
                '}';
        }
    }


    static class ScopedContext implements AutoCloseable {
        final Context prev;

        public ScopedContext(LabelsSet labels) {
            prev = context.get();
            HashMap<Long, Long> nextContext;
            ValueRef<Map<Long, Long>> contextRef;

            synchronized (lock) {
                nextContext = new HashMap<>(prev.labels.labels.size() + labels.args.length / 2);
                for (Map.Entry<Long, Long> it : prev.labels.labels.entrySet()) {
                    Long valueId = it.getValue();
                    ValueRef<String> ref = values.getRefById(valueId);
                    ref.acquire();
                    nextContext.put(it.getKey(), it.getValue());
                }

                for (int i = 0; i < labels.args.length; i += 2) {
                    Long k = key(labels.args[i].toString());
                    ValueRef<String> v = values.createRef(labels.args[i + 1].toString());
                    v.acquire();
                    Long prevValueId = nextContext.put(k, v.id);
                    if (prevValueId != null) {
                        ValueRef<String> ref = values.getRefById(prevValueId);
                        if (ref != null) {
                            ref.release();
                        }
                    }
                }

                contextRef = contexts.createRef(nextContext);
                long counter = contextRef.acquire();
                if (counter != 1) {
                    for (Long valueId : nextContext.values()) {
                        ValueRef<String> v = values.getRefById(valueId);
                        v.release();
                    }
                }
            }

            Long nextContextId = contextRef.id;
            context.set(new Context(nextContextId, new LabelsMap(Collections.unmodifiableMap(nextContext))));
            AsyncProfiler.getInstance().setContextId(nextContextId);
        }

        @Override
        public void close() {
            Context currentContext = context.get();
            synchronized (lock) {
                long counter = contexts.getRefById(currentContext.id)
                    .release();
                if (counter == 0) {
                    for (Long valueId : currentContext.labels.labels.values()) {
                        ValueRef<String> ref = values.getRefById(valueId);
                        if (ref != null) {
                            ref.release();
                        }
                    }
                }
            }
            context.set(prev);
            AsyncProfiler.getInstance().setContextId(prev.id);
        }
    }

    static class LabelsMap {
        public final Map<Long, Long> labels;

        private LabelsMap(Map<Long, Long> labels) {
            this.labels = labels;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LabelsMap labelsMap = (LabelsMap) o;

            return labels.equals(labelsMap.labels);
        }

        @Override
        public int hashCode() {
            return labels.hashCode();
        }
    }

    static class Context {
        public final Long id;
        public final LabelsMap labels;

        public Context(Long id, LabelsMap labels) {
            this.id = id;
            this.labels = labels;
        }
    }
}
