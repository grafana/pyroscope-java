package io.pyroscope.labels.v2;


import io.pyroscope.PyroscopeAsyncProfiler;
import io.pyroscope.labels.pb.JfrLabels.LabelsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class LabelsTest {
    static {
        PyroscopeAsyncProfiler.getAsyncProfiler();
    }

    @BeforeEach
    void setUp() {
        resetForTesting();
    }

    @Test
    void testOneLabelSet() {
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            {
                assertSnapshot(
                        expectSnapshot()
                                .add(1L, "k1", "v1")
                        ,
                        Pyroscope.LabelsWrapper.dump());
            }
            {
                assertSnapshot(
                        expectSnapshot()
                                .add(1L, "k1", "v1")
                        ,
                        Pyroscope.LabelsWrapper.dump());
            }
        }
        {
            assertSnapshot(
                    expectSnapshot()
                            .add(1L, "k1", "v1")
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }

        {
            assertSnapshot(
                    expectSnapshot()
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
    }


    @Test
    void testNestedEqualLabelSets() {
        try (ScopedContext ignored = new ScopedContext(new LabelsSet("k1", "v1"))) {
            try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
                assertSnapshot(
                        expectSnapshot()
                                .add(1L, "k1", "v1")
                                .add(2L, "k1", "v1")
                        ,
                        Pyroscope.LabelsWrapper.dump());
            }
            assertSnapshot(
                    expectSnapshot()
                            .add(1L, "k1", "v1")
                            .add(2L, "k1", "v1")
                    ,
                    Pyroscope.LabelsWrapper.dump());
            assertSnapshot(
                    expectSnapshot()
                            .add(1L, "k1", "v1")
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
        {
            assertSnapshot(
                    expectSnapshot()
                            .add(1L, "k1", "v1")
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
        {
            assertSnapshot(
                    expectSnapshot()
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
    }


    @Test
    void exception() {
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            try {
                try (ScopedContext s2 = new ScopedContext(new LabelsSet("k1", "v2"))) {
                    assertSnapshot(
                            expectSnapshot()
                                    .add(1L, "k1", "v1")
                                    .add(2L, "k1", "v2")
                            ,
                            Pyroscope.LabelsWrapper.dump());
                    throw new AssertionError();
                }
            } catch (AssertionError e) {
                {
                    assertSnapshot(
                            expectSnapshot()
                                    .add(1L, "k1", "v1")
                                    .add(2L, "k1", "v2")
                            ,
                            Pyroscope.LabelsWrapper.dump());
                }
                {
                    assertSnapshot(
                            expectSnapshot()
                                    .add(1L, "k1", "v1")
                            ,
                            Pyroscope.LabelsWrapper.dump());
                }
            }
        }
        {
            assertSnapshot(
                    expectSnapshot()
                            .add(1L, "k1", "v1")
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
        {
            assertSnapshot(
                    expectSnapshot()
                    ,
                    Pyroscope.LabelsWrapper.dump());
        }
    }

    @Test
    void testConst() {
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {

        }

        ConstantContext.of(new LabelsSet("path", "/foo/bar", "qwe", "asd"));
        ConstantContext.of(new LabelsSet("path", "/qwe/asd", "zxc", "ass"));

        assertSnapshot(
                expectSnapshot()
                        .add(1L, "k1", "v1")
                        .add(2L, "path", "/foo/bar", "qwe", "asd")
                        .add(3L, "path", "/qwe/asd", "zxc", "ass")
                ,
                Pyroscope.LabelsWrapper.dump());

        assertSnapshot(
                expectSnapshot()
                        .add(2L, "path", "/foo/bar", "qwe", "asd")
                        .add(3L, "path", "/qwe/asd", "zxc", "ass")
                ,
                Pyroscope.LabelsWrapper.dump());
    }

    void assertSnapshot(ExpectedContextBuilder expected, LabelsSnapshot snapshot) {
        Map<Long, Map<String, String>> expectedContexts = expected.contexts;
        final HashSet<String> uniqueStrings = new HashSet<>();
        Map<Long, Map<String, String>> actualContexts = new HashMap<>();
        snapshot.getContextsMap().forEach((contextID, context) -> {
            final Map<String, String> ctx = new HashMap<>();
            context.getLabelsMap().forEach((key, value) -> {
                ctx.put(snapshot.getStringsMap().get(key), snapshot.getStringsMap().get(value));
                uniqueStrings.add(snapshot.getStringsMap().get(key));
                uniqueStrings.add(snapshot.getStringsMap().get(value));
            });
            actualContexts.put(contextID, ctx);
        });
        uniqueStrings.addAll(expected.constant.values());
        assertEquals(uniqueStrings.size(), snapshot.getStringsCount());
        assertEquals(expectedContexts, actualContexts);
    }


    @Test
    void stressTest() throws InterruptedException {
        final int n = 8;
        final ExecutorService e = Executors.newFixedThreadPool(n);
        final int iter = 10000;
        final int nspans = 1024;
        final List<String> spasn = new ArrayList<>(nspans);
        for (int i = 0; i < nspans; i++) {
            spasn.add("span" + i);
        }
        final long t1 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            e.submit(() -> {
                final Random r = new Random();
                for (int j = 0; j < iter; j++) {
                    final LabelsSet ls = new LabelsSet(
                            "SpanName", spasn.get(r.nextInt(spasn.size())),
                            "SpanId", Long.toHexString(r.nextLong())
                    );
                    Pyroscope.LabelsWrapper.run(ls, () -> {
                        final LabelsSet ls2 = new LabelsSet(
                                "SpanName", spasn.get(r.nextInt(spasn.size())),
                                "SpanId", Long.toHexString(r.nextLong())
                        );
                        Pyroscope.LabelsWrapper.run(ls2, () -> {


                        });
                    });

                }
            });
        }
        e.shutdown();
        e.awaitTermination(100, TimeUnit.SECONDS);
        final long t2 = System.currentTimeMillis();
        System.out.println("time: " + (t2 - t1));
//        Thread.sleep(1123123123123L);
        Pyroscope.LabelsWrapper.dump();
        final long t3 = System.currentTimeMillis();
        System.out.println("dump time: " + (t3 - t2));
    }

    @Test
    void stressTestConst() throws InterruptedException {
        final int n = 8;
        final Random r = new Random();
        final ExecutorService e = Executors.newFixedThreadPool(n);
        final int iter = 100000;
        final List<ConstantContext> ctxs = generateConstantContexts(r);
        final long t1 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            e.submit(() -> {
                final Random tr = new Random();
                for (int j = 0; j < iter; j++) {
                    ConstantContext c1 = ctxs.get(tr.nextInt(ctxs.size()));
                    ConstantContext c2 = ctxs.get(tr.nextInt(ctxs.size()));
                    ConstantContext c3 = ctxs.get(tr.nextInt(ctxs.size()));
                    c1.activate();
                    c2.activate();
                    c3.activate();
                    c3.deactivate();
                }
            });
        }
        e.shutdown();
        e.awaitTermination(100, TimeUnit.SECONDS);
        final long t2 = System.currentTimeMillis();
        System.out.println("time: " + (t2 - t1));
//        Thread.sleep(1123123123123L);
        Pyroscope.LabelsWrapper.dump();
    }

    @Test
    void registerStringConstant() {
        long c1 = Pyroscope.LabelsWrapper.registerConstant("const1");
        long c2;
        try (ScopedContext s = new ScopedContext(new LabelsSet("k1", "v1"))) {
            c2 = Pyroscope.LabelsWrapper.registerConstant("const2");
        }
        long c3 = Pyroscope.LabelsWrapper.registerConstant("const3");
        {
            LabelsSnapshot snapshot = Pyroscope.LabelsWrapper.dump();
            assertSnapshot(
                    expectSnapshot()
                            .constant(1L, "const1")
                            .constant(2L, "const2")
                            .constant(3L, "const3")
                            .add(1L, "k1", "v1"),
                    snapshot
            );
        }
        {
            LabelsSnapshot snapshot = Pyroscope.LabelsWrapper.dump();
            assertSnapshot(
                    expectSnapshot()
                            .constant(1L, "const1")
                            .constant(2L, "const2")
                            .constant(3L, "const3"),
                    snapshot
            );
        }
    }

    private static ExpectedContextBuilder expectSnapshot() {
        return new ExpectedContextBuilder();
    }

    private static List<ConstantContext> generateConstantContexts(Random r) {
        final int nctx = 10240;
        final List<ConstantContext> ctxs = new ArrayList<>(nctx);
        for (int i = 0; i < nctx; i++) {
            List<String> labels = new ArrayList<>();
            labels.add("PATH");
            labels.add("/foo/bar");
            labels.add("METHOD");
            labels.add("GET");
            for (int j = 0; j < 100; j++) {
                labels.add("randomkey" + r.nextLong());
                labels.add("randombal" + r.nextLong());
            }
            LabelsSet k1 = new LabelsSet(labels.toArray(new String[0]));
            ctxs.add(ConstantContext.of(k1));
        }
        return ctxs;
    }

    private static class ExpectedContextBuilder {
        final Map<Long, Map<String, String>> contexts = new HashMap<>();
        final Map<Long, String> constant = new HashMap<>();


        ExpectedContextBuilder add(Long cid, String... ss) {
            contexts.put(cid, mapOf(ss));
            return this;
        }

        ExpectedContextBuilder constant(Long cid, String s) {
            constant.put(cid, s);
            return this;
        }

        ExpectedContextBuilder remove(Long cid) {
            contexts.remove(cid);
            return this;
        }
    }

    private static Map<Long, Long> mapOf(Long k, Long v) {
        HashMap<Long, Long> res = new HashMap<>();
        res.put(k, v);
        return res;
    }

    private static Map<String, String> mapOf(String... ss) {
        assertTrue(ss.length % 2 == 0);
        HashMap<String, String> res = new HashMap<>();
        for (int i = 0; i < ss.length; i++) {
            res.put(ss[i], ss[i + 1]);
            i += 1;
        }
        return res;
    }

    static void resetForTesting() {
        ScopedContext.ENABLED.set(true);
        ScopedContext.CONTEXTS.clear();
        ScopedContext.CONSTANT_CONTEXTS.clear();
        ScopedContext.CONTEXT_COUNTER.set(0);
        Pyroscope.LabelsWrapper.CONSTANTS.clear();
    }
}
