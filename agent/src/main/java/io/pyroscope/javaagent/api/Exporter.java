package io.pyroscope.javaagent.api;

import io.pyroscope.javaagent.Snapshot;

public interface Exporter {
    /**
     * PyroscopeAgent expects `export` method to be synchronous to profiling schedule, and should return as fast as
     * possible. See QueuedExporter for an asynchronous implementation example.
     * Here is an example of an alternative to {@link io.pyroscope.javaagent.impl.PyroscopeExporter}
     * {@code
     * class KafkaExporter implements Exporter {
     *     final KafkaProducer<String, String> kafkaProducer;
     *     private MyExporter(KafkaProducer<String, String> producer) {
     *         this.kafkaProducer = producer;
     *     }
     *     @Override
     *     public void export(Snapshot snapshot) {
     *         kafkaProducer.send(new ProducerRecord<>("test.app.jfr", gson.toJson(snapshot)));
     *     }
     * }
     * }
     *
     */
    void export(Snapshot snapshot);
}
