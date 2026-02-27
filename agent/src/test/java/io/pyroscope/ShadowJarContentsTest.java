package io.pyroscope;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the shadowJar (pyroscope.jar) contains only our own unrelocated classes.
 * All third-party classes must be relocated under io/pyroscope/vendor/.
 */
public class ShadowJarContentsTest {

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
}
