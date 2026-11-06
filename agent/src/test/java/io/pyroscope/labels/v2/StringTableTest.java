package io.pyroscope.labels.v2;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringTableTest {

    @Test
    void internAssignsDenseIdsAndDeduplicates() {
        StringTable table = new StringTable(16);
        assertEquals(1, table.intern("a"));
        assertEquals(2, table.intern("b"));
        assertEquals(1, table.intern("a"));
        assertEquals(1, table.intern(new String("a")), "equal strings share an id");
        assertEquals(2, table.size());
        assertEquals("a", table.get(1));
        assertEquals("b", table.get(2));
    }

    @Test
    void seededIdsAreKeptAndNewIdsContinuePastTheHighest() {
        Map<String, Long> constants = new LinkedHashMap<>();
        constants.put("a", 1L);
        constants.put("c", 3L);   // 2 not observed yet

        StringTable table = new StringTable(16);
        table.seed(constants);

        assertEquals(3, table.size());
        assertEquals("a", table.get(1));
        assertNull(table.get(2), "the unobserved id stays a hole");
        assertEquals("c", table.get(3));
        assertEquals(1, table.intern("a"), "a seeded string keeps its id");
        assertEquals(3, table.intern("c"));
        assertEquals(4, table.intern("k"), "new ids continue past the highest seeded id");
    }

    /**
     * Regression test: seeding used to resize the index to fit the constants alone, throwing away
     * the capacity the caller asked for. Since a dump seeds every registered constant before
     * interning anything, that silently undid the sizing hint carried over from the previous dump
     * and re-grew the index from 64 slots on every single dump.
     */
    @Test
    void seedingDoesNotShrinkTheIndex() throws Exception {
        StringTable table = new StringTable(20480);
        int sized = indexCapacity(table);
        assertTrue(sized >= 20480, "constructor should size the index for the hint, was " + sized);

        Map<String, Long> constants = new LinkedHashMap<>();
        constants.put("one", 1L);
        constants.put("two", 2L);
        constants.put("three", 3L);
        table.seed(constants);

        assertEquals(sized, indexCapacity(table), "seeding must keep the requested index size");
    }

    @Test
    void seedingGrowsTheIndexWhenThereAreManyConstants() throws Exception {
        StringTable table = new StringTable(16);
        int sized = indexCapacity(table);

        Map<String, Long> constants = new LinkedHashMap<>();
        for (int i = 1; i <= 1000; i++) {
            constants.put("const" + i, (long) i);
        }
        table.seed(constants);

        assertTrue(indexCapacity(table) > sized, "1000 constants should not fit the initial index");
        assertEquals(1000, table.size());
        assertEquals(1, table.intern("const1"));
        assertEquals(1000, table.intern("const1000"));
        assertEquals(1001, table.intern("fresh"));
    }

    @Test
    void internSurvivesRehashingAndHashCollisions() {
        StringTable table = new StringTable(16);
        // "Aa" and "BB" collide, as do all their concatenations, so this exercises probe chains.
        String[] colliding = {"Aa", "BB", "AaAa", "AaBB", "BBAa", "BBBB"};
        for (int i = 0; i < colliding.length; i++) {
            assertEquals(i + 1, table.intern(colliding[i]));
        }
        // Enough entries to force several rehashes.
        for (int i = 0; i < 5000; i++) {
            assertEquals(colliding.length + i + 1, table.intern("s" + i));
        }
        for (int i = 0; i < colliding.length; i++) {
            assertEquals(i + 1, table.intern(colliding[i]), "id changed across rehash");
        }
        for (int id = 1; id <= table.size(); id++) {
            assertEquals(id, table.intern(table.get(id)), "id " + id + " is not round tripping");
        }
    }

    private static int indexCapacity(StringTable table) throws Exception {
        Field slots = StringTable.class.getDeclaredField("slots");
        slots.setAccessible(true);
        return ((int[]) slots.get(table)).length;
    }
}
