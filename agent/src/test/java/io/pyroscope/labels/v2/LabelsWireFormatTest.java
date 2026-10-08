package io.pyroscope.labels.v2;

import io.pyroscope.labels.pbref.JfrLabels;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden byte vectors for the {@code LabelsSnapshot} wire format
 * ({@code agent/jfr_labels.proto}).
 *
 * <p>The format is a frozen contract with Grafana Pyroscope's ingest path, so these vectors are
 * the regression lock: they were pinned against protobuf-java's own output and must keep holding
 * for the hand-written encoder.
 */
public class LabelsWireFormatTest {

    /**
     * A context set with deterministic iteration order, so both encoders can be fed identical
     * input and compared byte for byte.
     */
    static final class Fixture {
        final LinkedHashMap<Long, String[]> contexts = new LinkedHashMap<>();
        /** Pre-assigned ids, as {@code Pyroscope.LabelsWrapper.CONSTANTS} would supply them. */
        final LinkedHashMap<String, Long> constants = new LinkedHashMap<>();

        Fixture add(long contextId, String... labels) {
            contexts.put(contextId, labels);
            return this;
        }

        Fixture constant(String s, long id) {
            constants.put(s, id);
            return this;
        }

        /**
         * String ids as the encoder assigns them: seeded constants keep their own ids, and
         * everything else is numbered in first-seen order continuing past the highest seeded id.
         */
        LinkedHashMap<String, Long> stringIds() {
            LinkedHashMap<String, Long> ids = new LinkedHashMap<>();
            long next = 0;
            for (Map.Entry<String, Long> it : constants.entrySet()) {
                ids.put(it.getKey(), it.getValue());
                next = Math.max(next, it.getValue());
            }
            for (String[] args : contexts.values()) {
                for (String arg : args) {
                    if (!ids.containsKey(arg)) {
                        ids.put(arg, ++next);
                    }
                }
            }
            return ids;
        }
    }

    static Fixture fixture() {
        return new Fixture();
    }

    /** Encodes with protobuf-java, in the same field and map-entry order the encoder uses. */
    static byte[] encodeReference(Fixture f) {
        LinkedHashMap<String, Long> ids = f.stringIds();
        JfrLabels.LabelsSnapshot.Builder sb = JfrLabels.LabelsSnapshot.newBuilder();
        for (Map.Entry<Long, String[]> e : f.contexts.entrySet()) {
            JfrLabels.Context.Builder cb = JfrLabels.Context.newBuilder();
            String[] args = e.getValue();
            for (int i = 0; i < args.length; i += 2) {
                cb.putLabels(ids.get(args[i]), ids.get(args[i + 1]));
            }
            sb.putContexts(e.getKey(), cb.build());
        }
        // Ascending id, skipping ids that seeding left as holes - the order the encoder emits.
        TreeMap<Long, String> byId = new TreeMap<>();
        ids.forEach((s, id) -> byId.put(id, s));
        byId.forEach(sb::putStrings);
        return sb.build().toByteArray();
    }

    /** Encodes with the hand-written encoder. */
    static byte[] encode(Fixture f) {
        LabelsSnapshotEncoder enc = new LabelsSnapshotEncoder(64, 16);
        enc.seedStringTable(f.constants);
        for (Map.Entry<Long, String[]> e : f.contexts.entrySet()) {
            enc.writeContext(e.getKey(), e.getValue());
        }
        enc.writeStringTable();
        return enc.finish().toByteArray();
    }

    @Test
    void oneContextOneLabel() {
        assertBytes(""
                + "0A 0A"                   // contexts entry, len 10
                + "  08 01"                 //   key = context 1
                + "  12 06"                 //   value = Context, len 6
                + "    0A 04"               //     labels entry, len 4
                + "      08 01"             //       key = string 1 ("k1")
                + "      10 02"             //       value = string 2 ("v1")
                + "12 06 08 01 12 02 6B 31" // strings entry: 1 -> "k1"
                + "12 06 08 02 12 02 76 31" // strings entry: 2 -> "v1"
                , fixture().add(1, "k1", "v1"));
    }

    @Test
    void emptySnapshotIsEmpty() {
        assertBytes("", fixture());
    }

    /** Map entries always emit both fields, even at proto3 defaults. */
    @Test
    void contextWithNoLabels() {
        assertBytes("0A 04 08 01 12 00", fixture().add(1));
    }

    @Test
    void emptyStringStillEmitsValueField() {
        assertBytes(""
                + "0A 0A 08 01 12 06 0A 04 08 01 10 01"
                + "12 04 08 01 12 00"       // strings entry: 1 -> ""
                , fixture().add(1, "", ""));
    }

    @Test
    void contextIdVarintBoundaries() {
        for (long id : new long[]{0, 1, 127, 128, 16383, 16384, 2097151, 2097152, Long.MAX_VALUE}) {
            Fixture f = fixture().add(id, "k", "v");
            assertArrayEquals(encodeReference(f), encode(f), "context id " + id);
            assertEquals(
                    id,
                    LabelsSnapshots.parseCanonical(encode(f))
                            .getContextsMap().keySet().iterator().next().longValue(),
                    "context id " + id);
        }
    }

    @Test
    void stringIdAndLengthVarintBoundaries() {
        for (int labels : new int[]{1, 63, 64, 100}) {
            Fixture f = fixture();
            String[] args = new String[labels * 2];
            for (int i = 0; i < labels; i++) {
                args[i * 2] = "k" + i;
                args[i * 2 + 1] = "v" + i;
            }
            f.add(1, args);
            assertArrayEquals(encodeReference(f), encode(f), labels + " labels");
        }
        for (int len : new int[]{0, 1, 127, 128, 129, 16384}) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append('x');
            }
            Fixture f = fixture().add(1, "k", sb.toString());
            assertArrayEquals(encodeReference(f), encode(f), "string of length " + len);
        }
    }

    @Test
    void nonAsciiStrings() {
        for (String s : new String[]{
                "é",             // 2-byte
                "ÿ",             // 2-byte, still fits a latin1 compact string
                "Ж",             // Cyrillic
                "中文",       // CJK
                "😀",       // emoji, surrogate pair -> 4 bytes
                "a\ud800b",           // unpaired high surrogate
                "a\udc00b",           // unpaired low surrogate
                "a\u0000b",      // embedded NUL
        }) {
            Fixture f = fixture().add(1, "k", s);
            assertArrayEquals(encodeReference(f), encode(f), "string " + escape(s));
        }
    }

    /** Documents what protobuf-java does with an unpaired surrogate, so we can match it. */
    @Test
    void unpairedSurrogateIsEncodedLikeGetBytes() {
        Fixture f = fixture().add(1, "k", "a\ud800b");
        String decoded = LabelsSnapshots.parse(encodeReference(f)).getStringsMap().get(2L);
        assertArrayEquals("a\ud800b".getBytes(StandardCharsets.UTF_8),
                decoded.getBytes(StandardCharsets.UTF_8));
        assertEquals("a?b", decoded);
    }

    @Test
    void multipleContextsShareTheStringTable() {
        Fixture f = fixture()
                .add(1, "k1", "v1")
                .add(2, "k1", "v2")
                .add(3, "k2", "v1");
        JfrLabels.LabelsSnapshot p = LabelsSnapshots.parseCanonical(encode(f));
        assertEquals(3, p.getContextsCount());
        assertEquals(4, p.getStringsCount());
        assertArrayEquals(encodeReference(f), encode(f));
    }

    /**
     * Registered constants keep the ids they were handed out under, and new ids continue past the
     * highest one. A dump racing {@code registerConstant} can observe a sparse view of the
     * constants map, and handing out {@code size() + 1} would then collide with a live id.
     */
    @Test
    void sparseConstantIdsAreSeededWithoutCollision() {
        LinkedHashMap<String, Long> constants = new LinkedHashMap<>();
        constants.put("a", 1L);
        constants.put("c", 3L);   // 2 not observed yet

        LabelsSnapshotEncoder enc = new LabelsSnapshotEncoder(64, 16);
        enc.seedStringTable(constants);
        enc.writeContext(1, new String[]{"k", "a"});
        enc.writeStringTable();
        JfrLabels.LabelsSnapshot p = LabelsSnapshots.parseCanonical(enc.finish().toByteArray());

        assertEquals("a", p.getStringsMap().get(1L));
        assertEquals("c", p.getStringsMap().get(3L));
        assertEquals("k", p.getStringsMap().get(4L),
                "new ids must continue past the highest seeded id");
        assertFalse(p.getStringsMap().containsKey(2L), "the unobserved id stays a hole");
        // The label reuses the seeded id for "a" instead of interning it again.
        assertEquals(Long.valueOf(1L), p.getContextsMap().get(1L).getLabelsMap().get(4L));
    }

    /**
     * Shows the canonicality check has teeth. async-profiler's own protobuf writer back-patches a
     * padded, non-minimal length varint; protobuf's parser accepts that silently, so only the
     * re-serialization check would catch the encoder drifting that way.
     */
    @Test
    void paddedLengthVarintParsesButIsNotCanonical() {
        // oneContextOneLabel(), except the Context length 0x06 is written as 0x86 0x00, and the
        // enclosing entry length grows from 0x0A to 0x0B to match.
        byte[] padded = unhex(""
                + "0A 0B 08 01 12 86 00 0A 04 08 01 10 02"
                + "12 06 08 01 12 02 6B 31"
                + "12 06 08 02 12 02 76 31");
        byte[] canonical = encode(fixture().add(1, "k1", "v1"));

        // protobuf reads both as the same message...
        assertArrayEquals(canonical, LabelsSnapshots.parse(padded).toByteArray());
        // ...but only the minimal encoding survives the round trip unchanged.
        assertThrows(AssertionError.class, () -> LabelsSnapshots.parseCanonical(padded));
        LabelsSnapshots.parseCanonical(canonical);
    }

    /**
     * An app that uses no dynamic labels dumps nothing every cycle, and the snapshot is reachable
     * from the export queue until the upload finishes, so an empty dump must not carry a buffer.
     */
    @Test
    void anEmptySnapshotDoesNotRetainABuffer() {
        LabelsSnapshotEncoder enc = new LabelsSnapshotEncoder(8192, 1024);
        enc.writeStringTable();
        io.pyroscope.labels.pb.JfrLabels.LabelsSnapshot snapshot = enc.finish();

        assertSame(io.pyroscope.labels.pb.JfrLabels.LabelsSnapshot.EMPTY, snapshot);
        assertEquals(0, snapshot.size());
        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.buffer().length, "an empty snapshot should hold no buffer");
        assertEquals(0, snapshot.toByteArray().length);
    }

    /**
     * The encoder hands its buffer to the caller and the string table keeps its ids, so reuse
     * would emit a snapshot carrying the previous batch's ids. Rejected rather than left to
     * produce quietly wrong labels.
     */
    @Test
    void encoderIsSingleUse() {
        LabelsSnapshotEncoder enc = new LabelsSnapshotEncoder(64, 16);
        enc.writeContext(1, new String[]{"k", "v"});
        enc.writeStringTable();
        enc.finish();

        assertThrows(IllegalStateException.class, enc::finish);
        assertThrows(IllegalStateException.class, () -> enc.writeContext(2, new String[]{"k", "v"}));
        assertThrows(IllegalStateException.class, enc::writeStringTable);
        assertThrows(IllegalStateException.class,
                () -> enc.seedStringTable(Collections.singletonMap("a", 1L)));
    }

    private static void assertBytes(String expectedHex, Fixture f) {
        byte[] expected = unhex(expectedHex);
        assertArrayEquals(expected, encodeReference(f), "protobuf-java reference output changed");
        assertArrayEquals(expected, encode(f), "encoder output changed");
    }

    private static byte[] unhex(String s) {
        String hex = s.replaceAll("\\s+", "");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(String.format("\\u%04x", (int) s.charAt(i)));
        }
        return sb.toString();
    }
}
