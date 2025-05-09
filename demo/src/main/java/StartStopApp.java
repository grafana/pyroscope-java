import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.Pyroscope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StartStopApp {
    public static final int N_THREADS = 2;
    public static final Config CONFIG = new Config.Builder()
        .setApplicationName("demo.app{qweqwe=asdasd}")
        .setServerAddress("http://localhost:4040")
        .setFormat(Format.JFR)
        .setProfilingEvent(EventType.ITIMER)
        .setLogLevel(Logger.Level.DEBUG)
        .setLabels(mapOf("user", "tolyan"))
        .build();
    public static final PyroscopeAgent.Options OPTIONS = new PyroscopeAgent.Options.Builder(CONFIG)
        .build();

    public static final int RUN_TIME = 30000;
    public static final int SLEEP_TIME = 30000;

    public static void main(String[] args) {


        appLogic();

        while (true) {
            Pyroscope.setStaticLabels(mapOf("region", "us-east-1"));
            PyroscopeAgent.start(OPTIONS);

            System.out.println(">>> running for " + RUN_TIME);
            try {
                Thread.sleep(RUN_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            PyroscopeAgent.stop();

            System.out.println(">>> sleeping for " + RUN_TIME);
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
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
