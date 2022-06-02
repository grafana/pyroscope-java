import io.pyroscope.labels.Pyroscope;
import io.pyroscope.labels.LabelsSet;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    public static final int N_THREADS = 8;

    public static void main(String[] args) {
        Pyroscope.setStaticLabels(Map.of("region", "us-east-1"));
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

    private static long fib(Long n) throws InterruptedException {
        if (n == 0L) {
            return 0L;
        }
        if (n == 1L) {
            return 1L;
        }
        Thread.sleep(100);
        return fib(n - 1) + fib(n - 2);
    }
}