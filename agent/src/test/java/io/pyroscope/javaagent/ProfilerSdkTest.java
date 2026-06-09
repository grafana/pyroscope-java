package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfilerSdkTest {

    @Test
    void parseHex64_lowercase() {
        // 0x0123456789abcdef
        assertEquals(0x0123456789abcdefL, ProfilerSdk.parseHex64("0123456789abcdef", 0));
    }

    @Test
    void parseHex64_uppercase() {
        // OTel emits lowercase per W3C spec, but defensive: accept uppercase too.
        assertEquals(0x0123456789ABCDEFL, ProfilerSdk.parseHex64("0123456789ABCDEF", 0));
    }

    @Test
    void parseHex64_allZeros() {
        assertEquals(0L, ProfilerSdk.parseHex64("0000000000000000", 0));
    }

    @Test
    void parseHex64_allFs() {
        assertEquals(-1L, ProfilerSdk.parseHex64("ffffffffffffffff", 0));
    }

    @Test
    void parseHex64_offset() {
        // Parse the low half of a full 32 char trace id.
        String traceId = "0123456789abcdeffedcba9876543210";
        assertEquals(0x0123456789abcdefL, ProfilerSdk.parseHex64(traceId, 0));
        assertEquals(0xfedcba9876543210L, ProfilerSdk.parseHex64(traceId, 16));
    }

    @Test
    void parseHex64_invalidChar() {
        assertThrows(NumberFormatException.class, () -> ProfilerSdk.parseHex64("0123456789abcdez", 0));
    }
}
