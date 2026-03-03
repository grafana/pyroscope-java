package io.pyroscope.javaagent.api;

import java.util.concurrent.atomic.AtomicReference;

public class ProfilerApiHolder {
    public static final AtomicReference<ProfilerApi> INSTANCE = new AtomicReference<>();
}
