package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.Pyroscope;
import io.pyroscope.labels.io.pyroscope.PyroscopeAsyncProfiler;
import one.profiler.AsyncProfiler;
import one.profiler.Counter;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;

class Profiler {
    private final Logger logger;
    private final EventType eventType;
    private final String alloc;
    private final String lock;
    private final Duration interval;
    private final Format format;


    private final AsyncProfiler instance = PyroscopeAsyncProfiler.getAsyncProfiler();

    private final File tempJFRFile;

    Profiler(final Logger logger, final EventType eventType, final String alloc, final String lock, final Duration interval, final Format format) {
        this.logger = logger;
        this.alloc = alloc;
        this.lock = lock;
        this.eventType = eventType;
        this.interval = interval;
        this.format = format;

        if (format == Format.JFR) {
            try {
                // flight recorder is built on top of a file descriptor, so we need a file.
                tempJFRFile = File.createTempFile("pyroscope", ".jfr");
                tempJFRFile.deleteOnExit();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            tempJFRFile = null;
        }
    }

    final synchronized void start() {
        if (format == Format.JFR) {
            try {
                instance.execute(createJFRCommand());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            instance.start(eventType.id, interval.toNanos());
        }
    }

    private String createJFRCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("start,event=").append(eventType.id);
        if (alloc != null && !alloc.isEmpty()) {
            sb.append(",alloc=").append(alloc);
        }
        if (lock != null && !lock.isEmpty()) {
            sb.append(",lock=").append(lock);
        }
        sb.append(",interval=").append(interval.toNanos())
            .append(",file=").append(tempJFRFile.toString());
        return sb.toString();
    }

    final synchronized Snapshot dump(Instant profilingIntervalStartTime) {
        instance.stop();

        Snapshot result = dumpImpl(profilingIntervalStartTime);

        start();

        return result;
    }

    private Snapshot dumpImpl(Instant profilingIntervalStartTime) {
        final byte[] data;
        if (format == Format.JFR) {
            data = dumpJFR();
        } else {
            data = instance.dumpCollapsed(Counter.SAMPLES).getBytes(StandardCharsets.UTF_8);
        }
        return new Snapshot(
            eventType,
            profilingIntervalStartTime,
            data,
            Pyroscope.LabelsWrapper.dump()
        );
    }

    private byte[] dumpJFR() {
        try {
            byte[] bytes = new byte[(int) tempJFRFile.length()];
            try (DataInputStream ds = new DataInputStream(new FileInputStream(tempJFRFile))) {
                ds.readFully(bytes);
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
