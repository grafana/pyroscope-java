package io.pyroscope.javaagent.api;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public interface ProfilerApi {
    void startProfiling();

    boolean isProfilingStarted();

    @Deprecated
    ProfilerScopedContext createScopedContext(Map<String, String> labels);

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);

    class Holder {
        public static final AtomicReference<ProfilerApi> INSTANCE = new AtomicReference<>();
    }
}
