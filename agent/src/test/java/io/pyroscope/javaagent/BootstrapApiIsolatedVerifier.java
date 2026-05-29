package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.ProfilerApi;
import io.pyroscope.javaagent.api.ProfilerApiHolder;

/**
 * Loaded through an isolated classloader so {@link ProfilerApiHolder} and {@link ProfilerApi}
 * resolve the same way they would for an external extension classloader.
 */
public class BootstrapApiIsolatedVerifier {

    public static void verify() {
        ProfilerApi api = ProfilerApiHolder.INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException("ProfilerApiHolder is empty");
        }
        if (!api.isProfilingStarted()) {
            throw new IllegalStateException("Profiler API reports profiling not started");
        }
    }
}
