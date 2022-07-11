package io.pyroscope.javaagent;


import java.util.Calendar;

public class DateUtils {
    public static final long NANOS_PER_SECOND = 1_000_000_000L;
    static final long SECONDS_PER_DAY = 86400;

    public static long truncate(long it, long unitDur) {
        long dur = unitDur;
        long seconds = it / NANOS_PER_SECOND;
        long nanos = it % NANOS_PER_SECOND;
        long nod = (seconds % SECONDS_PER_DAY) * NANOS_PER_SECOND + nanos;
        long result = (nod / dur) * dur;
        long res = it + result - nod;
        return res;
    }
}
