package io.pyroscope.agent.api;

public interface ProfilerApi {
    void startProfiling();

    void setTracingContext(long spanId, long spanName);

    long registerConstant(String constant);
}
