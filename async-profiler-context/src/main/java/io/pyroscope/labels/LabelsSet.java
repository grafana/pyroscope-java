package io.pyroscope.labels;

public class LabelsSet {
    public final Object[] args;

    public LabelsSet(Object... args) {
        this.args = args;
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("args.length % 2 != 0: " +
                    "api.LabelsSet's  constructor arguments should be key-value pairs");
        }
    }
}
