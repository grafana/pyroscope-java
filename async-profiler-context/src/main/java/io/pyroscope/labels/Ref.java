package io.pyroscope.labels;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicLong;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class Ref<T> {
    public final T val;
    public final AtomicLong refCount = new AtomicLong(1);

    @EqualsAndHashCode.Include
    public final Long id;

    public Ref(T val, Long id) {
        this.val = val;
        this.id = id;
    }
}
