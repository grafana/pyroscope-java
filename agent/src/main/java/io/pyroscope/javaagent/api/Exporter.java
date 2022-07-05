package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Snapshot;

public interface Exporter {
    /**
     * PyroscopeAgent expects {@link Exporter#export(Snapshot)} method to be synchronous to profiling schedule, and should return as fast as
     * possible. <br>See QueuedExporter for an asynchronous implementation example.<br>
     * Here is an example of an alternative to {@link io.pyroscope.javaagent.impl.PyroscopeExporter}
     * <pre>
     * class KafkaExporter implements Exporter {
     *     final KafkaProducer<String, String> kafkaProducer;
     *     private MyExporter(KafkaProducer<String, String> producer) {
     *         this.kafkaProducer = producer;
     *     }
     *     &#064;Override
     *     public void export(Snapshot snapshot) {
     *         kafkaProducer.send(new ProducerRecord<>("test.app.jfr", gson.toJson(snapshot)));
     *     }
     * }
     * </pre>
     *
     */
    void export(Snapshot snapshot);
}
