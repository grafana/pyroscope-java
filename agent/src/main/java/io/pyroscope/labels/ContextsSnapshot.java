package io.pyroscope.labels;

import java.util.HashMap;
import java.util.Map;

public class ContextsSnapshot {
    public final Map<Long, Map<Long, Long>> contexts;
    public final Map<Long, String> strings;

    public ContextsSnapshot(Map<Long, Map<Long, Long>> contexts, Map<Long, String> strings) {
        this.contexts = contexts;
        this.strings = strings;
    }
}
