package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InputStreamUtilsTest {
    @Test
    public void test() throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("test\n");
        }
        final String s = sb.toString();
        final ByteArrayInputStream is = new ByteArrayInputStream(s.getBytes());
        final String result = InputStreamUtils.readToString(is);
        assertEquals(s, result);
    }
}
