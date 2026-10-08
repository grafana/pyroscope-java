package io.pyroscope.labels.v2;

import io.pyroscope.labels.pb.JfrLabels;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.pyroscope.labels.v2.ProtoBuffer.putVarint;
import static io.pyroscope.labels.v2.ProtoBuffer.varintSize;

/**
 * Encodes the {@code LabelsSnapshot} protobuf message defined in {@code agent/jfr_labels.proto}
 * without a protobuf runtime.
 *
 * <pre>
 * message Context { map&lt;int64,int64&gt; labels = 1; }
 * message LabelsSnapshot {
 *   map&lt;int64, Context&gt; contexts = 1;
 *   map&lt;int64, string&gt;  strings  = 2;
 * }
 * </pre>
 *
 * <p>This class owns the schema: field numbers, nesting, and the sizes of each record. The bytes
 * live in {@link ProtoBuffer} and the string interning in {@link StringTable}.
 *
 * <p>The wire format is a frozen contract with Grafana Pyroscope's ingest path. Output is
 * byte-identical to protobuf-java's: minimal varints everywhere, map entries always emitting both
 * key and value (protobuf's map entries ignore proto3 default-value suppression), all of field 1
 * followed by all of field 2.
 *
 * <p>Contexts are written as they are walked, while the string table is being built, and the table
 * is appended at the end. Nested message lengths are computed exactly before writing rather than
 * back-patched, which keeps the encoding minimal and needs only one capacity check per record.
 *
 * <p>Not thread safe, and single use: one instance encodes exactly one snapshot. {@link #finish()}
 * hands the buffer to the caller, and the string table keeps the ids it assigned, so reusing an
 * instance afterwards would emit a snapshot carrying the previous batch's ids. That is rejected
 * rather than left to produce quietly wrong labels.
 */
final class LabelsSnapshotEncoder {

    // All tags below are a single byte: a tag is varint(field << 3 | wireType), which fits in one
    // byte only while field < 16. Every field number in jfr_labels.proto is 1 or 2. A field number
    // >= 16 would have to be written as a varint instead.
    private static final byte T_CONTEXTS = (1 << 3) | 2;   // 0x0A LabelsSnapshot.contexts
    private static final byte T_STRINGS = (2 << 3) | 2;    // 0x12 LabelsSnapshot.strings
    private static final byte T_LABELS = (1 << 3) | 2;     // 0x0A Context.labels
    private static final byte T_KEY = (1 << 3);            // 0x08 MapEntry.key   (varint)
    private static final byte T_VAL_VARINT = (2 << 3);     // 0x10 MapEntry.value (varint)
    private static final byte T_VAL_LEN = (2 << 3) | 2;    // 0x12 MapEntry.value (length delimited)

    private final ProtoBuffer out;
    private final StringTable table;
    private boolean finished;

    /** Scratch for one context's resolved string ids, alternating key, value. */
    private int[] ids = new int[64];

    LabelsSnapshotEncoder(int bufCapacity, int stringCapacity) {
        this.out = new ProtoBuffer(bufCapacity);
        this.table = new StringTable(stringCapacity);
    }

    int size() {
        return out.size();
    }

    int stringCount() {
        return table.size();
    }

    void seedStringTable(Map<String, Long> constants) {
        checkNotFinished();
        table.seed(constants);
    }

    /**
     * Appends one {@code LabelsSnapshot.contexts} entry:
     * {@code ContextsEntry{key = 1: contextId, value = 2: Context{labels: LabelsEntry{1: k, 2: v}}}}.
     *
     * <p>Keys are not deduplicated: a {@link LabelsSet} built with the same key twice emits two
     * entries, and the decoder's map merge keeps the last one, exactly as if the duplicate had
     * been collapsed here. Scanning for duplicates would cost a quadratic pass per context to
     * fix nothing but malformed input.
     *
     * @param args flat array of alternating label keys and values, see {@link LabelsSet#args()}
     */
    void writeContext(long contextId, String[] args) {
        checkNotFinished();
        int n = args.length;
        if (n > ids.length) {
            ids = new int[Math.max(n, ids.length * 2)];
        }
        int[] ids = this.ids;

        // Pass 1: intern the strings and compute the exact nested lengths. The sizes accumulate in
        // long so a pathological input cannot overflow them into a negative that then reads as a
        // plausible length; the single check below is what rejects it.
        long bodyLen = 0;
        for (int i = 0; i < n; i += 2) {
            int k = table.intern(args[i]);
            int v = table.intern(args[i + 1]);
            ids[i] = k;
            ids[i + 1] = v;
            int entry = 1 + varintSize(k) + 1 + varintSize(v);
            bodyLen += 1 + varintSize(entry) + entry;
        }
        long valueLen = 1 + varintSize(contextId) + 1 + varintSize(bodyLen) + bodyLen;
        long totalLen = 1 + varintSize(valueLen) + valueLen;
        if (totalLen > ProtoBuffer.MAX_LENGTH) {
            throw new IllegalStateException("labels of context " + contextId
                    + " are too large to encode: " + totalLen + " bytes");
        }
        int body = (int) bodyLen;
        int value = (int) valueLen;
        int total = (int) totalLen;

        // Pass 2: one capacity check, then plain stores.
        int start = out.reserve(total);
        byte[] b = out.array();
        int p = start;
        b[p++] = T_CONTEXTS;
        p = putVarint(b, p, value);
        b[p++] = T_KEY;
        p = putVarint(b, p, contextId);
        b[p++] = T_VAL_LEN;
        p = putVarint(b, p, body);
        for (int i = 0; i < n; i += 2) {
            int k = ids[i];
            int v = ids[i + 1];
            b[p++] = T_LABELS;
            p = putVarint(b, p, 1 + varintSize(k) + 1 + varintSize(v));
            b[p++] = T_KEY;
            p = putVarint(b, p, k);
            b[p++] = T_VAL_VARINT;
            p = putVarint(b, p, v);
        }
        assert p == start + total : "context size mismatch: wrote " + (p - start) + ", computed " + total;
        out.commit(p);
    }

    /** Appends the whole string table as {@code LabelsSnapshot.strings} entries, id ascending. */
    void writeStringTable() {
        checkNotFinished();
        for (int id = 1, n = table.size(); id <= n; id++) {
            String s = table.get(id);
            if (s != null) {
                writeStringEntry(id, s);
            }
        }
    }

    JfrLabels.LabelsSnapshot finish() {
        checkNotFinished();
        finished = true;
        int len = out.size();
        if (len == 0) {
            // An app with no dynamic labels dumps nothing, and the snapshot stays reachable from
            // the export queue until the upload completes. Drop the buffer instead of carrying a
            // few kilobytes of zeros through it; costs no copy.
            return JfrLabels.LabelsSnapshot.EMPTY;
        }
        // Handed over without copying. Growth can leave the buffer holding up to twice the encoded
        // length, and the snapshot stays reachable until the exporter has uploaded it, so this
        // trades some transient footprint for not copying the payload. Trimming to size was tried
        // and reverted: it cost a payload-sized allocation on every dump whose buffer had grown,
        // which on a non-ASCII workload wiped out the allocation win entirely, and it contradicted
        // the zero-copy hand-off the exporter relies on. The sizing hint in
        // Pyroscope.LabelsWrapper.dump() is what actually keeps the slack small (~12%).
        return new JfrLabels.LabelsSnapshot(out.take(), len);
    }

    private static void checkStringFits(int id, long totalLen) {
        if (totalLen > ProtoBuffer.MAX_LENGTH) {
            throw new IllegalStateException("label string " + id + " is too large to encode: "
                    + totalLen + " bytes");
        }
    }

    private void checkNotFinished() {
        if (finished) {
            throw new IllegalStateException("this encoder already produced a snapshot");
        }
    }

    private void writeStringEntry(int id, String s) {
        int n = s.length();
        int i = 0;
        while (i < n && s.charAt(i) < 0x80) {
            i++;
        }
        if (i == n) {
            // ASCII, which is the overwhelmingly common case for label keys and values: write
            // straight into the buffer, no intermediate byte[].
            long bodyLen = 1 + varintSize(id) + 1 + varintSize(n) + (long) n;
            long totalLen = 1 + varintSize(bodyLen) + bodyLen;
            checkStringFits(id, totalLen);
            int body = (int) bodyLen;
            int total = (int) totalLen;
            int start = out.reserve(total);
            byte[] b = out.array();
            int p = start;
            b[p++] = T_STRINGS;
            p = putVarint(b, p, body);
            b[p++] = T_KEY;
            p = putVarint(b, p, id);
            b[p++] = T_VAL_LEN;
            p = putVarint(b, p, n);
            for (int j = 0; j < n; j++) {
                b[p++] = (byte) s.charAt(j);
            }
            assert p == start + total : "string size mismatch";
            out.commit(p);
            return;
        }
        // Anything else goes through the JDK encoder. That is deliberate: protobuf-java's own
        // encoder falls back to String.getBytes(UTF_8) for unpaired surrogates (which the JDK
        // turns into '?'), so delegating here is byte-identical for valid and malformed input
        // alike, with no surrogate handling of our own.
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        int len = utf8.length;
        long bodyLen = 1 + varintSize(id) + 1 + varintSize(len) + (long) len;
        long totalLen = 1 + varintSize(bodyLen) + bodyLen;
        checkStringFits(id, totalLen);
        int body = (int) bodyLen;
        int total = (int) totalLen;
        int start = out.reserve(total);
        byte[] b = out.array();
        int p = start;
        b[p++] = T_STRINGS;
        p = putVarint(b, p, body);
        b[p++] = T_KEY;
        p = putVarint(b, p, id);
        b[p++] = T_VAL_LEN;
        p = putVarint(b, p, len);
        System.arraycopy(utf8, 0, b, p, len);
        p += len;
        assert p == start + total : "string size mismatch";
        out.commit(p);
    }
}
