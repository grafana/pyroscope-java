package io.pyroscope.javaagent;

import java.util.Random;

/**
 * Exponential backoff counter implementing the Full Jitter algorithm from
 * <a href=https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
 */
final class ExponentialBackoff {
    private final Random random;

    private final int base;
    private final int cap;

    private int attempt = -1;

    ExponentialBackoff(final int base, final int cap, final Random random) {
        this.base = base;
        this.cap = cap;
        this.random = random;
    }

    final int error() {
        attempt += 1;
        final int multiplier = (int) Math.pow(2, attempt);
        final int upper = Math.min(cap, base * multiplier);
        return random.nextInt(upper);
    }

    final void reset() {
        attempt = -1;
    }
}
