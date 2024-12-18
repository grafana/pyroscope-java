package io.pyroscope.labels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RefTest {
    @Test
    void testCreateRef() {
        Ref<String> ref = new Ref<>("test", 1L);
        assertEquals("test", ref.val);
        assertEquals(1L, ref.id);
        assertEquals(1L, ref.refCount.get());
    }

    @Test
    @SuppressWarnings("unlikely-arg-type")
    void testEquals() {
        Ref<String> ref1 = new Ref<>("test", 1L);
        assertTrue(ref1.equals(ref1));
        assertFalse(ref1.equals(null));
        assertFalse(ref1.equals(new Integer(3)));
        assertTrue(ref1.equals(new Ref<Integer>(3, 1L))); // We don't compare the value types.
    }

    @Test
    void testHashCode() {
        Ref<String> ref1 = new Ref<>("test", 1L);
        assertEquals(1, ref1.hashCode());
    }
}
