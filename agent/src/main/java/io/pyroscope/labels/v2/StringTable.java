package io.pyroscope.labels.v2;

import java.util.Arrays;
import java.util.Map;

/**
 * Interns strings to dense 1-based ids, and hands them back by id.
 *
 * <p>Both directions are needed: {@link #intern(String)} while walking labels, and
 * {@link #get(int)} in ascending id order to emit the string table. So this is a dense
 * {@code String[]} indexed by id, plus an open-addressed {@code int[]} of ids used as a lookup
 * index over it. The index stores only the id and recovers the key through the dense array, which
 * halves its memory and avoids the per-entry node and boxed value a {@code HashMap<String, Integer>}
 * would allocate — at a few million strings per dump, that was the dominant cost.
 *
 * <p>Not thread safe.
 */
final class StringTable {

    private static final int MIN_TABLE_SIZE = 64;
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    /** strings[id - 1]; dense apart from holes left by seeded constant ids. */
    private String[] strings;
    /** Highest assigned id. */
    private int count;
    /** Open-addressed ids, 0 means empty. Always a power of two. */
    private int[] slots;
    private int mask;
    private int threshold;

    StringTable(int capacity) {
        this.strings = new String[Math.max(16, capacity)];
        setTableSize(tableSizeFor(capacity));
    }

    /** Highest assigned id, so ids to emit are {@code 1..size()}. */
    int size() {
        return count;
    }

    /** The string with this id, or null for an id that was seeded over but never used. */
    String get(int id) {
        return strings[id - 1];
    }

    /**
     * Pre-assigns the ids of globally registered constants, which must keep the ids they were
     * handed out under. Ids come from the map rather than being reassigned, and the next id
     * continues past the highest one seen — seeding by maximum rather than by count, so a caller
     * observing a partially updated constants map cannot make us hand out a live id again.
     */
    void seed(Map<String, Long> constants) {
        if (constants.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> it : constants.entrySet()) {
            Long boxed = it.getValue();
            if (boxed == null) {
                continue;
            }
            long id = boxed;
            if (id < 1 || id > MAX_CAPACITY) {
                continue;
            }
            int i = (int) id;
            growStrings(i);
            strings[i - 1] = it.getKey();
            if (i > count) {
                count = i;
            }
        }
        // Rebuild the index in one pass rather than inserting the constants one at a time, but
        // keep the size the constructor was asked for: sizing down to fit the constants alone
        // would throw away the caller's hint and make the next few hundred thousand interns
        // re-grow the index from scratch.
        int size = Math.max(slots.length, tableSizeFor(count));
        if (size == slots.length) {
            Arrays.fill(slots, 0);
        } else {
            setTableSize(size);
        }
        // Rebuilding from strings[] keeps this correct even if something was interned first.
        reindex();
    }

    /** Interns {@code s}, returning its 1-based id. */
    int intern(String s) {
        int h = s.hashCode();
        h ^= h >>> 16;
        int i = h & mask;
        for (; ; i = (i + 1) & mask) {
            int id = slots[i];
            if (id == 0) {
                int newId = ++count;
                growStrings(newId);
                strings[newId - 1] = s;
                slots[i] = newId;
                if (count >= threshold) {
                    setTableSize(slots.length << 1);
                    reindex();
                }
                return newId;
            }
            String cur = strings[id - 1];
            // Identity first: keys and values come out of the same LabelsSet array, so the same
            // String object recurs for every repeated label key.
            if (cur == s || cur.equals(s)) {
                return id;
            }
        }
    }

    private void growStrings(int minLength) {
        if (minLength <= strings.length) {
            return;
        }
        long next = (long) strings.length * 2;
        strings = Arrays.copyOf(strings, (int) Math.min(MAX_CAPACITY, Math.max(minLength, next)));
    }

    private void setTableSize(int size) {
        slots = new int[size];
        mask = size - 1;
        threshold = size - (size >> 2);
    }

    /** Reinserts every assigned id into a freshly sized {@link #slots}. */
    private void reindex() {
        int[] slots = this.slots;
        int mask = this.mask;
        for (int id = 1; id <= count; id++) {
            String s = strings[id - 1];
            if (s == null) {
                continue;
            }
            int h = s.hashCode();
            h ^= h >>> 16;
            int i = h & mask;
            while (slots[i] != 0) {
                i = (i + 1) & mask;
            }
            slots[i] = id;
        }
    }

    private static int tableSizeFor(int entries) {
        long min = Math.max(MIN_TABLE_SIZE, (long) entries + (entries >> 1) + 1);
        int size = MIN_TABLE_SIZE;
        while (size < min && size < (1 << 30)) {
            size <<= 1;
        }
        return size;
    }
}
