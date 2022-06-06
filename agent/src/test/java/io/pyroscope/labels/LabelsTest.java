package io.pyroscope.labels;


import io.pyroscope.javaagent.TestAsyncProfilerHelper;
import io.pyroscope.labels.pb.JfrLabels.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelsTest {
    static {
        TestAsyncProfilerHelper.loadAsyncProfiler();
    }

    @BeforeEach
    void setUp() {
        RefCounted.strings.resetForTesting();
        RefCounted.contexts.resetForTesting();
        Pyroscope.LabelsWrapper.dump();
    }

    Map<String, Ref<String>> stringRefMap(Map<Ref<String>, Ref<String>> labels) {
        HashMap<String, Ref<String>> res = new HashMap<>();
        for (Map.Entry<Ref<String>, Ref<String>> it : labels.entrySet()) {
            res.put(it.getKey().val, it.getKey());
            res.put(it.getValue().val, it.getValue());
        }
        return res;
    }

    @Test
    void testOneLabelSet() {
        Ref<Map<Ref<String>, Ref<String>>> ctxRef;
        Ref<String> k1;
        Ref<String> v1;
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            ctxRef = s.currentRef;
            assertEquals(0, s.previous.id);
            assertEquals(0, s.previous.labels.size());
            assertEquals(1, s.current.id);
            assertEquals(1, s.current.labels.size());
            Map<String, Ref<String>> stringRefMap = stringRefMap(s.current.labels);
            k1 = stringRefMap.get("k1");
            v1 = stringRefMap.get("v1");
            assertEquals("k1", k1.val);
            assertEquals("v1", v1.val);
            assertEquals(1, k1.refCount.get());
            assertEquals(1, v1.refCount.get());
            assertEquals(1, ctxRef.refCount.get());

            {
                Snapshot snapshot = Pyroscope.LabelsWrapper.dump();
                assertEquals(1, snapshot.getContextsCount());
                assertEquals(2, snapshot.getStringsCount());
                assertEquals("k1", snapshot.getStringsMap().get(1L));
                assertEquals("v1", snapshot.getStringsMap().get(2L));
                assertEquals(mapOf(1L, 2L), snapshot.getContextsMap().get(1L).getLabelsMap());
                assertEquals(1, ctxRef.refCount.get());
                assertEquals(1, k1.refCount.get());
                assertEquals(1, v1.refCount.get());
            }
        }
        assertEquals(0, ScopedContext.context.get().id);
        assertEquals(0, ctxRef.refCount.get());
        assertEquals(1, k1.refCount.get());
        assertEquals(1, v1.refCount.get());
        {
            Snapshot snapshot = Pyroscope.LabelsWrapper.dump();
            assertEquals(1, snapshot.getContextsCount());
            assertEquals(2, snapshot.getStringsCount());
            assertEquals("k1", snapshot.getStringsMap().get(1L));
            assertEquals("v1", snapshot.getStringsMap().get(2L));
            assertEquals(mapOf(1L, 2L), snapshot.getContextsMap().get(1L).getLabelsMap());
            assertEquals(-1, k1.refCount.get());
            assertEquals(-1, v1.refCount.get());
            assertEquals(-1, ctxRef.refCount.get());
        }

        {
            Snapshot snapshot = Pyroscope.LabelsWrapper.dump();
            assertEquals(0, snapshot.getContextsCount());
            assertEquals(0, snapshot.getStringsCount());
            assertEquals(-1, k1.refCount.get());
            assertEquals(-1, v1.refCount.get());
            assertEquals(-1, ctxRef.refCount.get());
        }


    }


    @Test
    void testNestedEqualLabelSets() {
        Ref<Map<Ref<String>, Ref<String>>> ctxRef;
        Ref<String> k1;
        Ref<String> v1;
        try (ScopedContext ignored = new ScopedContext(new LabelsSet("k1", "v1"))) {
            try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
                ctxRef = s.currentRef;
                assertEquals(1, s.previous.id);
                assertEquals(1, s.previous.labels.size());
                assertEquals(1, s.current.id);
                assertEquals(1, s.current.labels.size());
                Map<String, Ref<String>> stringRefMap = stringRefMap(s.current.labels);
                k1 = stringRefMap.get("k1");
                v1 = stringRefMap.get("v1");
                assertEquals("k1", k1.val);
                assertEquals("v1", v1.val);
                assertEquals(1, k1.refCount.get());
                assertEquals(1, v1.refCount.get());
                assertEquals(2, ctxRef.refCount.get());

                {
                    Snapshot snapshot = Pyroscope.LabelsWrapper.dump();
                    assertEquals(1, snapshot.getContextsCount());
                    assertEquals(2, snapshot.getStringsCount());
                    assertEquals("k1", snapshot.getStringsMap().get(1L));
                    assertEquals("v1", snapshot.getStringsMap().get(2L));
                    assertEquals(mapOf(1L, 2L), snapshot.getContextsMap().get(1L).getLabelsMap());
                    assertEquals(1, k1.refCount.get());
                    assertEquals(1, v1.refCount.get());
                    assertEquals(2, ctxRef.refCount.get());
                }
            }
            assertEquals(1, ctxRef.refCount.get());
        }
        assertEquals(0, ctxRef.refCount.get());
        Pyroscope.LabelsWrapper.dump();
        assertEquals(-1L, ctxRef.refCount.get());
    }

    @Test
    void exception() {

        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            try {
                try (ScopedContext s2 = new ScopedContext(new LabelsSet("k1", "v2"))) {
                    throw new AssertionError();
                }
            } catch (AssertionError e) {
                // check k1, v1
            }
        }

    }

    @Test
    void testLabelsSetMerge() {
        Ref<Map<Ref<String>, Ref<String>>> ctxRef;
        Ref<String> k1;
        Ref<String> v2;
        try (ScopedContext s3 = new ScopedContext(new LabelsSet("k1", "v1"))) {
            try (ScopedContext s2 = new ScopedContext(new LabelsSet("k1", "v2"))) {

                try (ScopedContext s = new ScopedContext(new LabelsSet("k2", "v3"))) {
                    ctxRef = s.currentRef;
                    assertEquals(2, s.previous.id);
                    assertEquals(1, s.previous.labels.size());
                    assertEquals(3, s.current.id);
                    assertEquals(2, s.current.labels.size());
                    Map<String, Ref<String>> stringRefMap = stringRefMap(s.current.labels);
                    k1 = stringRefMap.get("k1");
                    v2 = stringRefMap.get("v2");
                    assertEquals("k1", k1.val);
                    assertEquals("v2", v2.val);
                    assertEquals(3, k1.refCount.get());
                    assertEquals(2, v2.refCount.get());
                    assertEquals(1, ctxRef.refCount.get());

                    {
                        Snapshot snapshot = Pyroscope.LabelsWrapper.dump();
                        assertEquals(3, snapshot.getContextsCount());
                        assertEquals(5, snapshot.getStringsCount());
                        assertEquals("k1", snapshot.getStringsMap().get(1L));
                        assertEquals("v1", snapshot.getStringsMap().get(2L));
                        assertEquals("v2", snapshot.getStringsMap().get(3L));
                        assertEquals("k2", snapshot.getStringsMap().get(4L));
                        assertEquals("v3", snapshot.getStringsMap().get(5L));
                        assertEquals(mapOf(1L, 2L), snapshot.getContextsMap().get(1L).getLabelsMap());
                        assertEquals(3, k1.refCount.get());
                        assertEquals(2, v2.refCount.get());
                        assertEquals(1, ctxRef.refCount.get());
                    }
                }

            }

        }
        Pyroscope.LabelsWrapper.dump();
        assertEquals(0, ScopedContext.context.get().id);
        assertEquals(0, RefCounted.strings.valueToRef.size());
        assertEquals(0, RefCounted.contexts.valueToRef.size());
    }

    @Test
    void stressTest() throws InterruptedException {
        final int n = 8;
        final ExecutorService e = Executors.newFixedThreadPool(n);
        for (int i = 0; i < n; i++) {
            e.submit(() -> {
                final Random r = new Random();

                for (int j = 0; j < 10000; j++) {
                    String k = "s" + r.nextInt(20);
                    String v = "s" + r.nextInt(20);
                    Pyroscope.LabelsWrapper.run(new LabelsSet(k, v), () -> {
                        String k2 = "s" + r.nextInt(20);
                        String v2 = "s" + r.nextInt(20);
                        Pyroscope.LabelsWrapper.run(new LabelsSet(k2, v2), () -> {

                        });

                    });

                }
            });
        }
        e.shutdown();
        e.awaitTermination(100, TimeUnit.SECONDS);
        Snapshot res = Pyroscope.LabelsWrapper.dump();
        assertEquals(0, ScopedContext.context.get().id);
        assertEquals(0, RefCounted.strings.valueToRef.size());
        assertEquals(0, RefCounted.contexts.valueToRef.size());
    }

    private static Map<Long, Long> mapOf(Long k, Long v) {
        HashMap<Long, Long> res = new HashMap<>();
        res.put(k, v);
        return res;
    }
}
