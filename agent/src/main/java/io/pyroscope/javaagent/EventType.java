package io.pyroscope.javaagent;

import java.util.EnumSet;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import one.profiler.Events;
import io.pyroscope.http.Units;
import io.pyroscope.http.AggregationType;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
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

    public static EventType fromId(String id) throws IllegalArgumentException {
        Optional<EventType> maybeEventType =
            EnumSet.allOf(EventType.class)
            .stream()
            .filter(eventType -> eventType.id.equals(id))
            .findAny();
        return maybeEventType.orElseThrow(IllegalArgumentException::new);
    }
}
