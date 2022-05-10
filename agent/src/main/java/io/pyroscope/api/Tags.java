package io.pyroscope.api;


import com.google.gson.annotations.SerializedName;
import one.profiler.AsyncProfiler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class Tags {

    private static final Object lock = new Object();

    private static final AtomicLong stringCounter = new AtomicLong(0);
    private static final AtomicLong contextCounter = new AtomicLong(0);

    private static final Function<String, Long> NEXT_STRING_ID = s -> stringCounter.incrementAndGet();


    static final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();
    static final RefCounted<String> values = new RefCounted<>(new AtomicCounterFactory(stringCounter));
    static final RefCounted<Map<Long, Long>> contexts = new RefCounted<>(new AtomicCounterFactory(contextCounter));

    static final ThreadLocal<Long> contextId = ThreadLocal.withInitial(() -> 0L);
    static final ThreadLocal<HashMap<Long, Long>> context = ThreadLocal.withInitial(() -> new HashMap<>());


    public static <T> T run(Scope s, SafeCallable<T> c) {
        try (Scope ss = s) {
            return c.call();
        }
    }

    public static Snapshot dump() {
        Snapshot s = new Snapshot(new HashMap<>(), new HashMap<>());

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



    public static class Snapshot {
        @SerializedName("contexts")
        public final HashMap<Long, Map<Long, Long>> contexts;
        @SerializedName("strings")
        public final HashMap<Long, String> strings;

        public Snapshot(HashMap<Long, Map<Long, Long>> contexts, HashMap<Long, String> strings) {
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

    public static class Scope implements AutoCloseable {
        final HashMap<Long, Long> prev;
        final Long prevContextId;

        public Scope(Object... args) { //todo create constructor for 2, 4, 6 args
            if (args.length %2  != 0) {
                throw new IllegalArgumentException();
            }
            prev = context.get();
            prevContextId = contextId.get();

            HashMap<Long, Long> nextContext;
            ValueRef<Map<Long, Long>> contextRef;

            synchronized (lock) {
                //todo oom case
                nextContext = new HashMap<>(prev.size() + args.length / 2);
                for (Map.Entry<Long, Long> it : prev.entrySet()) {
                    Long valueId = it.getValue();
                    ValueRef<String> ref = values.getRefById(valueId);
                    if (ref != null) {
                        ref.acquire();
                        nextContext.put(it.getKey(), it.getValue());
                    } else {
                        throw new IllegalStateException();//todo this should not happen, can we log the error
                    }
                }

                for (int i = 0; i < args.length; i += 2) {
                    Long k = key(args[i].toString());
                    ValueRef<String> v = values.createRef(args[i + 1].toString());
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

            context.set(nextContext);
            contextId.set(contextRef.id);
            AsyncProfiler.getInstance().setContextId(contextRef.id);
        }


        @Override
        public void close() {
            synchronized (lock) {
                long counter = contexts.getRefById(contextId.get())
                    .release();
                if (counter == 0) {
                    for (Long valueId : context.get().values()) {
                        ValueRef<String> ref = values.getRefById(valueId);
                        if (ref != null) {
                            ref.release();
                        }
                    }
                }
            }
            context.set(prev);
            contextId.set(prevContextId);
            AsyncProfiler.getInstance().setContextId(prevContextId);
        }
    }




}



