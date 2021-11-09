package io.pyroscope.http;

public enum AggregationType {
    SUM ("sum"),
    AVERAGE ("average");

    /**
    * Pyroscope aggregation type id, as expected by Pyroscope's HTTP API.
    */
    public final String id;

    AggregationType(String id) {
        this.id = id;
    }
}
