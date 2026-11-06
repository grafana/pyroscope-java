package io.pyroscope.javaagent.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;

/** Writes a deterministic profile for validation by an independent pprof implementation. */
public final class PprofCompatibilityFixture {
    private PprofCompatibilityFixture() {}

    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args[0]);
        Files.createDirectories(output.getParent());
        Files.write(output, PprofEncoder.encode(
            "root;shared;leaf 3\nroot;shared 2\n",
            Instant.ofEpochSecond(100), Instant.ofEpochSecond(102),
            10_000_000, Collections.singletonMap("region", "west")));
    }
}
