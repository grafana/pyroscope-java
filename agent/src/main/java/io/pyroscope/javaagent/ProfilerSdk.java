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

    public ProfilerSdk() {
        this.asprof = PyroscopeAsyncProfiler.getAsyncProfiler();
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
        asprof.setTracingContext(spanId, spanName);
    }

    @Override
    public long registerConstant(String constant) {
        return Pyroscope.LabelsWrapper.registerConstant(constant);
    }

    @Override
    public void setTraceId(@NotNull String traceId) {
        // W3C trace ID is 32 hex chars (128 bits). Split into two longs.
        long hi = Long.parseUnsignedLong(traceId.substring(0, 16), 16);
        long lo = Long.parseUnsignedLong(traceId.substring(16, 32), 16);
        asprof.setTraceId(hi, lo);
    }

    @Override
    public void clearTraceId() {
        asprof.setTraceId(0L, 0L);
    }
}
