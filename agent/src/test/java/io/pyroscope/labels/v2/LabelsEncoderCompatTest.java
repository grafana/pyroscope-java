package io.pyroscope.labels.v2;

import io.pyroscope.labels.pbref.JfrLabels;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static io.pyroscope.labels.v2.LabelsSnapshots.flatten;
import static io.pyroscope.labels.v2.LabelsWireFormatTest.Fixture;
import static io.pyroscope.labels.v2.LabelsWireFormatTest.encode;
import static io.pyroscope.labels.v2.LabelsWireFormatTest.encodeReference;
import static io.pyroscope.labels.v2.LabelsWireFormatTest.fixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-checks the hand-written encoder against protobuf-java over randomized input: identical
 * bytes, and a round trip back through protobuf's own parser.
 */
public class LabelsEncoderCompatTest {

    /** Strings that stress the encoder: UTF-8 shapes, lengths, and hash collisions. */
    private static final String[] POOL = build();

    private static String[] build() {
        List<String> pool = new ArrayList<>();
        pool.add("");
        pool.add("k");
        pool.add("SpanName");
        pool.add("SpanId");
        pool.add("/foo/bar");
        pool.add("é");                 // 2-byte
        pool.add("ÿ");                 // 2-byte, latin1 compact string
        pool.add("Ж");                 // Cyrillic
        pool.add("中文");           // CJK
        pool.add("😀");           // emoji, surrogate pair
        pool.add("a\ud800b");               // unpaired high surrogate
        pool.add("a\udc00b");               // unpaired low surrogate
        pool.add("ab");               // embedded NUL
        // Equal hashCodes, to exercise the open-addressing probe chains.
        pool.add("Aa");
        pool.add("BB");
        pool.add("AaAa");
        pool.add("AaBB");
        pool.add("BBAa");
        pool.add("BBBB");
        // Strings long enough to push the length prefix past one and two varint bytes.
        for (int len : new int[]{126, 127, 128, 129, 300, 20000}) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append((char) ('a' + (i % 26)));
            }
            pool.add(sb.toString());
        }
        return pool.toArray(new String[0]);
    }

    private static final long[] CONTEXT_IDS = {
            0, 1, 2, 126, 127, 128, 129, 16383, 16384, 2097151, 2097152,
            268435455, 268435456, Integer.MAX_VALUE, Long.MAX_VALUE, -1,
    };

    @Test
    void randomFixturesMatchProtobufJavaByteForByte() {
        Random r = new Random(20260908L);
        for (int iteration = 0; iteration < 2000; iteration++) {
            Fixture f = randomFixture(r);
            assertArrayEquals(encodeReference(f), encode(f), "iteration " + iteration);
        }
    }

    @Test
    void randomFixturesRoundTripThroughProtobufJava() throws Exception {
        Random r = new Random(7L);
        for (int iteration = 0; iteration < 500; iteration++) {
            Fixture f = randomFixture(r);
            JfrLabels.LabelsSnapshot parsed = JfrLabels.LabelsSnapshot.parseFrom(encode(f));
            assertEquals(expected(f), flatten(parsed), "iteration " + iteration);
        }
    }

    @Test
    void manyUniqueStringsGrowTheTableCorrectly() throws Exception {
        Fixture f = fixture();
        int contexts = 500;
        for (int c = 0; c < contexts; c++) {
            String[] args = new String[20];
            for (int i = 0; i < 10; i++) {
                args[i * 2] = "key" + c + "_" + i;
                args[i * 2 + 1] = "value" + c + "_" + i;
            }
            f.add(c + 1, args);
        }
        assertArrayEquals(encodeReference(f), encode(f));
        JfrLabels.LabelsSnapshot parsed = JfrLabels.LabelsSnapshot.parseFrom(encode(f));
        assertEquals(contexts, parsed.getContextsCount());
        assertEquals(contexts * 20, parsed.getStringsCount());
        assertEquals(expected(f), flatten(parsed));
    }

    /**
     * A {@link LabelsSet} with a repeated key emits both entries rather than paying for a
     * duplicate scan; proto map merge semantics mean the decoded result is the same as
     * protobuf's, even though the bytes are not.
     */
    @Test
    void repeatedKeysDecodeToTheLastValue() throws Exception {
        Fixture f = fixture().add(1, "k", "v1", "k", "v2");
        byte[] ours = encode(f);
        assertTrue(ours.length > encodeReference(f).length, "expected both entries on the wire");

        JfrLabels.LabelsSnapshot parsed = JfrLabels.LabelsSnapshot.parseFrom(ours);
        Map<String, String> labels = flatten(parsed).get(1L);
        assertEquals(1, labels.size());
        assertEquals("v2", labels.get("k"));

        JfrLabels.LabelsSnapshot reference = JfrLabels.LabelsSnapshot.parseFrom(encodeReference(f));
        assertEquals(flatten(reference), flatten(parsed));
    }

    @Test
    void seededConstantsMatchProtobufJavaAndAreReusedByLabels() throws Exception {
        Fixture f = fixture()
                .constant("service", 1L)
                .constant("region", 3L)          // 2 left as a hole
                .add(1, "service", "checkout")   // "service" must reuse the seeded id 1
                .add(2, "zone", "eu-west-1");

        assertArrayEquals(encodeReference(f), encode(f));

        JfrLabels.LabelsSnapshot parsed = JfrLabels.LabelsSnapshot.parseFrom(encode(f));
        Map<Long, String> strings = parsed.getStringsMap();
        assertEquals("service", strings.get(1L));
        assertEquals("region", strings.get(3L), "an unreferenced constant is still emitted");
        assertNull(strings.get(2L), "the id gap stays a hole");
        assertEquals(Long.valueOf(1L),
                parsed.getContextsMap().get(1L).getLabelsMap().keySet().iterator().next(),
                "the label should reuse the seeded id rather than intern a second copy");
        assertEquals(expected(f), flatten(parsed));
    }

    private static Fixture randomFixture(Random r) {
        Fixture f = fixture();
        // Seed registered constants on roughly half the fixtures, sometimes with a gap in the ids,
        // which is what a dump racing registerConstant observes. Constants keep their ids and new
        // strings continue past the highest one, and that is the semantic this PR changed.
        if (r.nextBoolean()) {
            long id = 0;
            int count = 1 + r.nextInt(3);
            Set<String> used = new HashSet<>();
            for (int i = 0; i < count; i++) {
                id += 1 + r.nextInt(3);   // gaps leave holes in the table
                // Half the time a constant is a string that labels also use, so the encoder has to
                // reuse the seeded id instead of interning a second copy.
                String s = POOL[r.nextInt(POOL.length)];
                String constant = r.nextBoolean() ? s : s + "#const" + i;
                if (used.add(constant)) {
                    f.constant(constant, id);
                }
            }
        }
        int contexts = r.nextInt(6);
        for (int c = 0; c < contexts; c++) {
            long id = r.nextBoolean()
                    ? CONTEXT_IDS[r.nextInt(CONTEXT_IDS.length)]
                    : Math.abs(r.nextLong());
            // Keys are kept distinct: a repeated key is the one case where the encoder emits
            // different bytes than protobuf-java on purpose, covered by
            // repeatedKeysDecodeToTheLastValue() instead.
            int labels = r.nextInt(5);
            List<String> args = new ArrayList<>(labels * 2);
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < labels; i++) {
                String key = POOL[r.nextInt(POOL.length)];
                if (!keys.add(key)) {
                    continue;
                }
                args.add(key);
                args.add(POOL[r.nextInt(POOL.length)]);
            }
            f.add(id, args.toArray(new String[0]));
        }
        return f;
    }

    /** The contexts a fixture describes, as they look after a UTF-8 round trip. */
    private static Map<Long, Map<String, String>> expected(Fixture f) {
        Map<Long, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<Long, String[]> e : f.contexts.entrySet()) {
            Map<String, String> labels = new LinkedHashMap<>();
            String[] args = e.getValue();
            for (int i = 0; i < args.length; i += 2) {
                labels.put(utf8(args[i]), utf8(args[i + 1]));
            }
            out.put(e.getKey(), labels);
        }
        return out;
    }

    /**
     * Encoding is lossy for malformed input: both protobuf-java and the hand-written encoder
     * turn an unpaired surrogate into '?', because both end up in the JDK's UTF-8 encoder.
     */
    private static String utf8(String s) {
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

}
