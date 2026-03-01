package io.pyroscope.javaagent;

import io.pyroscope.PyroscopeAsyncProfiler;
import io.pyroscope.agent.api.ProfilerApi;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.v2.Pyroscope;
import one.profiler.AsyncProfiler;

class ProfilerSdk implements ProfilerApi {

    private final AsyncProfiler asprof;

    ProfilerSdk() {
        this.asprof = PyroscopeAsyncProfiler.getAsyncProfiler();
    }

    @Override
    public void startProfiling() {
        if (!PyroscopeAgent.isStarted()) {
            PyroscopeAgent.start(Config.build());
        }
    }

    @Override
    public void setTracingContext(long spanId, String spanName) {
        long spanNameId = spanName != null ? Pyroscope.LabelsWrapper.registerConstant(spanName) : 0;
        asprof.setTracingContext(spanId, spanNameId);
    }
}
