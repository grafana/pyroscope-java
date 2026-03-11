package io.pyroscope.javaagent.api;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-classloader rendezvous point for the Pyroscope profiling API. The bootstrap-api jar
 * is injected into the bootstrap classloader by the OTel extension at startup
 * (via {@link java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch}),
 * making this holder visible to both the extension and application classloaders.
 *
 * <p>The logic of setting this instance:
 * <ol>
 *   <li>{@code PyroscopeAgent#start} sets an instance unconditionally.</li>
 *   <li>OTel extension does not overwrite an already-set instance.</li>
 *   <li>OTel extension sets an instance from the system classloader if possible.</li>
 *   <li>OTel extension sets an instance of the vendored/extension classloader ProfilerSdk
 *       if the system classloader is not available.</li>
 * </ol>
 *
 * <p>Any modification to this class is a <b>breaking change</b> that requires a coordinated
 * release of both pyroscope-java and otel-profiling-java.
 */
public class ProfilerApiHolder {
    public static final AtomicReference<ProfilerApi> INSTANCE = new AtomicReference<>();
}
