package io.pyroscope.labels;

import java.util.Map;

public class LabelsSet {
    public final Object[] args;

    public LabelsSet(Object... args) {
        this.args = args;
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("args.length % 2 != 0: " +
                "api.LabelsSet's  constructor arguments should be key-value pairs");
        }
    }

    public LabelsSet(Map<String, String> args) {
        this.args = new Object[args.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> it : args.entrySet()) {
            this.args[i] = it.getKey();
            this.args[i + 1] = it.getValue();
            i += 2;
        }
    }
}
