package io.pyroscope;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConcurrentUseTest {

    @Test
    public void testConcurrentUsage() throws IOException {
        int iterations = 10;
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            Process process = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"), TestApplication.class.getName()
            ).inheritIO().start();
            processes.add(process);
        }

        processes.parallelStream().forEach(p -> {
            int exitCode = 0;
            try {
                exitCode = p.waitFor();
            } catch (InterruptedException e) {
                Assertions.fail("could not get process status", e);
            }
            if (exitCode != 0) {
                Assertions.fail("process failed with exit code: " + exitCode);
            }
        });
    }
}

