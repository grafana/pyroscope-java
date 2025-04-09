package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.AsyncProfilerDelegate;
import io.pyroscope.javaagent.JFRProfilerDelegate;
import io.pyroscope.javaagent.ProfilerDelegate;

import java.util.function.Function;

public enum ProfilerType {
    /**
     * JFR profiler type.
     * <p>
     * NOTE: This is an experimental feature and is subject to API changes or may be removed in future releases.
     */
    JFR(JFRProfilerDelegate::new),
    ASYNC(AsyncProfilerDelegate::new);

    private final Function<Config, ProfilerDelegate> profilerDelegateFactory;

    ProfilerType(Function<Config, ProfilerDelegate> profilerDelegateFactory) {
        this.profilerDelegateFactory = profilerDelegateFactory;
    }

    public ProfilerDelegate create(Config config) {
        return profilerDelegateFactory.apply(config);
    }
}
