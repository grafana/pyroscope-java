package io.pyroscope.javaagent.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public final class IntervalParser {
    public static Duration parse(final String str) throws NumberFormatException {
        final long amount;
        final TemporalUnit unit;
        if (str.endsWith("ms")) {
            unit = ChronoUnit.MILLIS;
            amount = Long.parseLong(str.substring(0, str.length() - 2));
        } else if (str.endsWith("us")) {
            unit = ChronoUnit.MICROS;
            amount = Long.parseLong(str.substring(0, str.length() - 2));
        } else if (str.endsWith("s")) {
            unit = ChronoUnit.SECONDS;
            amount = Long.parseLong(str.substring(0, str.length() - 1));
        } else if (Character.isDigit(str.charAt(str.length() - 1))) {
            unit = ChronoUnit.NANOS;
            amount = Long.parseLong(str);
        } else {
            throw new NumberFormatException("Cannot parse interval " + str);
        }

        if (amount <= 0) {
            throw new NumberFormatException("Interval must be positive, but " + str + " given");
        }

        return Duration.of(amount, unit);
    }
}
