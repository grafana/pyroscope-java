package io.pyroscope.javaagent;

import one.profiler.Events;

public enum EventType {
    CPU (Events.CPU),
    ALLOC (Events.ALLOC),
    LOCK (Events.LOCK),
    WALL (Events.WALL),
    ITIMER (Events.ITIMER);

    /**
    * Event type id, as defined in one.profiler.Events.
    */
    public final String id;

    /**
    * Unit option, as expected by Pyroscope's HTTP API.
    */
    public final PyroscopeUnits units;

    /**
    * Aggregation type option, as expected by Pyroscope's HTTP API.
    */
    public final PyroscopeAggregationType aggregationType;

    EventType(String id) {
        this.id = id;

        // Currently, use the same units and aggregationType for all event types.
        // These properties being defined on EventType presumes that they are solely EventType dependent.
        this.units = PyroscopeUnits.SAMPLES;
        this.aggregationType = PyroscopeAggregationType.SUM;
    }

    public static EventType parse(String id) throws IllegalArgumentException {
        return EventType.valueOf(id);
    }
}
