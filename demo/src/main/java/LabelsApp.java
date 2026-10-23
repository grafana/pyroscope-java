import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.v2.ConstantContext;
import io.pyroscope.labels.v2.LabelsSet;
import io.pyroscope.labels.v2.Pyroscope;

import java.util.Collections;

/**
 * Labelled workload for the integration test in itest/labels_test.go.
 *
 * <p>Unlike {@link Fib}, every busy thread here runs inside a labelled context, so the profiles this
 * app uploads exercise the whole labels pipeline end to end: the encoded LabelsSnapshot in the
 * multipart {@code labels} part, and the static labels folded into the ingest URL.
 *
 * <p>Each workload has its own recursive method so that the collapsed flamegraph of one label value
 * is distinguishable from the others. The test asserts both that each value selects its own stacks
 * and that it does not select anybody else's.
 */
public class LabelsApp {

    private static final long FIB_N = 25L;
    private static final long IDLE_MILLIS = 2L;

    public static void main(String[] args) {
        // Seed the constant string table before the first dump(). LabelsWrapper.dump() pre-populates
        // its string table from the registered constants and derives the ids of the dynamic label
        // strings from its size, so a non-empty constant table is what makes the per-workload
        // assertions in the test sensitive to string id collisions between the two.
        Pyroscope.LabelsWrapper.registerConstant("alpha_const");
        Pyroscope.LabelsWrapper.registerConstant("beta_const");

        // Must happen before the agent starts: PyroscopeExporter folds the static labels into the
        // ingest URL once, in its constructor. The container therefore runs with
        // PYROSCOPE_AGENT_ENABLED=false, so premain injects the bootstrap api but stops short of
        // building the exporter, and this app starts the agent itself below.
        Pyroscope.setStaticLabels(Collections.singletonMap("static_label", "static_value"));

        PyroscopeAgent.start(new Config.Builder(Config.build())
            .setAgentEnabled(true)
            .build());

        // Everything below has to run after the agent started: ScopedContext silently falls back to
        // context id 0 - dropping the labels - while ScopedContext.ENABLED is false.
        final Runnable alpha = () -> {
            while (true) {
                fibAlpha(FIB_N);
                idle();
            }
        };
        final Runnable beta = () -> {
            while (true) {
                fibBeta(FIB_N);
                idle();
            }
        };

        // Scoped contexts: labels are dumped from ScopedContext.CONTEXTS.
        spawn("labels-alpha", () -> Pyroscope.LabelsWrapper.run(new LabelsSet("workload", "alpha"), alpha));
        spawn("labels-beta", () -> Pyroscope.LabelsWrapper.run(new LabelsSet("workload", "beta"), beta));

        // Constant context: labels are dumped from ScopedContext.CONSTANT_CONTEXTS instead, and the
        // context id is never released. activate() has to run on the profiled thread, the
        // async-profiler context id is thread local.
        final ConstantContext gamma = ConstantContext.of(new LabelsSet("workload", "gamma"));
        spawn("labels-gamma", () -> {
            gamma.activate();
            while (true) {
                fibGamma(FIB_N);
                idle();
            }
        });
    }

    private static void spawn(String name, Runnable body) {
        // Non daemon on purpose: main() returns immediately and these threads keep the JVM alive.
        new Thread(body, name).start();
    }

    /**
     * Leaves some cpu for the other workloads and for the pyroscope container, which shares the two
     * cores of a CI runner with this app.
     */
    private static void idle() {
        try {
            Thread.sleep(IDLE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Three copies on purpose: the test tells the workloads apart by their frame names.

    private static long fibAlpha(long n) {
        if (n < 2L) {
            return n;
        }
        return fibAlpha(n - 1) + fibAlpha(n - 2);
    }

    private static long fibBeta(long n) {
        if (n < 2L) {
            return n;
        }
        return fibBeta(n - 1) + fibBeta(n - 2);
    }

    private static long fibGamma(long n) {
        if (n < 2L) {
            return n;
        }
        return fibGamma(n - 1) + fibGamma(n - 2);
    }
}
