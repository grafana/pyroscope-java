package io.pyroscope.javaagent;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;

class DateUtils {
    static final long NANOS_PER_SECOND = 1_000_000_000L;
    static final long SECONDS_PER_DAY = 86400;

    /**
     * copy-paste from java.time.Instant#truncatedTo(java.time.temporal.TemporalUnit)
     * to support Duration instead of TemporalUnit
     */
    public static Instant truncatedTo(Instant thiz, Duration unitDur) {
        long dur = unitDur.toNanos();
        long seconds = thiz.getLong(ChronoField.INSTANT_SECONDS);
        long nanos = thiz.getLong(ChronoField.NANO_OF_SECOND);
        long nod = (seconds % SECONDS_PER_DAY) * NANOS_PER_SECOND + nanos;
        long result = (nod / dur) * dur;
        return thiz.plusNanos(result - nod);
    }
}
