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
    private final String id;
    public final String units;
    public final String aggregationType;

    EventType(String id) {
        this.id = id;

        // Currently, use the same units and aggregationType for all event types.
        // These properties being defined on EventType presumes that they are solely EventType dependent.
        this.units = "samples";
        this.aggregationType = "sum";
    }

    public static EventType parse(String id) throws IllegalArgumentException {
        return EventType.valueOf(id);
    }

    public String toString() {
        return this.id;
    }
}
