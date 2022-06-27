package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.DateUtils;

public final class IntervalParser {

    public static long parse(final String str) throws NumberFormatException {
        final long amount;
        final long unit;
        if (str.endsWith("ms")) {
            unit = 1_000_000L;
            amount = Long.parseLong(str.substring(0, str.length() - 2));
        } else if (str.endsWith("us")) {
            unit = 1_000L;
            amount = Long.parseLong(str.substring(0, str.length() - 2));
        } else if (str.endsWith("s")) {
            unit = DateUtils.NANOS_PER_SECOND;
            amount = Long.parseLong(str.substring(0, str.length() - 1));
        } else if (Character.isDigit(str.charAt(str.length() - 1))) {
            unit = 1L;
            amount = Long.parseLong(str);
        } else {
            throw new NumberFormatException("Cannot parse interval " + str);
        }

        if (amount <= 0) {
            throw new NumberFormatException("Interval must be positive, but " + str + " given");
        }

        return amount * unit;
    }
}
