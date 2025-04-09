package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.DefaultLogger;

import java.time.Instant;

import static java.lang.String.format;

/**
 * This is a JFR profiler delegate, which checks JVM version and registers proper delegate implementation.
 * <p>
 * NOTE: This is an experimental feature and is subject to API changes or may be removed in future releases.
 */
public final class JFRProfilerDelegate implements ProfilerDelegate {

    private ProfilerDelegate delegate;

    public JFRProfilerDelegate(Config config) {
        String javaVersion = System.getProperty("java.version");
        if (javaVersion.startsWith("1.8")) {
            delegate = new JFRJCMDProfilerDelegate(config);
        } else {
            delegate = new JFRJDKProfilerDelegate(config);
        }
    }

    @Override
    public void setConfig(final Config config) {
        delegate.setConfig(config);
    }

    /**
     * Start JFR profiler
     */
    @Override
    public synchronized void start() {
        delegate.start();
    }

    /**
     * Stop JFR profiler
     */
    @Override
    public synchronized void stop() {
        delegate.stop();
    }

    /**
     * @param started - time when profiling has been started
     * @param ended   - time when profiling has ended
     * @return Profiling data and dynamic labels as {@link Snapshot}
     */
    @Override
    public synchronized Snapshot dumpProfile(Instant started, Instant ended) {
        return delegate.dumpProfile(started, ended);
    }

}
