package io.pyroscope.javaagent;

import one.profiler.Events;
import io.pyroscope.http.Units;
import io.pyroscope.http.AggregationType;

public enum EventType {
    CPU (Events.CPU, Units.SAMPLES, AggregationType.SUM),
    ALLOC (Events.ALLOC, Units.OBJECTS, AggregationType.SUM),
    LOCK (Events.LOCK, Units.SAMPLES, AggregationType.SUM),
    WALL (Events.WALL, Units.SAMPLES, AggregationType.SUM),
    ITIMER (Events.ITIMER, Units.SAMPLES, AggregationType.SUM);

    /**
    * Event type id, as defined in one.profiler.Events.
    */
    public final String id;

    /**
    * Unit option, as expected by Pyroscope's HTTP API.
    */
    public final Units units;

    /**
    * Aggregation type option, as expected by Pyroscope's HTTP API.
    */
    public final AggregationType aggregationType;

    EventType(String id, Units units, AggregationType aggregationType) {
        this.id = id;
        this.units = units;
        this.aggregationType = aggregationType;
    }

    public static EventType fromId(String id) throws IllegalArgumentException {
        for (EventType it : values()) {
            if (it.id.equals(id)) {
                return it;
            }
        }
        throw new IllegalStateException(id);
    }
}
