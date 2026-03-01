package io.pyroscope.agent.api;

import java.util.concurrent.atomic.AtomicReference;

public interface IProfilingTracing {
    void setTracingContext(long spanId, String spanName);

    /**
     * Cross-classloader communication channel.
     * The instrumentation advice (inlined into the app classloader) sets the
     * IProfilingTracing instance here, and PyroscopeOtelSpanProcessor (in the extension
     * classloader) reads from it. This works because IProfilingTracing is injected as a
     * helper class into the app classloader by the OTel instrumentation module.
     */
    class Holder {
        public static final AtomicReference<IProfilingTracing> INSTANCE = new AtomicReference<>();
    }
}
