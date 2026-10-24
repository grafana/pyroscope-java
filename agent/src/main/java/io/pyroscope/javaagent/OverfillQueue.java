package io.pyroscope.javaagent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A blocking queue with a limited capacity.</p>
 *
 * <p>When the queue is attempted to put a new element and is overfilled, the oldest element is dropped
 * so the capacity limit is preserved.</p>
 *
 * @param <E> the type of elements.
 */
public final class OverfillQueue<E> {
    private final ArrayBlockingQueue<E> innerQueue;
    // Guards innerQueue.
    private final ReentrantLock lock = new ReentrantLock(false);
    private final Condition notEmpty = lock.newCondition();

    public OverfillQueue(final int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1");
        }
        this.innerQueue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so without exceeding the queue's capacity. If not,
     * drops one element from the head of the queue.
     *
     * @return the dropped element, or {@code null} if none was dropped
     */
    public E put(final E element) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            boolean offerSuccessful = innerQueue.offer(element);
            if (offerSuccessful) {
                notEmpty.signal();
                return null;
            } else {
                // Drop one old element to ensure the capacity for the new one.
                E dropped = innerQueue.poll();
                offerSuccessful = innerQueue.offer(element);
                if (offerSuccessful) {
                    notEmpty.signal();
                    return dropped;
                } else {
                    // Doing this as a sanity check.
                    throw new RuntimeException("innerQueue.offer was not successful");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting for the element to become available if needed.
     */
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            E result;
            while ((result = innerQueue.poll()) == null) {
                notEmpty.await();
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes and returns all elements in the queue.
     */
    public List<E> drain() {
        lock.lock();
        try {
            List<E> result = new ArrayList<>(innerQueue.size());
            innerQueue.drainTo(result);
            return result;
        } finally {
            lock.unlock();
        }
    }
}
