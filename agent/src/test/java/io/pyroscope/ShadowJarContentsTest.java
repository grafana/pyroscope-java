package io.pyroscope;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the shadowJar (pyroscope.jar) contains only our own unrelocated classes.
 * All third-party classes must be relocated under io/pyroscope/vendor/.
 */
public class ShadowJarContentsTest {

    @Test
    void shadedTransportDoesNotReferenceUnsafe() throws Exception {
        String jarPath = System.getProperty("shadowJar.path");
        assertNotNull(jarPath, "Run this test via Gradle");
        int transportClasses = 0;
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertFalse(name.startsWith("io/pyroscope/vendor/io/netty/"), name);
                if (!name.startsWith("io/pyroscope/vendor/org/apache/http/") || !name.endsWith(".class")) {
                    continue;
                }
                transportClasses++;
                try (InputStream input = jar.getInputStream(entry)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    // Class constants include both bytecode references and reflective class names.
                    String constants = new String(output.toByteArray(), StandardCharsets.ISO_8859_1)
                        .replace('.', '/');
                    assertFalse(constants.contains("sun/misc/Unsafe"), name);
                    assertFalse(constants.contains("jdk/internal/misc/Unsafe"), name);
                }
            }
        }
        assertTrue(transportClasses > 0, "The packaged transport must be inspected");
    }

    @Test
    void pullProfilerUsesShadedTransportAndCanEncodeFromTheShadedJar() throws Exception {
        String jarPath = System.getProperty("shadowJar.path");
        assertNotNull(jarPath, "Run this test via Gradle");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(jarPath).toURI().toURL()},
            ClassLoader.getSystemClassLoader().getParent())) {
            Class<?> scheduler = loader.loadClass("io.pyroscope.javaagent.impl.PullProfilingScheduler");
            assertEquals("io.pyroscope.vendor.org.apache.http.impl.nio.reactor.DefaultListeningIOReactor",
                scheduler.getDeclaredField("server").getType().getName());
            Class<?> configClass = loader.loadClass("io.pyroscope.javaagent.config.Config");
            Class<?> builderClass = loader.loadClass("io.pyroscope.javaagent.config.Config$Builder");
            Class<?> modeClass = loader.loadClass("io.pyroscope.javaagent.config.ProfilingMode");
            Class<?> formatClass = loader.loadClass("io.pyroscope.http.Format");
            Object builder = builderClass.getConstructor().newInstance();
            builderClass.getMethod("setProfilingMode", modeClass).invoke(builder, modeClass.getField("PULL").get(null));
            builderClass.getMethod("setFormat", formatClass).invoke(builder, formatClass.getField("PPROF").get(null));
            builderClass.getMethod("setPullPort", int.class).invoke(builder, 0);
            Object config = builderClass.getMethod("build").invoke(builder);
            Class<?> loggerClass = loader.loadClass("io.pyroscope.javaagent.api.Logger");
            Class<?> profilerClass = loader.loadClass("io.pyroscope.javaagent.ProfilerDelegate");
            Object logger = Proxy.newProxyInstance(loader, new Class<?>[]{loggerClass}, (proxy, method, args) -> null);
            Object profiler = Proxy.newProxyInstance(loader, new Class<?>[]{profilerClass}, (proxy, method, args) -> {
                throw new AssertionError("Starting the listener must not start profiling");
            });
            Object listener = scheduler.getConstructor(configClass, loggerClass).newInstance(config, logger);
            try {
                scheduler.getMethod("start", profilerClass).invoke(listener, profiler);
                assertTrue(((InetSocketAddress) scheduler.getMethod("getAddress").invoke(listener)).getPort() > 0);
            } finally {
                scheduler.getMethod("stop").invoke(listener);
            }
            Class<?> encoder = loader.loadClass("io.pyroscope.javaagent.impl.PprofEncoder");
            byte[] profile = (byte[]) encoder.getMethod("encode", String.class, Instant.class,
                Instant.class, long.class, Map.class).invoke(null, "root;leaf 1\n", Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(1), 10_000_000L, Collections.emptyMap());
            assertEquals(0x1f, profile[0] & 0xff);
            assertEquals(0x8b, profile[1] & 0xff);
        }
    }

    @Test
    void onlyPyroscopeClassesAreUnrelocated() throws Exception {
        String jarPath = System.getProperty("shadowJar.path");
        if (jarPath == null || jarPath.isEmpty()) {
            fail("System property 'shadowJar.path' is not set. Run this test via Gradle.");
        }

        File jarFile = new File(jarPath);
        assertTrue(jarFile.exists(), "shadowJar not found at: " + jarPath);

        List<String> violations = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class")) {
                    continue;
                }

                // Skip module-info descriptors (multi-release jar metadata)
                if (name.endsWith("module-info.class")) {
                    continue;
                }

                // Multi-release JARs store versioned classes under META-INF/versions/<N>/
                // Strip that prefix before checking the package
                String effectiveName = name.replaceFirst("^META-INF/versions/\\d+/", "");

                // All class files must be under io/pyroscope/
                if (!effectiveName.startsWith("io/pyroscope/")) {
                    violations.add(name);
                }
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(violations.size()).append(" unrelocated non-pyroscope class(es) in shadowJar:\n");
            for (String v : violations) {
                sb.append("  ").append(v).append("\n");
            }
            fail(sb.toString());
        }
    }

    @Test
    void containsBootstrapApiResource() throws Exception {
        String jarPath = System.getProperty("shadowJar.path");
        if (jarPath == null || jarPath.isEmpty()) {
            fail("System property 'shadowJar.path' is not set. Run this test via Gradle.");
        }

        File jarFile = new File(jarPath);
        assertTrue(jarFile.exists(), "shadowJar not found at: " + jarPath);

        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("pyroscope-bootstrap.jar.bin");
            assertNotNull(entry, "pyroscope-bootstrap.jar.bin resource not found in shadow jar");
            assertTrue(entry.getSize() > 0, "pyroscope-bootstrap.jar.bin is empty");
        }
    }

    @Test
    void bootstrapApiClassesInShadowJar() throws Exception {
        String jarPath = System.getProperty("shadowJar.path");
        if (jarPath == null || jarPath.isEmpty()) {
            fail("System property 'shadowJar.path' is not set. Run this test via Gradle.");
        }

        File jarFile = new File(jarPath);
        assertTrue(jarFile.exists(), "shadowJar not found at: " + jarPath);

        String[] bootstrapClasses = {
            "io/pyroscope/javaagent/api/ProfilerApi.class",
            "io/pyroscope/javaagent/api/ProfilerApiHolder.class",
            "io/pyroscope/javaagent/api/ProfilerScopedContext.class"
        };

        try (JarFile jar = new JarFile(jarFile)) {
            for (String className : bootstrapClasses) {
                assertNotNull(jar.getJarEntry(className),
                    "Bootstrap-api class should be in shadow jar: " + className);
            }
        }
    }
}
