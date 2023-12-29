package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.AsyncProfilerDelegate;
import io.pyroscope.javaagent.JFRProfilerDelegate;
import io.pyroscope.javaagent.ProfilerDelegate;

import java.lang.reflect.InvocationTargetException;

public enum ProfilerType {
    JFR(JFRProfilerDelegate.class), ASYNC(AsyncProfilerDelegate.class);

    private final Class<? extends ProfilerDelegate> profilerDelegateClass;

    ProfilerType(Class<? extends ProfilerDelegate> profilerDelegateClass) {
        this.profilerDelegateClass = profilerDelegateClass;
    }

    public ProfilerDelegate create(Config config) {
        try {
            return profilerDelegateClass.getConstructor(Config.class).newInstance(config);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
