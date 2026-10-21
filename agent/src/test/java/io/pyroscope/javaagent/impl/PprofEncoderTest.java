package io.pyroscope.javaagent.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PprofEncoderTest {
    private static final Instant START = Instant.ofEpochSecond(100, 123);

    @Test
    void encodesCpuSamplesWithLeafFirstStacksAndSharedFunctions() throws Exception {
        byte[] bytes = PprofEncoder.encode("root;shared;leaf with spaces 3\nroot;shared 2\n",
            START, START.plusSeconds(2), 10_000_000, Collections.singletonMap("region", "west"));
        UnknownFieldSet profile = decode(bytes);
        List<String> strings = strings(profile);
        assertEquals("", strings.get(0));
        assertEquals(100_000_000_123L, number(profile, 9));
        assertEquals(2_000_000_000L, number(profile, 10));
        assertEquals(10_000_000L, number(profile, 12));
        assertEquals("cpu", strings.get((int) number(profile, 14)));
        List<UnknownFieldSet> types = messages(profile, 1);
        assertEquals(2, types.size());
        assertEquals("samples", strings.get((int) number(types.get(0), 1)));
        assertEquals("count", strings.get((int) number(types.get(0), 2)));
        assertEquals("cpu", strings.get((int) number(types.get(1), 1)));
        assertEquals("nanoseconds", strings.get((int) number(types.get(1), 2)));
        assertEquals(types.get(1), messages(profile, 11).get(0));

        Map<Long, String> functions = new HashMap<>();
        for (UnknownFieldSet function : messages(profile, 5)) {
            assertTrue(number(function, 1) > 0);
            assertNull(functions.put(number(function, 1), strings.get((int) number(function, 2))));
        }
        assertEquals(3, functions.size());
        Map<Long, String> locations = new HashMap<>();
        for (UnknownFieldSet location : messages(profile, 4)) {
            long function = number(messages(location, 4).get(0), 1);
            assertTrue(functions.containsKey(function));
            assertNull(locations.put(number(location, 1), functions.get(function)));
        }
        List<UnknownFieldSet> samples = messages(profile, 2);
        assertEquals(2, samples.size());
        assertEquals(Arrays.asList(3L, 30_000_000L), samples.get(0).getField(2).getVarintList());
        assertEquals(Arrays.asList(2L, 20_000_000L), samples.get(1).getField(2).getVarintList());
        assertEquals(Arrays.asList("leaf with spaces", "shared", "root"), stack(samples.get(0), locations));
        assertEquals(Arrays.asList("shared", "root"), stack(samples.get(1), locations));
        for (UnknownFieldSet sample : samples) {
            UnknownFieldSet label = messages(sample, 3).get(0);
            assertEquals("region", strings.get((int) number(label, 1)));
            assertEquals("west", strings.get((int) number(label, 2)));
        }
    }

    @Test
    void emptyRecordingStillHasAValidProfileHeader() throws Exception {
        UnknownFieldSet profile = decode(PprofEncoder.encode("", START, START.plusSeconds(1),
            10_000_000, Collections.emptyMap()));
        assertEquals(2, messages(profile, 1).size());
        assertTrue(messages(profile, 2).isEmpty());
        assertEquals("", strings(profile).get(0));
    }

    @Test
    void preservesRecursiveFramesAndUnicode() throws Exception {
        UnknownFieldSet profile = decode(PprofEncoder.encode("méthode;méthode 1\r\n",
            START, START.plusSeconds(1), 10, Collections.emptyMap()));
        assertEquals(1, messages(profile, 5).size());
        assertEquals(Arrays.asList(1L, 1L), messages(profile, 2).get(0).getField(1).getVarintList());
        assertTrue(strings(profile).contains("méthode"));
    }

    @Test
    void rejectsMalformedCountsAndOverflowInsteadOfProducingCorruptProfiles() {
        for (String collapsed : new String[]{"missing count", "root -1", "root 0", "root NaN"}) {
            assertThrows(IllegalArgumentException.class, () -> PprofEncoder.encode(collapsed,
                START, START.plusSeconds(1), 10, Collections.emptyMap()));
        }
        assertThrows(ArithmeticException.class, () -> PprofEncoder.encode("root 9223372036854775807",
            START, START.plusSeconds(1), 10, Collections.emptyMap()));
    }

    private static UnknownFieldSet decode(byte[] bytes) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return UnknownFieldSet.parseFrom(gzip);
        }
    }

    private static long number(UnknownFieldSet message, int field) {
        return message.getField(field).getVarintList().get(0);
    }

    private static List<UnknownFieldSet> messages(UnknownFieldSet message, int field) throws Exception {
        List<UnknownFieldSet> result = new ArrayList<>();
        for (ByteString bytes : message.getField(field).getLengthDelimitedList()) {
            result.add(UnknownFieldSet.parseFrom(bytes));
        }
        return result;
    }

    private static List<String> strings(UnknownFieldSet profile) {
        List<String> result = new ArrayList<>();
        for (ByteString bytes : profile.getField(6).getLengthDelimitedList()) {
            result.add(bytes.toStringUtf8());
        }
        return result;
    }

    private static List<String> stack(UnknownFieldSet sample, Map<Long, String> locations) {
        List<String> result = new ArrayList<>();
        for (long id : sample.getField(1).getVarintList()) {
            assertTrue(locations.containsKey(id));
            result.add(locations.get(id));
        }
        return result;
    }
}
