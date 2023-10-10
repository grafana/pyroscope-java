package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Profiler;

import java.time.Instant;

/**
 *
 */
public interface ProfilingScheduler {
    /**
     * Use Profiler's to start, stop, dumpProfile
     * {@link Profiler#start()}
     * {@link Profiler#stop()}
     * {@link Profiler#dumpProfile(Instant, Instant)}
     * Here is an example of naive implementation
     * <pre>
     * public void start(Profiler profiler) {
     *      new Thread(() -&#062; {
     *          while (true) {
     *              Instant startTime = Instant.now();
     *              profiler.start();
     *              sleep(10);
     *              profiler.stop();
     *              exporter.export(
     *                  profiler.dumpProfile(startTime)
     *              );
     *              sleep(50);
     *          }
     *      }).start();
     *  }
     * </pre>
     * The real-world example will be more complex since profile start and stop time should be aligned to 10s intervals
     * See {@link io.pyroscope.javaagent.impl.ContinuousProfilingScheduler} and <a href="https://github.com/pyroscope-io/pyroscope-java/issues/40">
     *     Github issue #40</a> for more details.
     *
     **/
    void start(Profiler profiler);


    /**
     * This method exposes the stop method
     * Use Profiler's to start, stop, dumpProfile
     * {@link Profiler#start()}
     * {@link Profiler#stop()}
     * {@link Profiler#dumpProfile(Instant, Instant)}
     **/
    void stop(Profiler profiler);
}
