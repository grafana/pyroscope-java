package io.pyroscope.javaagent.impl;

import com.google.protobuf.CodedOutputStream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes async-profiler's root-first collapsed CPU stacks as a gzip pprof profile.
 * Field numbers follow https://github.com/google/pprof/blob/main/proto/profile.proto.
 * Uses the existing protobuf runtime without adding generated profile classes.
 */
public final class PprofEncoder {
    private final Map<String, Integer> strings = new LinkedHashMap<>();
    private final Map<String, Integer> functions = new LinkedHashMap<>();

    private PprofEncoder() {
        stringId("");
    }

    public static byte[] encode(String collapsed, Instant started, Instant ended,
                                long periodNanos, Map<String, String> labels) throws IOException {
        if (periodNanos <= 0 || ended.isBefore(started)) {
            throw new IllegalArgumentException("Invalid profiling interval");
        }
        return new PprofEncoder().encodeProfile(collapsed, started, ended, periodNanos, labels);
    }

    private byte[] encodeProfile(String collapsed, Instant started, Instant ended,
                                 long periodNanos, Map<String, String> labels) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            CodedOutputStream profile = CodedOutputStream.newInstance(gzip);
            profile.writeByteArray(1, valueType("samples", "count"));
            profile.writeByteArray(1, valueType("cpu", "nanoseconds"));
            profile.writeInt64(9, Math.addExact(Math.multiplyExact(started.getEpochSecond(),
                1_000_000_000L), started.getNano()));
            profile.writeInt64(10, Duration.between(started, ended).toNanos());
            profile.writeByteArray(11, valueType("cpu", "nanoseconds"));
            profile.writeInt64(12, periodNanos);
            profile.writeInt64(14, stringId("cpu"));

            byte[] sampleLabels = message(out -> {
                for (Map.Entry<String, String> label : labels.entrySet()) {
                    out.writeByteArray(3, message(value -> {
                        value.writeInt64(1, stringId(label.getKey()));
                        value.writeInt64(2, stringId(label.getValue()));
                    }));
                }
            });
            try (BufferedReader reader = new BufferedReader(new StringReader(collapsed))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    int separator = line.lastIndexOf(' ');
                    if (separator <= 0) {
                        throw new IllegalArgumentException("Invalid collapsed stack");
                    }
                    long count = Long.parseLong(line.substring(separator + 1));
                    if (count <= 0) {
                        throw new IllegalArgumentException("Invalid collapsed sample count");
                    }
                    String[] frames = line.substring(0, separator).split(";", -1);
                    profile.writeByteArray(2, message(sample -> {
                        // pprof locations are leaf-first; collapsed stacks are root-first.
                        for (int i = frames.length - 1; i >= 0; i--) {
                            sample.writeUInt64(1, functionId(frames[i]));
                        }
                        sample.writeInt64(2, count);
                        sample.writeInt64(2, Math.multiplyExact(count, periodNanos));
                        sample.writeRawBytes(sampleLabels);
                    }));
                }
            }
            for (Map.Entry<String, Integer> function : functions.entrySet()) {
                int id = function.getValue();
                profile.writeByteArray(4, message(location -> {
                    location.writeUInt64(1, id);
                    location.writeByteArray(4, message(line -> line.writeUInt64(1, id)));
                }));
                profile.writeByteArray(5, message(value -> {
                    value.writeUInt64(1, id);
                    value.writeInt64(2, stringId(function.getKey()));
                }));
            }
            for (String value : strings.keySet()) {
                profile.writeString(6, value);
            }
            profile.flush();
        }
        return bytes.toByteArray();
    }

    private byte[] valueType(String type, String unit) throws IOException {
        return message(value -> {
            value.writeInt64(1, stringId(type));
            value.writeInt64(2, stringId(unit));
        });
    }

    private int stringId(String value) {
        return strings.computeIfAbsent(value, key -> strings.size());
    }

    private int functionId(String value) {
        return functions.computeIfAbsent(value, key -> functions.size() + 1);
    }

    private static byte[] message(MessageWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        writer.write(out);
        out.flush();
        return bytes.toByteArray();
    }

    private interface MessageWriter {
        void write(CodedOutputStream out) throws IOException;
    }
}
