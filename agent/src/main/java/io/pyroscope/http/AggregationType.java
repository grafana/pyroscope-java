package io.pyroscope.http;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum AggregationType {
    SUM ("sum"),
    AVERAGE ("average");

    /**
    * Pyroscope aggregation type id, as expected by Pyroscope's HTTP API.
    */
    public final String id;
}
