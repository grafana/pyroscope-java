package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Snapshot;

public interface Exporter {
    /**
     * PyroscopeAgent expects {@link Exporter#export(Snapshot)} method to be synchronous to profiling schedule, and should return as fast as
     * possible. <br>See QueuedExporter for an asynchronous implementation example.<br>
     * Here is an example of an alternative to {@link io.pyroscope.javaagent.impl.PyroscopeExporter}
     * <pre>
     * class KafkaExporter implements Exporter {
     *     final KafkaProducer&#060;String, String&#062; kafkaProducer;
     *     private MyExporter(KafkaProducer&#060;String, String&#062; producer) {
     *         this.kafkaProducer = producer;
     *     }
     *     &#064;Override
     *     public void export(Snapshot snapshot) {
     *         kafkaProducer.send(new ProducerRecord&#060;&#062;("test.app.jfr", gson.toJson(snapshot)));
     *     }
     * }
     * </pre>
     *
     */
    void export(Snapshot snapshot);

    /**
     * Stop the resources that are held by the exporter like Threads and so on...
     */
    void stop();
}
