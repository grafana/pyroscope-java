package io.pyroscope.javaagent;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Probe entrypoint for the forked-JVM regression test.
 *
 * <p>The process is started with {@code -javaagent:pyroscope.jar} and then loads a verifier
 * class through a fresh classloader whose parent is bootstrap-only. That mirrors the OTel
 * extension case more closely than using the application classloader directly: the verifier
 * resolves bootstrap-injected API classes via parent delegation, while the agent classes still
 * come from the regular application classpath.
 */
public class BootstrapApiClassloaderProbe {

    public static void main(String[] args) throws Exception {
        try {
            try (URLClassLoader isolatedLoader = new URLClassLoader(classPathUrls(), null)) {
                Class<?> verifier = Class.forName(BootstrapApiIsolatedVerifier.class.getName(), true, isolatedLoader);
                verifier.getMethod("verify").invoke(null);
            }
            System.out.println("bootstrap-api-ok");
        } finally {
            PyroscopeAgent.stop();
        }
    }

    private static URL[] classPathUrls() throws Exception {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i]).toURI().toURL();
        }
        return urls;
    }
}
