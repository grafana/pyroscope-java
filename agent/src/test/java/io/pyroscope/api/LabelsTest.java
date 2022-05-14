package io.pyroscope.api;

import io.pyroscope.api.Labels.LabelsSet;
import io.pyroscope.api.Labels.ScopedContext;
import one.profiler.AsyncProfiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelsTest {
    private final AsyncProfiler instance = AsyncProfiler.getInstance("/Users/korniltsev/github/libasyncProfiler_context.so");


    @Test
    void testLabelsSetMerge() {
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            assertEquals(0, s.prev.labels.labels.size());
            assertEquals(1, Labels.context.get().id);
            try (ScopedContext s2 = new ScopedContext(new LabelsSet("k1", "v1"))) {
                assertEquals(1, s2.prev.labels.labels.size());
                assertEquals(1, Labels.context.get().id);
                try (ScopedContext s3 = new ScopedContext(new LabelsSet("k1", "v2"))) {
                    assertEquals(1, s3.prev.labels.labels.size());
                    assertEquals(1, Labels.context.get().labels.labels.size());
                    assertEquals(2, Labels.context.get().id);
                    try (ScopedContext s4 = new ScopedContext(new LabelsSet("k2", "v3"))) {
                        assertEquals(1, s4.prev.labels.labels.size());
                        assertEquals(2, Labels.context.get().labels.labels.size());
                        assertEquals(3, Labels.context.get().id);
                        {
                            Labels.ContextsSnapshot contextsSnapshot = Labels.dump();
                            assertEquals(5, contextsSnapshot.strings.size());
                            assertEquals(3, contextsSnapshot.contexts.size());
                        }
                    }
                    assertEquals(2, Labels.context.get().id);
                }
                assertEquals(1,Labels.context.get().id);
            }
            assertEquals(1, Labels.context.get().id);
        }
        Labels.ContextsSnapshot contextsSnapshot2 = Labels.dump();
        assertEquals(0, Labels.context.get().id);
        assertEquals(0, Labels.values.idToRef.size());
        assertEquals(0, Labels.values.valueToRef.size());
        assertEquals(0, Labels.contexts.idToRef.size());
        assertEquals(0, Labels.contexts.valueToRef.size());
    }
}
