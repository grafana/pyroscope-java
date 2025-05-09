import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.config.ProfilerType;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.Pyroscope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    public static final int N_THREADS = 8;

    public static void main(String[] args) {
        PyroscopeAgent.start(
            new PyroscopeAgent.Options.Builder(
                new Config.Builder()
                    .setApplicationName("demo.app{qweqwe=asdasd}")
                    .setServerAddress("http://localhost:4040")
                    .setFormat(Format.JFR)
                    .setProfilingEvent(EventType.CTIMER)
                    .setLogLevel(Logger.Level.DEBUG)
                    .setProfilerType(ProfilerType.JFR)
                    .setLabels(mapOf("user", "tolyan"))
                    .build())
                .build()
        );
        Pyroscope.setStaticLabels(mapOf("region", "us-east-1"));

        appLogic();
    }

    private static void appLogic() {
        ExecutorService executors = Executors.newFixedThreadPool(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            executors.submit(() -> {
                Pyroscope.LabelsWrapper.run(new LabelsSet("thread_name", Thread.currentThread().getName()), () -> {
                        while (true) {
                            try {
                                fib(32L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                );
            });
        }
    }

    private static Map<String, String> mapOf(String... args) {
        Map<String, String> staticLabels = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            staticLabels.put(args[i], args[i + 1]);
        }
        return staticLabels;
    }

    private static long fib(Long n) throws InterruptedException {
        if (n == 0L) {
            return 0L;
        }
        if (n == 1L) {
            return 1L;
        }
        return fib(n - 1) + fib(n - 2);
    }
}
