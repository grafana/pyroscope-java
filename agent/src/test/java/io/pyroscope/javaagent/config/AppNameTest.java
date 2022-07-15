package io.pyroscope.javaagent.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;

class AppNameTest {
    @Test
    void withoutLabels() {
        AppName app = AppName.parse("test.app");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, emptyMap());
    }

    @Test
    void emptyLabels() {
        AppName app = AppName.parse("test.app{}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, emptyMap());
    }

    @Test
    void singleLabel() {
        AppName app = AppName.parse("test.app{foo=bar}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, mapOf("foo", "bar"));
    }

    @Test
    void twoLabels() {
        AppName app = AppName.parse("test.app{foo=bar,fiz=baz}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, mapOf("foo", "bar", "fiz", "baz"));
        assertEquals("test.app{fiz=baz,foo=bar}", app.toString());
    }

    @Test
    void emptyKey() {
        AppName app = AppName.parse("test.app{=bar , fiz=baz}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, mapOf("fiz", "baz"));
    }

    @Test
    void emptyValue() {
        AppName app = AppName.parse("test.app{foo= , fiz=baz}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, mapOf("fiz", "baz"));
    }

    @Test
    void noEqSign() {
        AppName app = AppName.parse("test.app{foo= , fiz=baz}");
        assertEquals(app.name, "test.app");
        assertEquals(app.labels, mapOf("fiz", "baz"));
    }

    private static Map<String, String> mapOf(String ...ss) {
        HashMap<String, String> res = new HashMap<>();
        for (int i = 0; i < ss.length; i+=2) {
            res.put(ss[i], ss[i + 1]);
        }
        return res;
    }
}