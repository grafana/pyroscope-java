package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.ProfilerDelegate;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 *
 */
public interface ProfilingScheduler {
    /**
     * Use AsyncProfilerDelegate's to start, stop, dumpProfile
     * {@link ProfilerDelegate#start()}
     * {@link ProfilerDelegate#stop()}
     * {@link ProfilerDelegate#dumpProfile(Instant, Instant)}
     * Here is an example of naive implementation
     * <pre>
     * public void start(ProfilerDelegate profiler) {
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
     **/
    void start(@NotNull ProfilerDelegate profiler);

    void stop();
}
