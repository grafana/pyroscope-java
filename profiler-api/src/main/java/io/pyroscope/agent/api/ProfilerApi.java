package io.pyroscope.agent.api;

import java.util.concurrent.atomic.AtomicReference;

public interface ProfilerApi {
    void startProfiling();

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);

    /**
     * Cross-classloader communication channel.
     * The instrumentation advice (inlined into the app classloader) sets the
     * ProfilerApi instance here, and PyroscopeOtelSpanProcessor (in the extension
     * classloader) reads from it. This works because ProfilerApi is injected as a
     * helper class into the app classloader by the OTel instrumentation module.
     */
    class Holder {
        public static final AtomicReference<ProfilerApi> INSTANCE = new AtomicReference<>();
    }
}
