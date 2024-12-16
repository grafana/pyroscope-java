package io.pyroscope.labels;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

class Ref<T> {
    public final T val;
    public final AtomicLong refCount = new AtomicLong(1);
    public final Long id;

    public Ref(T val, Long id) {
        this.val = val;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ref<?> valueRef = (Ref<?>) o;
        if (val.getClass() != o.getClass()) return false;

        return id.equals(valueRef.id) && val.equals(valueRef.val);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, val);
    }

    @Override
    public String toString() {
        return "ValueRef{" +
                "val='" + val + '\'' +
                ", refCounter=" + refCount +
                ", id=" + id +
                '}';
    }
}
