package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.ProfilerScopedContext;
import io.pyroscope.javaagent.api.ProfilerApi;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.ProfilerScopedContextWrapper;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.ScopedContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ProfilerSdk implements ProfilerApi {

    @Override
    public void startProfiling() {
        PyroscopeAgent.start(Config.build());
    }

    @Override
    public boolean isProfilingStarted() {
        return PyroscopeAgent.isStarted();
    }

    @Override
    @NotNull
    public ProfilerScopedContext createScopedContext(@NotNull Map<@NotNull String, @NotNull String> labels) {
        return new ProfilerScopedContextWrapper(new ScopedContext(new LabelsSet(labels)));
    }
}
