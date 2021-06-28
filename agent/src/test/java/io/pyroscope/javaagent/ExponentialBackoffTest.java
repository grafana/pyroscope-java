package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExponentialBackoffTest {
    @Test
    void test() {
        final Random random = mock(Random.class);
        when(random.nextInt(anyInt())).then(invocation -> invocation.getArgument(0));

        final ExponentialBackoff exponentialBackoff = new ExponentialBackoff(1_000, 30_000, random);
        assertEquals(1_000, exponentialBackoff.error());
        assertEquals(2_000, exponentialBackoff.error());
        assertEquals(4_000, exponentialBackoff.error());
        assertEquals(8_000, exponentialBackoff.error());
        assertEquals(16_000, exponentialBackoff.error());
        assertEquals(30_000, exponentialBackoff.error());
        assertEquals(30_000, exponentialBackoff.error());
        exponentialBackoff.reset();
        assertEquals(1_000, exponentialBackoff.error());
        assertEquals(2_000, exponentialBackoff.error());
        assertEquals(4_000, exponentialBackoff.error());
        assertEquals(8_000, exponentialBackoff.error());
        assertEquals(16_000, exponentialBackoff.error());
        assertEquals(30_000, exponentialBackoff.error());
        assertEquals(30_000, exponentialBackoff.error());
    }
}
