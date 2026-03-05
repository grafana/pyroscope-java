import io.pyroscope.PyroscopeAsyncProfiler
import io.pyroscope.javaagent.EventType
import io.pyroscope.javaagent.PyroscopeAgent
import io.pyroscope.javaagent.Snapshot
import io.pyroscope.javaagent.api.Exporter
import io.pyroscope.javaagent.api.Logger
import io.pyroscope.javaagent.config.Config
import io.pyroscope.javaagent.impl.DefaultLogger
import io.pyroscope.javaagent.impl.PyroscopeExporter
import io.pyroscope.javaagent.impl.QueuedExporter
import io.pyroscope.labels.v2.LabelsSet
import io.pyroscope.labels.v2.Pyroscope
import io.pyroscope.labels.v2.ScopedContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

fun fib(n: Int): Int {
    if (n <= 1) {
        return 1
    }
    return fib(n - 1) + fib(n - 2)
}

fun inf() {
    while (true) {

    }
}

fun main() {
    val constFib = Pyroscope.LabelsWrapper.registerConstant("Fibonachi")
    val constInf = Pyroscope.LabelsWrapper.registerConstant("Infinite")
    println(constFib)
    println(constInf)

    val config = Config.Builder()
        .setApplicationName("spanContextApp")
        .setProfilingEvent(EventType.CTIMER)
        .build()
    val logger = DefaultLogger(Logger.Level.DEBUG, System.out)
    val exporter = QueuedExporter(config, PyroscopeExporter(config, logger), logger)

    val opt = PyroscopeAgent.Options.Builder(config)
        .setLogger(logger)
        .setExporter(Dumper(exporter))
        .build()
    PyroscopeAgent.start(opt)

    val t1 = Thread {
        PyroscopeAsyncProfiler.getAsyncProfiler()
            .setTracingContext(239, constFib);
        ScopedContext(LabelsSet("dyn2", "v2")).use {
            while (true) {
                fib(13)
            }
        }
    }
    val t2 = Thread {
        PyroscopeAsyncProfiler.getAsyncProfiler()
            .setTracingContext(4242, constInf);

        ScopedContext(LabelsSet("dyn1", "v1")).use {
            inf()
        }
    }
    t1.start()
    t2.start()

}

class Dumper(
    val next: Exporter,
) : Exporter {
    private val counter: AtomicLong = AtomicLong()
    override fun export(p0: Snapshot) {
        val no = counter.incrementAndGet()
        File("./profile.${no}.bin").writeBytes(p0.data)
        File("./profile.${no}.labels.bin").writeBytes(p0.labels.toByteArray())
        next.export(p0)
    }

    override fun stop() {
        next.stop()
    }

}