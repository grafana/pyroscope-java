package io.pyroscope.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelsTest {

    @Test
    void testLabelsSetMerge() {
        try (Labels.LabelsSet s = new Labels.LabelsSet("k1", "v1")) {
            assertEquals(0, s.prev.size());
            assertEquals(1, Labels.contextId.get());
            try (Labels.LabelsSet s2 = new Labels.LabelsSet("k1", "v1")) {
                assertEquals(1, s2.prev.size());
                assertEquals(1, Labels.contextId.get());
                try (Labels.LabelsSet s3 = new Labels.LabelsSet("k1", "v2")) {
                    assertEquals(1, s3.prev.size());
                    assertEquals(1, Labels.context.get().size());
                    assertEquals(2, Labels.contextId.get());
                    try (Labels.LabelsSet s4 = new Labels.LabelsSet("k2", "v3")) {
                        assertEquals(1, s4.prev.size());
                        assertEquals(2, Labels.context.get().size());
                        assertEquals(3, Labels.contextId.get());
                        {
                            Labels.ContextsSnapshot contextsSnapshot = Labels.dump();
                            assertEquals(5, contextsSnapshot.strings.size());
                            assertEquals(3, contextsSnapshot.contexts.size());
                        }
                    }
                    assertEquals(2, Labels.contextId.get());
                }
                assertEquals(1, Labels.contextId.get());
            }
            assertEquals(1, Labels.contextId.get());
        }
        Labels.ContextsSnapshot contextsSnapshot2 = Labels.dump();
        assertEquals(0, Labels.contextId.get());
        assertEquals(0, Labels.values.idToRef.size());
        assertEquals(0, Labels.values.valueToRef.size());
        assertEquals(0, Labels.contexts.idToRef.size());
        assertEquals(0, Labels.contexts.valueToRef.size());
    }
}
