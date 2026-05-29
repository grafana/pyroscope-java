package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilerApi;
import io.pyroscope.javaagent.api.ProfilerApiHolder;

/**
 * Publishes the {@link ProfilerSdk} instance into the shared {@link ProfilerApiHolder}.
 *
 * This class is intentionally separate from {@link PyroscopeAgent} to avoid placing
 * {@code ProfilerApi} in {@code PyroscopeAgent.class}'s constant pool. If {@code ProfilerApi}
 * were referenced directly from {@code PyroscopeAgent}, the JVM verifier would resolve it
 * from the app classloader when loading {@code PyroscopeAgent} for {@code premain} — before
 * {@link BootstrapApiInjector} has a chance to inject the bootstrap-api JAR. That causes a
 * classloader split where {@code ProfilerSdk} implements the app-CL copy of {@code ProfilerApi}
 * while the OTel extension sees the bootstrap-CL copy, resulting in a {@code ClassCastException}.
 */
class ProfilerApiPublisher {

    static void publish(Logger logger) {
        try {
            ProfilerApi api = new ProfilerSdk();
            ProfilerApi existing = ProfilerApiHolder.INSTANCE.get();
            if (existing != null && existing.isProfilingStarted()
                    && existing.getClass().getClassLoader() != api.getClass().getClassLoader()) {
                logger.log(Logger.Level.ERROR,
                        "Another ProfilerApi instance is already active from a different classloader. " +
                        "Starting profiling from multiple classloaders is not supported and may produce incorrect results. " +
                        "See https://github.com/grafana/otel-profiling-java/issues/76");
            }
            ProfilerApiHolder.INSTANCE.set(api);
            logger.log(Logger.Level.DEBUG, "published profiler sdk");
        } catch (Throwable th) {
            logger.log(Logger.Level.DEBUG, "publish profiler failed %s", th);
        }
    }
}
