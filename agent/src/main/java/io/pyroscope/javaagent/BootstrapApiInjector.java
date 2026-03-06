package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.impl.DefaultLogger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * Injects the shared bootstrap-api classes (ProfilerApi, ProfilerApiHolder, ProfilerScopedContext)
 * into the bootstrap classloader at agent startup.
 *
 * <p>This ensures that both the agent classloader and any application classloader resolve the same
 * ProfilerApiHolder class with the same static fields, enabling cross-classloader communication
 * (e.g., with the OTel extension).
 *
 * <p>The bootstrap-api JAR is embedded as a resource ({@code pyroscope-bootstrap.jar.bin}) inside
 * pyroscope.jar. It uses a {@code .bin} extension to prevent the shadow jar plugin from
 * merging or relocating its contents.
 *
 * <p>There are two places that perform this injection:
 * <ul>
 *   <li>Here, in the agent premain ({@code PyroscopeAgent.premain})</li>
 *   <li>In the otel-profiling-java extension ({@code io.otel.pyroscope.BootstrapApiInjector})</li>
 * </ul>
 * Whichever runs first injects the classes; the second call is a no-op since the classes
 * are already on the bootstrap classloader.
 */
class BootstrapApiInjector {

    private static final String RESOURCE_NAME = "/pyroscope-bootstrap.jar.bin";

    static void inject(Instrumentation instrumentation) {
        try {
            try (InputStream is = BootstrapApiInjector.class.getResourceAsStream(RESOURCE_NAME)) {
                if (is == null) {
                    DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.WARN,
                        "BootstrapApiInjector: %s not found in resources, skipping bootstrap injection",
                        RESOURCE_NAME);
                    return;
                }
                Path tempJar = Files.createTempFile("pyroscope-bootstrap-", ".jar");
                tempJar.toFile().deleteOnExit();
                Files.copy(is, tempJar, StandardCopyOption.REPLACE_EXISTING);

                instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(tempJar.toFile()));
                DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.DEBUG,
                    "BootstrapApiInjector: Injected API classes into bootstrap classloader");
            }
        } catch (IOException e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.ERROR,
                "BootstrapApiInjector: Failed to inject bootstrap API: %s", e);
        }
    }
}
