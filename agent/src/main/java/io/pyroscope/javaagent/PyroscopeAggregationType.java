package io.pyroscope.javaagent;

public enum PyroscopeAggregationType {
    SUM ("sum"),
    AVERAGE ("average");

    /**
    * Pyroscope aggregation type id, as expected by Pyroscope's HTTP API.
    */
    public final String id;

    PyroscopeAggregationType(String id) {
        this.id = id;
    }
}
