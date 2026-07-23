package io.pyroscope.javaagent;

import io.pyroscope.PyroscopeAsyncProfiler;
import io.pyroscope.javaagent.api.ProfilerScopedContext;
import io.pyroscope.javaagent.api.ProfilerApi;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.ProfilerScopedContextWrapper;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.Pyroscope;
import io.pyroscope.labels.v2.ScopedContext;
import one.profiler.AsyncProfiler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ProfilerSdk implements ProfilerApi {

    private final AsyncProfiler asprof;
    private final boolean tracingContextSupported;
    private final boolean traceIdSupported;

    public ProfilerSdk() {
        this.asprof = PyroscopeAsyncProfiler.getAsyncProfiler();
        this.tracingContextSupported = PyroscopeAsyncProfiler.isTracingContextSupported();
        this.traceIdSupported = PyroscopeAsyncProfiler.isTraceIdSupported();
    }
    @Override
    public void startProfiling() {
        PyroscopeAgent.start(Config.build());
    }

    @Override
    public boolean isProfilingStarted() {
        return PyroscopeAgent.isStarted();
    }

    @Deprecated
    @Override
    @NotNull
    public ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels) {
        return new ProfilerScopedContextWrapper(new ScopedContext(new LabelsSet(labels)));
    }

    @Override
    public void setTracingContext(long spanId, long spanName) {
        if (tracingContextSupported) {
            asprof.setTracingContext(spanId, spanName);
        }
    }

    @Override
    public long registerConstant(String constant) {
        return Pyroscope.LabelsWrapper.registerConstant(constant);
    }

    @Override
    public void setTraceId(@NotNull String traceId) {
        // W3C trace ID is 32 hex chars (128 bits). Parse directly into two longs
        // to avoid the String#substring allocations on this hot path.
        if (traceId.length() != 32) {
            throw new NumberFormatException("trace_id must be 32 hex chars, got length " + traceId.length());
        }
        long hi = parseHex64(traceId, 0);
        long lo = parseHex64(traceId, 16);
        if (traceIdSupported) {
            asprof.setTraceId(hi, lo);
        }
    }

    @Override
    public void clearTraceId() {
        if (traceIdSupported) {
            asprof.setTraceId(0L, 0L);
        }
    }

    static long parseHex64(String s, int offset) {
        long result = 0L;
        for (int i = 0; i < 16; i++) {
            int c = s.charAt(offset + i);
            int nibble;
            if (c >= '0' && c <= '9') {
                nibble = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                nibble = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                nibble = c - 'A' + 10;
            } else {
                throw new NumberFormatException("invalid hex char in trace_id at index " + (offset + i));
            }
            result = (result << 4) | nibble;
        }
        return result;
    }
}
