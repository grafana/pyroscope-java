package io.pyroscope.javaagent;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OverfillQueueTest {
    @Test
    public void withoutOverfill1() throws InterruptedException {
        final OverfillQueue<Integer> queue = new OverfillQueue<>(5);
        queue.put(0);
        queue.put(1);
        queue.put(2);
        queue.put(3);
        queue.put(4);

        assertEquals(0, (long)queue.take());
        assertEquals(1, (long)queue.take());
        assertEquals(2, (long)queue.take());
        assertEquals(3, (long)queue.take());
        assertEquals(4, (long)queue.take());
    }

    @Test
    public void withoutOverfill2() throws InterruptedException {
        final OverfillQueue<Integer> queue = new OverfillQueue<>(5);
        queue.put(0);
        queue.put(1);
        queue.put(2);
        queue.put(3);
        queue.put(4);

        queue.take();
        queue.take();
        queue.take();

        queue.put(5);
        queue.put(6);
        queue.put(7);

        assertEquals(3, (long)queue.take());
        assertEquals(4, (long)queue.take());
        assertEquals(5, (long)queue.take());
        assertEquals(6, (long)queue.take());
        assertEquals(7, (long)queue.take());
    }

    @Test
    public void withOverfill() throws InterruptedException {
        final OverfillQueue<Integer> queue = new OverfillQueue<>(5);
        queue.put(0);
        queue.put(1);
        queue.put(2);
        queue.put(3);
        queue.put(4);
        queue.put(5);
        queue.put(6);
        queue.put(7);
        queue.put(8);
        queue.put(9);

        assertEquals(5, (long)queue.take());
        assertEquals(6, (long)queue.take());
        assertEquals(7, (long)queue.take());
        assertEquals(8, (long)queue.take());
        assertEquals(9, (long)queue.take());
    }
}
