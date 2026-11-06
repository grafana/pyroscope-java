package io.pyroscope.labels.pb;

import java.util.Arrays;

/**
 * Hand-written stand-in for the code that used to be generated from
 * {@code agent/jfr_labels.proto} by the protobuf compiler.
 *
 * <p>The agent only ever needs to <em>encode</em> a {@code LabelsSnapshot}, so this holds the
 * encoded bytes instead of an object tree. See
 * {@code io.pyroscope.labels.v2.LabelsSnapshotEncoder} for the encoder.
 */
public final class JfrLabels {

    private JfrLabels() {
    }

    /**
     * An encoded {@code io.pyroscope.labels.pb.LabelsSnapshot} protobuf message.
     *
     * <p>The wire format is defined by {@code agent/jfr_labels.proto} and is a frozen contract
     * with Grafana Pyroscope's ingest path.
     *
     * <p>This is a carrier for bytes, not a value object: it inherits identity
     * {@code equals}/{@code hashCode}, unlike the protobuf-generated message it replaced, which
     * compared parsed content. Comparing snapshots is not something the agent does, and doing it
     * properly would mean comparing {@link #size()} bytes of {@link #buffer()} — never the whole
     * backing array, which can be longer. Compare {@link #toByteArray()} if you need it.
     */
    public static final class LabelsSnapshot {

        /** An encoded empty snapshot, i.e. no bytes at all. */
        public static final LabelsSnapshot EMPTY = new LabelsSnapshot(new byte[0], 0);

        private final byte[] buf;
        private final int len;

        /**
         * @param buf buffer holding the encoded message in {@code [0, len)}
         * @param len encoded length in bytes
         */
        public LabelsSnapshot(byte[] buf, int len) {
            if (len < 0 || len > buf.length) {
                throw new IllegalArgumentException("len " + len + " out of bounds for buffer of "
                        + buf.length);
            }
            this.buf = buf;
            this.len = len;
        }

        /** The encoded message, as a fresh exact-size array. */
        public byte[] toByteArray() {
            return Arrays.copyOf(buf, len);
        }

        /** Encoded length in bytes. */
        public int size() {
            return len;
        }

        public boolean isEmpty() {
            return len == 0;
        }

        /**
         * Internal: the backing buffer, holding the encoded message in {@code [0, size())}. It may
         * be longer than {@link #size()}. Must not be modified. Prefer {@link #toByteArray()}.
         */
        public byte[] buffer() {
            return buf;
        }

        @Override
        public String toString() {
            return "LabelsSnapshot{size=" + len + "}";
        }
    }
}
