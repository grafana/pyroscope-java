package io.pyroscope.javaagent.api;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-classloader rendezvous point for the Pyroscope profiling API. These classes
 * are injected into the bootstrap classloader by the OTel extension at startup
 * (via {@link java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch}),
 * making this holder visible to both the extension and application classloaders.
 *
 * <p>Any modification to this class is a <b>breaking change</b> that requires a coordinated
 * release of both pyroscope-java and otel-profiling-java.
 */
public class ProfilerApiHolder {
    public static final AtomicReference<ProfilerApi> INSTANCE = new AtomicReference<>();
}
