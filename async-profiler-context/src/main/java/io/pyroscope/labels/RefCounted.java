package io.pyroscope.labels;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class RefCounted<T> {

    public static final RefCounted<String> strings = new RefCounted<>(new ReleasedCallback<String>() {
        @Override
        public void released(String String) {
        }
    });

    public static final RefCounted<Map<Ref<String>, Ref<String>>> contexts = new RefCounted<>(
        new ReleasedCallback<Map<Ref<String>, Ref<String>>>() {
            @Override
            public void released(Map<Ref<String>, Ref<String>> context) {
                for (Map.Entry<Ref<String>, Ref<String>> it : context.entrySet()) {
                    it.getKey().refCount.decrementAndGet();
                    it.getValue().refCount.decrementAndGet();
                }
            }
        }
    );

    public final ReleasedCallback<T> releasedCallback;
    public final ConcurrentHashMap<T, Ref<T>> valueToRef = new ConcurrentHashMap<>();
    public final AtomicLong idCounter = new AtomicLong(0);

    RefCounted(ReleasedCallback<T> releasedCallback) {
        this.releasedCallback = releasedCallback;
    }

    Ref<T> acquireRef(T v) {
        boolean[] fresh = new boolean[1];
        return acquireRef(v, fresh);
    }

    Ref<T> acquireRef(T v, boolean[] outFresh) {
        outFresh[0] = false;
        while (true) {
            Ref<T> fresh = new Ref<>(v, idCounter.incrementAndGet());
            Ref<T> prev = valueToRef.putIfAbsent(v, fresh);
            if (prev == null) {
                outFresh[0] = true;
                return fresh;
            } else {
                while (true) {
                    long counter = prev.refCount.get();
                    if (counter < 0) {
                        break; // dead
                    }
                    boolean success = prev.refCount.compareAndSet(counter, counter + 1);
                    if (success) {
                        return prev;
                    }
                }
            }
        }
    }


    void gc() {
        Iterator<Ref<T>> it = valueToRef.values().iterator();
        while (it.hasNext()) {
            Ref<T> ref = it.next();

            if (ref.refCount.get() == 0) {
                boolean success = ref.refCount.compareAndSet(0, -1);
                if (success) {
                    it.remove();
                    releasedCallback.released(ref.val);
                }  // else resurrected

            }
        }
    }

    void resetForTesting() {
        idCounter.set(0);
    }

    interface ReleasedCallback<T> {
        void released(T t);
    }
}
