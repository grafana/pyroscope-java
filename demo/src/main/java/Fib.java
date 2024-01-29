
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Fib {
    public static final int N_THREADS;

    static {
        int n;
        try {
            n = Integer.parseInt(System.getenv("N_THREADS"));
        } catch (NumberFormatException e) {
            n = 1;
        }
        N_THREADS = n;
    }

    public static void main(String[] args) {
        appLogic();
    }

    private static void appLogic() {
        ExecutorService executors = Executors.newFixedThreadPool(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            executors.submit(() -> {
                while (true) {
                    try {
                        fib(32L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
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
        return fib(n - 1) + fib(n - 2);
    }

}
