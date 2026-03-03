package io.pyroscope.agent.api;

import java.util.concurrent.atomic.AtomicReference;

public interface IProfilingBridge {
    void setTracingContext(long spanId, String spanName);

    void startProfiling();

    class Holder {
        public static final AtomicReference<IProfilingBridge> INSTANCE = new AtomicReference<>();
    }
}
